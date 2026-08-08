package com.ewaf;

import com.ewaf.IPUtils;
import com.ewaf.IPBlockManager;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.ewaf.WafLogger;
import jakarta.servlet.http.HttpSession;

public class BlockedIPFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(BlockedIPFilter.class);


    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // Ya no necesitamos cargar nada aquí ni manejar IOExceptions.
        // El IPBlockManager ya tiene la lista en RAM lista para usarse.
        log.info("eWAF BlockedIPFilter initialized successfully.");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        // Usamos IPUtils para bloquear la IP real detrás del proxy
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String clientIp = IPUtils.getClientIp(httpRequest);

        // VALIDACIÓN TEMPRANA (Escudo DoS): Proteger CPU y RAM si el atacante falsifica
        // cabeceras como X-Forwarded-For con megabytes de texto basura.
        if (clientIp != null && clientIp.length() > 50) {
            log.warn("DoS SHIELD: Blocked extremely long spoofed IP header (length: {}) in eWAF BlockedIPFilter. Target URI: {}", clientIp.length(), httpRequest.getRequestURI());
            HttpServletResponse httpResponse = (HttpServletResponse) response;
            httpResponse.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid IP address format.");
            return; // Detiene la petición y protege el servidor
        }

        // Verificamos directamente contra el Manager.
        // Al ser memoria RAM (CopyOnWriteArrayList), esto tarda 0ms.
        if (IPBlockManager.getBlockedIPs().contains(clientIp)) {
            String uri = httpRequest.getRequestURI();
            String method = httpRequest.getMethod();
            HttpSession session = httpRequest.getSession(false);
            String username = (session != null) ? (String) session.getAttribute("user") : null;

            log.warn("eWAF BLOCK: Rejected request from blacklisted IP '{}'. Target URI: {}", clientIp, uri);

            // Registramos el impacto en la BD sin frenar el rechazo
            WafLogger.getInstance().logAsync(clientIp, username, "BLACKLISTED", uri, method, "Access denied by IP blacklist.");

            // Si la IP del cliente está en la lista, se le prohíbe el acceso.
            HttpServletResponse httpResponse = (HttpServletResponse) response;
            httpResponse.sendError(HttpServletResponse.SC_FORBIDDEN, "Your IP address has been blocked.");
            return; // Detiene la petición
        }

        // Si no está bloqueada, la petición continúa.
        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {}
}