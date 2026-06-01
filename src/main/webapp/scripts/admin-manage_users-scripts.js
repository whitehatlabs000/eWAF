let currentPage = 1;
let isLoading = false;
let hasMore = true;
const USERS_PER_PAGE = 20;

function escapeHtml(unsafe) {
    if (unsafe === null || unsafe === undefined) return "";
    return unsafe.toString().replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;").replace(/'/g, "&#039;");
}

function showErrorModal(message) {
    $('#errorModalBody').text(message);
    const errorModal = new bootstrap.Modal(document.getElementById('errorModal'));
    errorModal.show();
}

function createUserCard(user) {
    const safeUsername = escapeHtml(user.username);
    const initial = safeUsername.charAt(0).toUpperCase();


    // Status Badge
    const statusBadge = user.active
        ? '<span class="badge bg-success-subtle text-success border border-success-subtle status-badge"><i class="bi bi-check-circle-fill me-1"></i>Active</span>'
        : '<span class="badge bg-danger-subtle text-danger border border-danger-subtle status-badge"><i class="bi bi-slash-circle-fill me-1"></i>Banned</span>';

    // Last Login Badge
    let lastLoginHtml = '';
    if (user.lastLogin) {
        lastLoginHtml = `<span class="small text-muted" title="Last successful login"><i class="bi bi-clock-history me-1"></i>Last seen: ${user.lastLogin}</span>`;
    } else {
        lastLoginHtml = `<span class="small text-muted fst-italic"><i class="bi bi-dash-circle me-1"></i>Never logged in</span>`;
    }

    // Toggle Button
    const toggleIcon = user.active ? 'bi-lock-fill' : 'bi-unlock-fill';
    const toggleTitle = user.active ? 'Disable Access' : 'Enable Access';
    const toggleButton = `<button type="submit" class="btn-icon toggle" title="${toggleTitle}"><i class="bi ${toggleIcon}"></i></button>`;

    return `
        <div class="user-card shadow-sm" data-username="${safeUsername}">
            
            <div class="user-avatar-letter flex-shrink-0">
                ${initial}
            </div>
            
            <div class="user-info ms-3">
                <h5 class="mb-0 fw-bold">
                    ${safeUsername} 
                </h5>
                <div class="mt-1">
                    ${lastLoginHtml}
                </div>
            </div>

            <div class="d-flex align-items-center gap-3">
                
                <div class="user-status d-none d-sm-block">
                    ${statusBadge}
                </div>

                <div class="user-actions border-start ps-3 ms-2">
                    
                    <a href="admin_change_password?u=${safeUsername}" class="btn-icon edit" title="Change Password">
                        <i class="bi bi-key-fill"></i>
                    </a>

                    <form class="d-inline toggle-account-form m-0">
                        <input type="hidden" name="username" value="${safeUsername}">
                        <input type="hidden" name="action" value="${user.active ? 'disable' : 'enable'}">
                        ${toggleButton}
                    </form>

                    <form class="d-inline delete-user-form m-0">
                        <input type="hidden" name="username" value="${safeUsername}">
                        <button type="submit" class="btn-icon delete" title="Delete User">
                            <i class="bi bi-trash-fill"></i>
                        </button>
                    </form>
                </div>
            </div>
        </div>
    `;
}

function loadUsers(page = 1) {
    if (isLoading || !hasMore && page > 1) return;
    isLoading = true;
    $('#loadingIndicator').show();

    if (page === 1) {
        $('#usersContainer').empty();
        $('#noUsersMessage').hide();
        hasMore = true;
    }

    const params = {
        action: 'load_users',
        q: $('#searchForm input[name="q"]').val(),
        order: $('input[name="order"]:checked').val() || $('select[name="order"]').val(),
        page: page
    };

    // switch de baneados
    if ($('#filter_banned').is(':checked')) {
        params.filter = 'banned';
    }

    $.ajax({
        url: 'admin-manage_users',
        type: 'GET',
        data: params,
        dataType: 'json',
        success: function(users) {
            if (users && users.length > 0) {
                users.forEach(user => {
                    $('#usersContainer').append(createUserCard(user));
                });
                if (users.length < USERS_PER_PAGE) hasMore = false;
            } else {
                hasMore = false;
                if (page === 1) $('#noUsersMessage').show();
            }
        },
        error: (xhr, status, error) => {
            console.error("Error loading users:", status, error);
            showErrorModal('Could not load user list.');
        },
        complete: () => {
            isLoading = false;
            $('#loadingIndicator').hide();
            currentPage = page + 1;
        }
    });
}

