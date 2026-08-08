package com.ewaf;

import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import jakarta.servlet.ServletContext;
import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.Enumeration;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ProxyServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(ProxyServlet.class);

    private DataSource ds;
    private HttpClient httpClient;

    @Override
    public void init() throws ServletException {
        ServletContext context = getServletContext();
        this.ds = (DataSource) context.getAttribute("dbDataSource");

        if (this.ds == null) {
            log.error("Critical failure: DataSource not found in context for eWAF ProxyServlet.");
            throw new ServletException("Critical failure: DataSource not found in context for eWAF ProxyServlet.");
        }
        log.info("eWAF ProxyServlet HTTP Engine initialized successfully.");
        // Cliente HTTP optimizado para reutilizar conexiones
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String pathInfo = req.getPathInfo();

        if (pathInfo == null || pathInfo.isEmpty()) {
            resp.sendError(404, "Ruta no especificada");
            return;
        }

        // 1. OBTENER CONFIGURACIÓN (URL + JSON)
        RouteConfig routeConfig = resolveTarget(pathInfo);
        if (routeConfig == null) {
            resp.sendError(404, "Aplicación no encontrada en eWAF Routes");
            return;
        }

        final String targetBaseUrl = routeConfig.targetUrl;
        final String customReplacements = routeConfig.replacementsJson; // Reglas específicas de la DB
        final String engineType = routeConfig.engineType; // El motor elegido por el administrador
        final int cacheTtl = routeConfig.cacheTtlSeconds; // Tiempo de caché
        final boolean useModSecurity = routeConfig.useModSecurity; // Estado del WAF

        // Detectar si venimos por Subdominio o por Ruta
        String routingMode = (String) req.getAttribute("ewaf.routing.mode");
        boolean isSubdomainMode = "SUBDOMAIN".equals(routingMode);

        String incomingMap = getMappedPath(pathInfo); // ej: "/github"
        String remainingPath = pathInfo.substring(incomingMap.length());

        // SUBDOMINIO: Si estamos en "github.localhost", la base visual es simplemente "/"
        // Si estamos en "localhost/github", la base visual es "/github"
        String proxyAppRoot = isSubdomainMode ? req.getContextPath() : req.getContextPath() + incomingMap;

        // =========================================================================
        // EL ORQUESTADOR DE MOTORES (SMART HYBRID CONTROL PLANE)
        // =========================================================================

        // Identificamos si es un recurso estático pesado.
        // Las imágenes, fuentes y videos no necesitan reescritura de código fuente (rewriteContent).
        boolean isHeavyAsset = remainingPath.matches(".*\\.(png|jpg|jpeg|gif|webp|svg|ico|woff|woff2|ttf|eot|mp3|mp4|webm|pdf|zip|tar|gz)(\\?.*)?");

        if (!"NATIVE".equals(engineType)) {
            if (isHeavyAsset) {
                // Si es un archivo pesado, delegamos toda la carga y memoria al motor seleccionado (NGINX/SPRING)
                if ("NGINX".equals(engineType)) {
                    // Elegimos el pasillo secreto según la base de datos
                    String nginxInternalPath = useModSecurity ? "/internal-nginx-proxy-modsec" : "/internal-nginx-proxy";

                    log.debug("eWAF ORCHESTRATOR: Delegando recurso PESADO a NGINX (WAF: {}) para {}", useModSecurity ? "ON" : "OFF", remainingPath);
                    resp.setHeader("X-Accel-Redirect", nginxInternalPath + remainingPath);
                    resp.setHeader("X-Target-Url", targetBaseUrl);

                    // Control Dinámico de Caché
                    if (cacheTtl > 0) {
                        // eWAF le ordena a NGINX guardar este recurso en su disco duro
                        resp.setHeader("X-Accel-Expires", String.valueOf(cacheTtl));
                    } else {
                        // eWAF le prohíbe a NGINX cachear (util para APIs y datos en vivo)
                        resp.setHeader("X-Accel-Expires", "0");
                    }
                    return;
                }
                else if ("SPRING".equals(engineType)) {
                    // DELEGACIÓN A SPRING CLOUD GATEWAY
                    // Usamos X-Accel-Redirect, pero mandamos el tráfico a un pasillo distinto de NGINX.
                    log.debug("eWAF ORCHESTRATOR: Delegando recurso PESADO a SPRING para {}", remainingPath);
                    resp.setHeader("X-Accel-Redirect", "/internal-spring-proxy" + remainingPath);
                    resp.setHeader("X-Target-Url", targetBaseUrl);
                    return;
                }
            } else {
                // Si es HTML, JSON, CSS, JS o una ruta dinámica (Navegación SPA),
                // eWAF ignora la configuración externa y lo procesa en NATIVE obligatoriamente.
                // Necesitamos ejecutar 'rewriteContent()' para reescribir URLs e inyectar el <base>.
                log.debug("eWAF ORCHESTRATOR: Forzando NATIVE para reescritura profunda en {}", remainingPath);
            }
        }

        // INYECCIÓN DE COOKIE DE ENRUTAMIENTO (Para salvar recursos huérfanos sin Referer)
        // Extraemos la ruta principal (ej: '/github') y la guardamos en la cookie.
        if (!incomingMap.isEmpty() && !"/".equals(incomingMap)) {
            jakarta.servlet.http.Cookie routingCookie = new jakarta.servlet.http.Cookie("ewaf_active_app", incomingMap);
            routingCookie.setPath("/");
            routingCookie.setMaxAge(3600); // 1 hora de memoria temporal
            resp.addCookie(routingCookie);
        }

        // --- LÓGICA DE URL INTELIGENTE ---
        String effectiveTargetUrl = targetBaseUrl;
        if ("/".equals(remainingPath)) remainingPath = "";

        if (remainingPath.isEmpty()) {
            effectiveTargetUrl = targetBaseUrl;
        } else {
            int lastSlash = effectiveTargetUrl.lastIndexOf('/');
            if (lastSlash > 8) { // >8 evita cortar https://
                String filename = effectiveTargetUrl.substring(lastSlash + 1);
                if (filename.contains(".")) {
                    effectiveTargetUrl = effectiveTargetUrl.substring(0, lastSlash);
                }
            }
        }

        // 404: Evitar el doble slash (//) al concatenar que rompe servidores estrictos como GitHub o Flask.
        if (effectiveTargetUrl.endsWith("/") && remainingPath.startsWith("/")) {
            effectiveTargetUrl = effectiveTargetUrl.substring(0, effectiveTargetUrl.length() - 1);
        }

        String finalUrl = effectiveTargetUrl + remainingPath;
        if (req.getQueryString() != null) {
            finalUrl += (finalUrl.contains("?") ? "&" : "?") + req.getQueryString();
        }

        // Usamos nivel DEBUG para no saturar el log de producción con cada recurso estático
        log.debug("eWAF PROXY ROUTING: In: '{}' -> Out: '{}'", pathInfo, finalUrl);

        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(finalUrl))
                    .method(req.getMethod(), HttpRequest.BodyPublishers.noBody());

            if (req.getContentLengthLong() > 0) {
                builder.method(req.getMethod(), HttpRequest.BodyPublishers.ofInputStream(() -> {
                    try { return req.getInputStream(); }
                    catch (IOException e) { throw new java.io.UncheckedIOException(e); }
                }));
            }

            // Headers & Referer Fix
            Enumeration<String> headerNames = req.getHeaderNames();
            while (headerNames.hasMoreElements()) {
                String h = headerNames.nextElement();
                if (isIgnoredHeader(h)) continue;
                if (h.equalsIgnoreCase("accept-encoding")) continue;

                if (h.equalsIgnoreCase("referer")) {
                    String ref = req.getHeader(h);
                    if (ref != null) {
                        try {
                            String targetHost = URI.create(targetBaseUrl).getHost();
                            ref = ref.replace(req.getServerName() + ":" + req.getServerPort(), targetHost);
                            ref = ref.replace(req.getServerName(), targetHost);
                            builder.header(h, ref);
                            continue;
                        } catch (Exception e) {}
                    }
                }

                // --- REESCRITURA DEL HEADER ORIGIN ---
                // Las peticiones Fetch/AJAX inyectan su origen real. Si no lo falsificamos,
                // el backend remoto rechaza la petición con un 403 por protección CORS/CSRF.
                if (h.equalsIgnoreCase("origin")) {
                    String origin = req.getHeader(h);
                    if (origin != null) {
                        try {
                            URI targetUri = URI.create(targetBaseUrl);
                            // Construimos el origen falso: "https://dominio-destino.com"
                            String fakeOrigin = targetUri.getScheme() + "://" + targetUri.getHost();
                            // Si el puerto no es el estándar, lo agregamos
                            if (targetUri.getPort() != -1 && targetUri.getPort() != 80 && targetUri.getPort() != 443) {
                                fakeOrigin += ":" + targetUri.getPort();
                            }
                            builder.header(h, fakeOrigin);
                            continue; // Saltamos la copia del origin original
                        } catch (Exception e) {}
                    }
                }
                // --- FIN ORIGIN ---

                builder.header(h, req.getHeader(h));
            }

            builder.header("X-Forwarded-For", IPUtils.getClientIp(req));
            builder.header("X-Forwarded-Proto", req.getScheme());
            builder.header("X-Forwarded-Prefix", proxyAppRoot);

            HttpResponse<InputStream> backendResponse = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());

            resp.setStatus(backendResponse.statusCode());

            backendResponse.headers().map().forEach((k, v) -> {
                if (!isIgnoredHeader(k)) {
                    if ("Location".equalsIgnoreCase(k)) {
                        v.forEach(val -> resp.addHeader(k, rewriteUrl(val, targetBaseUrl, proxyAppRoot)));
                    } else if ("Set-Cookie".equalsIgnoreCase(k)) {
                        v.forEach(val -> resp.addHeader(k, rewriteCookie(val, proxyAppRoot, req)));
                    } else {
                        v.forEach(val -> resp.addHeader(k, val));
                    }
                }
            });

            String contentType = backendResponse.headers().firstValue("Content-Type").orElse("").toLowerCase();

            // AGREGAMOS "css" AQUI PARA QUE REESCRIBA RUTAS EN LAS HOJAS DE ESTILO TAMBIEN
            if (contentType.contains("text/html") || contentType.contains("javascript") || contentType.contains("json") || contentType.contains("css")) {

                // Evitar OutOfMemory limitando el tamaño del contenido que intentamos reescribir.
                // Si el texto es mayor a 5MB, probablemente no sea HTML/JS legítimo o sea inmanejable.
                long contentLength = backendResponse.headers().firstValueAsLong("Content-Length").orElse(-1L);

                if (contentLength > 5 * 1024 * 1024) {
                    // Si es demasiado grande, hacemos streaming directo sin reescribir
                    try (InputStream is = backendResponse.body(); ServletOutputStream os = resp.getOutputStream()) {
                        is.transferTo(os);
                    }
                } else {
                    // Es seguro leer en memoria
                    String body = new String(backendResponse.body().readAllBytes(), StandardCharsets.UTF_8);
                    String currentRequestPath = proxyAppRoot + remainingPath;

                    // PASAMOS EL CONTENT-TYPE PARA NO DESTRUIR LA SINTAXIS JAVASCRIPT/CSS
                    // PASAMOS LA BANDERA DE SUBDOMINIO PARA EVITAR REESCRITURAS INNECESARIAS
                    String modifiedBody = rewriteContent(body, contentType, targetBaseUrl, proxyAppRoot, currentRequestPath, customReplacements, isSubdomainMode);

                    byte[] modifiedBytes = modifiedBody.getBytes(StandardCharsets.UTF_8);
                    resp.setContentLength(modifiedBytes.length);

                    // PROTECCIÓN DE ESCRITURA (Evita el error en consola si el cliente cierra)
                    try {
                        resp.getOutputStream().write(modifiedBytes);
                    } catch (IOException e) {
                        // Cliente cerró conexión, ignoramos.
                    }
                }

            } else {
                // STREAMING PROTEGIDO Y OPTIMIZADO (Aprovecha NIO de Java moderno)
                try (InputStream is = backendResponse.body();
                     ServletOutputStream os = resp.getOutputStream()) {

                    // transferTo es nativo, usa buffers del SO y es increíblemente más rápido que un bucle manual
                    is.transferTo(os);
                } catch (IOException clientAbort) {
                    // Cliente cerró la conexión (ClientAbortException), es normal en streaming de imágenes. Ignoramos silenciosamente.
                }
            }

        } catch (Exception e) {
            // Filtro final para no ensuciar logs con desconexiones
            if (e.getClass().getName().contains("ClientAbortException")) {
                log.trace("eWAF PROXY: Client aborted connection for URI '{}'", pathInfo);
                return;
            }
            log.error("eWAF PROXY ERROR: Failed to route or process request for URI '{}'", pathInfo, e);
            if (!resp.isCommitted()) resp.sendError(502, "Error Proxy: " + e.getMessage());
        }
    }


    // --- CLASE INTERNA PARA CONFIGURACIÓN ---
    private static class RouteConfig {
        String targetUrl;
        String replacementsJson;
        String engineType; // (NATIVE, NGINX, SPRING)
        int cacheTtlSeconds; // Segundos de cache para NGINX
        boolean useModSecurity; // Estado del WAF C++

        RouteConfig(String targetUrl, String replacementsJson, String engineType, int cacheTtlSeconds, boolean useModSecurity) {
            this.targetUrl = targetUrl;
            this.replacementsJson = replacementsJson;
            this.engineType = engineType != null ? engineType.toUpperCase() : "NATIVE";
            this.cacheTtlSeconds = cacheTtlSeconds;
            this.useModSecurity = useModSecurity;
        }
    }

    // Busca en la DB la URL destino y la configuración basada en el path
    private RouteConfig resolveTarget(String path) {
        // Lógica simple: asume que la ruta se define por el primer segmento "/appName"
        String[] parts = path.split("/");
        if (parts.length < 2) return null;
        String appKey = "/" + parts[1];


        String sql = "SELECT target_url, custom_replacements, engine_type, cache_ttl_seconds, use_modsecurity FROM proxy_routes WHERE incoming_path = ? AND active = 1";
        try (Connection cn = ds.getConnection();
             PreparedStatement ps = cn.prepareStatement(sql)) {

            ps.setString(1, appKey);

            // Aseguramos el cierre inmediato del cursor
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new RouteConfig(
                            rs.getString("target_url"),
                            rs.getString("custom_replacements"),
                            rs.getString("engine_type"),
                            rs.getInt("cache_ttl_seconds"),
                            rs.getBoolean("use_modsecurity")
                    );
                }
            }
        } catch (Exception e) {
            log.warn("eWAF PROXY WARN: Database error while resolving target for app key '{}'", appKey, e);
        }
        return null;
    }


    private String getMappedPath(String path) {
        String[] parts = path.split("/");
        if (parts.length < 2) return "";
        return "/" + parts[1];
    }

    private boolean isIgnoredHeader(String name) {
        return name.equalsIgnoreCase("content-length") ||
                name.equalsIgnoreCase("transfer-encoding") ||
                name.equalsIgnoreCase("connection") ||
                name.equalsIgnoreCase("host") ||
                name.equalsIgnoreCase("content-encoding") ||
                name.equalsIgnoreCase("content-security-policy") ||
                name.equalsIgnoreCase("content-security-policy-report-only") ||
                name.equalsIgnoreCase("strict-transport-security");
    }

    // --- MÉTODOS DE REESCRITURA DE HTML Y URLS ---
    // --- MÉTODOS DE REESCRITURA SENSITIVOS AL TIPO MIME ---
    // Modificamos la firma para aceptar currentRequestPath y customReplacementsJson y para aceptar isSubdomainMode

    private String rewriteContent(String body, String contentType, String targetBase, String proxyBase, String currentRequestPath, String customReplacementsJson, boolean isSubdomainMode) {

        String res = body;

        // Detección estricta: Solo aplicaremos manipulaciones de etiquetas a contenido puramente HTML.
        boolean isHtml = contentType != null && contentType.toLowerCase().contains("text/html");

        // 1. REEMPLAZO DE URLS ABSOLUTAS
        // Convertimos https://github.com/search -> /search (o /github/search según el modo)
        String cleanTargetBase = targetBase.endsWith("/") ? targetBase.substring(0, targetBase.length() - 1) : targetBase;
        res = res.replace(cleanTargetBase, proxyBase);


        if (isHtml) {
            // Limitamos esto EXCLUSIVAMENTE a las etiquetas HTML <script> y <link>.
            // Eliminar Subresource Integrity (SRI) y CORS restrictivo
            res = res.replaceAll("(?i)(<(?:script|link)[^>]*?)\\s+integrity\\s*=\\s*[\"'][^\"']*[\"']", "$1");
            res = res.replaceAll("(?i)(<(?:script|link)[^>]*?)\\s+crossorigin\\s*=\\s*[\"'][^\"']*[\"']", "$1");
        }

        // --- REESCRITURA DE RUTAS RELATIVAS (Solo si NO es Subdominio) ---
        // Si el usuario navega por github.localhost, la raíz "/" ya le pertenece,
        // por lo que evitamos ejecutar estas pesadas expresiones regulares y dejamos que el navegador trabaje solo.
        if (!isSubdomainMode) {
            String cleanProxyBase = proxyBase.startsWith("/") ? proxyBase.substring(1) : proxyBase;

            if (isHtml) {
                // Si no hacemos esto, los scripts relativos ("js/functions.js") se buscarán en la raíz y fallarán.
                String dynamicBase = proxyBase + "/";
                if (currentRequestPath.contains("/")) {
                    int lastSlash = currentRequestPath.lastIndexOf('/');
                    if (lastSlash > 0) {
                        dynamicBase = currentRequestPath.substring(0, lastSlash + 1);
                    }
                }

                // Inyectar o reemplazar <base> con la ruta dinámica correcta
                if (res.contains("<base")) {
                    res = res.replaceAll("(?i)<base\\s+href=[\"'](.*?)[\"']\\s*/?>", "<base href=\"" + dynamicBase + "\">");
                } else if (res.contains("<head>")) {
                    res = res.replace("<head>", "<head><base href=\"" + dynamicBase + "\">");
                }

                // Las aplicaciones usan cientos de atributos personalizados (ej: data-url, data-search-path, data-href).
                // Al capturar la raíz "data-", cubrimos dinámicamente la barra de búsqueda de GitHub y cualquier otro framework.
                String safeAttrs = "(href|src|action|data-[a-zA-Z0-9_-]+)";
                res = res.replaceAll("(?i)\\b" + safeAttrs + "\\s*=\\s*\"/(?!/)(?!" + java.util.regex.Pattern.quote(cleanProxyBase) + ")", "$1=\"" + proxyBase + "/");
                res = res.replaceAll("(?i)\\b" + safeAttrs + "\\s*=\\s*'/(?!/)(?!" + java.util.regex.Pattern.quote(cleanProxyBase) + ")", "$1='" + proxyBase + "/");
            }

            // Si reescribimos todas las rutas "/..." en JS, corrompemos el estado interno de React/Redux (Error 500).
            // Solución: En JS/JSON solo reescribimos rutas que apunten claramente a archivos.
            // Las rutas de navegación (ej. "/search") las dejamos escapar, y nuestro RootCatchAllFilter las atrapará.
            //  Usamos un "non-capturing group" (?:...) para no alterar el orden de las variables
            //  de reemplazo, evitando que el motor regex borre la comilla de cierre en los scripts de Swagger/React.
            String staticExtensions = "\\.(?:png|jpg|jpeg|gif|webp|svg|ico|js|css|woff|woff2|ttf|eot|glb|gltf|json|wasm)";
            // Busca cualquier string "/..." que termine en una extensión estática y lo reescribe.
            res = res.replaceAll("([\"'])/(?!/)(?!" + java.util.regex.Pattern.quote(cleanProxyBase) + ")([-a-zA-Z0-9_./?=&]+" + staticExtensions + ")([\"'])", "$1" + proxyBase + "/$2$3");

            // Atrapa los recursos importados dentro de hojas de estilo o etiquetas <style>
            res = res.replaceAll("(?i)url\\s*\\(\\s*[\"']?/(?!/)(?!" + java.util.regex.Pattern.quote(cleanProxyBase) + ")([^\"')]+)[\"']?\\s*\\)", "url('" + proxyBase + "/$1')");
        }

        // 5. REEMPLAZOS PERSONALIZADOS DESDE DB (JSON)
        // Esto permite arreglar rutas rotas específicas de cada sitio sin tocar código Java.
        if (customReplacementsJson != null && !customReplacementsJson.isBlank()) {
            try {
                // Parser simple de JSON plano
                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\"((?:\\\\.|[^\"\\\\])*)\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
                java.util.regex.Matcher matcher = pattern.matcher(customReplacementsJson);

                while (matcher.find()) {
                    String key = matcher.group(1); // Texto a buscar
                    String val = matcher.group(2); // Texto de reemplazo
                    res = res.replace(key, val);
                }
            } catch (Exception e) {
                log.warn("eWAF PROXY WARN: Error parsing custom replacements JSON for path '{}'.", currentRequestPath, e);
            }
        }

        // 6. LIMPIEZA FINAL Y FIX DE ANCLAJES (DINÁMICO Y SEGURO)
        // Restaurar anclajes puros (href="#") sin corromper rutas legítimas que
        // casualmente repitan el nombre de la app (Ej: github.com/github).
        res = res.replace("href=\"" + proxyBase + "/#\"", "href=\"#\"");
        res = res.replace("href='" + proxyBase + "/#'", "href='#'");

        // Fix para saltos de página anclados
        String baseAnchor = proxyBase + "/#";
        if (res.contains(baseAnchor)) {
            res = res.replace(baseAnchor, "#");
        }

        return res;
    }


    private String rewriteUrl(String url, String targetBase, String proxyBase) {
        if (url == null) return null;
        if (url.startsWith(targetBase)) {
            return url.replace(targetBase, proxyBase);
        }
        if (url.startsWith("/")) {
            return proxyBase + url;
        }
        return url;
    }

    private String rewriteCookie(String cookieHeader, String proxyPath, HttpServletRequest req) {
        String rewritten = cookieHeader;

        // Si proxyPath está vacío (ej: Root Context en modo Subdominio), la ruta de la cookie debe ser "/"
        String finalCookiePath = (proxyPath == null || proxyPath.isEmpty()) ? "/" : proxyPath;

        // 1. Reescribir la ruta (Path)
        if (rewritten.contains("Path=/")) {
            rewritten = rewritten.replaceAll("(?i)Path=/[^;]*", "Path=" + finalCookiePath);
        }

        // 2. Eliminar el dominio estricto del backend.
        // Esto fuerza al navegador a atar la cookie al dominio de eWAF (ej: localhost)
        rewritten = rewritten.replaceAll("(?i);\\s*Domain=[^;]*", "");

        // 3. Fix para desarrollo local: Si la cookie exige HTTPS (Secure) pero estamos en HTTP,
        // el navegador la bloquea. Se lo quitamos si la petición es insegura.
        if ("http".equalsIgnoreCase(req.getScheme())) {
            rewritten = rewritten.replaceAll("(?i);\\s*Secure", "");
        }

        return rewritten;
    }
}