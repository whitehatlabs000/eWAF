package com.ewaf;

import com.google.gson.JsonObject;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.io.PrintWriter;
import java.security.SecureRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@WebServlet("/api/csrf-token")
public class CsrfTokenServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(CsrfTokenServlet.class);


    private static final SecureRandom secureRandom = new SecureRandom();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        // 1: Validación de Origen (Evitar robo del token)
        // Nos aseguramos de que esta petición venga de un Fetch/AJAX interno y no de un navegador directo o sitio de terceros.
        String requestedWith = request.getHeader("X-Requested-With");
        String fetchSite = request.getHeader("Sec-Fetch-Site");

        if (!"XMLHttpRequest".equals(requestedWith) && !"same-origin".equals(fetchSite)) {
            log.warn("CSRF API blocked suspicious request from IP: {}", request.getRemoteAddr());
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "API access denied");
            return;
        }

        // 2: Prevención de DoS en Memoria (Sesiones Zombie)
        // Usamos false. Si no hay sesión, NO creamos una nueva.
        HttpSession session = request.getSession(false);

        if (session == null) {
            log.debug("CSRF request rejected: No active session for IP: {}", request.getRemoteAddr());
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "No active session.");
            return;
        }

        // 2. Buscar el token en la sesión.
        String csrfToken = (String) session.getAttribute("csrfToken");

        // 3. Si por alguna razón no existe en esta sesión, generarlo.
        if (csrfToken == null) {
            csrfToken = generateCSRFToken();
            session.setAttribute("csrfToken", csrfToken);
            log.debug("New CSRF token generated via API for session ID: {}", session.getId());
        }

        // 4. Preparar la respuesta JSON.
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        JsonObject jsonResponse = new JsonObject();
        jsonResponse.addProperty("csrfToken", csrfToken);

        // 5. Enviar la respuesta.
        try (PrintWriter out = response.getWriter()) {
            out.print(jsonResponse.toString());
            out.flush();
        }
    }

    private String generateCSRFToken() {
        byte[] bytes = new byte[16];
        secureRandom.nextBytes(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}