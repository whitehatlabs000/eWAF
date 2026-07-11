package com.ewaf;

import com.ewaf.IPBlockManager;
import com.ewaf.IPUtils;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

import javax.naming.InitialContext;
import javax.sql.DataSource;
import java.io.InputStream;
import com.ewaf.WafLogger;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@WebListener
public class AppLifecycleListener implements ServletContextListener {

    private static final Logger log = LoggerFactory.getLogger(AppLifecycleListener.class);

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        ServletContext context = sce.getServletContext();
        log.info("eWAF Application starting: Configuring security resources...");

        try {
            // 1. Cargar config.properties
            Properties props = new Properties();
            try (InputStream input = context.getResourceAsStream("/WEB-INF/config.properties")) {
                if (input == null) {
                    throw new RuntimeException("Cannot find /WEB-INF/config.properties.");
                }
                props.load(input);
            }

            // 2. Leer propiedades del archivo
            String baseUrl = props.getProperty("app.baseUrl", "");
            String blockedIpsPath = props.getProperty("path.blocked-ips");
            // Usamos la misma ruta base para el archivo del honeypot
            String honeypotPathsStr = props.getProperty("path.honeypot-paths");

            // --- Rate Limiting ---
            String rateLimitCountStr = props.getProperty("limits.rate.requestLimit");
            String rateLimitStaticCountStr = props.getProperty("limits.rate.staticRequestLimit", "500");
            String rateLimitStrictCountStr = props.getProperty("limits.rate.strictRequestLimit", "15");
            String rateLimitWindowSecStr = props.getProperty("limits.rate.timeWindowSeconds");
            String rateLimitBlockDurationSecStr = props.getProperty("limits.rate.blockDurationSeconds");
            String rateLimitStaticExtStr = props.getProperty("ratelimit.static.extensions", "");
            String rateLimitStaticPathsStr = props.getProperty("ratelimit.static.paths", "");
            String rateLimitStrictPathsStr = props.getProperty("ratelimit.strict.paths", "");
            String trustedProxiesStr = props.getProperty("server.trustedProxies");

            // --- Login Security ---
            String maxLoginAttemptsStr = props.getProperty("limits.login.maxAttempts");
            String loginTimeWindowSecStr = props.getProperty("limits.login.timeWindowSeconds");

            // --- Logging Filter ---
            String logIgnoreExtensionsStr = props.getProperty("logfilter.ignore.extensions", "");
            String logIgnorePathsStr = props.getProperty("logfilter.ignore.paths", "");

            // --- WAF & Security Paths ---
            String wafXssAllowedPathsStr = props.getProperty("waf.xss.allowedPaths", "");
            String wafSqliAllowedPathsStr = props.getProperty("waf.sqli.allowedPaths", "");
            String wafSqliNoBanPathsStr = props.getProperty("waf.sqli.noBanPaths", "");
            String wafBlockMaxAttemptsStr = props.getProperty("waf.block.maxAttempts");
            String wafBlockTimeWindowStr = props.getProperty("waf.block.timeWindowSeconds");
            String adminPathsStr = props.getProperty("security.admin.paths", "");

            // --- User Status Filter ---
            String userStatusIgnorePathsStr = props.getProperty("userstatus.ignore.paths", "");

            // 3. Validar Propiedades Críticas
            if (blockedIpsPath == null || rateLimitCountStr == null || rateLimitStaticCountStr == null ||
                    rateLimitWindowSecStr == null || rateLimitBlockDurationSecStr == null ||
                    trustedProxiesStr == null || maxLoginAttemptsStr == null ||
                    loginTimeWindowSecStr == null) {

                throw new RuntimeException("Missing critical properties in config.properties for eWAF.");
            }

            // 4. Parsear Valores Numéricos
            int rateLimitCount = Integer.parseInt(rateLimitCountStr);
            int rateLimitStaticCount = Integer.parseInt(rateLimitStaticCountStr);
            int rateLimitStrictCount = Integer.parseInt(rateLimitStrictCountStr);
            int rateLimitWindowSeconds = Integer.parseInt(rateLimitWindowSecStr);
            int rateLimitBlockDurationSeconds = Integer.parseInt(rateLimitBlockDurationSecStr);
            int maxLoginAttempts = Integer.parseInt(maxLoginAttemptsStr);
            int loginTimeWindowSeconds = Integer.parseInt(loginTimeWindowSecStr);
            int wafBlockMaxAttempts = Integer.parseInt(wafBlockMaxAttemptsStr);
            int wafBlockTimeWindowSeconds = Integer.parseInt(wafBlockTimeWindowStr);

            // 5. Inicializar Servicios Estáticos
            IPBlockManager.init(blockedIpsPath);
            IPUtils.init(trustedProxiesStr);

            // --- Cargar lista Honeypot en RAM (O(1) lookup) ---
            java.util.Set<String> honeypotSet = new java.util.HashSet<>();
            if (honeypotPathsStr != null && !honeypotPathsStr.isEmpty()) {
                try {
                    String finalHoneypotPath = honeypotPathsStr;
                    String realPath = context.getRealPath(honeypotPathsStr);

                    // Lógica inteligente: Si Tomcat encuentra el archivo dentro de su estructura web, usamos esa ruta.
                    // Si no, conserva la ruta original asumiendo que es una ruta absoluta del Sistema Operativo (C:/... o /var/...)
                    if (realPath != null && java.nio.file.Files.exists(java.nio.file.Paths.get(realPath))) {
                        finalHoneypotPath = realPath;
                    }

                    java.nio.file.Path honeypotFile = java.nio.file.Paths.get(finalHoneypotPath);
                    if (java.nio.file.Files.exists(honeypotFile)) {
                        java.util.List<String> lines = java.nio.file.Files.readAllLines(honeypotFile, java.nio.charset.StandardCharsets.UTF_8);
                        for (String line : lines) {
                            String trimmed = line.trim();
                            if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                                honeypotSet.add(trimmed.toLowerCase());
                            }
                        }
                        log.info("Loaded {} Honeypot paths into memory from: {}", honeypotSet.size(), finalHoneypotPath);
                    } else {
                        log.warn("Honeypot file not found at resolved path: {}", finalHoneypotPath);
                    }
                } catch (Exception e) {
                    log.error("Error reading honeypot file. Proceeding with empty set.", e);
                }
            }
            context.setAttribute("honeypotPathsSet", honeypotSet);

