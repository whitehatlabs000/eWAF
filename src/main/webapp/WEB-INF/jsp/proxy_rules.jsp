<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>

<!DOCTYPE html>
<html lang="en" data-bs-theme="light">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>eWAF - Proxy Rules</title>
    <meta name="csrf-token" content="${csrfToken}">

    <link href="${pageContext.request.contextPath}/css/bootstrap.min.css" rel="stylesheet">
    <link href="${pageContext.request.contextPath}/css/bootstrap-icons.css" rel="stylesheet">
    <link href="${pageContext.request.contextPath}/css/root.css" rel="stylesheet">
    <link href="${pageContext.request.contextPath}/css/proxy-rules.css" rel="stylesheet">

    <jsp:include page="/WEB-INF/jsp/common/head_setup.jsp" />
</head>
<body>

<jsp:include page="common/modelo_general.jsp" />

<div class="container main-container">

    <div class="d-flex justify-content-between align-items-center mb-4 fade-in-up">
        <div>
            <h1 class="h2 mb-0 fw-bold">Reverse Proxy Rules</h1>
            <p class="text-muted small mb-0">Manage traffic routing to internal applications.</p>
        </div>
        <a href="${pageContext.request.contextPath}/admin-dashboard" class="btn btn-outline-secondary">
            <i class="bi bi-arrow-left me-1"></i> Dashboard
        </a>
    </div>

    <%-- Mensajes Flash --%>
    <c:if test="${param.msg eq 'success'}">
        <div class="alert alert-success alert-dismissible fade show shadow-sm" role="alert">
            <i class="bi bi-check-circle-fill me-2"></i> Operation completed successfully.
            <button type="button" class="btn-close" data-bs-dismiss="alert"></button>
        </div>
    </c:if>
    <c:if test="${not empty error}">
        <div class="alert alert-danger alert-dismissible fade show shadow-sm" role="alert">
            <i class="bi bi-exclamation-triangle-fill me-2"></i> ${error}
            <button type="button" class="btn-close" data-bs-dismiss="alert"></button>
        </div>
    </c:if>

    <%-- (Crear Regla) --%>
    <div class="row g-4 mb-4 fade-in-up" style="animation-delay: 0.1s;">
        <div class="col-md-12">
            <div class="card shadow-sm h-100 border-primary-subtle">
                <div class="card-body d-flex align-items-center justify-content-between p-4">
                    <div>
                        <h5 class="card-title text-primary fw-bold mb-1">
                            <i class="bi bi-signpost-split-fill me-2"></i>Routing Configuration
                        </h5>
                        <p class="card-text text-secondary small mb-0">Add new entry points to expose internal microservices or apps.</p>
                    </div>
                    <button class="btn btn-primary" onclick="openModal('create')">
                        <i class="bi bi-plus-lg me-1"></i> Add New Rule
                    </button>
                </div>
            </div>
        </div>
    </div>

    <%-- Lista de Reglas --%>
    <div class="card shadow-sm fade-in-up" style="animation-delay: 0.2s;">
        <div class="card-header bg-transparent py-3 border-bottom">
            <div class="d-flex justify-content-between align-items-center">
                <h6 class="mb-0 fw-bold"><i class="bi bi-list-columns-reverse me-2"></i>Active Routes</h6>
                <span class="badge bg-secondary rounded-pill">${routes != null ? routes.size() : 0} Rules</span>
            </div>
        </div>

        <div class="table-responsive">
            <table class="table table-hover align-middle mb-0">
                <thead>
                <tr>
                    <th class="ps-4">Incoming Path</th>
                    <th>Destination (Backend)</th>
                    <th>Engine</th>
                    <th>Cache</th>
                    <th>Status</th>
                    <th class="text-end pe-4">Actions</th>
                </tr>
                </thead>
                <tbody>
                <c:forEach var="route" items="${routes}">
                    <tr>
                        <td class="ps-4">
                            <div class="d-flex align-items-center">
                                <span class="badge-path fw-bold text-primary">${route.incomingPath}</span>
                            </div>
                        </td>
                        <td>
                            <div class="d-flex flex-column">
                                <span class="text-truncate" style="max-width: 300px;" title="${route.targetUrl}">
                                    <i class="bi bi-hdd-network me-1 text-secondary opacity-50"></i> ${route.targetUrl}
                                </span>
                                <c:if test="${not empty route.customReplacements}">
                                    <small class="text-info mt-1">
                                        <i class="bi bi-code-slash me-1"></i>Has JSON Replacements
                                    </small>
                                </c:if>
                            </div>
                        </td>
                        <td>
                            <c:choose>
                                <c:when test="${route.engineType == 'NGINX'}">
                                    <span class="badge bg-success"><i class="bi bi-lightning-charge-fill me-1"></i>NGINX</span>
                                    <c:if test="${route.useModSecurity}">
                                        <span class="badge bg-danger ms-1" title="ModSecurity WAF Enabled"><i class="bi bi-shield-fill-check"></i> WAF</span>
                                    </c:if>
                                </c:when>
                                <c:when test="${route.engineType == 'SPRING'}">
                                    <span class="badge bg-info text-dark"><i class="bi bi-diagram-3-fill me-1"></i>SPRING</span>
                                </c:when>
                                <c:otherwise>
                                    <span class="badge bg-secondary"><i class="bi bi-cpu-fill me-1"></i>NATIVE</span>
                                </c:otherwise>
                            </c:choose>
                        </td>
                        <td>
                            <c:choose>
                                <c:when test="${route.cacheTtlSeconds > 0}">
                                    <span class="text-success small fw-bold"><i class="bi bi-hdd-fill me-1"></i>${route.cacheTtlSeconds}s</span>
                                </c:when>
                                <c:otherwise>
                                    <span class="text-muted small"><i class="bi bi-hdd me-1"></i>Off</span>
                                </c:otherwise>
                            </c:choose>
                        </td>
                        <td>
                            <c:choose>
                                <c:when test="${route.active}">
                                    <span class="badge rounded-pill bg-success-subtle text-success border border-success-subtle">
                                        <span class="status-dot status-active"></span> Active
                                    </span>
                                </c:when>
                                <c:otherwise>
                                    <span class="badge rounded-pill bg-secondary-subtle text-secondary border border-secondary-subtle">
                                        <span class="status-dot status-inactive"></span> Inactive
                                    </span>
                                </c:otherwise>
                            </c:choose>
                        </td>
                        <td class="text-end pe-4">
                            <div class="btn-group">
                                <button class="btn btn-sm btn-outline-secondary border-0"
                                        onclick="openModal('update', ${route.id}, '${route.incomingPath}', '${route.targetUrl}', ${route.active}, '${route.escapedCustomReplacements}', '${route.engineType}', ${route.cacheTtlSeconds}, ${route.useModSecurity})"
                                        title="Edit Rule">
                                    <i class="bi bi-pencil-square"></i>
                                </button>
                                <button class="btn btn-sm btn-outline-danger border-0"
                                        onclick="confirmDelete(${route.id})" title="Delete Rule">
                                    <i class="bi bi-trash"></i>
                                </button>
                            </div>
                        </td>
                    </tr>
                </c:forEach>
                <c:if test="${empty routes}">
                    <tr>
                        <td colspan="4" class="text-center py-5">
                            <div class="opacity-50 mb-3"><i class="bi bi-signpost display-4 text-muted"></i></div>
                            <h6 class="text-muted fw-bold">No routing rules found</h6>
                            <p class="text-secondary small">Click "Add New Rule" to configure your first proxy path.</p>
                        </td>
                    </tr>
                </c:if>
                </tbody>
            </table>
        </div>
    </div>
