<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>

<!DOCTYPE html>
<html lang="en" data-bs-theme="light">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta name="csrf-token" content="${csrfToken}">
    <title>eWAF - Access Logs</title>

    <link href="${pageContext.request.contextPath}/css/bootstrap.min.css" rel="stylesheet">
    <link href="${pageContext.request.contextPath}/css/bootstrap-icons.css" rel="stylesheet">
    <link href="${pageContext.request.contextPath}/css/root.css" rel="stylesheet">
    <link href="${pageContext.request.contextPath}/css/admin-logs.css" rel="stylesheet">

    <jsp:include page="/WEB-INF/jsp/common/head_setup.jsp" />
</head>
<body class="page-log">

<jsp:include page="common/modelo_general.jsp" />


<div class="container-fluid main-container">


    <div class="d-flex justify-content-between align-items-center mb-4 fade-in-up">
        <div>
            <h1 class="h2 mb-0 fw-bold">Access Logs</h1>
            <p class="text-muted small mb-0">Security audit trail.</p>
        </div>
        <a href="${pageContext.request.contextPath}/admin-dashboard" class="btn btn-outline-secondary">
            <i class="bi bi-arrow-left me-1"></i> Dashboard
        </a>
    </div>

    <%-- Filtros --%>
    <div class="card mb-4 shadow-sm fade-in-up" style="animation-delay: 0.1s;">
        <div class="card-header bg-transparent border-bottom-0 pt-3">
            <h6 class="card-title mb-0 text-primary fw-bold">
                <i class="bi bi-funnel-fill me-2"></i>Filter Logs
            </h6>
        </div>
        <div class="card-body">
            <form id="filterForm">
                <div class="row g-3 align-items-end">

                    <div class="col-md-3 col-lg-2">
                        <label for="filterDate" class="form-label fw-bold small text-uppercase">Date</label>
                        <input type="date" class="form-control form-control-sm" id="filterDate" name="filterDate" value="<c:out value="${filterDate}"/>">
                    </div>

                    <div class="col-md-3 col-lg-2">
                        <label for="filterUsername" class="form-label fw-bold small text-uppercase">Username</label>
                        <input type="text" class="form-control form-control-sm" id="filterUsername" name="filterUsername" value="<c:out value="${filterUsername}"/>" placeholder="username">
                    </div>

                    <div class="col-md-3 col-lg-2">
                        <label for="filterIp" class="form-label fw-bold small text-uppercase">IP Address</label>
                        <input type="text" class="form-control form-control-sm" id="filterIp" name="filterIp" value="<c:out value="${filterIp}"/>" placeholder="192.168...">
                    </div>

                    <div class="col-md-3 col-lg-2">
                        <label for="filterEvent" class="form-label fw-bold small text-uppercase">Event Type</label>
                        <select id="filterEvent" name="filterEvent" class="form-select form-select-sm">
                            <option value="">All Events</option>
                            <c:forEach var="eventType" items="${eventTypes}">
                                <option value="${eventType}" <c:if test="${eventType == filterEvent}">selected</c:if>>
                                    <c:out value="${eventType}"/>
                                </option>
                            </c:forEach>
                        </select>
                    </div>

                    <div class="col-md-6 col-lg-2">
                        <label for="filterDetails" class="form-label fw-bold small text-uppercase">Details</label>
                        <input type="text" class="form-control form-control-sm" id="filterDetails" name="filterDetails" value="<c:out value="${filterDetails}"/>" placeholder="Keywords...">
                    </div>

                    <div class="col-md-6 col-lg-2 d-grid">
                        <button type="submit" class="btn btn-primary btn-sm">
                            <i class="bi bi-search me-1"></i> Search
                        </button>
                    </div>
                </div>
            </form>
        </div>
    </div>

    <%-- Tabla de Resultados --%>
    <div class="card shadow-sm position-relative fade-in-up" style="min-height: 400px; animation-delay: 0.2s;">

        <div id="loadingIndicator">
            <div class="text-center">
                <div class="spinner-border text-primary" role="status" style="width: 3rem; height: 3rem;"></div>
                <p class="mt-2 fw-bold text-muted">Loading Logs...</p>
            </div>
        </div>

        <div class="table-responsive">
            <table class="table table-striped table-hover align-middle mb-0">
                <thead class="table-light sticky-top">
                <tr>
                    <th class="text-uppercase small fw-bold text-muted ps-3">Timestamp</th>
                    <th class="text-uppercase small fw-bold text-muted">IP Address</th>
                    <th class="text-uppercase small fw-bold text-muted">Username</th>
                    <th class="text-uppercase small fw-bold text-muted">Method</th>
                    <th class="text-uppercase small fw-bold text-muted">Target Path</th>
                    <th class="text-uppercase small fw-bold text-muted">Event</th>
                    <th class="text-uppercase small fw-bold text-muted">Details</th>
                </tr>
                </thead>

                <tbody id="logsTableBody">
                <c:choose>
                    <c:when test="${not empty logs}">
                        <c:forEach var="log" items="${logs}">
                            <tr>
                                <td class="text-muted small text-monospace ps-3"><c:out value="${log.timestamp}"/></td>
                                <td class="text-monospace small"><c:out value="${log.ip}"/></td>
                                <td class="fw-bold text-primary"><c:out value="${log.username}"/></td>
                                <td class="small fw-bold text-secondary"><c:out value="${empty log.http_method ? '-' : log.http_method}"/></td>
                                <td class="small text-monospace text-truncate" style="max-width: 150px;" title="${log.target_path}"><c:out value="${empty log.target_path ? '-' : log.target_path}"/></td>

                                <td>
                                    <c:choose>
                                        <%-- EVENTOS DEL WAF --%>
                                        <c:when test="${log.event == 'SQL_INJECTION'}">
                                            <span class="badge bg-danger text-white rounded-pill">
                                                <i class="bi bi-bug-fill me-1"></i>SQLi BLOCKED
                                            </span>
                                        </c:when>
                                        <c:when test="${log.event == 'XSS_BLOCKED'}">
                                            <span class="badge bg-warning text-dark rounded-pill">
                                                <i class="bi bi-shield-exclamation me-1"></i>XSS BLOCKED
                                            </span>
                                        </c:when>
                                        <c:when test="${log.event == 'RATE_LIMITED'}">
                                            <span class="badge bg-warning-subtle text-warning-emphasis border border-warning-subtle rounded-pill">
                                                <i class="bi bi-stopwatch-fill me-1"></i>RATE LIMITED
                                            </span>
                                        </c:when>
                                        <c:when test="${log.event == 'BLACKLISTED'}">
                                            <span class="badge bg-dark text-white rounded-pill">
                                                <i class="bi bi-ban me-1"></i>BLACKLISTED
                                            </span>
                                        </c:when>
                                        <c:when test="${log.event == 'UNAUTHORIZED_ADMIN_ACCESS'}">
                                            <span class="badge bg-secondary text-white rounded-pill">
                                                <i class="bi bi-lock-fill me-1"></i>NO ADMIN
                                            </span>
                                        </c:when>

                                        <c:when test="${log.event == 'LOGIN_SUCCESS'}">
                                            <span class="badge bg-success-subtle text-success border border-success-subtle rounded-pill">
                                                <i class="bi bi-check-circle-fill me-1"></i>SUCCESS
                                            </span>
                                        </c:when>
                                        <c:when test="${log.event == 'LOGIN_FAIL'}">
                                            <span class="badge bg-danger-subtle text-danger border border-danger-subtle rounded-pill">
                                                <i class="bi bi-x-circle-fill me-1"></i>FAIL
                                            </span>
                                        </c:when>
                                        <c:when test="${log.event == 'ACCOUNT_CREATED'}">
                                            <span class="badge bg-primary-subtle text-primary border border-primary-subtle rounded-pill">
                                                <i class="bi bi-person-plus-fill me-1"></i>NEW USER
                                            </span>
                                        </c:when>
                                        <c:when test="${log.event == 'ACCOUNT_DISABLED'}">
                                            <span class="badge bg-warning-subtle text-warning-emphasis border border-warning-subtle rounded-pill">
                                                <i class="bi bi-slash-circle-fill me-1"></i>DISABLED
                                            </span>
                                        </c:when>
                                        <c:when test="${log.event == 'ACCOUNT_ENABLED'}">
                                            <span class="badge bg-info-subtle text-info-emphasis border border-info-subtle rounded-pill">
                                                <i class="bi bi-check-circle-fill me-1"></i>ENABLED
                                            </span>
                                        </c:when>
                                        <c:when test="${log.event == 'PASSWORD_CHANGE'}">
                                            <span class="badge text-white rounded-pill" style="background-color: #6610f2;">
                                                <i class="bi bi-key-fill me-1"></i>PWD CHANGE
                                            </span>
                                        </c:when>
                                        <c:when test="${log.event == 'ACCOUNT_DELETED'}">
                                            <span class="badge bg-dark text-danger border border-danger rounded-pill">
                                                <i class="bi bi-trash-fill me-1"></i>DELETED
                                            </span>
                                        </c:when>
                                        <c:when test="${log.event == 'PAGE_VIEW'}">
                                            <span class="badge bg-secondary-subtle text-secondary border border-secondary-subtle rounded-pill">
                                                VIEW
                                            </span>
                                        </c:when>
                                        <c:otherwise>
                                            <span class="badge bg-light text-dark border rounded-pill"><c:out value="${log.event}"/></span>
                                        </c:otherwise>
                                    </c:choose>
                                </td>


                                <td class="small text-muted text-break"><c:out value="${log.details}"/></td>
                            </tr>
                        </c:forEach>
                    </c:when>
                    <c:otherwise>
                        <tr>
                            <td colspan="7" class="text-center py-5 text-muted">
                                <i class="bi bi-search display-4 mb-3 opacity-25"></i>
                                <p class="mb-0 fw-bold">No logs found.</p>
                                <p class="small">Try adjusting your filters.</p>
                            </td>
                        </tr>
                    </c:otherwise>
                </c:choose>
                </tbody>
            </table>
        </div>
    </div>

    <%-- Paginación --%>
    <nav id="paginationNav" aria-label="Page navigation" class="mt-4 mb-5 fade-in-up" style="animation-delay: 0.3s;">
        <ul id="paginationContainer" class="pagination justify-content-center pagination-sm">
            <c:if test="${totalPages > 1}">
                <c:if test="${currentPage > 1}">
                    <li class="page-item">
                        <a class="page-link" href="#" data-page="${currentPage - 1}">Previous</a>
                    </li>
                </c:if>

                <li class="page-item disabled"><span class="page-link">Page ${currentPage} of ${totalPages}</span></li>

                <c:if test="${currentPage < totalPages}">
                    <li class="page-item">
                        <a class="page-link" href="#" data-page="${currentPage + 1}">Next</a>
                    </li>
                </c:if>
            </c:if>
        </ul>
    </nav>

</div>

<script src="${pageContext.request.contextPath}/js/jquery-3.6.0.min.js"></script>
<script src="${pageContext.request.contextPath}/js/bootstrap.bundle.min.js"></script>

<script src="${pageContext.request.contextPath}/scripts/admin-logs-scripts.js"></script>
<script src="${pageContext.request.contextPath}/scripts/csrf-refresher.js" defer></script>

</body>
</html>