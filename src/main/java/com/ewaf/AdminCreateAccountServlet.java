package com.ewaf;

import com.ewaf.IPUtils;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.mindrot.jbcrypt.BCrypt;


import jakarta.servlet.ServletContext;
import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@WebServlet("/admin-create_account")
public class AdminCreateAccountServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(AdminCreateAccountServlet.class);

    private DataSource ds;
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_.-]{3,25}$");

    @Override
    public void init() throws ServletException {
        ServletContext context = getServletContext();
        this.ds = (DataSource) context.getAttribute("dbDataSource");

        if (this.ds == null) {
            log.error("Critical failure: DataSource not found in context for eWAF AdminCreateAccountServlet.");
            throw new ServletException("Critical failure: DataSource not found in context for eWAF AdminCreateAccountServlet.");
        }
        log.info("eWAF AdminCreateAccountServlet initialized successfully.");
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        HttpSession session = req.getSession(false);
        // 1. Verificar Permisos: Solo Admin
        if (session == null || session.getAttribute("user") == null || !"admin".equalsIgnoreCase((String) session.getAttribute("tipoUsuario"))) {
            String user = (session != null && session.getAttribute("user") != null) ? (String) session.getAttribute("user") : "Unauthenticated";
            log.warn("SECURITY WARNING: Non-admin user '{}' attempted to access eWAF Admin Create Account page.", user);
            resp.sendRedirect("home");
            return;
        }

        // Recuperar mensajes flash (Error/Ok)
        String flashError = (String) session.getAttribute("flashError");
        if (flashError != null) {
            req.setAttribute("error", flashError);
            session.removeAttribute("flashError");
        }
        String okMessage = (String) session.getAttribute("ok");
        if (okMessage != null) {
            req.setAttribute("ok", okMessage);
            session.removeAttribute("ok");
        }

        // Generar CSRF
        String csrfToken = UUID.randomUUID().toString();
        session.setAttribute("csrfToken", csrfToken);
        req.setAttribute("csrfToken", csrfToken);

        req.getRequestDispatcher("/WEB-INF/jsp/admin-create_account.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        HttpSession session = req.getSession(false);

        // 1. Verificar Permisos: Solo Admin
        if (session == null || session.getAttribute("user") == null || !"admin".equalsIgnoreCase((String) session.getAttribute("tipoUsuario"))) {
            String user = (session != null && session.getAttribute("user") != null) ? (String) session.getAttribute("user") : "Unauthenticated";
            log.warn("SECURITY WARNING: Non-admin user '{}' attempted to POST to eWAF Admin Create Account.", user);
            resp.sendRedirect("login");
            return;
        }

        String creatorAdmin = (String) session.getAttribute("user");
        String newUser = req.getParameter("username");
        String newPass = req.getParameter("password");
        String formToken = req.getParameter("csrfToken");
        String sessionToken = (String) session.getAttribute("csrfToken");

        // 2. CSRF
        if (sessionToken == null || formToken == null || !sessionToken.equals(formToken)) {
            log.warn("SECURITY WARNING: Invalid CSRF token on account creation attempt by eWAF admin '{}'", creatorAdmin);
            session.setAttribute("flashError", "Invalid CSRF token.");
            resp.sendRedirect("admin-create_account");
            return;
        }

        // 3. Validaciones de Entrada
        // Escudo DoS: Verificamos la longitud máxima ANTES de invocar al motor Regex para ahorrar CPU
        if (newUser == null || newUser.length() > 25 || !USERNAME_PATTERN.matcher(newUser).matches()) {
            session.setAttribute("flashError", "Invalid username format.");
            resp.sendRedirect("admin-create_account");
            return;
        }
        if (newPass == null || newPass.length() < 6 || newPass.length() > 100) {
            session.setAttribute("flashError", "Password must be 6-100 characters.");
            resp.sendRedirect("admin-create_account");
            return;
        }

        Connection conn = null;
        try {
            conn = ds.getConnection();
            conn.setAutoCommit(false); // Transacción Manual

            // 4. Verificar duplicados
            try (PreparedStatement checkPs = conn.prepareStatement("SELECT 1 FROM usuarios WHERE username=?")) {
                checkPs.setString(1, newUser);
                try (ResultSet rs = checkPs.executeQuery()) {
                    if (rs.next()) {
                        session.setAttribute("flashError", "Username already exists.");
                        conn.rollback();
                        resp.sendRedirect("admin-create_account");
                        return;
                    }
                }
            }

            // 5. Insertar Nuevo Admin (Tipo forzado a 'admin')
            String hash = BCrypt.hashpw(newPass, BCrypt.gensalt());
            String insertSql = "INSERT INTO usuarios (username, password, tipo, active) VALUES (?, ?, 'admin', 1)";

            try (PreparedStatement insPs = conn.prepareStatement(insertSql)) {
                insPs.setString(1, newUser);
                insPs.setString(2, hash);
                insPs.executeUpdate();
            }

            // 6. Insertar Log de Auditoría en access_logs
            String logSql = "INSERT INTO access_logs (ip_address, username, event_type, details) VALUES (?, ?, ?, ?)";
            try (PreparedStatement logPs = conn.prepareStatement(logSql)) {
                String adminIp = IPUtils.getClientIp(req);

                logPs.setString(1, adminIp);
                logPs.setString(2, creatorAdmin); // Registramos QUÉ admin creó la cuenta
                logPs.setString(3, "ACCOUNT_CREATED");
                logPs.setString(4, "Created new admin user: " + newUser);
                logPs.executeUpdate();
            }

            // 7. Commit
            conn.commit();

            session.setAttribute("ok", "Admin user '" + newUser + "' created successfully.");
            resp.sendRedirect("admin-create_account");

        } catch (SQLException e) {
            log.error("Database error while creating new admin account '{}' by '{}' in eWAF. Initiating rollback.", newUser, creatorAdmin, e);
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    log.error("CRITICAL ERROR: Failed to rollback eWAF transaction.", ex);
                }
            }
            throw new ServletException("Database error creating account", e);
        } finally {
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
}