</div>

<%-- MODAL: Create / Edit --%>
<div class="modal fade" id="ruleModal" tabindex="-1" aria-hidden="true">
    <div class="modal-dialog modal-dialog-centered">
        <div class="modal-content">
            <form action="${pageContext.request.contextPath}/proxy-rules" method="post">
                <input type="hidden" name="action" id="modalAction" value="create">
                <input type="hidden" name="id" id="modalId" value="0">
                <input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}">

                <div class="modal-header">
                    <h5 class="modal-title fw-bold" id="modalTitle">New Rule</h5>
                    <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
                </div>
                <div class="modal-body">
                    <div class="mb-3">
                        <label for="incomingPath" class="form-label">Incoming Path</label>
                        <input type="text" class="form-control" id="incomingPath" name="incomingPath" placeholder="/github" required pattern="^/[a-zA-Z0-9_\-]+$">
                        <div class="form-text small">Must start with <code>/</code> (e.g., <code>/github</code> or <code>/api</code>). External users access this path.</div>
                    </div>

                    <div class="mb-3">
                        <label for="targetUrl" class="form-label">Target Backend URL</label>
                        <input type="url" class="form-control" id="targetUrl" name="targetUrl" placeholder="http://localhost:8080/app" required>
                        <div class="form-text small">Full internal URL where traffic will be forwarded.</div>
                    </div>

                    <div class="mb-3">
                        <label for="customReplacements" class="form-label">Replacements (JSON)</label>
                        <textarea class="form-control font-monospace" id="customReplacements" name="customReplacements" rows="3" style="font-size: 0.85rem;" placeholder='{"/old-path": "/new-path"}'></textarea>
                        <div class="form-text small">Optional string replacements for the response body.</div>
                    </div>

                    <div class="mb-3">
                        <label for="engineType" class="form-label">Processing Engine</label>
                        <select class="form-select" id="engineType" name="engineType">
                            <option value="NATIVE">NATIVE (Tomcat / Java)</option>
                            <option value="NGINX">NGINX (High Performance / C++)</option>
                            <option value="SPRING">SPRING (Cloud Gateway / Reactive)</option>
                        </select>
                        <div class="form-text small">Select which underlying engine will process requests for this route.</div>
                    </div>

                    <div class="mb-3">
                        <label for="cacheTtlSeconds" class="form-label">Cache TTL (Seconds)</label>
                        <input type="number" class="form-control" id="cacheTtlSeconds" name="cacheTtlSeconds" value="0" min="0">
                        <div class="form-text small">Time in seconds to cache the response in NGINX. Use <code>0</code> to disable caching. Example: 3600 = 1 Hour.</div>
                    </div>

                    <div id="modSecurityContainer" class="form-check form-switch pt-2 text-danger" style="display: none;">
                        <input class="form-check-input" type="checkbox" id="useModSecurity" name="useModSecurity">
                        <label class="form-check-label fw-bold" for="useModSecurity"><i class="bi bi-shield-fill-check me-1"></i>Enable ModSecurity WAF</label>
                        <div class="form-text small text-muted">Only available for NGINX engine. Protects against SQLi, XSS, and LFI.</div>
                    </div>

                    <div class="form-check form-switch pt-2">
                        <input class="form-check-input" type="checkbox" id="active" name="active" checked>
                        <label class="form-check-label" for="active">Enable this rule immediately</label>
                    </div>
                </div>
                <div class="modal-footer bg-transparent">
                    <button type="button" class="btn btn-link text-decoration-none text-muted" data-bs-dismiss="modal">Cancel</button>
                    <button type="submit" class="btn btn-primary px-4">Save Changes</button>
                </div>
            </form>
        </div>
    </div>
