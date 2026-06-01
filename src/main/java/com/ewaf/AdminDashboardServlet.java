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

@WebServlet("/admin-dashboard")
public class AdminDashboardServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(AdminDashboardServlet.class);

    @Override
    public void init() throws ServletException {
        log.info("eWAF AdminDashboardServlet initialized successfully.");
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        // 1. Obtener la sesión existente (false: no crear una nueva si no existe)
        HttpSession session = request.getSession(false);

        // 2. SEGURIDAD: Verificar si es Administrador
        if (session == null || !"admin".equalsIgnoreCase((String) session.getAttribute("tipoUsuario"))) {
            String user = (session != null && session.getAttribute("user") != null) ? (String) session.getAttribute("user") : "Unauthenticated";
            log.warn("SECURITY WARNING: Non-admin user '{}' attempted to access eWAF Admin Dashboard.", user);
            response.sendRedirect(request.getContextPath() + "/home");
            return;
        }

        // 3. GESTIÓN DE CSRF
        // Reutilizamos el token de sesión o creamos uno nuevo si no existe.
        String csrfToken = (String) session.getAttribute("csrfToken");
        if (csrfToken == null) {
            csrfToken = UUID.randomUUID().toString();
            session.setAttribute("csrfToken", csrfToken);
        }
        // Lo pasamos al request para que el JSP lo imprima en el <meta name="csrf-token">
        request.setAttribute("csrfToken", csrfToken);

        // 4. Forward
        request.getRequestDispatcher("/WEB-INF/jsp/admin-dashboard.jsp").forward(request, response);
    }
}