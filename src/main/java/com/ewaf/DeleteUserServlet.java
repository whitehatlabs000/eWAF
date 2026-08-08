package com.ewaf;

import com.ewaf.IPUtils;
import com.google.gson.JsonObject;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.ewaf.WafLogger;

@WebServlet("/delete_user")
public class DeleteUserServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(DeleteUserServlet.class);

    private DataSource ds;

    @Override
    public void init() throws ServletException {
        ServletContext context = getServletContext();
        this.ds = (DataSource) context.getAttribute("dbDataSource");

        if (this.ds == null) {
            log.error("Critical failure: Missing essential resources ('dbDataSource') in context for eWAF DeleteUserServlet.");
            throw new ServletException("Critical failure: Missing essential resources in context for eWAF DeleteUserServlet.");
        }
        log.info("eWAF DeleteUserServlet initialized successfully.");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // 1. Verificaciones de Sesión básicas
        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            sendJsonError(resp, HttpServletResponse.SC_FORBIDDEN, "No session.");
            return;
        }

        String adminUser = (String) session.getAttribute("user");
        String targetUser = req.getParameter("username"); // username a borrar

        // VALIDACIÓN TEMPRANA (Escudo DoS): Evitar sobrecarga de RAM con nombres gigantes
        if (targetUser != null && targetUser.length() > 50) {
            log.warn("DoS SHIELD: Blocked extremely long target username (length: {}) in eWAF user deletion.", targetUser.length());
            sendJsonError(resp, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "Target username is too long.");
            return;
        }

        // 2. Verificaciones de Seguridad (CSRF y Datos)
        String csrfHeader = req.getHeader("X-CSRF-Token");
        String csrfSession = (String) session.getAttribute("csrfToken");

        if (targetUser == null || targetUser.trim().isEmpty() || csrfHeader == null || csrfSession == null || !csrfHeader.equals(csrfSession)) {
            log.warn("SECURITY WARNING: Invalid request or CSRF token on user deletion attempt by eWAF admin '{}'.", adminUser);
            sendJsonError(resp, HttpServletResponse.SC_FORBIDDEN, "Invalid request or CSRF token.");
            return;
        }

        // 3. Evitar el "Suicidio" del Admin (Un admin no puede borrarse a sí mismo desde este panel)
        if (adminUser.equals(targetUser)) {
            log.warn("SECURITY WARNING: eWAF Admin '{}' attempted to delete their own account.", adminUser);
            sendJsonError(resp, HttpServletResponse.SC_BAD_REQUEST, "Admin cannot delete their own account.");
            return;
        }

        Connection conn = null;
        try {
            conn = ds.getConnection();

            // 4. Verificar si quien solicita ES realmente Admin (Lectura)
            boolean isAdmin = false;
            try (PreparedStatement ps = conn.prepareStatement("SELECT tipo FROM usuarios WHERE username=?")) {
                ps.setString(1, adminUser);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && "admin".equalsIgnoreCase(rs.getString("tipo"))) {
                        isAdmin = true;
                    }
                }
            }

            if (!isAdmin) {
                log.warn("SECURITY WARNING: Non-admin user '{}' attempted to delete account '{}' in eWAF.", adminUser, targetUser);
                conn.close();
                sendJsonError(resp, HttpServletResponse.SC_FORBIDDEN, "Action not allowed.");
                return;
            }

            // --- INICIO DE TRANSACCIÓN ATÓMICA ---
            conn.setAutoCommit(false);

            // 5. Borrar el usuario (Escritura 1)
            boolean deleteSuccess = false;
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM usuarios WHERE username = ?")) {
                ps.setString(1, targetUser);
                int rowsAffected = ps.executeUpdate();
                if (rowsAffected > 0) {
                    deleteSuccess = true;
                }
            }

            if (deleteSuccess) {
                // Confirmar la eliminación del usuario en la base de datos
                conn.commit();

                // 6. Insertar Log de Auditoría de forma asíncrona
                String adminIp = IPUtils.getClientIp(req);
                String path = req.getRequestURI();
                String method = req.getMethod();
                WafLogger.getInstance().logAsync(adminIp, adminUser, "ACCOUNT_DELETED", path, method, "Deleted user: " + targetUser);

                // Respuesta Exitosa
                resp.setContentType("application/json");
                resp.setCharacterEncoding("UTF-8");
                JsonObject jsonResponse = new JsonObject();
                jsonResponse.addProperty("success", true);
                jsonResponse.addProperty("message", "User deleted successfully.");
                resp.getWriter().write(jsonResponse.toString());

            } else {
                // El usuario no existía o no se pudo borrar
                conn.rollback();
                sendJsonError(resp, HttpServletResponse.SC_NOT_FOUND, "User not found.");
            }

        } catch (SQLException e) {
            // Rollback en caso de error técnico
            log.error("Database error while deleting user '{}' by admin '{}' in eWAF. Initiating rollback.", targetUser, adminUser, e);
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    log.error("CRITICAL ERROR: Failed to rollback eWAF transaction.", ex);
                }
            }
            sendJsonError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database error.");
        } finally {
            // Limpieza y cierre
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                } catch (SQLException e) {
                    log.error("Failed to restore autoCommit in eWAF.", e);
                }
                try {
                    conn.close();
                } catch (SQLException e) {
                    log.error("Failed to close database connection in eWAF.", e);
                }
            }
        }
    }

    private void sendJsonError(HttpServletResponse resp, int statusCode, String message) throws IOException {
        resp.setStatus(statusCode);
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        JsonObject errorJson = new JsonObject();
        errorJson.addProperty("success", false);
        errorJson.addProperty("message", message);
        resp.getWriter().write(errorJson.toString());
    }
}