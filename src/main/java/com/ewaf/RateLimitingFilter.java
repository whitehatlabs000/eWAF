package com.ewaf;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ewaf.IPUtils;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.ewaf.WafLogger;
import jakarta.servlet.http.HttpSession;


public class RateLimitingFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitingFilter.class);

    //(config.properties)
    private int requestLimit;
    private int staticRequestLimit;
    private int strictRequestLimit;
    private long timeWindowMs;
    private long blockDurationMs;
    private String[] staticExtensions;
    private String[] staticPaths;
    private String[] strictPaths;
    // Reemplazamos el Map por una Cache de Caffeine.

    // Esta caché manejará el límite de tamaño y la expiración por nosotros.
    private Cache<String, RequestCounter> requestCache;

    // Cada vez que actualicemos, crearemos un NUEVO objeto.
    private static class RequestCounter {
        final long count;
        final long windowStartTime;
        final long blockExpiresTime;

        RequestCounter(long count, long windowStartTime, long blockExpiresTime) {
            this.count = count;
            this.windowStartTime = windowStartTime;
            this.blockExpiresTime = blockExpiresTime;
        }
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // IPutils para obtener la IP real
        String clientIp = IPUtils.getClientIp(httpRequest);
        long currentTime = System.currentTimeMillis();

        HttpSession session = httpRequest.getSession(false);
        String username = (session != null) ? (String) session.getAttribute("user") : null;

        // Usamos requestCache.asMap().compute()
        // Esto nos da los beneficios de la caché (expiracion, tamanio)
        // y la atomicidad de compute().


        String method = httpRequest.getMethod();       // GET, POST, etc.
        String endpoint = httpRequest.getRequestURI(); // /index.css, etc.

        // VALIDACIÓN TEMPRANA (Escudo DoS): Proteger CPU de .contains() y evitar logs masivos
        if (endpoint != null && endpoint.length() > 2048) {
            log.warn("DoS SHIELD: Blocked extremely long URI (length: {}) in eWAF RateLimitingFilter.", endpoint.length());
            httpResponse.sendError(HttpServletResponse.SC_REQUEST_URI_TOO_LONG, "URI is too long.");
            return;
        }

        // --- LÓGICA DE SEPARACIÓN DE RUTAS ---
        String cacheKey = clientIp;
        boolean isStatic = false;
        boolean isStrict = false; // NUEVO

        // 1. Chequeo por rutas estrictas (Prioridad Alta)
        for (String path : strictPaths) {
            // Usamos endsWith para evitar falsos positivos en URLs similares
            if (endpoint.endsWith(path)) {
                isStrict = true;
                break;
            }
        }

        // 2. Si NO es estricto, Chequeo por extensión (.css, .js)
        if (!isStrict) {
            for (String ext : staticExtensions) {
                if (endpoint.endsWith(ext)) {
                    isStatic = true;
                    break;
                }
            }
        }

        // 3. Si no es estricto ni extensión, chequeo por ruta estática (/profile-img)
        if (!isStrict && !isStatic) {
            for (String path : staticPaths) {
                if (endpoint.contains(path)) {
                    isStatic = true;
                    break;
                }
            }
        }

        // Asignación de sufijos a la llave
        if (isStrict) {
            cacheKey = clientIp + "_STRICT";
        } else if (isStatic) {
            cacheKey = clientIp + "_STATIC";
        }
        // ------------------------------------------

        // Log Verificación (Nivel TRACE para cero impacto en producción)
        log.trace("eWAF RL Key [{}] -> {}: {}", cacheKey, method, endpoint);

        // --- 1. BLOQUEO MAESTRO ---
        // Antes de contar, verificamos si la IP "Principal" ya está bloqueada.
        // Si la IP principal está castigada, no le servimos ni estáticos ni dinámicos.
        RequestCounter mainCounter = requestCache.getIfPresent(clientIp);
        if (mainCounter != null && mainCounter.blockExpiresTime > 0 && currentTime < mainCounter.blockExpiresTime) {
            long retryAfterSeconds = (mainCounter.blockExpiresTime - currentTime) / 1000 + 1;

            WafLogger.getInstance().logAsync(clientIp, username, "RATE_LIMITED", endpoint, method, "Master Block Active. Retry in " + retryAfterSeconds + "s");

            httpResponse.addHeader("Retry-After", String.valueOf(retryAfterSeconds));
            httpResponse.sendError(429, "Too Many Requests");
            return; // ¡Importante! Detenemos la ejecución aquí.
        }

        // --- 2. CÁLCULO (COMPUTE) ---
        RequestCounter counter = requestCache.asMap().compute(cacheKey, (ip, existingCounter) -> {
            // Caso 1: Primera peticion
            if (existingCounter == null) return new RequestCounter(1, currentTime, 0L);

            // Caso 2: Ya bloqueado en este canal específico
            if (currentTime < existingCounter.blockExpiresTime) return existingCounter;

            // Caso 3: Ventana expirada (Reset)
            if (currentTime - existingCounter.windowStartTime > this.timeWindowMs) {
                return new RequestCounter(1, currentTime, 0L);
            }

            // Caso 4: Incremento
            long newCount = existingCounter.count + 1;

            // Caso 5: Excede límite -> Penalización
            int currentLimit;
            if (ip.endsWith("_STRICT")) {
                currentLimit = RateLimitingFilter.this.strictRequestLimit;
            } else if (ip.endsWith("_STATIC")) {
                currentLimit = RateLimitingFilter.this.staticRequestLimit;
            } else {
                currentLimit = RateLimitingFilter.this.requestLimit;
            }

            if (newCount > currentLimit) {
                long newBlockExpiresTime = currentTime + this.blockDurationMs;
                return new RequestCounter(newCount, existingCounter.windowStartTime, newBlockExpiresTime);
            }

            // Continuar conteo normal
            return new RequestCounter(newCount, existingCounter.windowStartTime, 0L);
        });

        // --- 3. CONTAGIO DE PENALIZACIÓN ---
        // Si se bloqueó un canal secundario (estático o estricto), bloqueamos también la IP principal inmediatamente.
        if ((isStatic || isStrict) && counter.blockExpiresTime > 0 && currentTime < counter.blockExpiresTime) {
            requestCache.put(clientIp, counter); // Sobrescribimos la IP principal con el bloqueo
            String canal = isStrict ? "strict paths" : "static resources";
            log.warn("eWAF PENALTY CONTAGION: IP '{}' globally blocked due to rate limit abuse on {}.", clientIp, canal);
        }

        // --- 4. RESPUESTA FINAL ---
        if (counter.blockExpiresTime > 0 && currentTime < counter.blockExpiresTime) {
            long retryAfterSeconds = (counter.blockExpiresTime - currentTime) / 1000 + 1;

            String details = "Rate Limit Exceeded on key: " + cacheKey;
            WafLogger.getInstance().logAsync(clientIp, username, "RATE_LIMITED", endpoint, method, details);

            httpResponse.addHeader("Retry-After", String.valueOf(retryAfterSeconds));
            httpResponse.sendError(429, "Too Many Requests");
        } else {
            chain.doFilter(request, response);
        }
    }

    @Override
    public void init(FilterConfig filterConfig) {
        ServletContext context = filterConfig.getServletContext();

        // Obtenemos los valores del context (Límites)
        Integer limit = (Integer) context.getAttribute("rateLimitCount");
        Integer staticLimit = (Integer) context.getAttribute("rateLimitStaticCount");
        Integer strictLimit = (Integer) context.getAttribute("rateLimitStrictCount");
        Integer windowSec = (Integer) context.getAttribute("rateLimitWindowSeconds");
        Integer blockDurationSec = (Integer) context.getAttribute("rateLimitBlockDurationSeconds");

        // AppLifecycleListener ya inicializó IPUtils.

        // Asignamos valores (con defaults)
        this.requestLimit = (limit != null) ? limit : 200;
        this.staticRequestLimit = (staticLimit != null) ? staticLimit : 500;
        this.strictRequestLimit = (strictLimit != null) ? strictLimit : 15;
        this.timeWindowMs = (windowSec != null) ? (long) windowSec * 1000 : 60 * 1000;
        this.blockDurationMs = (blockDurationSec != null) ? (long) blockDurationSec * 1000 : 60 * 1000;

        // Cargar extensiones estáticas
        String extStr = (String) context.getAttribute("rateLimitStaticExtensions");
        this.staticExtensions = (extStr != null && !extStr.isEmpty()) ? extStr.split(",") : new String[0];

        // Cargar rutas estáticas
        String pathStr = (String) context.getAttribute("rateLimitStaticPaths");
        this.staticPaths = (pathStr != null && !pathStr.isEmpty()) ? pathStr.split(",") : new String[0];

        // Cargar rutas estrictas
        String strictStr = (String) context.getAttribute("rateLimitStrictPaths");
        this.strictPaths = (strictStr != null && !strictStr.isEmpty()) ? strictStr.split(",") : new String[0];

        // --- Configuración de Caffeine ---

        long maxEntryAgeMs = this.timeWindowMs + this.blockDurationMs + 10000;

        this.requestCache = Caffeine.newBuilder()
                .maximumSize(50_000)
                .expireAfterWrite(maxEntryAgeMs, TimeUnit.MILLISECONDS)
                .build();

        log.info(
                "eWAF RateLimitingFilter initialized successfully. Limits per {}s: Normal={}, Static={}, Strict={}. Block Duration: {}s. Max Cache Size: 50,000.",
                (this.timeWindowMs / 1000), this.requestLimit, this.staticRequestLimit, this.strictRequestLimit, (this.blockDurationMs / 1000)
        );
    }

    @Override
    public void destroy() {
        // No necesitamos hacer nada para limpiar la cache,
        // el Garbage Collector se encargara de ella.
        log.info("eWAF RateLimitingFilter destroyed.");
    }

}