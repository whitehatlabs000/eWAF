package com.ewaf;

import com.ewaf.IPUtils;
import jakarta.servlet.*;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import com.ewaf.WafLogger;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IPLoggingFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(IPLoggingFilter.class);

    private Set<String> ignoreExtensions = new HashSet<>();
    private Set<String> ignorePaths = new HashSet<>();
    private ServletContext servletContext; // Para poder loguear

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        try {
            ServletContext context = filterConfig.getServletContext();
            this.servletContext = context; // Guardamos el contexto para loguear

            // 1.A. Obtener la baseUrl (que el Listener ya cargó)
            String baseUrl = (String) context.getAttribute("appBaseUrl");
            if (baseUrl == null) {
                baseUrl = ""; // Fallback por si acaso
            }
            // Corregir si baseUrl es "/" para evitar rutas como "//home"
            if (baseUrl.equals("/")) {
                baseUrl = "";
            }
            // variable 'final' para usarla dentro del lambda
            final String finalBaseUrl = baseUrl;


            // 2. Cargar y parsear la lista de extensiones a ignorar
            String extensionsStr = (String) context.getAttribute("logfilter.ignore.extensions");
            if (extensionsStr != null && !extensionsStr.isEmpty()) {
                this.ignoreExtensions = Arrays.stream(extensionsStr.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .map(String::toLowerCase)
                        .collect(Collectors.toSet());
            }

            // 3. Cargar y parsear la lista de rutas a ignorar
            String pathsStr = (String) context.getAttribute("logfilter.ignore.paths");
            if (pathsStr != null && !pathsStr.isEmpty()) {

                this.ignorePaths = Arrays.stream(pathsStr.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        // agregar el baseUrl a cada ruta
                        .map(path -> finalBaseUrl + path)
                        .collect(Collectors.toSet());

            }

            log.info("eWAF IPLoggingFilter initialized successfully. Ignoring {} extensions and {} paths.", this.ignoreExtensions.size(), this.ignorePaths.size());

        } catch (Exception e) {
            log.error("Failed to initialize eWAF IPLoggingFilter.", e);
            throw new ServletException("Failed to initialize eWAF IPLoggingFilter", e);
        }
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String uri = httpRequest.getRequestURI();

        // VALIDACIÓN TEMPRANA (Escudo DoS): Rechazar URIs masivas antes de enviarlas a memoria (toLowerCase)
        if (uri != null && uri.length() > 2048) {
            log.warn("DoS SHIELD: Blocked extremely long URI (length: {}) in eWAF IPLoggingFilter.", uri.length());
            ((HttpServletResponse) response).sendError(HttpServletResponse.SC_REQUEST_URI_TOO_LONG, "URI is too long.");
            return;
        }

        String uriLower = uri.toLowerCase();

        // Obtenemos el path raíz de la aplicación
        String rootPath = httpRequest.getContextPath() + "/";

        //1. Lógica de Ignorar
        boolean shouldLog = true;

        // Bucle principal para ignorar por ruta
        for (String path : ignorePaths) {

            // CASO 1: El path a ignorar es LA RAÍZ (ej: /ewaf/)
            // Hacemos una COINCIDENCIA EXACTA.
            if (path.equals(rootPath)) {
                if (uri.equals(rootPath)) {
                    shouldLog = false;
                    break;
                }
            }
            // CASO 2: Cualquier otro path (ej: /ewaf/admin-logs)
            // Usamos STARTS_WITH como antes.
            // Comprueba si la URI comienza con una ruta ignorada
            else {
                if (uri.startsWith(path)) {
                    shouldLog = false;
                    break;
                }
            }
        }

        // Si no fue ignorada por ruta, comprobar por extensión
        if (shouldLog) {
            for (String ext : ignoreExtensions) {
                if (uriLower.endsWith(ext)) { // Usamos la URI en minúsculas para extensiones
                    shouldLog = false;
                    break;
                }
            }
        }
        // --- Fin de Lógica de Ignorar ---


        // Si NO fue ignorada (shouldLog = true), registramos la visita
        if (shouldLog) {
            String ipAddress = IPUtils.getClientIp(httpRequest); // Usamos IPUtils para obtener la IP real
            HttpSession session = httpRequest.getSession(false);
            String username = (session != null) ? (String) session.getAttribute("user") : null;

            String queryString = httpRequest.getQueryString();
            String fullPath = uri;

            String httpMethod = httpRequest.getMethod();
            String details = null;

            if (queryString != null && !queryString.isEmpty()) {
                // Escudo DoS: Cortamos el query string ANTES de asfixiar la RAM
                if (queryString.length() > 500) {
                    queryString = queryString.substring(0, 500) + "...";
                }
                details = "Query: " + queryString;
            }

            // Enviamos al motor asíncrono. Él se encarga del truncamiento final y de la BD.
            WafLogger.getInstance().logAsync(
                    ipAddress,
                    username,
                    "PAGE_VIEW",
                    uri, // target_path limpio
                    httpMethod,
                    details
            );
        }

        // Dejamos que la petición continúe su curso normal
        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {}
}