            log.info("eWAF Static services (IPBlockManager, IPUtils) initialized successfully.");

            // 6. Inyectar configuración en el ServletContext
            context.setAttribute("appBaseUrl", baseUrl);

            // Rate Limit Atributos
            context.setAttribute("rateLimitCount", rateLimitCount);
            context.setAttribute("rateLimitStaticCount", rateLimitStaticCount);
            context.setAttribute("rateLimitStrictCount", rateLimitStrictCount);
            context.setAttribute("rateLimitWindowSeconds", rateLimitWindowSeconds);
            context.setAttribute("rateLimitBlockDurationSeconds", rateLimitBlockDurationSeconds);
            context.setAttribute("rateLimitStaticExtensions", rateLimitStaticExtStr);
            context.setAttribute("rateLimitStaticPaths", rateLimitStaticPathsStr);
            context.setAttribute("rateLimitStrictPaths", rateLimitStrictPathsStr);
            context.setAttribute("trustedProxies", trustedProxiesStr);

            // Login Security Atributos
            context.setAttribute("maxLoginAttempts", maxLoginAttempts);
            context.setAttribute("loginTimeWindowSeconds", loginTimeWindowSeconds);

            // Logging Atributos
            context.setAttribute("logfilter.ignore.extensions", logIgnoreExtensionsStr);
            context.setAttribute("logfilter.ignore.paths", logIgnorePathsStr);

            // WAF & Security Atributos
            context.setAttribute("waf.xss.allowedPaths", wafXssAllowedPathsStr);
            context.setAttribute("waf.sqli.allowedPaths", wafSqliAllowedPathsStr);
            context.setAttribute("waf.sqli.noBanPaths", wafSqliNoBanPathsStr);
            context.setAttribute("waf.block.maxAttempts", wafBlockMaxAttempts);
            context.setAttribute("waf.block.timeWindowSeconds", wafBlockTimeWindowSeconds);
            context.setAttribute("security.admin.paths", adminPathsStr);

            // User Status Atributos
            context.setAttribute("userstatus.ignore.paths", userStatusIgnorePathsStr);

            // 7. INICIALIZAR BASE DE DATOS
            // Sin esto, los filtros fallan al iniciar porque dbDataSource es null
            InitialContext ctx = new InitialContext();
            DataSource ds = (DataSource) ctx.lookup("java:comp/env/jdbc/PostDB");
            context.setAttribute("dbDataSource", ds);
            log.info("eWAF Database connected via JNDI: jdbc/PostDB");

            // 8. Inicializar el motor de logs WAF Asíncrono
            WafLogger.init(ds);

            log.info("eWAF All resources have been configured successfully.");

        } catch (Exception e) {
            log.error("CATASTROPHIC FAILURE: Could not initialize eWAF application.", e);
            throw new RuntimeException("eWAF initialization failed", e);
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        try {
            // Apagado ordenado del pool de hilos para no perder logs pendientes
            WafLogger.getInstance().shutdown();
        } catch (Exception e) {
            log.error("Error shutting down WafLogger.", e);
        }
        log.info("eWAF Context destroyed. Application stopped.");
    }
}