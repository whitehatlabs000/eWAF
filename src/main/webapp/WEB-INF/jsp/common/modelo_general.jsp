<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>

<link href="${pageContext.request.contextPath}/css/modelo_general.css" rel="stylesheet">

<%-- MENU MÓVIL --%>
<div class="offcanvas offcanvas-start" tabindex="-1" id="mobileSidebar" aria-labelledby="mobileSidebarLabel">
    <div class="offcanvas-header border-bottom">
        <h5 class="offcanvas-title fw-bold" id="mobileSidebarLabel">
            <i class="bi bi-shield-lock-fill me-2 text-primary"></i>eWAF Admin
        </h5>
        <button type="button" class="btn-close" data-bs-dismiss="offcanvas" aria-label="Close"></button>
    </div>
    <div class="offcanvas-body d-flex flex-column p-0">
        <div class="flex-grow-1 overflow-y-auto">
            <div class="p-3 d-grid gap-2">
                <span class="text-uppercase text-muted small fw-bold mb-2">Management tools</span>


                <a href="admin-dashboard" class="btn mobile-nav-btn text-start py-2">
                    <i class="bi bi-speedometer2 me-2 text-primary"></i> Dashboard
                </a>


                <a href="admin-waf-stats" class="btn mobile-nav-btn text-start py-2">
                    <i class="bi bi-graph-up-arrow me-2 text-info"></i> Stats
                </a>

                <a href="admin-logs" class="btn mobile-nav-btn text-start py-2">
                    <i class="bi bi-journal-text me-2 text-success"></i> Access Logs
                </a>


                <a href="admin-manage_users" class="btn mobile-nav-btn text-start py-2">
                    <i class="bi bi-people-fill me-2 text-primary"></i> Manage Users
                </a>


                <a href="admin-create_account" class="btn mobile-nav-btn text-start py-2">
                    <i class="bi bi-person-plus-fill me-2 text-success"></i> Create Account
                </a>

                <a href="proxy-rules" class="btn mobile-nav-btn text-start py-2">
                    <i class="bi bi-hdd-network me-2 text-primary"></i> Proxy Rules
                </a>

                <a href="admin-block-ip" class="btn mobile-nav-btn text-start py-2">
                    <i class="bi bi-slash-circle me-2 text-danger"></i> IP Blocking
                </a>
                <a href="admin-maintenance" class="btn mobile-nav-btn text-start py-2">
                    <i class="bi bi-tools me-2 text-warning"></i> Maintenance
                </a>

                <hr class="opacity-25">

                <span class="text-uppercase text-muted small fw-bold mb-2">Account</span>
                <c:if test="${not empty sessionScope.user}">
                    <a href="admin_change_password?u=${sessionScope.user}" class="btn mobile-nav-btn text-start py-2">
                        <i class="bi bi-key-fill me-2"></i> Change Password
                    </a>
                    <a href="logout" class="btn mobile-nav-btn text-start py-2 text-danger">
                        <i class="bi bi-box-arrow-right me-2"></i> Logout
                    </a>
                </c:if>
                <c:if test="${empty sessionScope.user}">
                    <a href="login" class="btn btn-primary w-100">Login</a>
                </c:if>
            </div>
        </div>
        <div class="offcanvas-footer p-3 border-top">
            <div class="d-flex justify-content-between align-items-center">
                <span class="fw-semibold"><i class="bi bi-palette-fill me-2"></i>Theme</span>
                <div class="form-check form-switch m-0">
                    <input class="form-check-input" type="checkbox" id="themeSwitch">
                </div>
            </div>
        </div>
    </div>
</div>

