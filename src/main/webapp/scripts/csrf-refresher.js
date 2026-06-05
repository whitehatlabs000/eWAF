/**
 * - Obtiene la ruta base (contextPath) de una variable global provista por el JSP.
 * - Añade una función `syncTokenOnLoad` que se ejecuta en cada carga para manejar
 * cargas normales y pestañas duplicadas de forma robusta.
 * - Incluye un mecanismo anti-caché en todas las llamadas fetch.
 */

if (typeof window.isCsrfRefresherLoaded === 'undefined') {

    window.isCsrfRefresherLoaded = true;

    // --- LÓGICA DE SINCRONIZACIÓN ---

    const csrfChannel = new BroadcastChannel('csrf-channel');

    function updateTokenOnPage(newToken) {
        if (!newToken) return;
        let updatesPerformed = 0;
        const metaTag = document.querySelector('meta[name="csrf-token"]');
        if (metaTag) {
            metaTag.setAttribute('content', newToken);
            updatesPerformed++;
        }
        if (typeof window.csrfToken !== 'undefined') {
            window.csrfToken = newToken;
        }
        const hiddenInputs = document.querySelectorAll('input[name="csrfToken"]');
        if (hiddenInputs.length > 0) {
            hiddenInputs.forEach(input => {
                input.value = newToken;
            });
            updatesPerformed++;
        }
        if (updatesPerformed > 0) {
            console.log('CSRF token was successfully updated on the page.');
        }
    }

    csrfChannel.onmessage = (event) => {
        if (event.data && event.data.type === 'CSRF_TOKEN_UPDATE' && event.data.token) {
            console.log('Received new CSRF token from another tab via BroadcastChannel. Updating...');
            updateTokenOnPage(event.data.token);
        }
    };

    window.broadcastCsrfToken = (newToken) => {
        console.log('Broadcasting new CSRF token to other tabs.');
        csrfChannel.postMessage({
            type: 'CSRF_TOKEN_UPDATE',
            token: newToken
        });
        updateTokenOnPage(newToken);
    };

    // --- FIN: LÓGICA DE SINCRONIZACIÓN ---


    /**
     * Obtiene la ruta base desde la variable global 'contextPath' que es
     * inyectada por el JSP (el cual la obtiene del Listener y config.properties).
     */
    function getBaseUrl() {
        if (typeof contextPath !== 'undefined') {
            return contextPath;
        }
        console.error('The global variable "contextPath" is not defined. Make sure your JSP is including it.');
        return ''; // Devuelve vacío como último recurso para evitar errores.
    }

    /**
     * Esta función se ejecuta en CADA carga de página normal.
     * Es la que soluciona el problema de las PESTAÑAS DUPLICADAS.
     */
    function syncTokenOnLoad() {
        const baseUrl = getBaseUrl();
        const cacheBuster = `?t=${new Date().getTime()}`;
        const fetchUrl = `${baseUrl}/api/csrf-token${cacheBuster}`;

        console.log(`Syncing token on normal page load from: ${fetchUrl}`);

        fetch(fetchUrl)
            .then(response => response.ok ? response.json() : Promise.reject('Network error'))
            .then(data => {
                if (data && data.csrfToken) {
                    const domToken = document.querySelector('meta[name="csrf-token"]').getAttribute('content');
                    if (data.csrfToken !== domToken) {
                        console.log('Server token differs from DOM, updating this page AND broadcasting to others.');
                        broadcastCsrfToken(data.csrfToken);
                    } else {
                        console.log('Token is already in sync.');
                        // Notificamos a las demás pestañas por si ellas están desactualizadas.
                        csrfChannel.postMessage({
                            type: 'CSRF_TOKEN_UPDATE',
                            token: data.csrfToken
                        });
                    }
                }
            })
            .catch(error => {
                console.error('Could not sync CSRF token on page load:', error);
            });
    }

    // --- LÓGICA DE BFCACHE ---

    window.addEventListener('pageshow', function(event) {
        if (event.persisted) {
            console.log('Page loaded from bfcache. Requesting a fresh CSRF token...');

            // Reutilizamos nuestra función para obtener la ruta y añadimos anti-caché.
            const baseUrl = getBaseUrl();
            const cacheBuster = `?t=${new Date().getTime()}`;
            const fetchUrl = `${baseUrl}/api/csrf-token${cacheBuster}`;

            fetch(fetchUrl)
                .then(response => response.ok ? response.json() : Promise.reject('Network error'))
                .then(data => {
                    if (data && data.csrfToken) {
                        console.log('New token received for bfcache page. Updating...');
                        updateTokenOnPage(data.csrfToken);
                    } else {
                        console.error('Invalid CSRF token data received from server.');
                    }
                })
                .catch(error => {
                    console.error('Failed to refresh CSRF token for bfcache:', error);
                });
        }
    });

    // --- Se ejecuta la nueva función en cada carga del script ---
    syncTokenOnLoad();
}