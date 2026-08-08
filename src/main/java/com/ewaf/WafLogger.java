package com.ewaf;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class WafLogger {

    private static final Logger log = LoggerFactory.getLogger(WafLogger.class);

    // Instancia Singleton
    private static WafLogger instance;

    private final DataSource ds;
    // Pool de hilos dedicado para no bloquear el flujo de los requests web
    private final ExecutorService executor;

    private WafLogger(DataSource ds) {
        this.ds = ds;
        // Usamos un solo hilo (SingleThreadExecutor) para garantizar el orden FIFO (First-In-First-Out)
        // de los logs. Esto evita condiciones de carrera donde el redirect se guarda antes que por ej el login.
        // (estudiar como mejorar)
        this.executor = Executors.newSingleThreadExecutor();
    }

    /**
     * Se llama una sola vez al arrancar la app (AppLifecycleListener)
     */
    public static synchronized void init(DataSource ds) {
        if (instance == null) {
            instance = new WafLogger(ds);
            log.info("eWAF WafLogger initialized successfully with async thread pool.");
        }
    }

    /**
     * Acceso global para los Filtros
     */
    public static WafLogger getInstance() {
        if (instance == null) {
            log.error("CRITICAL: WafLogger accessed before initialization!");
            throw new IllegalStateException("WafLogger not initialized");
        }
        return instance;
    }

    /**
     * Registra un evento de seguridad de forma totalmente asíncrona.
     * Retorna instantáneamente, delegando la inserción SQL al Executor.
     */
    public void logAsync(String ip, String username, String eventType, String targetPath, String httpMethod, String details) {
        if (executor.isShutdown()) {
            log.warn("Cannot log event, WafLogger is shutting down.");
            return;
        }

        // VALIDACIÓN TEMPRANA (Escudo SQL Truncation): Cortamos los strings antes de enviarlos a la BD
        final String safePath = (targetPath != null && targetPath.length() > 250) ? targetPath.substring(0, 247) + "..." : targetPath;
        final String safeDetails = (details != null && details.length() > 250) ? details.substring(0, 247) + "..." : details;

        // Encolamos la tarea
        executor.submit(() -> {
            String sql = "INSERT INTO access_logs (ip_address, username, event_type, target_path, http_method, details) VALUES (?, ?, ?, ?, ?, ?)";

            try (Connection conn = ds.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setString(1, ip);
                ps.setString(2, username);
                ps.setString(3, eventType);  // Ej: PAGE_VIEW, XSS_BLOCKED, SQL_INJECTION
                ps.setString(4, safePath);
                ps.setString(5, httpMethod); // Ej: GET, POST
                ps.setString(6, safeDetails);

                ps.executeUpdate();

            } catch (SQLException e) {
                // Si la BD falla, logueamos en el archivo pero el request del usuario NUNCA se entera ni se frena
                log.error("Database error saving asynchronous WAF log for IP: {}", ip, e);
            }
        });
    }

    /**
     * Apagado ordenado para evitar pérdida de logs al reiniciar Tomcat
     */
    public void shutdown() {
        log.info("Shutting down eWAF WafLogger async pool...");
        executor.shutdown();
        try {
            // Esperamos hasta 2 segundos para que los logs pendientes se guarden
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                log.warn("Forcing shutdown of WafLogger executor...");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}