<%-- BARRA DE NAVEGACIÓN DESKTOP --%>
<nav class="navbar navbar-expand-lg navbar-dark sticky-top shadow-sm">
    <div class="container-fluid px-lg-4">

        <button class="navbar-toggler me-2 border-0" type="button" data-bs-toggle="offcanvas" data-bs-target="#mobileSidebar">
            <span class="navbar-toggler-icon"></span>
        </button>

        <a class="navbar-brand fw-bold d-flex align-items-center me-4" href="admin-dashboard">
            <i class="bi bi-shield-check me-2 fs-4 text-primary"></i>
            <span class="tracking-tight">eWAF</span>
        </a>

        <div class="collapse navbar-collapse" id="navbarSupportedContent">
            <ul class="navbar-nav me-auto mb-2 mb-lg-0 fw-medium">
                <li class="nav-item">
                    <a class="nav-link px-3" href="admin-dashboard">Dashboard</a>
                </li>

                <li class="nav-item">
                    <a class="nav-link px-3" href="admin-waf-stats">Stats</a>
                </li>
                <li class="nav-item">
                    <a class="nav-link px-3" href="admin-logs">Logs</a>
                </li>

                <li class="nav-item">
                    <a class="nav-link px-3" href="admin-manage_users">Users</a>
                </li>
                <li class="nav-item">
                    <a class="nav-link px-3" href="admin-create_account">Create Account</a>
                </li>

                <li class="nav-item">
                    <a class="nav-link px-3" href="proxy-rules">Proxy Rules</a>
                </li>

                <li class="nav-item">
                    <a class="nav-link px-3" href="admin-block-ip">IP Block</a>
                </li>
                <li class="nav-item">
                    <a class="nav-link px-3" href="admin-maintenance">Maintenance</a>
                </li>
            </ul>

            <div class="d-flex align-items-center gap-3">

                <div class="text-white d-flex align-items-center" title="Toggle Theme">
                    <i class="bi bi-sun-fill me-2 small text-secondary"></i>
                    <div class="form-check form-switch m-0">
                        <input class="form-check-input" type="checkbox" id="themeSwitchDesktop">
                    </div>
                    <i class="bi bi-moon-stars-fill ms-2 small text-secondary"></i>
                </div>

                <div class="vr bg-secondary opacity-25 mx-2" style="height: 25px;"></div>

                <c:choose>
                    <c:when test="${not empty sessionScope.user}">
                        <div class="dropdown">
                            <a class="nav-link dropdown-toggle d-flex align-items-center gap-2 text-white" href="#" role="button" data-bs-toggle="dropdown" aria-expanded="false">
                                <div class="bg-primary rounded-circle d-flex align-items-center justify-content-center text-white fw-bold" style="width: 32px; height: 32px; font-size: 0.9rem;">
                                        ${sessionScope.user.charAt(0).toString().toUpperCase()}
                                </div>
                                <span>${sessionScope.user}</span>
                            </a>
                            <ul class="dropdown-menu dropdown-menu-end shadow border-0 mt-2 rounded-3 overflow-hidden">
                                <li>

                                    <div class="px-3 py-2 mb-2 dropdown-user-header">
                                        <div class="fw-bold">Signed in as</div>
                                        <div class="small opacity-75 text-truncate" style="max-width: 150px;">${sessionScope.user}</div>
                                    </div>
                                </li>
                                <li>
                                    <a class="dropdown-item py-2 d-flex align-items-center" href="admin_change_password?u=${sessionScope.user}">
                                        <i class="bi bi-key me-2 opacity-50"></i> Change Password
                                    </a>
                                </li>
                                <li><hr class="dropdown-divider"></li>
                                <li>
                                    <a class="dropdown-item py-2 d-flex align-items-center text-danger" href="logout">
                                        <i class="bi bi-box-arrow-right me-2"></i> Logout
                                    </a>
                                </li>
                            </ul>
                        </div>
                    </c:when>
                    <c:otherwise>
                        <a href="login" class="btn btn-outline-light btn-sm rounded-pill px-3">Log in</a>
                    </c:otherwise>
                </c:choose>
            </div>
        </div>
    </div>
</nav>

<jsp:include page="/scripts/theme-switcher-baseUrl-CSRFrefresher.jsp" />