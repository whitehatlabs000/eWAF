<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>

<!DOCTYPE html>
<html lang="en" data-bs-theme="light">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="csrf-token" content="${csrfToken}">
    <title>Login · eWAF Console</title>
    <link href="${pageContext.request.contextPath}/css/bootstrap.min.css" rel="stylesheet">
    <link href="${pageContext.request.contextPath}/css/bootstrap-icons.css" rel="stylesheet">

    <link href="${pageContext.request.contextPath}/css/login.css" rel="stylesheet">

    <jsp:include page="/WEB-INF/jsp/common/head_setup.jsp" />
</head>
<body class="waf-login-body bg-body-tertiary">

<%-- Theme Switcher Flotante y Estilizado --%>
<div class="theme-toggle-wrapper shadow-sm px-3 py-2">
    <div class="d-flex align-items-center justify-content-center">
        <i class="bi bi-sun-fill text-warning me-2" style="font-size: 1.1rem;"></i>
        <div class="form-check form-switch m-0 p-0 d-flex align-items-center">

            <input class="form-check-input shadow-none m-0 float-none" type="checkbox" id="themeSwitch" title="Change theme" style="cursor: pointer; width: 2.5em; height: 1.25em;">
        </div>
        <i class="bi bi-moon-stars-fill text-info ms-2" style="font-size: 1rem;"></i>
    </div>
</div>

<main class="form-signin w-100 m-auto">
    <div class="card login-card shadow">
        <div class="card-body p-4 p-md-5">

            <div class="text-center mb-4">
                <%-- Contenedor del escudo interactivo --%>
                <div class="shield-container d-inline-block mb-2" id="wafGuardDog" title="Click to test Watchdog" style="cursor: crosshair;">
                    <i class="bi bi-shield-check text-primary shield-icon"></i>
                </div>
                <h1 class="h4 mb-1 fw-bold tracking-tight">eWAF Console</h1>
                <p class="text-muted small text-uppercase tracking-widest fw-semibold mb-0">System Access</p>
            </div>

            <%-- Captura de errores --%>
            <c:if test="${not empty error}">
                <div class="alert alert-danger d-flex align-items-center alert-custom fade-in" role="alert">
                    <i class="bi bi-shield-exclamation fs-5 me-3"></i>
                    <div class="small fw-medium"><c:out value="${error}" /></div>
                </div>
            </c:if>

            <form action="login" method="post" autocomplete="off">
                <input type="hidden" name="csrfToken" value="${csrfToken}" />

                <div class="form-floating mb-3 custom-floating">
                    <input type="text" class="form-control" id="username" name="username" placeholder="User" required autofocus autocomplete="off">
                    <label for="username"><i class="bi bi-person me-2 text-muted"></i>Username</label>
                </div>

                <div class="form-floating mb-4 custom-floating">
                    <input type="password" class="form-control" id="password" name="password" placeholder="Password" required autocomplete="new-password">
                    <label for="password"><i class="bi bi-key me-2 text-muted"></i>Password</label>
                </div>

                <button class="btn btn-primary w-100 py-2 fw-bold btn-login text-uppercase tracking-widest" type="submit">
                    Authenticate <i class="bi bi-box-arrow-in-right ms-1"></i>
                </button>
            </form>

            <%-- Snippet de Terminal --%>
            <div class="watchdog-terminal mt-4 p-3 rounded">
                <div class="terminal-header d-flex gap-1 mb-2">
                    <span class="dot bg-danger"></span>
                    <span class="dot bg-warning"></span>
                    <span class="dot bg-success"></span>
                </div>
                <div class="terminal-body font-monospace small">
                    <span class="text-success">sys@ewaf:~$</span> systemctl status watchdog<br>
                    <span class="text-info">></span> Service: <span class="text-white">Active</span><br>
                    <span class="text-info">></span> Status: <span id="watchdogStatus" class="text-secondary">Silent monitoring...</span>
                </div>
            </div>

        </div>
    </div>

    <footer class="mt-4 text-center">
        <p class="text-muted small fw-medium">&copy; <%= java.time.Year.now().getValue() %> eWAF Security. All rights reserved.</p>
    </footer>
</main>

<script src="${pageContext.request.contextPath}/js/bootstrap.bundle.min.js"></script>
<script src="${pageContext.request.contextPath}/scripts/csrf-refresher.js" defer></script>
<jsp:include page="/scripts/theme-switcher-baseUrl-CSRFrefresher.jsp" />

<script src="${pageContext.request.contextPath}/scripts/login.js" defer></script>

</body>
</html>