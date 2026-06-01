package com.ewaf;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SERVLET DE RESCATE (Error 404 Handler)
 * Utiliza la cabecera Referer y Cookies de sesión para determinar dinámicamente
 * si un Error 404 pertenece a Tomcat (nativo) o a una aplicación SPA del Proxy.
 */
@WebServlet("/fugitive-recovery")
public class FugitiveRecoveryServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(FugitiveRecoveryServlet.class);

    // Ya no dependemos de palabras estáticas, solo leemos la cookie que dejó el proxy
    private static final String COOKIE_NAME = "ewaf_active_app";

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        // 1. Obtener la URI original que falló (provista por Tomcat al disparar el 404)
        String originalUri = (String) req.getAttribute("jakarta.servlet.forward.request_uri");
        if (originalUri == null) {
            originalUri = req.getRequestURI();
        }

        // PREVENCIÓN DE BUCLES INFINITOS DE RED
        // Si el proxy intentó rescatar la ruta mandándola a "/internal-proxy" y aún así falló (porque
        // realmente no existe en el servidor destino), evitamos que vuelva a caer aquí y trabe la memoria.
        if (originalUri.startsWith(req.getContextPath() + "/internal-proxy")) {
            sendReal404(req, resp);
            return;
        }

        String appKey = null;
        boolean isSubdomainRescue = false;

        // 2.A. PRIORIDAD 1: Analizar el Host (Subdominio Agnóstico)
        String hostHeader = req.getHeader("Host");
        if (hostHeader != null) {
            String hostName = hostHeader.split(":")[0].toLowerCase();
            if (!hostName.equals("localhost") && !hostName.matches("^\\d{1,3}(\\.\\d{1,3}){3}$")) {
                int firstDot = hostName.indexOf('.');
                if (firstDot > 0) {
                    String potentialApp = "/" + hostName.substring(0, firstDot);
                    if (GlobalRouterFilter.hasRoute(potentialApp)) {
                        appKey = potentialApp;
                        isSubdomainRescue = true;
                    }
                }
            }
        }

        // 2.B. PRIORIDAD 2: Analizar el Referer (Modo Ruta Tradicional)
        if (appKey == null || appKey.isEmpty()) {
            String referer = req.getHeader("Referer");
            if (referer != null) {
                try {
                    String refPath = URI.create(referer).getPath();
                    String context = req.getContextPath();

                    String pathNoContext = (context != null && !context.isEmpty() && refPath.startsWith(context))
                            ? refPath.substring(context.length())
                            : refPath;

                    String[] parts = pathNoContext.split("/");
                    if (parts.length > 1 && !parts[1].isEmpty()) {
                        String potentialApp = "/" + parts[1];
                        // Le preguntamos a la RAM si esta ruta realmente existe.
                        // Si cambió la URL a "/search", el Referer dirá "/search".
                        // Como "/search" no existe en la base de datos, lo ignoramos y dejamos
                        // que caiga a la Estrategia B (La Cookie) para que lo asigne
                        if (GlobalRouterFilter.hasRoute(potentialApp)) {
                            appKey = potentialApp;
                        }
                    }
                } catch (Exception e) {
                    log.trace("eWAF 404-RECOVERY: Error parseando Referer '{}'", referer, e);
                }
            }
        }

        // 3. PRIORIDAD 3: Analizar Cookie (El último salvavidas)
        // Vital para SPAs que alteran el Referer,
        // y para peticiones Fetch/XHR ocultas en segundo plano.
        if ((appKey == null || appKey.isEmpty()) && req.getCookies() != null) {
            for (Cookie cookie : req.getCookies()) {
                if (COOKIE_NAME.equals(cookie.getName()) && !cookie.getValue().isEmpty()) {
                    appKey = cookie.getValue();
                    break;
                }
            }
        }

        // 4. RESCATE DE LA RUTA FUGITIVA
        if (appKey != null && !appKey.isEmpty()) {

            // Sincronizamos la bandera de enrutamiento para que el ProxyServlet no corrompa el HTML
            req.setAttribute("ewaf.routing.mode", isSubdomainRescue ? "SUBDOMAIN" : "PATH");

            // Quitamos la subcarpeta de tomcat a la url original si la tuviera
            String uriWithoutContext = originalUri;
            if (req.getContextPath() != null && !req.getContextPath().isEmpty() && originalUri.startsWith(req.getContextPath())) {
                uriWithoutContext = originalUri.substring(req.getContextPath().length());
            }

            String targetProxyPath;

            // Inteligencia Artificial de rutas: Evitar concatenar "/github/github/imagen.png"
            // Si la ruta rota YA empieza con el nombre de la app (ej: falló un JS local), no lo duplicamos.
            if (uriWithoutContext.startsWith(appKey + "/") || uriWithoutContext.equals(appKey)) {
                targetProxyPath = "/internal-proxy" + uriWithoutContext;
            } else {
                // Si es un cambio forzado de la SPA (ej: /search), le pegamos la app original.
                if (!uriWithoutContext.startsWith("/")) uriWithoutContext = "/" + uriWithoutContext;
                targetProxyPath = "/internal-proxy" + appKey + uriWithoutContext;
            }

            // Preservar la query string (?param=1) si existiera
            String originalQuery = (String) req.getAttribute("jakarta.servlet.forward.query_string");
            if (originalQuery != null) {
                targetProxyPath += "?" + originalQuery;
            }

            // FORWARD SILENCIOSO UNIVERSAL
            // Dejamos que la SPA manipule la URL en el navegador a su antojo.
            // Tomcat servirá el contenido correcto internamente basado en la Cookie,
            // evitando que el router interno de la SPA colapse al ver prefijos desconocidos.
            log.debug("eWAF 404-RECOVERY: Rescatando ruta SPA fugitiva '{}' hacia '{}'", originalUri, targetProxyPath);
            req.getRequestDispatcher(targetProxyPath).forward(req, resp);
        } else {
            // 5. CAÍDA LIBRE: No hay Referer válido ni Cookie. Es un 404 real absoluto.
            sendReal404(req, resp);
        }
    }

    private void sendReal404(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setStatus(HttpServletResponse.SC_NOT_FOUND);

        try {
            req.getRequestDispatcher("/WEB-INF/jsp/errors/404.jsp").forward(req, resp);
        } catch (Exception e) {
            // Fallback
            resp.setContentType("text/plain; charset=UTF-8");
            resp.getWriter().write("eWAF: Error 404 - El recurso solicitado no existe y no hay sesion de proxy activa.");
        }
    }
}