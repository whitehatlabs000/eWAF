<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>

<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta name="csrf-token" content="${csrfToken}">
    <title>eWAF · Dashboard</title>

    <link href="${pageContext.request.contextPath}/css/bootstrap.min.css" rel="stylesheet">
    <link href="${pageContext.request.contextPath}/css/bootstrap-icons.css" rel="stylesheet">

    <link href="${pageContext.request.contextPath}/css/admin-dashboard.css" rel="stylesheet">

    <jsp:include page="/WEB-INF/jsp/common/head_setup.jsp" />
</head>
<body>

<jsp:include page="common/modelo_general.jsp" />

<div class="container py-5">


    <div class="text-center mb-5 fade-in-up">
        <h1 class="display-5 fw-bold">eWAF Control Panel</h1>
        <p class="text-muted lead">Security management and system auditing.</p>
    </div>

    <%-- SECCIÓN 1: MONITORING --%>
    <h6 class="text-uppercase text-muted fw-bold mb-3 ms-1 fade-in-up" style="animation-delay: 0.1s;">
        <i class="bi bi-activity me-2"></i>Monitoring
    </h6>
    <div class="row g-4 mb-5 fade-in-up" style="animation-delay: 0.1s;">

        <div class="col-md-6">
            <a href="${pageContext.request.contextPath}/admin-waf-stats" class="card dashboard-card card-analytics h-100">
                <div class="card-body text-center p-4">
                    <i class="bi bi-speedometer2 card-icon text-info"></i>
                    <h5 class="card-title">System Statistics</h5>
                    <p class="card-text text-secondary small">Real-time statistics and global overview.</p>
                </div>
            </a>
        </div>

        <div class="col-md-6">
            <a href="${pageContext.request.contextPath}/admin-logs" class="card dashboard-card card-analytics h-100">
                <div class="card-body text-center p-4">
                    <i class="bi bi-journal-text card-icon text-success"></i>
                    <h5 class="card-title">Access Logs</h5>
                    <p class="card-text text-secondary small">Audit login attempts and visitor history.</p>
                </div>
            </a>
        </div>
    </div>

    <%-- SECCIÓN 2: USER MANAGEMENT --%>
    <h6 class="text-uppercase text-muted fw-bold mb-3 ms-1 fade-in-up" style="animation-delay: 0.2s;">
        <i class="bi bi-people me-2"></i>Accounts
    </h6>
    <div class="row g-4 mb-5 fade-in-up" style="animation-delay: 0.2s;">

        <div class="col-md-6">
            <a href="${pageContext.request.contextPath}/admin-manage_users" class="card dashboard-card card-manage h-100">
                <div class="card-body text-center p-4">
                    <i class="bi bi-person-gear card-icon text-primary"></i>
                    <h5 class="card-title">Manage Users</h5>
                    <%-- Corrección solicitada: Solo acciones permitidas --%>
                    <p class="card-text text-secondary small">Disable access, delete accounts, or reset passwords.</p>
                </div>
            </a>
        </div>

        <div class="col-md-6">

            <a href="${pageContext.request.contextPath}/admin-create_account" class="card dashboard-card card-manage h-100">
                <div class="card-body text-center p-4">
                    <i class="bi bi-person-plus-fill card-icon text-primary"></i>
                    <h5 class="card-title">Create Account</h5>
                    <p class="card-text text-secondary small">Register new users into the system.</p>
                </div>
            </a>
        </div>

    </div>

    <%-- SECCIÓN 3: SECURITY CONTROLS --%>
    <h6 class="text-uppercase text-muted fw-bold mb-3 ms-1 fade-in-up" style="animation-delay: 0.3s;">
        <i class="bi bi-shield-lock me-2"></i>Security Controls
    </h6>
    <div class="row g-4 fade-in-up" style="animation-delay: 0.3s;">

        <div class="col-md-6">
            <a href="${pageContext.request.contextPath}/admin-block-ip" class="card dashboard-card card-security h-100">
                <div class="card-body text-center p-4">
                    <i class="bi bi-slash-circle card-icon text-danger"></i>
                    <h5 class="card-title">IP Blocking</h5>
                    <%-- Corrección solicitada: Sin palabra firewall --%>
                    <p class="card-text text-secondary small">Manually deny access to specific IP addresses.</p>
                </div>
            </a>
        </div>

        <div class="col-md-6">

            <a href="${pageContext.request.contextPath}/proxy-rules" class="card dashboard-card card-security h-100">
                <div class="card-body text-center p-4">
                    <i class="bi bi-hdd-network card-icon"></i>
                    <h5 class="card-title">Reverse Proxy</h5>
                    <p class="card-text text-secondary small">Manage proxy rules and routing configuration.</p>
                </div>
            </a>
        </div>

        <div class="col-md-6">
            <a href="${pageContext.request.contextPath}/admin-maintenance" class="card dashboard-card card-security h-100">
                <div class="card-body text-center p-4">
                    <i class="bi bi-cone-striped card-icon text-warning"></i>
                    <h5 class="card-title">Maintenance Mode</h5>
                    <p class="card-text text-secondary small">Toggle server availability for users.</p>
                </div>
            </a>
        </div>




    </div>

</div>

<script src="${pageContext.request.contextPath}/js/bootstrap.bundle.min.js"></script>
<script src="${pageContext.request.contextPath}/scripts/csrf-refresher.js" defer></script>

</body>
</html>