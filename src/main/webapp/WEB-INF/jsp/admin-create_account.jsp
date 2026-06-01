<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>

<!DOCTYPE html>
<html lang="en" data-bs-theme="light">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>eWAF - Create Account</title>
    <meta name="csrf-token" content="${csrfToken}">

    <link href="${pageContext.request.contextPath}/css/bootstrap.min.css" rel="stylesheet">
    <link href="${pageContext.request.contextPath}/css/bootstrap-icons.css" rel="stylesheet">
    <link href="${pageContext.request.contextPath}/css/root.css" rel="stylesheet">
    <link href="${pageContext.request.contextPath}/css/admin-create-account.css" rel="stylesheet">

    <jsp:include page="/WEB-INF/jsp/common/head_setup.jsp" />
</head>
<body>


<jsp:include page="common/modelo_general.jsp" />


<div class="container main-container">
    <div class="row justify-content-center">
        <div class="col-lg-5 col-md-7">


            <div class="d-flex justify-content-between align-items-center mb-4 fade-in-up">
                <div>
                    <h1 class="h3 mb-0 fw-bold">Create Account</h1>
                    <p class="text-muted small mb-0">Register a new administrator.</p>
                </div>
                <a href="${pageContext.request.contextPath}/admin-dashboard" class="btn btn-outline-secondary">
                    <i class="bi bi-arrow-left me-1"></i> Dashboard
                </a>
            </div>

            <%-- Tarjeta del Formulario --%>
            <div class="card shadow-sm border-0 fade-in-up" style="animation-delay: 0.1s;">
                <div class="card-body p-4 p-md-5">

                    <div class="text-center">
                        <div class="icon-circle">
                            <i class="bi bi-person-plus-fill fs-2"></i>
                        </div>
                        <h5 class="fw-bold mb-4">New User Details</h5>
                    </div>

                    <%-- Mensajes de Feedback --%>
                    <c:if test="${not empty error}">
                        <div class="alert alert-danger d-flex align-items-center shadow-sm" role="alert">
                            <i class="bi bi-exclamation-triangle-fill me-2 fs-5"></i>
                            <div><c:out value="${error}" /></div>
                        </div>
                    </c:if>

                    <c:if test="${not empty ok}">
                        <div class="alert alert-success d-flex align-items-center shadow-sm" role="alert">
                            <i class="bi bi-check-circle-fill me-2 fs-5"></i>
                            <div><c:out value="${ok}" /></div>
                        </div>
                    </c:if>

                    <%-- Formulario --%>
                    <form action="admin-create_account" method="post" autocomplete="off">
                        <input type="hidden" name="csrfToken" value="${csrfToken}">

                        <%-- Username --%>
                        <div class="mb-3">
                            <label for="username" class="form-label fw-bold small text-uppercase">Username</label>
                            <div class="input-group">
                                <span class="input-group-text"><i class="bi bi-person"></i></span>
                                <input type="text" class="form-control" id="username" name="username"
                                       placeholder="e.g. admin_sys" minlength="3" maxlength="25" required autofocus>
                            </div>
                            <div class="form-text small">3-25 chars, letters, numbers, and underscores.</div>
                        </div>

                        <%-- Password --%>
                        <div class="mb-4">
                            <label for="password" class="form-label fw-bold small text-uppercase">Password</label>
                            <div class="input-group">
                                <span class="input-group-text"><i class="bi bi-key"></i></span>
                                <input type="password" class="form-control" id="password" name="password"
                                       placeholder="Secure password" minlength="6" required autocomplete="new-password">
                                <button class="btn btn-toggle-password" type="button" id="togglePassword">
                                    <i class="bi bi-eye"></i>
                                </button>
                            </div>
                        </div>

                        <%-- Botón Submit --%>
                        <div class="d-grid gap-2">
                            <button type="submit" class="btn btn-primary py-2 fw-semibold">
                                <i class="bi bi-plus-lg me-2"></i>Create Account
                            </button>
                        </div>
                    </form>
                </div>
            </div>

            <%-- Nota al pie --%>
            <div class="text-center mt-4 fade-in-up" style="animation-delay: 0.2s;">
                <small class="text-secondary opacity-75">
                    <i class="bi bi-shield-lock me-1"></i> New accounts have full administrative privileges immediately.
                </small>
            </div>

        </div>
    </div>
</div>

<script src="${pageContext.request.contextPath}/js/bootstrap.bundle.min.js"></script>
<script src="${pageContext.request.contextPath}/scripts/csrf-refresher.js" defer></script>


<script>
    // Script basico para mostrar/ocultar contraseña
    const togglePassword = document.querySelector('#togglePassword');
    const password = document.querySelector('#password');

    if(togglePassword && password) {
        togglePassword.addEventListener('click', function (e) {
            // Toggle type
            const type = password.getAttribute('type') === 'password' ? 'text' : 'password';
            password.setAttribute('type', type);
            // Toggle icon
            this.querySelector('i').classList.toggle('bi-eye');
            this.querySelector('i').classList.toggle('bi-eye-slash');
        });
    }
</script>

</body>
</html>