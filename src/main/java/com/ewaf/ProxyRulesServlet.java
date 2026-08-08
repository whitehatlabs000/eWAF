package com.ewaf;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import jakarta.servlet.ServletContext;
import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@WebServlet("/proxy-rules")
public class ProxyRulesServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(ProxyRulesServlet.class);

    private DataSource ds;

    @Override
    public void init() throws ServletException {
        ServletContext context = getServletContext();
        this.ds = (DataSource) context.getAttribute("dbDataSource");

        if (this.ds == null) {
            log.error("Critical failure: DataSource not found in context for eWAF ProxyRulesServlet.");
            throw new ServletException("Critical failure: DataSource not found in context for eWAF ProxyRulesServlet.");
        }
        log.info("eWAF ProxyRulesServlet initialized successfully.");
    }


    public static class ProxyRoute {
        private int id;
        private String incomingPath;
        private String targetUrl;
        private boolean active;
        private String customReplacements;
        private String engineType;
        private int cacheTtlSeconds;
        private boolean useModSecurity;

        public ProxyRoute(int id, String incomingPath, String targetUrl, boolean active, String customReplacements, String engineType, int cacheTtlSeconds, boolean useModSecurity) {
            this.id = id;
            this.incomingPath = incomingPath;
            this.targetUrl = targetUrl;
            this.active = active;
            this.customReplacements = customReplacements;
            this.engineType = engineType != null ? engineType.toUpperCase() : "NATIVE";
            this.cacheTtlSeconds = cacheTtlSeconds;
            this.useModSecurity = useModSecurity;
        }

        public int getId() { return id; }
        public String getIncomingPath() { return incomingPath; }
        public String getTargetUrl() { return targetUrl; }
        public boolean isActive() { return active; }
        public String getCustomReplacements() { return customReplacements; }
        public String getEngineType() { return engineType; }
        public int getCacheTtlSeconds() { return cacheTtlSeconds; }
        public boolean isUseModSecurity() { return useModSecurity; } // NUEVO GETTER

        // Prepara el string para ser puesto dentro de un onclick de JS: onclick="func('...')"
        public String getEscapedCustomReplacements() {
            if (customReplacements == null || customReplacements.isEmpty()) {
                return "";
            }
            // 1. Escapar barras invertidas existentes (para JSON)
            String safe = customReplacements.replace("\\", "\\\\");
            // 2. Escapar comillas simples (porque el onclick usa ')
            safe = safe.replace("'", "\\'");
            // 3. Escapar comillas dobles (para no romper el HTML attribute)
            safe = safe.replace("\"", "&quot;");
            // 4. Quitar saltos de línea para que no rompa el JS
            safe = safe.replace("\r", "").replace("\n", "\\n");
            return safe;
        }
    }

    private boolean isAdminSession(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            resp.sendRedirect(req.getContextPath() + "/login");
            return false;
        }
        String role = (String) session.getAttribute("tipoUsuario");
        if (role == null || !"admin".equalsIgnoreCase(role)) {
            String user = (String) session.getAttribute("user");
            log.warn("SECURITY WARNING: Non-admin user '{}' attempted to access eWAF Proxy Rules.", user);
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Acceso Denegado");
            return false;
        }
        return true;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (!isAdminSession(req, resp)) return;

        List<ProxyRoute> routes = new ArrayList<>();

        String sql = "SELECT id, incoming_path, target_url, active, custom_replacements, engine_type, cache_ttl_seconds, use_modsecurity FROM proxy_routes ORDER BY id DESC";

        try (Connection cn = ds.getConnection();
             PreparedStatement ps = cn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                routes.add(new ProxyRoute(
                        rs.getInt("id"),
                        rs.getString("incoming_path"),
                        rs.getString("target_url"),
                        rs.getBoolean("active"),
                        rs.getString("custom_replacements"),
                        rs.getString("engine_type"),
                        rs.getInt("cache_ttl_seconds"),
                        rs.getBoolean("use_modsecurity") // NUEVO PARÁMETRO
                ));
            }

        } catch (SQLException e) {
            req.setAttribute("error", "Error cargando reglas.");
            log.error("Database error while loading proxy rules in eWAF.", e);
        }

        req.setAttribute("routes", routes);
        req.getRequestDispatcher("/WEB-INF/jsp/proxy_rules.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (!isAdminSession(req, resp)) return;
        req.setCharacterEncoding("UTF-8");

        String action = req.getParameter("action");
        String path = req.getParameter("incomingPath");
        String target = req.getParameter("targetUrl");
        String replacements = req.getParameter("customReplacements");
        boolean active = req.getParameter("active") != null;

        // Leer y limpiar el motor seleccionado
        String engineTypeRaw = req.getParameter("engineType");
        String engineType = (engineTypeRaw != null && !engineTypeRaw.isEmpty()) ? engineTypeRaw.toUpperCase() : "NATIVE";

        // Leer los segundos de caché (con fallback a 0 si hay error)
        String cacheTtlRaw = req.getParameter("cacheTtlSeconds");
        int cacheTtl = 0;
        try {
            if (cacheTtlRaw != null && !cacheTtlRaw.isEmpty()) {
                cacheTtl = Integer.parseInt(cacheTtlRaw);
            }
        } catch (NumberFormatException e) {
            log.debug("TTL de caché inválido recibido, asignando 0");
        }

        // Capturar el switch de ModSecurity
        boolean useModSecurity = req.getParameter("useModSecurity") != null;

        // VALIDACIÓN TEMPRANA: Proteger base de datos contra Data Truncation y payloads gigantes (DoS)
        if (path != null && path.length() > 255) {
            log.warn("DoS SHIELD: Blocked extremely long incoming path (length: {}) in eWAF proxy rule creation.", path.length());
            resp.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "El Incoming Path excede el límite de 255 caracteres.");
            return;
        }
        if (target != null && target.length() > 2048) { //Largo maximo URLS
            log.warn("DoS SHIELD: Blocked extremely long target URL (length: {}) in eWAF proxy rule creation.", target.length());
            resp.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "La Target URL excede el límite seguro de 2048 caracteres.");
            return;
        }
        if (replacements != null && replacements.length() > 5000) {
            log.warn("DoS SHIELD: Blocked extremely long JSON replacements payload (length: {}) in eWAF proxy rule creation.", replacements.length());
            resp.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "Las reglas JSON exceden el límite de 5000 caracteres.");
            return;
        }

        try (Connection cn = ds.getConnection()) {
            boolean routesChanged = false; // Bandera para saber si alteramos la DB

            if ("create".equals(action)) {
                if(path != null && !path.isEmpty() && target != null && !target.isEmpty()) {
                    String sql = "INSERT INTO proxy_routes (incoming_path, target_url, active, custom_replacements, engine_type, cache_ttl_seconds, use_modsecurity) VALUES (?, ?, ?, ?, ?, ?, ?)";
                    try (PreparedStatement ps = cn.prepareStatement(sql)) {
                        ps.setString(1, path.trim());
                        ps.setString(2, target.trim());
                        ps.setBoolean(3, active);
                        ps.setString(4, replacements != null ? replacements.trim() : "");
                        ps.setString(5, engineType);
                        ps.setInt(6, cacheTtl);
                        ps.setBoolean(7, useModSecurity);
                        ps.executeUpdate();
                        routesChanged = true; // Marcar cambio
                    }
                }
            } else if ("update".equals(action)) {
                String idStr = req.getParameter("id");
                if (idStr != null && path != null && !path.isEmpty()) {
                    int id = Integer.parseInt(idStr);
                    String sql = "UPDATE proxy_routes SET incoming_path=?, target_url=?, active=?, custom_replacements=?, engine_type=?, cache_ttl_seconds=?, use_modsecurity=? WHERE id=?";
                    try (PreparedStatement ps = cn.prepareStatement(sql)) {
                        ps.setString(1, path.trim());
                        ps.setString(2, target.trim());
                        ps.setBoolean(3, active);
                        ps.setString(4, replacements != null ? replacements.trim() : "");
                        ps.setString(5, engineType);
                        ps.setInt(6, cacheTtl);
                        ps.setBoolean(7, useModSecurity);
                        ps.setInt(8, id);
                        ps.executeUpdate();
                        routesChanged = true; // Marcar cambio
                    }
                }
            } else if ("delete".equals(action)) {
                String idStr = req.getParameter("id");
                if (idStr != null) {
                    int id = Integer.parseInt(idStr);
                    try (PreparedStatement ps = cn.prepareStatement("DELETE FROM proxy_routes WHERE id=?")) {
                        ps.setInt(1, id);
                        ps.executeUpdate();
                        routesChanged = true; // Marcar cambio
                    }
                }
            }

            // Sincronización de Memoria RAM en Tiempo Real
            // Si creamos, editamos o borramos una regla con éxito, le avisamos al filtro
            // que recargue su ConcurrentHashMap inmediatamente.
            if (routesChanged) {
                GlobalRouterFilter.refreshMemoryMap(this.ds);
                log.info("eWAF ProxyRulesServlet: Reglas modificadas. Se ordenó refrescar el mapa de memoria RAM del GlobalRouter.");
            }

            resp.sendRedirect(req.getContextPath() + "/proxy-rules?msg=success");

        } catch (NumberFormatException e) {
            // Manejo limpio si alteran el HTML y envían un ID que no es numérico (Previene Error 500)
            log.warn("SECURITY WARNING: Invalid ID format received in eWAF ProxyRulesServlet. Possible manipulation attempt.", e);
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "ID inválido.");
        } catch (Exception e) {
            log.error("Internal server error while processing proxy rule in eWAF.", e);
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error interno del servidor.");
        }
    }
}