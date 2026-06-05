package com.ewaf;

import com.google.gson.Gson;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.ServletContext;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@WebServlet("/admin-waf-stats")
public class AdminWafStatsServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(AdminWafStatsServlet.class);

    private DataSource ds;
    private final Gson gson = new Gson();

    @Override
    public void init() throws ServletException {
        ServletContext context = getServletContext();
        this.ds = (DataSource) context.getAttribute("dbDataSource");

        if (this.ds == null) {
            log.error("Critical failure: DataSource not found in context for AdminWafStatsServlet.");
            throw new ServletException("Critical failure: DataSource not found in context for AdminWafStatsServlet.");
        }
        log.info("eWAF AdminWafStatsServlet initialized successfully.");
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {


        HttpSession session = req.getSession(false);
        if (session == null || !"admin".equalsIgnoreCase((String) session.getAttribute("tipoUsuario"))) {
            String user = (session != null) ? (String) session.getAttribute("user") : "Unauthenticated";
            log.warn("eWAF SECURITY WARNING: Non-admin user '{}' attempted to access Admin WAF Stats page from IP '{}'.", user, IPUtils.getClientIp(req));
            resp.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        String startDateStr = req.getParameter("startDate");
        String endDateStr = req.getParameter("endDate");

        LocalDate end, start;
        try {
            end = (endDateStr == null || endDateStr.isEmpty()) ? LocalDate.now() : LocalDate.parse(endDateStr);
            start = (startDateStr == null || startDateStr.isEmpty()) ? end.minusDays(30) : LocalDate.parse(startDateStr);
        } catch (DateTimeParseException e) {
            log.warn("Invalid date format received for stats filter. Using default 30-day range.");
            end = LocalDate.now();
            start = end.minusDays(30);
        }

        String sqlStart = start.toString() + " 00:00:00";
        String sqlEnd = end.toString() + " 23:59:59";

        WafDashboardData data = new WafDashboardData();

        try (Connection conn = ds.getConnection()) {

            // ==========================================
            // SECCIÓN A: KPIs GENERALES
            // ==========================================

            // KPI 1: Tráfico Legítimo Total (PAGE_VIEW)
            String sqlTraffic = "SELECT COUNT(*) FROM access_logs WHERE event_type = 'PAGE_VIEW' AND event_timestamp BETWEEN ? AND ?";
            data.kpiTotalTraffic = executeScalar(conn, sqlTraffic, Arrays.asList(sqlStart, sqlEnd));

            // KPI 2: Bloqueos de Seguridad Críticos (XSS + SQLi)
            String sqlSecBlocks = "SELECT COUNT(*) FROM access_logs WHERE event_type IN ('XSS_BLOCKED', 'SQL_INJECTION') AND event_timestamp BETWEEN ? AND ?";
            data.kpiSecurityBlocks = executeScalar(conn, sqlSecBlocks, Arrays.asList(sqlStart, sqlEnd));

            // KPI 3: Spam / Abuso Bloqueado (RATE_LIMITED)
            String sqlRateLimits = "SELECT COUNT(*) FROM access_logs WHERE event_type = 'RATE_LIMITED' AND event_timestamp BETWEEN ? AND ?";
            data.kpiRateLimits = executeScalar(conn, sqlRateLimits, Arrays.asList(sqlStart, sqlEnd));

            // KPI 4: Intentos de IP en Lista Negra (BLACKLISTED)
            String sqlBlacklistHits = "SELECT COUNT(*) FROM access_logs WHERE event_type = 'BLACKLISTED' AND event_timestamp BETWEEN ? AND ?";
            data.kpiBlacklistHits = executeScalar(conn, sqlBlacklistHits, Arrays.asList(sqlStart, sqlEnd));


            // ==========================================
            // SECCIÓN B: GRÁFICOS
            // ==========================================

            // 1. Gráfico Lineal: Tráfico Bueno vs Malo por día
            String chartSql =
                    "SELECT DATE(event_timestamp) as dia, 'traffic' as type, COUNT(*) as total FROM access_logs WHERE event_type = 'PAGE_VIEW' AND event_timestamp BETWEEN ? AND ? GROUP BY dia " +
                            "UNION ALL " +
                            "SELECT DATE(event_timestamp) as dia, 'blocks' as type, COUNT(*) as total FROM access_logs WHERE event_type IN ('SQL_INJECTION', 'XSS_BLOCKED', 'RATE_LIMITED', 'BLACKLISTED', 'UNAUTHORIZED_ADMIN_ACCESS') AND event_timestamp BETWEEN ? AND ? GROUP BY dia " +
                            "ORDER BY 1 ASC";

            processActivityChart(data, executeQuery(conn, chartSql, Arrays.asList(sqlStart, sqlEnd, sqlStart, sqlEnd)));

            // 2. Gráfico Circular: Distribución de Amenazas (Exclusivo WAF)
            String eventsSql = "SELECT event_type, COUNT(*) as total FROM access_logs WHERE event_type IN ('SQL_INJECTION', 'XSS_BLOCKED', 'RATE_LIMITED', 'BLACKLISTED', 'UNAUTHORIZED_ADMIN_ACCESS') AND event_timestamp BETWEEN ? AND ? GROUP BY event_type";
            List<Map<String, Object>> eventsRows = executeQuery(conn, eventsSql, Arrays.asList(sqlStart, sqlEnd));
            for(Map<String, Object> row : eventsRows) {
                data.threatLabels.add((String) row.get("event_type"));
                data.threatValues.add(((Number) row.get("total")).intValue());
            }

            // ==========================================
            // SECCIÓN C: TABLAS DE ANÁLISIS
            // ==========================================

            // Top 10 IPs Atacantes
            String sqlTopAttackers =
                    "SELECT ip_address, COUNT(*) as block_count FROM access_logs " +
                            "WHERE event_type IN ('SQL_INJECTION', 'XSS_BLOCKED', 'RATE_LIMITED', 'BLACKLISTED', 'UNAUTHORIZED_ADMIN_ACCESS') AND event_timestamp BETWEEN ? AND ? " +
                            "GROUP BY ip_address ORDER BY block_count DESC LIMIT 10";
            data.topAttackers = executeQuery(conn, sqlTopAttackers, Arrays.asList(sqlStart, sqlEnd));

            // Top 10 Rutas Más Atacadas
            String sqlTopTargets =
                    "SELECT target_path, COUNT(*) as block_count FROM access_logs " +
                            "WHERE event_type IN ('SQL_INJECTION', 'XSS_BLOCKED', 'RATE_LIMITED', 'BLACKLISTED', 'UNAUTHORIZED_ADMIN_ACCESS') AND target_path IS NOT NULL AND event_timestamp BETWEEN ? AND ? " +
                            "GROUP BY target_path ORDER BY block_count DESC LIMIT 10";
            data.topTargetedPaths = executeQuery(conn, sqlTopTargets, Arrays.asList(sqlStart, sqlEnd));

        } catch (SQLException e) {
            log.error("Database error while building eWAF stats dashboard.", e);
            throw new ServletException("Database error while loading WAF stats.", e);
        }

        // TOKEN CSRF
        String csrfToken = (String) session.getAttribute("csrfToken");
        if (csrfToken == null) {
            csrfToken = UUID.randomUUID().toString();
            session.setAttribute("csrfToken", csrfToken);
        }
        req.setAttribute("csrfToken", csrfToken);

        // ENVIAR DATOS A LA VISTA
        req.setAttribute("dashboardJson", gson.toJson(data));
        req.setAttribute("stats", data);
        req.setAttribute("startDate", start.toString());
        req.setAttribute("endDate", end.toString());

        req.getRequestDispatcher("/WEB-INF/jsp/waf-stats.jsp").forward(req, resp);
    }

    // --- HELPERS DATABASE ---

    private void processActivityChart(WafDashboardData data, List<Map<String, Object>> rows) {
        Map<String, int[]> tempMap = new TreeMap<>();
        for (Map<String, Object> row : rows) {
            String dia = row.get("dia").toString();
            String type = (String) row.get("type");
            int count = ((Number) row.get("total")).intValue();
            tempMap.putIfAbsent(dia, new int[]{0, 0});
            if ("traffic".equals(type)) tempMap.get(dia)[0] += count;
            else if ("blocks".equals(type)) tempMap.get(dia)[1] += count;
        }
        for (Map.Entry<String, int[]> entry : tempMap.entrySet()) {
            data.chartLabels.add(entry.getKey());
            data.chartTraffic.add(entry.getValue()[0]);
            data.chartBlocks.add(entry.getValue()[1]);
        }
    }

    private int executeScalar(Connection conn, String sql, List<Object> params) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return 0;
    }

    private List<Map<String, Object>> executeQuery(Connection conn, String sql, List<Object> params) throws SQLException {
        List<Map<String, Object>> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData md = rs.getMetaData();
                int colCount = md.getColumnCount();
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    for (int i = 1; i <= colCount; i++) row.put(md.getColumnLabel(i), rs.getObject(i));
                    list.add(row);
                }
            }
        }
        return list;
    }

    // --- DTO PARA LA VISTA Y GSON ---
    public static class WafDashboardData {
        public int kpiTotalTraffic, kpiSecurityBlocks, kpiRateLimits, kpiBlacklistHits;

        public List<String> chartLabels = new ArrayList<>();
        public List<Integer> chartTraffic = new ArrayList<>();
        public List<Integer> chartBlocks = new ArrayList<>();

        public List<String> threatLabels = new ArrayList<>();
        public List<Integer> threatValues = new ArrayList<>();

        public List<Map<String, Object>> topAttackers = new ArrayList<>();
        public List<Map<String, Object>> topTargetedPaths = new ArrayList<>();

        // Getters para JSTL en el JSP
        public int getKpiTotalTraffic() { return kpiTotalTraffic; }
        public int getKpiSecurityBlocks() { return kpiSecurityBlocks; }
        public int getKpiRateLimits() { return kpiRateLimits; }
        public int getKpiBlacklistHits() { return kpiBlacklistHits; }
        public List<String> getChartLabels() { return chartLabels; }
        public List<Integer> getChartTraffic() { return chartTraffic; }
        public List<Integer> getChartBlocks() { return chartBlocks; }
        public List<String> getThreatLabels() { return threatLabels; }
        public List<Integer> getThreatValues() { return threatValues; }
        public List<Map<String, Object>> getTopAttackers() { return topAttackers; }
        public List<Map<String, Object>> getTopTargetedPaths() { return topTargetedPaths; }
    }
}