package com.ewaf;

import com.ewaf.MaintenanceManager;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MaintenanceFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceFilter.class);

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        log.info("eWAF MaintenanceFilter initialized successfully.");
    }

    @Override
    public void destroy() {
        log.info("eWAF MaintenanceFilter destroyed.");
    }

    // Lista de rutas que siempre deben funcionar (assets, login, el propio admin)
    private static final List<String> ALLOWED_PATHS = Arrays.asList(
            "/css/", "/js/", "/scripts/", "/webfonts/", "/images/", // Estilos y scripts
            "/login", "/api/csrf-token", // Login y utilidades
            "/admin", // Permitimos rutas que empiecen con /admin para que el admin no se bloquee a sí mismo
            "/maintenance.html" // La página de aviso
    );

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // VALIDACIÓN TEMPRANA (Escudo DoS): Este filtro procesa TODAS las peticiones.
        // Rechazamos URIs absurdamente largas antes de agotar la RAM con substrings.
        String requestURI = httpRequest.getRequestURI();
        if (requestURI != null && requestURI.length() > 2048) {
            log.warn("DoS SHIELD: Blocked extremely long URI (length: {}) in eWAF MaintenanceFilter.", requestURI.length());
            httpResponse.sendError(HttpServletResponse.SC_REQUEST_URI_TOO_LONG, "URI is too long.");
            return;
        }

        // Obtenemos la ruta relativa (ej: /home, /css/style.css)
        String path = requestURI.substring(httpRequest.getContextPath().length());

        // 1. Si el mantenimiento está APAGADO, dejamos pasar todo.
        if (!MaintenanceManager.isMaintenanceModeEnabled()) {
            chain.doFilter(request, response);
            return;
        }

        // 2. Si está ENCENDIDO, verificamos excepciones:

        // A. Recursos estáticos y rutas permitidas
        boolean isAllowedPath = ALLOWED_PATHS.stream().anyMatch(path::startsWith);
        if (isAllowedPath) {
            chain.doFilter(request, response);
            return;
        }

        // B. Verificar si es ADMINISTRADOR
        HttpSession session = httpRequest.getSession(false);
        if (session != null && "admin".equalsIgnoreCase((String) session.getAttribute("tipoUsuario"))) {
            // Es admin, lo dejamos pasar como si nada ocurriera
            chain.doFilter(request, response);
            return;
        }

        // 3. Bloqueo final: Redirigir a página de mantenimiento
        // Evitamos bucles infinitos verificando que no estemos ya en maintenance.html
        if (!"/maintenance.html".equals(path)) {
            log.debug("eWAF MAINTENANCE: Redirecting non-admin request for '{}' to maintenance page.", path);
            httpResponse.sendRedirect(httpRequest.getContextPath() + "/maintenance.html");
        } else {
            chain.doFilter(request, response);
        }
    }
}