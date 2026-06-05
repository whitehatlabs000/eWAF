package com.ewaf;

import com.ewaf.IPUtils;
import com.ewaf.IPBlockManager;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.ewaf.WafLogger;

public class SimpleWaffFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(SimpleWaffFilter.class);

    // --- CONFIGURACIÓN DE RATE LIMITING ---
    private int maxAttempts = 3;
    private long timeWindowMs = 3600000L; // 1 hora por defecto

    // (Caffeine)
    private Cache<String, List<Long>> violationTracker;

    private List<Pattern> sqlPatterns = new ArrayList<>();
    private List<Pattern> xssPatterns = new ArrayList<>();

    // Rutas donde permitimos HTML/Scripts
    private Set<String> xssAllowedPaths = new HashSet<>();

    // Rutas donde permitimos SQL Injection (Allow List)
    private Set<String> sqliAllowedPaths = new HashSet<>();

    // Rutas donde BLOQUEAMOS (400) pero NO BANEAMOS la IP
    private Set<String> sqliNoBanPaths = new HashSet<>();

    // Rutas exclusivas para Administradores (config.properties)
    private Set<String> adminPaths = new HashSet<>();

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        ServletContext context = filterConfig.getServletContext();

        // --- 1. Cargar Rutas Dinámicas desde el Contexto (AppLifecycleListener) ---

        // A. Rutas permitidas para XSS
        String xssAllowedStr = (String) context.getAttribute("waf.xss.allowedPaths");
        if (xssAllowedStr != null && !xssAllowedStr.isEmpty()) {
            this.xssAllowedPaths = parsePaths(xssAllowedStr);
        }

        // B. Rutas permitidas para SQLi
        String sqliAllowedStr = (String) context.getAttribute("waf.sqli.allowedPaths");
        if (sqliAllowedStr != null && !sqliAllowedStr.isEmpty()) {
            this.sqliAllowedPaths = parsePaths(sqliAllowedStr);
        }

        // B.2. Rutas con Bloqueo Suave para SQLi (No Ban)
        String sqliNoBanStr = (String) context.getAttribute("waf.sqli.noBanPaths");
        if (sqliNoBanStr != null && !sqliNoBanStr.isEmpty()) {
            this.sqliNoBanPaths = parsePaths(sqliNoBanStr);
        }

        // B.3. Configuración de Rate Limiting
        Integer maxAttemptsConfig = (Integer) context.getAttribute("waf.block.maxAttempts");
        Integer timeWindowSecondsConfig = (Integer) context.getAttribute("waf.block.timeWindowSeconds");

        if (maxAttemptsConfig != null) this.maxAttempts = maxAttemptsConfig;
        if (timeWindowSecondsConfig != null) this.timeWindowMs = timeWindowSecondsConfig * 1000L;

        // --- INICIALIZAR CAFFEINE CACHE ---
        this.violationTracker = Caffeine.newBuilder()
                .expireAfterWrite(timeWindowMs, TimeUnit.MILLISECONDS)
                .maximumSize(100_000)
                .build();

        log.info("eWAF SQLi/XSS Config Loaded: SQLi Allow={}, SQLi NoBan={}, Strikes={}, Window={}s",
                sqliAllowedPaths.size(), sqliNoBanPaths.size(), maxAttempts, (timeWindowMs/1000));

        // C. Rutas de Administración
        String adminPathsStr = (String) context.getAttribute("security.admin.paths");
        if (adminPathsStr != null && !adminPathsStr.isEmpty()) {
            this.adminPaths = parsePaths(adminPathsStr);
        }

        // --- 2. Reglas SQL Injection ---

        // A. Inyección basada en Tautologías y Uniones (Ej: ' OR '1'='1', UNION SELECT)
        // Detecta comillas seguidas de palabras clave lógicas o de unión.
        sqlPatterns.add(Pattern.compile("(?i)(['\"])\\s*(or|and|union|select|insert|update|delete|drop|alter|create|truncate)\\s+"));

        // B. Comentarios CONTEXTUALES (La clave para eliminar falsos positivos)
        // no baneamos "--" o "#" sueltos. Solo se banean si vienen inmediatamente
        // después de un caracter de cierre de sentencia SQL (comilla simple, doble o punto y coma).
        // Detecta: admin' -- , admin"; # , etc.
        sqlPatterns.add(Pattern.compile("(?i)(['\";])\\s*(--|#|/\\*)"));

        // C. Funciones peligrosas de base de datos (Ej: version(), @@version, sleep())
        // Detecta intentos de fingerprinting o time-based blind SQLi
        sqlPatterns.add(Pattern.compile("(?i)\\b(sleep|benchmark|delay|waitfor)\\s*\\("));

        // D. Stacked Queries (Punto y coma seguido de nueva instrucción)
        // Detecta: ; DROP TABLE
        sqlPatterns.add(Pattern.compile("(?i);\\s*(drop|alter|shutdown|grant|exec)\\b"));

        // E. Inyección de UNION (Detecta "UNION SELECT" o "UNION ALL SELECT")
        // Usamos \b (límite de palabra) para que no detecte palabras normales que contengan "union".
        // Esto detecta: id=105 UNION SELECT ...
        sqlPatterns.add(Pattern.compile("(?i)\\bunion\\s+(all\\s+)?select\\b"));

        // F. Tautologías Numéricas Típicas (Detecta "OR 1=1", "AND 0=0")
        // Detecta: operador lógico + espacio + digito + igual + digito
        // Evita falsos positivos en texto como "And 1 thing more".
        sqlPatterns.add(Pattern.compile("(?i)\\b(or|and)\\s+\\d+\\s*=\\s*\\d+"));

        // G. Manipulación de Orden y Agrupamiento
        // Detecta: "ORDER BY 5", "GROUP BY user"
        // Es muy raro que un usuario legítimo escriba "order by" en un campo de texto.
        sqlPatterns.add(Pattern.compile("(?i)\\b(order|group)\\s+by\\s+"));

        // H. Tautologías de String en campos Numéricos (Cubre el hueco de Regla F)
        // Detecta: OR 'a'='a', AND "x"="x" (sin necesitar comilla al principio del input)
        // Buscamos: (OR/AND) + espacio + comilla + algo + comilla + igual + comilla
        sqlPatterns.add(Pattern.compile("(?i)\\b(or|and)\\s+['\"][a-zA-Z0-9]+['\"]\\s*=\\s*['\"][a-zA-Z0-9]+['\"]"));

        // I. Comentarios peligrosos al final de números
        // Detecta: numero + (espacio opcional) + -- + (espacio O final de linea)
        // Bloquea: "id=1--" (Final de linea) o "id=1-- drop" (Espacio)
        // PERMITE: "10--20" (Rango) o "Capitulo 1--Intro"
        sqlPatterns.add(Pattern.compile("(?i)\\d+\\s*--(\\s|$)"));

        // --- 3. Reglas XSS (SE APLICAN SOLO EN RUTAS NO PERMITIDAS) ---
        xssPatterns.add(Pattern.compile("(?i)(<script|%3Cscript)"));
        xssPatterns.add(Pattern.compile("(?i)(javascript:|vbscript:|data:)"));
        xssPatterns.add(Pattern.compile("(?i)(onload|onerror|onclick|onmouseover)\\s*="));

        log.info("eWAF SimpleWaffFilter initialized. XSS Allow={}, Admin Paths={}", xssAllowedPaths.size(), adminPaths.size());
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        String uri = httpRequest.getRequestURI();

        // --- 1. SEGURIDAD DE ADMINISTRADOR ---
        if (isAdminPath(uri)) {
            HttpSession session = httpRequest.getSession(false);
            String tipoUsuario = (session != null) ? (String) session.getAttribute("tipoUsuario") : null;

            if (session == null || !"admin".equalsIgnoreCase(tipoUsuario)) {
                String attackerIp = IPUtils.getClientIp(httpRequest);
                String username = (session != null) ? (String) session.getAttribute("user") : null;

                log.warn("eWAF SECURITY BLOCK: Unauthorized access attempt to protected admin path '{}' from IP '{}'", uri, attackerIp);
                WafLogger.getInstance().logAsync(attackerIp, username, "UNAUTHORIZED_ADMIN_ACCESS", uri, httpRequest.getMethod(), "Attempted to access admin zone.");

                httpResponse.sendError(HttpServletResponse.SC_FORBIDDEN, "Access Denied: Administrators only.");
                return;
            }
        }

        // --- 2. PREPARACIÓN DEL WRAPPER (SOLUCIÓN PROXY) ---
        CachedBodyHttpServletRequest wrappedRequest = null;
        String method = httpRequest.getMethod();
        String contentType = httpRequest.getContentType();
        boolean isMultipart = (contentType != null && contentType.toLowerCase().startsWith("multipart/"));

        // Solo envolvemos si es POST/PUT y NO es multipart (para no cargar archivos en RAM)
        if (("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)) && !isMultipart) {
            try {
                wrappedRequest = new CachedBodyHttpServletRequest(httpRequest);
            } catch (IOException e) {
                // Capturamos la excepción de tamaño excesivo lanzada por el Wrapper (Protección DoS)
                log.warn("eWAF DoS SHIELD: Blocked oversized payload from IP '{}' targeting URI '{}'", IPUtils.getClientIp(httpRequest), uri);
                httpResponse.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "Payload too large");
                return;
            }
        }

        // Usamos la petición envuelta si existe, sino la original
        HttpServletRequest requestToInspect = (wrappedRequest != null) ? wrappedRequest : httpRequest;

        // --- 3. INSPECCIÓN ---

        // A. Inspeccionar URL Parameters (Query String)
        // Esto es seguro siempre, incluso en multipart
        String queryString = requestToInspect.getQueryString();
        if (queryString != null) {
            if (checkThreats(queryString, uri, requestToInspect, httpResponse)) return;
        }

        // B. Inspeccionar CUERPO COMPLETO (Detecta JSON, XML y Forms URL-Encoded)
        // NO inspeccionamos Multipart (punto ciego aceptado por estabilidad)
        if (wrappedRequest != null) {
            String body = wrappedRequest.getBody();
            if (checkThreats(body, uri, requestToInspect, httpResponse)) return;
        }

        // --- 4. CONTINUAR ---
        // Usamos requestToInspect que ya tiene la lógica correcta
        chain.doFilter(requestToInspect, response);
    }

    // --- Métodos Auxiliares ---

    // Método helper para parsear las cadenas de config
    private Set<String> parsePaths(String rawConfig) {
        return Arrays.stream(rawConfig.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    // Método centralizado de verificación de amenazas
    private boolean checkThreats(String content, String uri, HttpServletRequest req, HttpServletResponse resp) throws IOException {

        String attackerIp = IPUtils.getClientIp(req);
        HttpSession session = req.getSession(false);
        String username = (session != null) ? (String) session.getAttribute("user") : null;
        String httpMethod = req.getMethod();

        // --- 1. SQL INJECTION CHECK ---
        if (!isPathAllowedForSQLi(uri) && isMatch(content, sqlPatterns)) {

            // A. CHECK: ¿Es ruta NoBan? (Soft Block)
            if (isPathNoBan(uri)) {
                log.warn("eWAF SOFT-BLOCK: SQLi signature detected in No-Ban zone '{}' from IP '{}'", uri, attackerIp);
                WafLogger.getInstance().logAsync(attackerIp, username, "SQL_INJECTION", uri, httpMethod, "Soft-Block applied. Content match in No-Ban zone.");
                resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid Characters Detected");
                return true;
            }

            // B. CHECK: Rate Limiting (Strikes)
            boolean shouldBan = trackViolationAndCheckBan(attackerIp);

            if (shouldBan) {
                // CASO: BANEO PERMANENTE
                log.error("eWAF CRITICAL EVENT: IP '{}' permanently banned. Exceeded {} SQLi attempts targeting URI '{}'", attackerIp, maxAttempts, uri);
                IPBlockManager.addIP(attackerIp);
                violationTracker.invalidate(attackerIp); // Limpiar cache
                WafLogger.getInstance().logAsync(attackerIp, username, "SQL_INJECTION", uri, httpMethod, "Permanent Ban applied. Max strikes reached.");
                resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Security Violation: IP Banned");
            } else {
                // CASO: ADVERTENCIA (400)
                int current = getCurrentAttempts(attackerIp);
                log.warn("eWAF STRIKE: SQLi attempt {}/{} detected from IP '{}' targeting URI '{}'", current, maxAttempts, attackerIp, uri);
                WafLogger.getInstance().logAsync(attackerIp, username, "SQL_INJECTION", uri, httpMethod, "Strike " + current + "/" + maxAttempts);
                resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Security Violation: Suspicious pattern detected");
            }
            return true; // Bloqueado
        }

        // --- 2. XSS CHECK ---
        if (!isPathAllowedForXSS(uri) && isMatch(content, xssPatterns)) {
            log.warn("eWAF THREAT BLOCKED: XSS signature detected from IP '{}' targeting URI '{}'", attackerIp, uri);
            WafLogger.getInstance().logAsync(attackerIp, username, "XSS_BLOCKED", uri, httpMethod, "XSS signature matched.");
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Security Violation: XSS detected");
            return true; // Bloqueado
        }
        return false; // Limpio
    }

    private boolean isMatch(String input, List<Pattern> patterns) {
        if (input == null || input.isEmpty()) return false;

        // VALIDACIÓN TEMPRANA (Escudo ReDoS): No inspeccionamos cadenas masivas
        // El Wrapper ya nos protegió del body, pero debemos proteger la queryString
        if (input.length() > 20000) return false;

        // Normalizamos (URL Decode) para evitar evasiones simples.
        // Hacemos el toLowerCase AQUÍ para que las Regex no necesiten la bandera (?i) en el futuro,
        // aunque ahora la tengan, esto optimiza el rendimiento.
        String normalized;
        try {
            // Decodificamos de forma segura. Tomcat ya decodifica parámetros,
            // pero esto atrapa ataques de "Doble URL Encoding" típicos de bypass de WAF.
            normalized = java.net.URLDecoder.decode(input, java.nio.charset.StandardCharsets.UTF_8).toLowerCase();
        } catch (Exception e) {
            // Fallback si la cadena tiene secuencias % inválidas que rompen el decoder
            normalized = input.toLowerCase();
        }

        for (Pattern pattern : patterns) {
            if (pattern.matcher(normalized).find()) {
                return true;
            }
        }

        // Segunda pasada de seguridad: Inspeccionar la cadena cruda original
        // Si el atacante mandó un JSON, el URLDecoder podría haber distorsionado la cadena
        // y saltarse el patrón. Evaluamos la original (en minúsculas) también.
        String originalLower = input.toLowerCase();
        if (!originalLower.equals(normalized)) {
            for (Pattern pattern : patterns) {
                if (pattern.matcher(originalLower).find()) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean isPathAllowedForXSS(String uri) {
        return xssAllowedPaths.stream().anyMatch(uri::endsWith);
    }

    private boolean isPathAllowedForSQLi(String uri) {
        return sqliAllowedPaths.stream().anyMatch(uri::endsWith);
    }

    private boolean isPathNoBan(String uri) {
        return sqliNoBanPaths.stream().anyMatch(uri::endsWith);
    }

    private boolean isAdminPath(String uri) {
        return adminPaths.stream().anyMatch(uri::contains);
    }

    // --- RATE LIMITING (CAFFEINE) ---
    private boolean trackViolationAndCheckBan(String ip) {
        long now = System.currentTimeMillis();

        List<Long> timestamps = violationTracker.get(ip, k -> new ArrayList<>());
        if (timestamps == null) return false;

        synchronized (timestamps) {
            timestamps.add(now);
            timestamps.removeIf(ts -> (now - ts) > timeWindowMs);

            boolean banned = timestamps.size() >= maxAttempts;
            violationTracker.put(ip, timestamps); // Refrescar expiración
            return banned;
        }
    }

    private int getCurrentAttempts(String ip) {
        List<Long> timestamps = violationTracker.getIfPresent(ip);
        if (timestamps == null) return 0;

        synchronized (timestamps) {
            long now = System.currentTimeMillis();
            timestamps.removeIf(ts -> (now - ts) > timeWindowMs);
            return timestamps.size();
        }
    }

    @Override
    public void destroy() {}
}