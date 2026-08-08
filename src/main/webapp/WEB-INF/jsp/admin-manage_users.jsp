<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>

<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>eWAF · Manage Users</title>
    <meta name="csrf-token" content="${csrfToken}">

    <link href="${pageContext.request.contextPath}/css/bootstrap.min.css" rel="stylesheet">
    <link href="${pageContext.request.contextPath}/css/bootstrap-icons.css" rel="stylesheet">
    <link href="${pageContext.request.contextPath}/css/root.css" rel="stylesheet">
    <link href="${pageContext.request.contextPath}/css/admin-manage_users.css" rel="stylesheet">

    <jsp:include page="/WEB-INF/jsp/common/head_setup.jsp" />
</head>
<body>

<jsp:include page="common/modelo_general.jsp" />


<div class="container-fluid main-container">
    <div class="row justify-content-center">

        <div class="col-lg-10">

            <div class="d-flex justify-content-between align-items-center mb-4 fade-in-up">
                <div>
                    <h2 class="fw-bold mb-1">User Management</h2>
                    <p class="text-muted small mb-0">Security controls and access auditing.</p>
                </div>
                <a href="${pageContext.request.contextPath}/admin-dashboard" class="btn btn-outline-secondary">
                    <i class="bi bi-arrow-left me-1"></i> Dashboard
                </a>
            </div>

            <div id="searchFormContainer" class="card mb-4 search-card fade-in-up" style="animation-delay: 0.1s;">
                <div class="card-body">
                    <form id="searchForm" autocomplete="off">
                        <div class="input-group mb-3">
                            <span class="input-group-text bg-transparent border-end-0"><i class="bi bi-search"></i></span>
                            <input type="text" maxlength="100" class="form-control border-start-0 ps-0" name="q" value="<c:out value="${q}"/>" placeholder="Search user by name...">
                            <button class="btn btn-primary px-4" type="submit">Search</button>
                        </div>

                        <div class="row align-items-center g-3">
                            <div class="col-md-6">
                                <label class="form-label text-muted small fw-bold text-uppercase mb-1">Order By</label>
                                <select class="form-select form-select-sm" name="order">
                                    <option value="newest" <c:if test="${empty order or order == 'newest'}">selected</c:if>>Newest Created</option>
                                    <option value="last_login" <c:if test="${order == 'last_login'}">selected</c:if>>Recently Active</option>
                                    <option value="username" <c:if test="${order == 'username'}">selected</c:if>>Alphabetical</option>
                                </select>
                            </div>
                            <div class="col-md-6">
                                <div class="form-check form-switch mt-4">
                                    <input class="form-check-input" type="checkbox" name="filter" id="filter_banned" value="banned" <c:if test="${filter == 'banned'}">checked</c:if>>
                                    <label class="form-check-label small fw-bold" for="filter_banned">Show Banned Only</label>
                                </div>
                            </div>
                        </div>
                    </form>
                </div>
            </div>

            <div id="usersContainer" class="fade-in-up" style="animation-delay: 0.2s;"></div>

            <div id="loadingIndicator" class="text-center my-4 fade-in-up" style="display: none; animation-delay: 0.3s;">
                <div class="spinner-border text-primary" role="status">
                    <span class="visually-hidden">Loading...</span>
                </div>
            </div>
            <div id="noUsersMessage" class="alert alert-secondary text-center border-0" style="display: none;">
                <i class="bi bi-inbox me-2"></i>No users found.
            </div>

        </div>
    </div>
</div>

<%-- Modales (Delete y Error) --%>
<div class="modal fade" id="deleteUserConfirmModal" tabindex="-1">
    <div class="modal-dialog modal-dialog-centered">
        <div class="modal-content">
            <div class="modal-header">
                <h5 class="modal-title">Confirm Deletion</h5>
                <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
            </div>
            <div class="modal-body">
                <p>Are you sure you want to <strong>permanently delete</strong> this user?</p>
                <div class="alert alert-danger mb-0 small">
                    <i class="bi bi-exclamation-triangle-fill me-1"></i> This action cannot be undone.
                </div>
            </div>
            <div class="modal-footer">
                <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Cancel</button>
                <button type="button" class="btn btn-danger" id="confirmDeleteUserBtn">Delete User</button>
            </div>
        </div>
    </div>
</div>

<div class="modal fade" id="errorModal" tabindex="-1">
    <div class="modal-dialog modal-dialog-centered">
        <div class="modal-content">
            <div class="modal-header bg-danger text-white">
                <h5 class="modal-title"><i class="bi bi-exclamation-octagon-fill me-2"></i>Error</h5>
                <button type="button" class="btn-close btn-close-white" data-bs-dismiss="modal"></button>
            </div>
            <div class="modal-body" id="errorModalBody"></div>
            <div class="modal-footer"><button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Close</button></div>
        </div>
    </div>
</div>

<script src="${pageContext.request.contextPath}/js/jquery-3.6.0.min.js"></script>
<script src="${pageContext.request.contextPath}/js/bootstrap.bundle.min.js"></script>
<script src="${pageContext.request.contextPath}/scripts/admin-manage_users-scripts.js"></script>
<script src="${pageContext.request.contextPath}/scripts/csrf-refresher.js" defer></script>

</body>
</html>