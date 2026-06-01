<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <title>eWAF Security Center</title>
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <meta name="csrf-token" content="${csrfToken}">
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/bootstrap.min.css">
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/bootstrap-icons.css">
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/all.min.css">
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/admin-stats.css">

  <jsp:include page="/WEB-INF/jsp/common/head_setup.jsp" />

  <style>
    .kpi-icon-waf { font-size: 2rem; opacity: 0.8; }
    .table-waf th { font-size: 0.85rem; text-transform: uppercase; letter-spacing: 0.5px; }
    .badge-threat { font-size: 0.8rem; padding: 0.4em 0.6em; }
  </style>
</head>
<body>

<jsp:include page="common/modelo_general.jsp" />

<div class="container-fluid p-4">

  <div class="d-flex justify-content-between align-items-center mb-4">
    <h3 class="m-0 fw-bold text-secondary"><i class="fa-solid fa-shield-halved text-primary me-2"></i>eWAF Security Center</h3>
  </div>

  <%-- Formulario de Filtros y Acciones --%>
  <div class="card p-3 mb-4 shadow-sm border-0">
    <form action="admin-waf-stats" method="GET" autocomplete="off">
      <div class="row g-3 align-items-end justify-content-center">
        <div class="col-md-3">
          <label class="small text-muted fw-bold mb-1">From</label>
          <input type="date" name="startDate" class="form-control" value="${startDate}" required>
        </div>
        <div class="col-md-3">
          <label class="small text-muted fw-bold mb-1">Until</label>
          <input type="date" name="endDate" class="form-control" value="${endDate}" required>
        </div>
        <div class="col-md-2">
          <button type="submit" class="btn btn-primary w-100 fw-bold"><i class="fa-solid fa-rotate me-1"></i> Update Data</button>
        </div>
        <div class="col-md-2">
          <a href="${pageContext.request.contextPath}/admin-block-ip" class="btn btn-outline-danger w-100 fw-bold">
            <i class="fa-solid fa-ban me-1"></i> Blocklist
          </a>
        </div>
        <div class="col-md-2">
          <a href="${pageContext.request.contextPath}/admin-dashboard" class="btn btn-outline-secondary w-100 fw-bold">
            <i class="fa-solid fa-house me-1"></i> Dashboard
          </a>
        </div>
      </div>
    </form>
  </div>

  <%-- KPIs de Seguridad --%>
  <div class="row g-3 mb-4">
    <div class="col-xl-3 col-md-6">
      <div class="card h-100 shadow-sm">
        <div class="card-body d-flex justify-content-between align-items-center">
          <div>
            <h6 class="text-success text-uppercase text-xs fw-bold mb-1">Legitimate Traffic</h6>
            <h3 class="mb-0 fw-bold text-secondary">${stats.kpiTotalTraffic}</h3>
          </div>
          <div class="kpi-icon bg-gradient-success">
            <i class="fa-solid fa-globe"></i>
          </div>
        </div>
      </div>
    </div>
    <div class="col-xl-3 col-md-6">
      <div class="card h-100 shadow-sm">
        <div class="card-body d-flex justify-content-between align-items-center">
          <div>
            <h6 class="text-danger text-uppercase text-xs fw-bold mb-1">Critical Threats</h6>
            <h3 class="mb-0 fw-bold text-secondary">${stats.kpiSecurityBlocks}</h3>
          </div>
          <div class="kpi-icon bg-gradient-danger">
            <i class="fa-solid fa-bug"></i>
          </div>
        </div>
      </div>
    </div>
    <div class="col-xl-3 col-md-6">
      <div class="card h-100 shadow-sm">
        <div class="card-body d-flex justify-content-between align-items-center">
          <div>
            <h6 class="text-warning text-uppercase text-xs fw-bold mb-1">Rate Limited</h6>
            <h3 class="mb-0 fw-bold text-secondary">${stats.kpiRateLimits}</h3>
          </div>
          <div class="kpi-icon bg-gradient-warning">
            <i class="fa-solid fa-stopwatch"></i>
          </div>
        </div>
      </div>
    </div>
    <div class="col-xl-3 col-md-6">
      <div class="card h-100 shadow-sm">
        <div class="card-body d-flex justify-content-between align-items-center">
          <div>
            <h6 class="text-primary text-uppercase text-xs fw-bold mb-1">Blacklist Hits</h6>
            <h3 class="mb-0 fw-bold text-secondary">${stats.kpiBlacklistHits}</h3>
          </div>
          <div class="kpi-icon bg-gradient-primary">
            <i class="fa-solid fa-skull-crossbones"></i>
          </div>
        </div>
      </div>
    </div>
  </div>

  <%-- Gráficos del WAF --%>
  <div class="row g-4 mb-4">
    <div class="col-lg-8">
      <div class="card h-100 shadow-sm">
        <div class="card-header bg-transparent d-flex justify-content-between align-items-center">
          <h6 class="m-0 fw-bold text-primary"><i class="fa-solid fa-chart-line me-2"></i>Traffic vs. Blocked Attacks</h6>
        </div>
        <div class="card-body">
          <div style="height: 300px;"><canvas id="trafficLineChart"></canvas></div>
        </div>
      </div>
    </div>
    <div class="col-lg-4">
      <div class="card h-100 shadow-sm">
        <div class="card-header bg-transparent">
          <h6 class="m-0 fw-bold text-danger"><i class="fa-solid fa-radar-radar me-2"></i>Threat Distribution</h6>
        </div>
        <div class="card-body d-flex justify-content-center align-items-center">
          <c:choose>
            <c:when test="${empty stats.threatLabels}">
              <div class="text-muted text-center p-4">
                <i class="fa-solid fa-shield-check fa-3x mb-3 text-success opacity-50"></i>
                <p class="mb-0">No threats detected in this period.</p>
              </div>
            </c:when>
            <c:otherwise>
              <div style="height: 250px; width: 100%;"><canvas id="threatDoughnutChart"></canvas></div>
            </c:otherwise>
          </c:choose>
        </div>
      </div>
    </div>
  </div>

  <%-- Tablas de Análisis Forense --%>
  <div class="row g-4 mb-4">

    <%-- Top Atacantes --%>
    <div class="col-lg-6">
      <div class="card h-100 shadow-sm">
        <div class="card-header py-3 bg-transparent d-flex justify-content-between align-items-center">
          <h6 class="m-0 fw-bold text-danger"><i class="fa-solid fa-user-ninja me-2"></i>Top Attackers (IPs)</h6>
          <small class="text-muted">By total blocks</small>
        </div>
        <div class="table-responsive">
          <table class="table table-hover align-middle mb-0 table-waf">
            <thead class="table-light sticky-top">
            <tr><th>IP Address</th><th class="text-center">Blocks</th><th class="text-end">Action</th></tr>
            </thead>
            <tbody>
            <c:forEach var="attacker" items="${stats.topAttackers}" varStatus="loop">
              <tr>
                <td class="font-monospace text-secondary fw-bold">
                  <c:out value="${attacker.ip_address}"/>
                </td>
                <td class="text-center">
                  <span class="badge bg-danger rounded-pill badge-threat">${attacker.block_count}</span>
                </td>
                <td class="text-end">
                  <button type="button" class="btn btn-sm text-danger bg-danger bg-opacity-10 border-0 fw-bold px-2 py-1" onclick="showBanModal('${attacker.ip_address}')" title="Ban IP">
                    <i class="fa-solid fa-gavel"></i>
                  </button>
                </td>
              </tr>
            </c:forEach>
            <c:if test="${empty stats.topAttackers}">
              <tr><td colspan="3" class="text-center text-muted py-4">No attackers found. System is clean.</td></tr>
            </c:if>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <%-- Rutas más atacadas --%>
    <div class="col-lg-6">
      <div class="card h-100 shadow-sm">
        <div class="card-header py-3 bg-transparent d-flex justify-content-between align-items-center">
          <h6 class="m-0 fw-bold text-warning"><i class="fa-solid fa-bullseye me-2"></i>Top Targeted Paths</h6>
          <small class="text-muted">Where are they aiming?</small>
        </div>
        <div class="table-responsive">
          <table class="table table-hover align-middle mb-0 table-waf">
            <thead class="table-light sticky-top">
            <tr><th>Target Path</th><th class="text-end">Hits Blocked</th></tr>
            </thead>
            <tbody>
            <c:forEach var="target" items="${stats.topTargetedPaths}">
              <tr>
                <td class="text-body fw-medium font-monospace small text-break">
                  <c:out value="${target.target_path}"/>
                </td>
                <td class="text-end">
                  <span class="badge bg-secondary bg-opacity-25 text-body rounded-pill">${target.block_count}</span>
                </td>
              </tr>
            </c:forEach>
            <c:if test="${empty stats.topTargetedPaths}">
              <tr><td colspan="2" class="text-center text-muted py-4">No targets registered.</td></tr>
            </c:if>
            </tbody>
          </table>
        </div>
      </div>
    </div>

  </div>

  <%-- Modal de Confirmación para Banear IP --%>
  <div class="modal fade" id="banModal" tabindex="-1" aria-hidden="true">
    <div class="modal-dialog modal-dialog-centered">
      <div class="modal-content shadow" style="background-color: var(--bs-body-bg); border: 1px solid var(--bs-border-color);">
        <div class="modal-header border-bottom-0">
          <h5 class="modal-title text-danger fw-bold"><i class="fa-solid fa-triangle-exclamation me-2"></i>Confirm IP Ban</h5>
          <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
        </div>
        <div class="modal-body text-secondary">
          Are you sure you want to add the IP <strong id="modalIpDisplay" class="font-monospace text-primary"></strong> to the blocklist? This will immediately deny all access from this address.
        </div>
        <div class="modal-footer border-top-0">
          <button type="button" class="btn btn-secondary fw-bold border-0" data-bs-dismiss="modal">Cancel</button>
          <form action="${pageContext.request.contextPath}/admin-block-ip" method="POST" class="m-0">
            <input type="hidden" name="csrfToken" value="${csrfToken}">
            <input type="hidden" name="action" value="add">
            <input type="hidden" name="ip" id="modalIpInput" value="">
            <button type="submit" class="btn btn-danger fw-bold"><i class="fa-solid fa-gavel me-2"></i>Ban IP</button>
          </form>
        </div>
      </div>
    </div>
  </div>

</div>

<script src="${pageContext.request.contextPath}/js/jquery-3.6.0.min.js"></script>
<script src="${pageContext.request.contextPath}/js/bootstrap.bundle.min.js"></script>
<script src="${pageContext.request.contextPath}/js/chart.min.js"></script>

<script>
  // Pasamos los datos de JSP a JS a través de una variable global
  const wafData = ${empty dashboardJson ? '{}' : dashboardJson};

  // Lógica para activar el Modal de baneo
  function showBanModal(ipAddress) {
    document.getElementById('modalIpDisplay').textContent = ipAddress;
    document.getElementById('modalIpInput').value = ipAddress;
    const banModal = new bootstrap.Modal(document.getElementById('banModal'));
    banModal.show();
  }
</script>
<script src="${pageContext.request.contextPath}/scripts/admin-stats.js" defer></script>

</body>
</html>