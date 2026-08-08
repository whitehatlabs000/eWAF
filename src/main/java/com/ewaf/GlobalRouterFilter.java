package com.ewaf;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ENRUTADOR GLOBAL EN MEMORIA (Front Controller)
 * Este filtro intercepta TODAS las peticiones antes de que Tomcat decida a qué Servlet enviarlas.
 * Mantiene un mapa ultra-rápido en memoria RAM con las rutas proxy activas.
 * Si la URL coincide con una regla proxy, reescribe internamente la ruta hacia el ProxyServlet.
 */
@WebFilter(filterName = "GlobalRouterFilter", urlPatterns = "/*", asyncSupported = true)
public class GlobalRouterFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(GlobalRouterFilter.class);

    // DICCIONARIO EN RAM: Almacena las rutas entrantes activas (Ej. ["/github"])
    // Usamos ConcurrentHashMap porque soporta miles de hilos leyéndolo a la vez sin bloquearse.
    private static final Set<String> activeProxyRoutes = ConcurrentHashMap.newKeySet();

    private DataSource ds;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        ServletContext context = filterConfig.getServletContext();
        this.ds = (DataSource) context.getAttribute("dbDataSource");

        if (this.ds != null) {
            reloadRoutesFromDatabase();
        } else {
            log.error("eWAF GlobalRouter: DataSource no encontrado durante la inicialización.");
        }
    }

    /**
     * Este método se llama al arrancar el servidor y cada vez que el administrador
     * guarda cambios en el panel web (ProxyRulesServlet).
     */
    public void reloadRoutesFromDatabase() {
        if (ds == null) return;

        String sql = "SELECT incoming_path FROM proxy_routes WHERE active = 1";

        try (Connection cn = ds.getConnection();
             PreparedStatement ps = cn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            // Limpiamos la memoria actual
            activeProxyRoutes.clear();

            while (rs.next()) {
                String path = rs.getString("incoming_path");
                if (path != null && !path.trim().isEmpty()) {
                    // Guardamos la ruta limpia en memoria (ej: "/github")
                    activeProxyRoutes.add(path.trim());
                }
            }
            log.info("eWAF GlobalRouter: Mapa de memoria actualizado exitosamente. Rutas activas: {}", activeProxyRoutes.size());

        } catch (Exception e) {
            log.error("eWAF GlobalRouter: Error fatal al cargar rutas proxy en memoria RAM.", e);
        }
    }

    /**
     * MÉTODOS ESTÁTICOS DE ACCESO GLOBAL
     * Permiten que otros Servlets notifiquen cambios o consulten la RAM.
     */
    public static void refreshMemoryMap(DataSource dataSource) {
        new GlobalRouterFilter().manualRefresh(dataSource);
    }

    // Permite al Paramédico (FugitiveRecovery) saber si una ruta existe realmente
    public static boolean hasRoute(String path) {
        return activeProxyRoutes.contains(path);
    }

    private void manualRefresh(DataSource dataSource) {
        this.ds = dataSource;
        reloadRoutesFromDatabase();
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;

        // 1. OBTENER LA RUTA LIMPIA
        String requestUri = req.getRequestURI();
        String contextPath = req.getContextPath();
        String pathInfo = requestUri.substring(contextPath.length());

        // 2. OBTENER EL HOST (Enrutamiento por Subdominio - Agnóstico)
        // Ejemplos de Host: "github.localhost:8080", "github.ewaf.io", "localhost"
        String hostHeader = req.getHeader("Host");
        String subdomainAppKey = null;

        if (hostHeader != null) {
            // Quitamos el puerto para analizar solo el dominio
            String hostName = hostHeader.split(":")[0].toLowerCase();

            // Si NO es el dominio base local (localhost, 127.0.0.1 o una IP directa), extraemos el subdominio
            if (!hostName.equals("localhost") && !hostName.matches("^\\d{1,3}(\\.\\d{1,3}){3}$")) {
                int firstDot = hostName.indexOf('.');
                if (firstDot > 0) {
                    // Extraemos la primera palabra y le agregamos "/" para que coincida con la DB (Ej: "/github")
                    subdomainAppKey = "/" + hostName.substring(0, firstDot);
                }
            }
        }

        // 3. EXTRACCIÓN DE LA APP KEY POR RUTA (Fallback Tradicional)
        // Por si el usuario entra por "localhost:8080/github" en lugar de "github.localhost:8080"
        String[] parts = pathInfo.split("/");
        String pathAppKey = parts.length > 1 ? "/" + parts[1] : pathInfo;

        // 4. LA BIFURCACIÓN HÍBRIDA
        String internalProxyPath = null;

        // A) Prioridad 1: Subdominio (Ej: github.localhost)
        if (subdomainAppKey != null && activeProxyRoutes.contains(subdomainAppKey)) {
            String cleanPath = pathInfo.equals("/") ? "" : pathInfo;
            // Le armamos la ruta al ProxyServlet para que crea que entraron por ruta normal
            internalProxyPath = "/internal-proxy" + subdomainAppKey + cleanPath;

            // Le avisamos al ProxyServlet que el navegador está usando Subdominios.
            // Esto es vital para decirle que NO reescriba el HTML con "/github/", porque el navegador ya cree ser el dueño de la raíz.
            request.setAttribute("ewaf.routing.mode", "SUBDOMAIN");
        }
        // B) Prioridad 2: Ruta Tradicional (Ej: localhost/github)
        else if (activeProxyRoutes.contains(pathAppKey)) {
            internalProxyPath = "/internal-proxy" + pathInfo;
            request.setAttribute("ewaf.routing.mode", "PATH");
        }

        // 5. EJECUCIÓN DEL PROXY
        if (internalProxyPath != null) {
            if (req.getQueryString() != null) {
                internalProxyPath += "?" + req.getQueryString();
            }
            log.debug("eWAF HYBRID ROUTER: Interceptado. Redirigiendo a pasillo interno: {}", internalProxyPath);
            req.getRequestDispatcher(internalProxyPath).forward(request, response);
            return;
        }

        // 6. FLUJO NORMAL (Panel de administración y recursos nativos)
        // Si no hay proxy, y es la raíz, dejamos cargar Tomcat normalmente.
        if (pathInfo.isEmpty() || "/".equals(pathInfo)) {
            chain.doFilter(request, response);
            return;
        }

        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
        activeProxyRoutes.clear();
    }
}