<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>

<!DOCTYPE html>
<html lang="en" data-bs-theme="light">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>eWAF - Maintenance Mode</title>
    <meta name="csrf-token" content="${csrfToken}">

    <link href="${pageContext.request.contextPath}/css/bootstrap.min.css" rel="stylesheet">
    <link href="${pageContext.request.contextPath}/css/bootstrap-icons.css" rel="stylesheet">
    <link href="${pageContext.request.contextPath}/css/root.css" rel="stylesheet">
    <link href="${pageContext.request.contextPath}/css/admin-maintenance.css" rel="stylesheet">

    <jsp:include page="/WEB-INF/jsp/common/head_setup.jsp" />
</head>
<body>

<jsp:include page="common/modelo_general.jsp"/>

<div class="container main-container">

    <%-- Encabezado --%>
    <div class="d-flex justify-content-between align-items-center mb-4 fade-in-up">
        <div>
            <h1 class="h2 mb-0 fw-bold">Maintenance Mode</h1>
            <p class="text-muted small mb-0">Control global availability and server status.</p>
        </div>
        <a href="${pageContext.request.contextPath}/admin-dashboard" class="btn btn-outline-secondary">
            <i class="bi bi-arrow-left me-1"></i> Dashboard
        </a>
    </div>

    <%-- Mensajes Flash --%>
    <c:if test="${not empty message}">
        <div class="alert alert-info alert-dismissible fade show shadow-sm" role="alert">
            <i class="bi bi-info-circle-fill me-2"></i> <c:out value="${message}"/>
            <button type="button" class="btn-close" data-bs-dismiss="alert"></button>
        </div>
    </c:if>

    <%-- Tarjeta de Estado --%>
    <div class="card shadow-sm fade-in-up" style="animation-delay: 0.1s;">
        <div class="card-body text-center p-5">

            <h6 class="text-uppercase text-muted fw-bold mb-4 tracking-wider">Current System Status</h6>


            <c:choose>
                <%-- CASO 1: MODO MANTENIMIENTO ACTIVO --%>
                <c:when test="${isMaintenanceMode}">
                    <div class="mb-4">
                        <div class="status-icon-wrapper status-maintenance mb-3">
                            <i class="bi bi-cone-striped display-3"></i>
                        </div>
                        <h2 class="text-danger fw-bold display-6">Under Maintenance</h2>
                        <p class="text-muted mb-0 lead">Public access is currently <strong>blocked</strong>.</p>
                        <p class="small text-secondary">Only administrators can access the system.</p>
                    </div>

                    <hr class="my-4 w-50 mx-auto opacity-25">

                    <form action="admin-maintenance" method="POST">
                        <input type="hidden" name="csrfToken" value="${csrfToken}">
                        <input type="hidden" name="action" value="toggle">

                        <button type="submit" class="btn btn-success btn-lg px-5 shadow-sm rounded-pill">
                            <i class="bi bi-play-circle-fill me-2"></i> Go Live (Disable Maintenance)
                        </button>
                        <div class="mt-3 text-muted small">
                            Restore access for all visitors.
                        </div>
                    </form>
                </c:when>

                <%-- CASO 2: SISTEMA ONLINE (NORMAL) --%>
                <c:otherwise>
                    <div class="mb-4">
                        <div class="status-icon-wrapper status-online mb-3">
                            <i class="bi bi-hdd-network display-3"></i>
                        </div>
                        <h2 class="text-success fw-bold display-6">System Online</h2>
                        <p class="text-muted lead mb-0">The server is running normally.</p>
                        <p class="small text-secondary">All services are accessible to the public.</p>
                    </div>

                    <hr class="my-4 w-50 mx-auto opacity-25">

                    <form action="admin-maintenance" method="POST">
                        <input type="hidden" name="csrfToken" value="${csrfToken}">
                        <input type="hidden" name="action" value="toggle">

                        <button type="submit" class="btn btn-danger btn-lg px-5 shadow-sm rounded-pill">
                            <i class="bi bi-stop-circle-fill me-2"></i> Enable Maintenance Mode
                        </button>
                        <div class="mt-3 text-muted small">
                            <i class="bi bi-exclamation-triangle me-1"></i>
                            Visitors will see a "Be Right Back" page.
                        </div>
                    </form>
                </c:otherwise>
            </c:choose>

        </div>
    </div>
</div>

<script src="${pageContext.request.contextPath}/js/bootstrap.bundle.min.js"></script>
<script src="${pageContext.request.contextPath}/scripts/csrf-refresher.js" defer></script>


</body>
</html>