<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>

<!DOCTYPE html>
<html lang="en" data-bs-theme="light">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>Change Password - ${targetUser}</title>
  <link href="${pageContext.request.contextPath}/css/bootstrap.min.css" rel="stylesheet">
  <link href="${pageContext.request.contextPath}/css/bootstrap-icons.css" rel="stylesheet">

  <jsp:include page="/WEB-INF/jsp/common/head_setup.jsp" />
</head>
<body class="d-flex flex-column justify-content-center align-items-center vh-100 bg-body-tertiary">
<div class="position-fixed top-0 end-0 p-3">
  <div class="form-check form-switch">
    <input class="form-check-input" type="checkbox" id="themeSwitchDesktop">
    <label class="form-check-label" for="themeSwitchDesktop"><i class="bi bi-moon-stars-fill"></i></label>
  </div>
</div>
<div class="card shadow-sm p-4" style="max-width: 420px; width: 100%;">
  <div class="text-center mb-4">
    <i class="bi bi-shield-lock-fill text-primary" style="font-size: 3rem;"></i>
    <h1 class="h3 mb-1 fw-normal">Admin Reset</h1>
    <p class="text-muted">Target user: <strong><c:out value="${targetUser}"/></strong></p>
  </div>
  <c:if test="${not empty error}">
    <div class="alert alert-danger"><c:out value="${error}" /></div>
  </c:if>
  <c:if test="${not empty ok}">
    <div class="alert alert-success"><c:out value="${ok}" /></div>
  </c:if>
  <div id="jsError" class="alert alert-danger d-none"></div>
  <form action="admin_change_password" method="post" onsubmit="return validarFormulario();">
    <input type="hidden" name="target_user" value="${targetUser}" />
    <input type="hidden" name="csrfToken" value="${csrfToken}" />
    <div class="input-group mb-3">
      <span class="input-group-text"><i class="bi bi-lock-fill"></i></span>
      <div class="form-floating">
        <input type="password" class="form-control" name="new_password" id="new_password" placeholder="New password" required minlength="6" maxlength="100">
        <label for="new_password">New password</label>
      </div>
    </div>

    <div class="input-group mb-4">
      <span class="input-group-text"><i class="bi bi-check-circle-fill"></i></span>
      <div class="form-floating">
        <input type="password" class="form-control" name="confirm_password" id="confirm_password" placeholder="Repeat new password" required minlength="6" maxlength="100">
        <label for="confirm_password">Repeat new password</label>
      </div>
    </div>
    <button class="btn btn-primary w-100" type="submit">Update</button>
    <a href="admin-manage_users" class="btn btn-link w-100 mt-2">← Return to user list</a>
  </form>
</div>
<script>
  function validarFormulario() {
    const nueva = document.getElementById('new_password').value;
    const repetir = document.getElementById('confirm_password').value;
    const errorDiv = document.getElementById('jsError');
    if (nueva !== repetir) {
      errorDiv.classList.remove('d-none');
      errorDiv.textContent = 'Passwords do not match.';
      return false;
    } else {
      errorDiv.classList.add('d-none');
      errorDiv.textContent = '';
    }

    return true;
  }
</script>
<script src="${pageContext.request.contextPath}/js/bootstrap.bundle.min.js"></script>
<jsp:include page="/scripts/theme-switcher-baseUrl-CSRFrefresher.jsp" />


</body>
</html>