</div>

<%-- MODAL: Delete Confirmation --%>
<div class="modal fade" id="deleteModal" tabindex="-1" aria-hidden="true">
    <div class="modal-dialog modal-dialog-centered modal-sm">
        <div class="modal-content">
            <div class="modal-header border-0 pb-0">
                <h5 class="modal-title text-danger fw-bold">Delete Rule</h5>
                <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
            </div>
            <div class="modal-body text-center py-4">
                <i class="bi bi-exclamation-circle text-danger display-1 mb-3"></i>
                <p class="mb-0 fw-medium">Are you sure?</p>
                <p class="text-muted small">This action cannot be undone.</p>
            </div>
            <div class="modal-footer border-0 pt-0 justify-content-center pb-4">
                <button type="button" class="btn btn-light" data-bs-dismiss="modal">Cancel</button>
                <button type="button" class="btn btn-danger px-4" onclick="executeDelete()">Delete</button>
            </div>
        </div>
    </div>
</div>

<%-- FORM: Delete (Hidden) --%>
<form id="deleteForm" action="${pageContext.request.contextPath}/proxy-rules" method="post">
    <input type="hidden" name="action" value="delete">
    <input type="hidden" name="id" id="deleteId">
    <input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}">
</form>

<script src="${pageContext.request.contextPath}/js/bootstrap.bundle.min.js"></script>
<script src="${pageContext.request.contextPath}/scripts/csrf-refresher.js" defer></script>