$(document).ready(function() {

    $.ajaxSetup({
        beforeSend: function(xhr) {
            const token = document.querySelector('meta[name="csrf-token"]').getAttribute('content');
            if (token) {
                xhr.setRequestHeader('X-CSRF-Token', token);
            }
        }
    });

    $('#searchForm').on('submit', function(e) {
        e.preventDefault();
        currentPage = 1;
        loadUsers(currentPage);
    });
    $('input[name="order"], select[name="order"]').on('change', function() {
        currentPage = 1;
        loadUsers(currentPage);
    });
    $('#filter_banned').on('change', function() {
        currentPage = 1;
        loadUsers(currentPage);
    });

    $(window).on('scroll', function() {
        if ($(window).scrollTop() + $(window).height() >= $(document).height() - 250 && !isLoading && hasMore) {
            loadUsers(currentPage);
        }
    });

    // --- TOGGLE ACCOUNT ---
    $(document).on('submit', '.toggle-account-form', function(e) {
        e.preventDefault();
        const form = $(this);
        const usernameVal = form.find('input[name="username"]').val();

        $.ajax({
            url: 'toggle_account',
            type: 'POST',
            data: form.serialize(),
            dataType: 'json'
        }).done(function(response) {
            if (response.success) {
                const userCard = form.closest('.user-card');
                const statusBadge = userCard.find('.status-badge');

                const newBadgeHtml = response.active
                    ? '<i class="bi bi-check-circle-fill me-1"></i>Active'
                    : '<i class="bi bi-slash-circle-fill me-1"></i>Banned';

                statusBadge.html(newBadgeHtml);

                if (response.active) {
                    statusBadge.removeClass('bg-danger-subtle text-danger border-danger-subtle')
                        .addClass('bg-success-subtle text-success border-success-subtle');
                } else {
                    statusBadge.removeClass('bg-success-subtle text-success border-success-subtle')
                        .addClass('bg-danger-subtle text-danger border-danger-subtle');
                }

                const newIcon = response.active ? 'bi-lock-fill' : 'bi-unlock-fill';
                const newTitle = response.active ? 'Disable Access' : 'Enable Access';

                const newFormHtml = `
                    <input type="hidden" name="username" value="${usernameVal}">
                    <input type="hidden" name="action" value="${response.active ? 'disable' : 'enable'}">
                    <button type="submit" class="btn-icon toggle" title="${newTitle}"><i class="bi ${newIcon}"></i></button>
                `;
                form.html(newFormHtml);

            } else {
                showErrorModal(response.message || 'Failed to update user status.');
            }
        }).fail(function() {
            showErrorModal('An error occurred while updating user status.');
        });
    });

    // --- DELETE USER ---
    let userToDeleteSafe = null;

    $(document).on('submit', '.delete-user-form', function(e) {
        e.preventDefault();
        userToDeleteSafe = $(this).find('input[name="username"]').val();
        const deleteModal = new bootstrap.Modal(document.getElementById('deleteUserConfirmModal'));
        deleteModal.show();
    });

    $('#confirmDeleteUserBtn').on('click', function() {
        if (!userToDeleteSafe) return;

        $.ajax({
            url: 'delete_user',
            type: 'POST',
            data: { username: userToDeleteSafe },
            dataType: 'json'
        }).done(function(response) {
            if (response.success) {
                const deleteModal = bootstrap.Modal.getInstance(document.getElementById('deleteUserConfirmModal'));
                deleteModal.hide();

                $('.user-card').filter(function() {
                    return $(this).attr('data-username') === userToDeleteSafe;
                }).fadeOut(500, function() { $(this).remove(); });

            } else {
                showErrorModal('Failed to delete user.');
            }
        }).fail(function() {
            showErrorModal('An error occurred while deleting user.');
        }).always(function() {
            userToDeleteSafe = null;
        });
    });

    loadUsers(1);
});