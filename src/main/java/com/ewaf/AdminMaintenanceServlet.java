package com.ewaf;


import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@WebServlet("/admin-maintenance")
public class AdminMaintenanceServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(AdminMaintenanceServlet.class);

    @Override
    public void init() throws ServletException {
        log.info("eWAF AdminMaintenanceServlet initialized successfully.");
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        // 1. Seguridad: Solo Admin
        HttpSession session = request.getSession(false);
        if (session == null || !"admin".equalsIgnoreCase((String) session.getAttribute("tipoUsuario"))) {
            String user = (session != null && session.getAttribute("user") != null) ? (String) session.getAttribute("user") : "Unauthenticated";
            log.warn("SECURITY WARNING: Non-admin user '{}' attempted to access eWAF Admin Maintenance page.", user);
            response.sendRedirect(request.getContextPath() + "/home");
            return;
        }

        // 2. Pasar estado actual al JSP
        request.setAttribute("isMaintenanceMode", MaintenanceManager.isMaintenanceModeEnabled());

        // 3. CSRF Token
        String csrfToken = UUID.randomUUID().toString();
        session.setAttribute("csrfToken", csrfToken);
        request.setAttribute("csrfToken", csrfToken);

        // 4. Pasar mensajes flash (si existen)
        if(session.getAttribute("message") != null){
            request.setAttribute("message", session.getAttribute("message"));
            session.removeAttribute("message");
        }

        request.getRequestDispatcher("/WEB-INF/jsp/admin-maintenance.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        // 1. Seguridad de Sesión
        HttpSession session = request.getSession(false);
        if (session == null || !"admin".equalsIgnoreCase((String) session.getAttribute("tipoUsuario"))) {
            String user = (session != null && session.getAttribute("user") != null) ? (String) session.getAttribute("user") : "Unauthenticated";
            log.warn("SECURITY WARNING: Non-admin user '{}' attempted to POST to eWAF Admin Maintenance page.", user);
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        // 2. Seguridad CSRF
        String formToken = request.getParameter("csrfToken");
        String sessionToken = (String) session.getAttribute("csrfToken");

        if (sessionToken == null || formToken == null || !sessionToken.equals(formToken)) {
            log.warn("SECURITY WARNING: Invalid CSRF token on maintenance toggle attempt by eWAF admin '{}'", session.getAttribute("user"));
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid CSRF token.");
            return;
        }
        session.removeAttribute("csrfToken");

        // 3. Procesar Acción
        String action = request.getParameter("action");

        // VALIDACIÓN TEMPRANA (Escudo DoS): Evitar que un parámetro gigante sature la RAM
        if (action != null && action.length() > 20) {
            log.warn("DoS SHIELD: Blocked extremely long action parameter (length: {}) in eWAF maintenance toggle by admin '{}'", action.length(), session.getAttribute("user"));
            response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "Action parameter is too long.");
            return;
        }

        if ("toggle".equals(action)) {
            boolean currentState = MaintenanceManager.isMaintenanceModeEnabled();
            boolean newState = !currentState;

            MaintenanceManager.setMaintenanceMode(newState);

            log.info("eWAF MAINTENANCE MODE TOGGLED: State changed to {} by admin '{}'", newState, session.getAttribute("user"));

            String msg = newState
                    ? "Maintenance is ON. Only administrators can access it.."
                    : "Maintenance is disabled. The site is public again..";

            session.setAttribute("message", msg);
        }

        // 4. Redirigir
        response.sendRedirect("admin-maintenance");
    }
}