<script>
    // Referencias a los Modales
    const ruleModalEl = document.getElementById('ruleModal');
    // Asegurarse de que deleteModal existe antes de inicializarlo para evitar errores en consola si no se usa
    const deleteModalEl = document.getElementById('deleteModal');

    const ruleModal = new bootstrap.Modal(ruleModalEl);
    const deleteModal = new bootstrap.Modal(deleteModalEl);

    // Variable temporal para guardar el ID a eliminar
    let idToDelete = null;

    // Escuchador dinámico: Mostrar/Ocultar ModSecurity según el motor seleccionado
    document.getElementById('engineType').addEventListener('change', function() {
        toggleModSecurityUI(this.value);
    });

    function toggleModSecurityUI(engine) {
        const modSecContainer = document.getElementById('modSecurityContainer');
        if (engine === 'NGINX') {
            modSecContainer.style.display = 'block';
        } else {
            modSecContainer.style.display = 'none';
            document.getElementById('useModSecurity').checked = false; // Apagar por seguridad si cambia de motor
        }
    }

    function openModal(mode, id, path, target, active, replacements, engine, cacheTtl, useModSec) {
        document.getElementById('modalTitle').textContent = mode === 'create' ? "New Proxy Rule" : "Edit Proxy Rule";
        document.getElementById('modalAction').value = mode === 'create' ? "create" : "update";
        document.getElementById('modalId').value = typeof id !== 'undefined' ? id : 0;
        document.getElementById('incomingPath').value = typeof path !== 'undefined' ? path : "";
        document.getElementById('targetUrl').value = typeof target !== 'undefined' ? target : "";
        document.getElementById('customReplacements').value = typeof replacements !== 'undefined' ? replacements : "";
        document.getElementById('active').checked = typeof active !== 'undefined' ? active : true;

        const selectedEngine = typeof engine !== 'undefined' ? engine : "NATIVE";
        document.getElementById('engineType').value = selectedEngine;

        document.getElementById('cacheTtlSeconds').value = typeof cacheTtl !== 'undefined' ? cacheTtl : 0;
        document.getElementById('useModSecurity').checked = typeof useModSec !== 'undefined' ? useModSec : false;

        // Actualizar interfaz visual al abrir
        toggleModSecurityUI(selectedEngine);

        ruleModal.show();
    }

    // Paso 1: Abrir el modal de confirmación
    function confirmDelete(id) {
        idToDelete = id;
        deleteModal.show();
    }

    // Paso 2: Ejecutar el submit si el usuario confirma en el modal
    function executeDelete() {
        if (idToDelete) {
            document.getElementById('deleteId').value = idToDelete;
            document.getElementById('deleteForm').submit();
        }
    }
</script>

</body>
</html>