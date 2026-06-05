package com.ewaf;

import com.google.gson.Gson;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import jakarta.servlet.ServletContext;
import javax.sql.DataSource;
import java.io.IOException;
import java.io.PrintWriter;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@WebServlet("/admin-manage_users")
public class AdminManageUsersServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(AdminManageUsersServlet.class);

    private DataSource ds;
    private static final SecureRandom sr = new SecureRandom();

    public static class UserData {
        public String username, tipo;
        public boolean active;
        public String lastLogin;
    }

    @Override
    public void init() throws ServletException {
        ServletContext context = getServletContext();
        this.ds = (DataSource) context.getAttribute("dbDataSource");

        if (this.ds == null) {
            log.error("Critical failure: DataSource not found in context for eWAF AdminManageUsersServlet.");
            throw new ServletException("Critical failure: DataSource not found in context for eWAF AdminManageUsersServlet.");
        }
        log.info("eWAF AdminManageUsersServlet initialized successfully.");
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            resp.sendRedirect("home");
            return;
        }

        // Verificación de Admin
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT tipo FROM usuarios WHERE username=?")) {
            ps.setString(1, (String) session.getAttribute("user"));
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || !"admin".equalsIgnoreCase(rs.getString("tipo"))) {
                    String user = (session != null && session.getAttribute("user") != null) ? (String) session.getAttribute("user") : "Unauthenticated";
                    log.warn("SECURITY WARNING: Non-admin user '{}' attempted to access eWAF Admin Manage Users.", user);
                    resp.sendRedirect("home");
                    return;
                }
            }
        } catch (SQLException e) {
            log.error("Database error during admin check for user '{}' in eWAF", session.getAttribute("user"), e);
            throw new ServletException("Admin check failed", e);
        }

        // CSRF Token Generation
        String csrfToken = (String) session.getAttribute("csrfToken");
        if (csrfToken == null) {
            byte[] tokenBytes = new byte[32];
            sr.nextBytes(tokenBytes); // Usamos la instancia estática rápida
            csrfToken = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
            session.setAttribute("csrfToken", csrfToken);
        }
        req.setAttribute("csrfToken", csrfToken);

        String action = req.getParameter("action");
        if ("load_users".equals(action)) {
            handleAjaxUsers(req, resp);
        } else {
            handleInitialPage(req, resp);
        }
    }

    private void handleInitialPage(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setAttribute("q", req.getParameter("q"));
        req.setAttribute("order", req.getParameter("order"));
        req.setAttribute("filter", req.getParameter("filter"));
        req.getRequestDispatcher("/WEB-INF/jsp/admin-manage_users.jsp").forward(req, resp);
    }

    private String escapeSqlLike(String value) {
        if (value == null || value.isEmpty()) return "";
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private void handleAjaxUsers(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String rawQ = req.getParameter("q");

        // VALIDACIÓN TEMPRANA (Escudo DoS): Rechazo estricto en lugar de truncado silencioso
        if (rawQ != null && rawQ.length() > 100) {
            log.warn("DoS SHIELD: Blocked extremely long search query (length: {}) in eWAF AdminManageUsersServlet.", rawQ.length());
            resp.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "Search query is too long.");
            return;
        }

        String q = rawQ != null ? rawQ.trim() : "";

        String order = req.getParameter("order") != null ? req.getParameter("order") : "newest";
        String filter = req.getParameter("filter");

        String orderBy;
        switch (order) {
            case "last_login": orderBy = "last_login_ts DESC"; break; // Ordenar por último acceso
            case "username": orderBy = "u.username ASC"; break;
            case "oldest": orderBy = "u.id ASC"; break;
            case "newest":
            default: orderBy = "u.id DESC"; break;
        }

        int page = 1;
        final int LIMIT = 20;

        try {
            String pageParam = req.getParameter("page");
            if (pageParam != null && !pageParam.isEmpty()) {
                page = Integer.parseInt(pageParam);
                if (page < 1) page = 1;
            }
        } catch (NumberFormatException e) {
            log.warn("Invalid page parameter format: '{}' in eWAF AdminManageUsersServlet. Defaulting to page 1.", req.getParameter("page"));
            page = 1;
        }
        int offset = (page - 1) * LIMIT;

        List<UserData> users = new ArrayList<>();
        try (Connection conn = ds.getConnection()) {

            // Obtenemos el timestamp del último LOGIN_SUCCESS desde access_logs
            StringBuilder sqlBuilder = new StringBuilder(
                    "SELECT u.username, u.active, u.tipo, " +
                            "(SELECT MAX(event_timestamp) FROM access_logs al WHERE al.username = u.username AND al.event_type = 'LOGIN_SUCCESS') as last_login_ts " +
                            "FROM usuarios u WHERE u.username LIKE ? ESCAPE '\\\\' "
            );

            // Si el filtro es "banned", buscamos usuarios inactivos (active = 0 o false)
            if ("banned".equals(filter)) {
                sqlBuilder.append(" AND u.active = 0");
            }

            sqlBuilder.append(" ORDER BY ").append(orderBy).append(" LIMIT ? OFFSET ?");

            try (PreparedStatement ps = conn.prepareStatement(sqlBuilder.toString())) {
                // Sanitizamos los comodines para evitar ataques de asfixia a la base de datos
                ps.setString(1, "%" + escapeSqlLike(q) + "%");
                ps.setInt(2, LIMIT);
                ps.setInt(3, offset);

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        UserData ud = new UserData();
                        ud.username = rs.getString("username");
                        ud.active = rs.getBoolean("active");
                        ud.tipo = rs.getString("tipo");

                        // Convertir Timestamp a String o "Never"
                        java.sql.Timestamp ts = rs.getTimestamp("last_login_ts");
                        if (ts != null) {
                            // Formato simple para JSON (yyyy-MM-dd HH:mm:ss)
                            // En JS lo formatearemos más bonito ("hace 2 horas")
                            ud.lastLogin = ts.toString().split("\\.")[0];
                        } else {
                            ud.lastLogin = null;
                        }

                        users.add(ud);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Database error while loading users for eWAF admin panel with query '{}'", q, e);
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error loading users.");
            return;
        }

        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        try (PrintWriter out = resp.getWriter()) {
            out.print(new Gson().toJson(users));
        }
    }
}