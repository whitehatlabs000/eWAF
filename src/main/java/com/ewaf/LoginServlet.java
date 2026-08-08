package com.ewaf;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import com.ewaf.IPUtils;
import com.ewaf.IPBlockManager;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.mindrot.jbcrypt.BCrypt;

import javax.sql.DataSource;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.ewaf.WafLogger;

@WebServlet("/login")
public class LoginServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(LoginServlet.class);

    private static final SecureRandom secureRandom = new SecureRandom();

    private DataSource ds;
    private String appBaseUrl;
    private int maxLoginAttempts;
    private int loginTimeWindowSeconds;

    // Cache para rastrear intentos fallidos.
    private Cache<String, LoginAttempt> attemptsCache;

    // Clase auxiliar para guardar el estado
    private static class LoginAttempt {
        int count;
        long firstAttemptTime; // Ventana fija desde el primer fallo

        LoginAttempt(int count, long firstAttemptTime) {
            this.count = count;
            this.firstAttemptTime = firstAttemptTime;
        }
    }

    @Override
    public void init() throws ServletException {
        try {
            ServletContext context = getServletContext();
            this.ds = (DataSource) context.getAttribute("dbDataSource");

            Integer maxAttempts = (Integer) context.getAttribute("maxLoginAttempts");
            Integer windowSec = (Integer) context.getAttribute("loginTimeWindowSeconds");

            // --- CARGAR APP BASE URL ---
            this.appBaseUrl = (String) context.getAttribute("appBaseUrl");
            // Nos aseguramos que no sea null para evitar NullPointerException después
            if (this.appBaseUrl == null) this.appBaseUrl = "";

            if (this.ds == null) {
                throw new ServletException("DataSource no encontrado en el contexto.");
            }

            this.maxLoginAttempts = (maxAttempts != null) ? maxAttempts : 5;
            this.loginTimeWindowSeconds = (windowSec != null) ? windowSec : 300;

            // --- INICIALIZACIÓN DE CAFFEINE ---
            // Configuramos expireAfterWrite con un tiempo un poco mayor a la ventana
            // solo para limpieza de memoria (garbage collection).
            // La lógica de negocio estricta la manejamos nosotros en el doPost.
            this.attemptsCache = Caffeine.newBuilder()
                    .expireAfterWrite(this.loginTimeWindowSeconds + 60, TimeUnit.SECONDS)
                    .maximumSize(10_000)
                    .build();

            log.info("eWAF LoginServlet initialized successfully. Max attempts: {}, Time window: {}s", this.maxLoginAttempts, this.loginTimeWindowSeconds);

        } catch (Exception e) {
            log.error("Failed to initialize eWAF LoginServlet.", e);
            throw new ServletException("Error initializing LoginServlet", e);
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        HttpSession session = req.getSession(false);
        if (session != null && session.getAttribute("user") != null) {
            resp.sendRedirect("admin-dashboard");
            return;
        }

        if (session == null) {
            session = req.getSession(true);
        }

        // --- LÓGICA FLASH ATTRIBUTE ---
        // Buscamos si hay un error guardado de un intento anterior
        String flashError = (String) session.getAttribute("flashError");

        if (flashError != null) {
            // 1. Lo pasamos al request para que el JSP lo vea
            req.setAttribute("error", flashError);

            // 2. Borramos de la sesión para que no salga si recarga la página
            session.removeAttribute("flashError");
        }
        // -----------------------------

        String csrfToken = generateCSRFToken();
        session.setAttribute("csrfToken", csrfToken);
        req.setAttribute("csrfToken", csrfToken);

        // Guarda la URL de la página anterior para redirigir después del login.
        String referrer = req.getHeader("Referer");

        // 1. Que no sea null.
        // 2. Que no sea login ni sign_up (bucle infinito).
        // 3. Que pertenezca a nuestro propio dominio (isValidReferrer).
        if (referrer != null &&
                !referrer.contains("/login") &&
                !referrer.contains("/sign_up") &&
                isValidReferrer(referrer, req)) {

            session.setAttribute("loginRedirectUrl", referrer);
        } else {
            // si viene de login o sign_up, o de un sitio externo, limpiamos.
            session.removeAttribute("loginRedirectUrl");
        }

        req.getRequestDispatcher("/WEB-INF/jsp/login.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        // IPUtils para obtener la IP real (soporte proxy)
        String ipAddress = IPUtils.getClientIp(req);


        // --- PRE-CHECK: ---
        // Solo leemos (peek), no modificamos.
        LoginAttempt existingAttempt = attemptsCache.getIfPresent(ipAddress);
        if (existingAttempt != null) {
            long now = System.currentTimeMillis();
            // Verificamos ventana de tiempo y límite
            if ((now - existingAttempt.firstAttemptTime <= loginTimeWindowSeconds * 1000L) &&
                    existingAttempt.count >= maxLoginAttempts) {

                // Bloqueo definitivo
                log.warn("eWAF SECURITY EVENT: IP '{}' exceeded max login attempts in pre-check. Adding to global blocklist.", ipAddress);
                IPBlockManager.addIP(ipAddress);
                attemptsCache.invalidate(ipAddress);
                resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Too many failed attempts. IP Blocked.");
                return;
            }
        }

        // --- . VALIDACIONES ESTÁNDAR ---
        String user = req.getParameter("username");
        String pass = req.getParameter("password");
        String token = req.getParameter("csrfToken");

        HttpSession session = req.getSession(false);
        if (session == null) {
            // Si la sesión expiró, creamos una nueva temporalmente solo para guardar el mensaje
            session = req.getSession(true);
            session.setAttribute("flashError", "Session expired. Please try again.");
            resp.sendRedirect("login");
            return;
        }

        String sessionToken = (String) session.getAttribute("csrfToken");
        if (token == null || !token.equals(sessionToken)) {
            log.warn("SECURITY WARNING: Invalid CSRF token on login attempt from IP '{}' in eWAF.", ipAddress);
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid CSRF token");
            return;
        }

        if (user == null || user.length() > 50 || pass == null || pass.length() > 100) {
            log.warn("DoS SHIELD: Blocked malformed or overly long login payload from IP '{}' targeting user '{}' in eWAF.", ipAddress, user);
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid data.");
            return;
        }

        try {
            UserResult result = validate(user, pass);

            if (result != null) {
                // --- LOGIN EXITOSO ---
                logLoginAttempt(result.username, ipAddress, true, req);

                // Limpiamos caché de intentos (Éxito borra historial de fallos)
                attemptsCache.invalidate(ipAddress);

                // Lógica de Sesión

                // Guardamos los datos que queremos mantener de la sesión vieja (la de invitado)
                String redirectUrl = (String) session.getAttribute("loginRedirectUrl");

                // Invalidamos la sesión actual (esto destruye el JSESSIONID viejo)
                session.invalidate();

                // Creamos una sesión completamente nueva con un JSESSIONID nuevo
                session = req.getSession(true);

                // Añadimos los datos del usuario autenticado A LA NUEVA SESIÓN
                session.setAttribute("user", result.username);
                session.setAttribute("userId", result.id);
                session.setAttribute("tipoUsuario", result.tipo);

                // rotamos el token CSRF también para la nueva sesión
                session.setAttribute("csrfToken", generateCSRFToken());

                // Usamos los datos que guardamos para redirigir
                if (redirectUrl != null && !redirectUrl.isEmpty()) {
                    // No es necesario remover "loginRedirectUrl", la sesión vieja fue destruida
                    resp.sendRedirect(redirectUrl);
                } else {
                    resp.sendRedirect("admin-dashboard");
                }

            } else {
                // --- LOGIN FALLIDO ---
                logLoginAttempt(user, ipAddress, false, req);

                // Usamos compute para manejar todo en una sola operación thread-safe
                LoginAttempt updatedStats = attemptsCache.asMap().compute(ipAddress, (key, val) -> {
                    long now = System.currentTimeMillis();

                    // Caso A: Primer intento fallido
                    if (val == null) {
                        return new LoginAttempt(1, now);
                    }

                    // Caso B: La ventana de tiempo expiró -> Reseteamos a 1
                    if (now - val.firstAttemptTime > (loginTimeWindowSeconds * 1000L)) {
                        return new LoginAttempt(1, now);
                    }

                    // Caso C: Dentro de la ventana -> Incrementamos
                    val.count++;
                    return val;
                });

                // Verificamos el resultado ATÓMICO
                if (updatedStats.count >= maxLoginAttempts) {
                    log.warn("eWAF SECURITY EVENT: IP '{}' reached max login failures while attempting to access account '{}'. Adding to global blocklist.", ipAddress, user);
                    IPBlockManager.addIP(ipAddress);
                    attemptsCache.invalidate(ipAddress); // Limpieza

                    // Bloqueo inmediato.
                    // No usamos Thread.sleep para evitar agotar los hilos del servidor (DoS).
                    // El filtro bloqueará cualquier petición futura instantáneamente.
                    resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Too many failed attempts. IP Blocked.");
                    return;
                }


                // Esto fuerza al navegador a hacer un GET limpio.
                session.setAttribute("flashError", "Invalid credentials. Please try again.");
                resp.sendRedirect("login"); // URL limpia
            }

        } catch (Exception e) {
            log.error("Internal server error during eWAF login process for IP '{}'", ipAddress, e);
            throw new ServletException("Login process failed", e);
        }
    }

    private class UserResult {
        Integer id;
        String tipo;
        String username;
    }

    private UserResult validate(String user, String pass) throws Exception {
        String sql = "SELECT id, username, password, tipo FROM usuarios WHERE LOWER(username) = LOWER(?) AND active=1";

        try (Connection cn = ds.getConnection();
             PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setString(1, user);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String hash = rs.getString("password");
                    boolean passwordMatches = hash.startsWith("$2") ?
                            BCrypt.checkpw(pass, hash) :
                            hash.equals(sha256(pass));

                    if (passwordMatches) {
                        UserResult result = new UserResult();
                        result.id = rs.getInt("id");
                        result.tipo = rs.getString("tipo");
                        result.username = rs.getString("username");
                        return result;
                    }
                }
            }
        }
        return null;
    }

    private void logLoginAttempt(String username, String ipAddress, boolean success, HttpServletRequest req) {
        String eventType = success ? "LOGIN_SUCCESS" : "LOGIN_FAIL";
        String path = req.getRequestURI();
        String method = req.getMethod();

        WafLogger.getInstance().logAsync(ipAddress, username, eventType, path, method, "Login attempt for user: " + username);
    }

    private String sha256(String str) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] bytes = md.digest(str.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    /**
     * Valida que la URL de referencia (Referer) pertenezca a nuestro propio servidor.
     * Previene vulnerabilidades de Open Redirect.
     */
    private boolean isValidReferrer(String referrer, HttpServletRequest req) {
        if (referrer == null || referrer.isEmpty()) return false;

        // Construimos la URL base dinámica del servidor
        // Usamos StringBuilder para eficiencia
        StringBuilder serverBase = new StringBuilder();
        serverBase.append(req.getScheme()).append("://").append(req.getServerName());

        // Agregamos el puerto solo si no es el estándar (80 o 443)
        int port = req.getServerPort();
        if (port != 80 && port != 443) {
            serverBase.append(":").append(port);
        }

        // Agregamos el path base de la app
        serverBase.append(this.appBaseUrl);

        // Verificamos si el Referer comienza con nuestra URL base
        return referrer.startsWith(serverBase.toString());
    }

    private String generateCSRFToken() {
        byte[] bytes = new byte[16];
        secureRandom.nextBytes(bytes); // Usamos la instancia estática
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}