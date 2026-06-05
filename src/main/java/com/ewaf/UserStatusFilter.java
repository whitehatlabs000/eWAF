package com.ewaf;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;


public class UserStatusFilter implements Filter {

    private DataSource ds;
    private Set<String> ignoreExtensions = new HashSet<>();
    private Set<String> ignorePaths = new HashSet<>();

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        ServletContext context = filterConfig.getServletContext();

        // 1. Obtener el DataSource
        this.ds = (DataSource) context.getAttribute("dbDataSource");
        if (this.ds == null) {
            throw new ServletException("DataSource not found in context.");
        }

        // 2. Obtener baseUrl para construir rutas absolutas correctamente
        String baseUrlRaw = (String) context.getAttribute("appBaseUrl");
        final String baseUrl = (baseUrlRaw == null || baseUrlRaw.equals("/")) ? "" : baseUrlRaw;

        // 3. Cargar extensiones a ignorar (Optimización)
        String extensionsStr = (String) context.getAttribute("logfilter.ignore.extensions");
        if (extensionsStr != null && !extensionsStr.isEmpty()) {
            this.ignoreExtensions = Arrays.stream(extensionsStr.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(String::toLowerCase)
                    .collect(Collectors.toSet());
        }

        // 4. Cargar rutas a ignorar
        String pathsStr = (String) context.getAttribute("userstatus.ignore.paths");
        if (pathsStr != null && !pathsStr.isEmpty()) {
            this.ignorePaths = Arrays.stream(pathsStr.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    // Concatenamos la baseUrl a la ruta configurada
                    .map(path -> baseUrl + path)
                    .collect(Collectors.toSet());
        }

        System.out.println("UserStatusFilter: Initialized. Ignoring " + ignoreExtensions.size() + " extensions and " + ignorePaths.size() + " routes.");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        String uri = httpRequest.getRequestURI();

        // VALIDACIÓN TEMPRANA (Escudo DoS): Rechazar URIs masivas antes de asfixiar la RAM con toLowerCase()
        if (uri != null && uri.length() > 2048) {
            httpResponse.sendError(HttpServletResponse.SC_REQUEST_URI_TOO_LONG, "URI is too long.");
            return;
        }

        String uriLower = uri.toLowerCase();

        // --- A. Optimización: Saltar por Extensión ---
        for (String ext : ignoreExtensions) {
            if (uriLower.endsWith(ext)) {
                chain.doFilter(request, response);
                return;
            }
        }

        // --- B. Optimización: Saltar por Ruta ---
        for (String path : ignorePaths) {
            // Usamos startsWith para ignorar la ruta y sus sub-rutas
            if (uri.startsWith(path)) {
                chain.doFilter(request, response);
                return;
            }
        }

        // --- Verificación de Estado del Usuario ---
        HttpSession session = httpRequest.getSession(false);

        // Solo verificamos si HAY una sesión y un usuario logueado
        if (session != null && session.getAttribute("user") != null) {
            String username = (String) session.getAttribute("user");

            if (!isUserActive(username)) {
                // Usuario BANEADO o ELIMINADO detectado

                // 1. Destruir la sesión inmediatamente
                session.invalidate();

                // 2. Redirigir al login con mensaje de error
                String loginPath = httpRequest.getContextPath() + "/login";

                // Evitamos bucle infinito si ya estamos en el login
                if (!uri.equals(loginPath)) {
                    httpResponse.sendRedirect(loginPath + "?error=account_disabled");
                    return;
                }
            }
        }

        // Si todo está bien (o no hay usuario), continuamos
        chain.doFilter(request, response);
    }

    private boolean isUserActive(String username) {
        String sql = "SELECT active FROM usuarios WHERE username = ?";

        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBoolean("active");
                } else {
                    return false; // Usuario no encontrado = inactivo
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            // En caso de error de DB, permitimos el paso para no bloquear a todos por un fallo técnico
            return true;
        }
    }

    @Override
    public void destroy() {}
}