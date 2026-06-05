<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>eWAF - Enterprise Web Application Firewall & Smart Proxy</title>
    <meta name="description" content="eWAF is a high-performance Hybrid Reverse Proxy and WAF. Combines Java routing intelligence with NGINX and ModSecurity C++ muscle.">

    <link href="${pageContext.request.contextPath}/css/bootstrap.min.css" rel="stylesheet">
    <link href="${pageContext.request.contextPath}/css/bootstrap-icons.css" rel="stylesheet">
    <link href="${pageContext.request.contextPath}/css/index.css" rel="stylesheet">

    <link href="https://fonts.googleapis.com/css2?family=Fira+Code:wght@400;500&family=Inter:wght@400;600;800&display=swap" rel="stylesheet">

    <jsp:include page="/WEB-INF/jsp/common/head_setup.jsp" />
</head>
<body>

<!-- NAVBAR -->
<nav class="navbar navbar-expand-lg bg-glass fixed-top border-bottom border-secondary">
    <div class="container">
        <a class="navbar-brand fw-bold text-white" href="#">
            <i class="bi bi-shield-shaded text-primary me-2"></i>eWAF<span class="text-primary">.io</span>
        </a>
        <button class="navbar-toggler" type="button" data-bs-toggle="collapse" data-bs-target="#navbarNav">
            <span class="navbar-toggler-icon"></span>
        </button>
        <div class="collapse navbar-collapse" id="navbarNav">
            <ul class="navbar-nav ms-auto align-items-center">
                <li class="nav-item"><a class="nav-link text-white-50" href="#features" data-i18n="nav_features">Features</a></li>
                <li class="nav-item"><a class="nav-link text-white-50" href="#documentation" data-i18n="nav_docs">Documentation</a></li>
                <li class="nav-item">
                    <a class="btn btn-outline-light btn-sm ms-3 rounded-pill" href="https://github.com/whitehatlabs000/eWAF" target="_blank">
                        <i class="bi bi-github me-1"></i> GitHub
                    </a>
                </li>
                <!-- Botón de Modo Oscuro/Claro -->
                <li class="nav-item ms-3">
                    <button id="themeToggleBtn" class="btn btn-sm btn-outline-secondary rounded-circle d-flex align-items-center justify-content-center" style="width: 32px; height: 32px; border:none;" title="Toggle Theme">
                        <i class="bi bi-moon-stars-fill" id="themeIcon"></i>
                    </button>
                </li>
                <!-- Switch de Idioma -->
                <li class="nav-item ms-2">
                    <div class="lang-switch">
                        <input type="checkbox" id="langToggle" class="lang-checkbox">
                        <label for="langToggle" class="lang-label">
                            <span class="en-lbl">EN</span>
                            <span class="es-lbl">ES</span>
                        </label>
                    </div>
                </li>
            </ul>
        </div>
    </div>
</nav>

<!-- HERO SECTION -->
<section class="hero-section d-flex align-items-center">
    <div class="container text-center position-relative z-1">
        <h1 class="display-3 fw-bold mb-4 animate-fade-in-up">
            <span data-i18n="hero_title_1">Smart Hybrid Routing.</span> <br>
            <span class="text-gradient" data-i18n="hero_title_2">Enterprise Grade Security.</span>
        </h1>
        <p class="lead text-secondary mb-5 mx-auto animate-fade-in-up delay-1" style="max-width: 700px;" data-i18n="hero_desc">
            eWAF combines the intelligent dynamic routing of Java with the raw C++ power of NGINX and ModSecurity. Build a zero-trust architecture in seconds.
        </p>

        <div class="terminal-window text-start mx-auto animate-fade-in-up delay-2 shadow-lg">
            <div class="terminal-header d-flex align-items-center px-3 py-2">
                <div class="terminal-dot bg-danger"></div>
                <div class="terminal-dot bg-warning"></div>
                <div class="terminal-dot bg-success"></div>
                <span class="ms-3 text-muted small">bash</span>
            </div>
            <div class="terminal-body p-4 font-monospace">
                <span class="text-success">$</span> git clone https://github.com/whitehatlabs000/eWAF.git<br>
                <span class="text-success">$</span> cd eWAF<br>
                <span class="text-success">$</span> sudo apt install openjdk-17-jdk -y<br>
                <span class="text-success">$</span> ./mvnw clean install<br>
                <span class="text-success">$</span> docker compose --profile prod up -d --build<br>
                <span class="text-info">> Deploying Smart Hybrid Control Plane... Done.</span>
            </div>
        </div>
    </div>
    <!-- Fondo animado -->
    <div class="hero-bg-glow"></div>
</section>

<!-- FEATURES SECTION -->
<section id="features" class="py-6 bg-darker">
    <div class="container">
        <div class="text-center mb-5">
            <h2 class="fw-bold" data-i18n="feat_title">Built for Modern Infrastructure</h2>
        </div>
        <div class="row g-4">
            <div class="col-md-4">
                <div class="feature-card p-4 rounded-4 border border-secondary h-100">
                    <div class="icon-box bg-primary bg-opacity-10 text-primary mb-4 rounded-3 d-inline-flex p-3">
                        <i class="bi bi-cpu display-6"></i>
                    </div>
                    <h4 data-i18n="feat_1_title">Smart Delegation</h4>
                    <p class="text-secondary" data-i18n="feat_1_desc">Heavy assets (images, videos) are automatically offloaded to NGINX via X-Accel-Redirect. Zero Java heap exhaustion.</p>
                </div>
            </div>
            <div class="col-md-4">
                <div class="feature-card p-4 rounded-4 border border-secondary h-100">
                    <div class="icon-box bg-danger bg-opacity-10 text-danger mb-4 rounded-3 d-inline-flex p-3">
                        <i class="bi bi-shield-fill-check display-6"></i>
                    </div>
                    <h4 data-i18n="feat_2_title">ModSecurity CRS</h4>
                    <p class="text-secondary" data-i18n="feat_2_desc">Toggle OWASP Core Rule Set on a per-route basis. Isolate malicious traffic in a dedicated C++ engine before it hits your app.</p>
                </div>
            </div>
            <div class="col-md-4">
                <div class="feature-card p-4 rounded-4 border border-secondary h-100">
                    <div class="icon-box bg-success bg-opacity-10 text-success mb-4 rounded-3 d-inline-flex p-3">
                        <i class="bi bi-code-slash display-6"></i>
                    </div>
                    <h4 data-i18n="feat_3_title">On-The-Fly Rewriting</h4>
                    <p class="text-secondary" data-i18n="feat_3_desc">Native engine parses and manipulates JSON payloads and HTML anchors dynamically to fix broken backend paths seamlessly.</p>
                </div>
            </div>
        </div>
    </div>
</section>

<!-- DOCUMENTATION SECTION -->
<section id="documentation" class="py-6">
    <div class="container">
        <h2 class="fw-bold mb-5" data-i18n="docs_title">Documentation</h2>

        <div class="row">
            <div class="col-lg-3 mb-4">
                <div class="nav flex-column nav-pills docs-sidebar" id="v-pills-tab" role="tablist" aria-orientation="vertical">
                    <button class="nav-link active" data-bs-toggle="pill" data-bs-target="#docs-install" type="button" data-i18n="tab_install">Installation</button>
                    <button class="nav-link" data-bs-toggle="pill" data-bs-target="#docs-arch" type="button" data-i18n="tab_arch">Architecture</button>
                    <button class="nav-link" data-bs-toggle="pill" data-bs-target="#docs-config" type="button" data-i18n="tab_config">Configuration</button>
                </div>
            </div>
            <div class="col-lg-9">
                <div class="tab-content docs-content" id="v-pills-tabContent">

                    <!-- TAB: INSTALLATION -->
                    <div class="tab-pane fade show active" id="docs-install">
                        <h3 data-i18n="doc_inst_title">Getting Started with Docker</h3>
                        <p class="text-secondary" data-i18n="doc_inst_p1">The easiest and recommended way to run eWAF is using the provided Docker Compose file. This ensures NGINX, ModSecurity, and the Java Core are perfectly synchronized.</p>

                        <div class="code-block position-relative mb-4">
                            <button class="btn-copy"><i class="bi bi-clipboard"></i></button>
                            <pre><code class="language-bash">git clone https://github.com/whitehatlabs000/eWAF.git
cd eWAF
sudo apt update
sudo apt install openjdk-17-jdk -y
chmod +x mvnw
./mvnw clean install
docker compose --profile prod up -d --build</code></pre>
                        </div>
                        <p class="text-secondary" data-i18n="doc_inst_p2">Once running, access the Admin Dashboard at <code>http://localhost/login</code>.</p>
                    </div>

                    <!-- TAB: ARCHITECTURE -->
                    <div class="tab-pane fade" id="docs-arch">
                        <h3 data-i18n="doc_arch_title">The Hybrid Control Plane</h3>
                        <p class="text-secondary" data-i18n="doc_arch_p1">eWAF does not process everything in a single thread. It acts as an orchestrator:</p>
                        <ul class="text-secondary">
                            <li data-i18n="doc_arch_li1"><strong>API/HTML Traffic:</strong> Handled by Java Native Engine for deep content inspection and URL rewriting.</li>
                            <li data-i18n="doc_arch_li2"><strong>Static Assets:</strong> Handled by NGINX. Java replies with <code>X-Accel-Redirect</code>.</li>
                            <li data-i18n="doc_arch_li3"><strong>WAF Routes:</strong> Traffic is shifted to a blind corridor, passing through OWASP CRS before reaching the backend.</li>
                        </ul>
                    </div>

                    <!-- TAB: CONFIGURATION -->
                    <div class="tab-pane fade" id="docs-config">
                        <h3 data-i18n="doc_cfg_title">Proxy Rules Management</h3>
                        <p class="text-secondary" data-i18n="doc_cfg_p1">Routes can be managed in real-time without restarting the server. In the Admin Dashboard, configure:</p>
                        <ul class="text-secondary mb-4">
                            <li data-i18n="doc_cfg_li1"><strong>Engine Selection:</strong> Choose between NATIVE, NGINX, or SPRING.</li>
                            <li data-i18n="doc_cfg_li2"><strong>ModSecurity Toggle:</strong> Enable the C++ WAF with a simple switch (NGINX engine only).</li>
                            <li data-i18n="doc_cfg_li3"><strong>Cache TTL:</strong> Define in seconds how long NGINX should hold the resource in disk.</li>
                        </ul>
                    </div>

                </div>
            </div>
        </div>
    </div>
</section>

<!-- FOOTER -->
<footer class="py-4 border-top border-secondary bg-black text-center">
    <div class="container text-secondary small">
        &copy; 2026 eWAF.io - Built with Java & NGINX. Open Source Project.
    </div>
</footer>

<!-- Bootstrap Bundle with Popper -->
<script src="${pageContext.request.contextPath}/js/bootstrap.bundle.min.js"></script>

<!-- i18n Dictionary & Script -->
<script>
    const i18n = {
        "en": {
            "nav_features": "Features",
            "nav_docs": "Documentation",
            "hero_title_1": "Smart Hybrid Routing.",
            "hero_title_2": "Enterprise Grade Security.",
            "hero_desc": "eWAF combines the intelligent dynamic routing of Java with the raw C++ power of NGINX and ModSecurity. Build a zero-trust architecture in seconds.",
            "feat_title": "Built for Modern Infrastructure",
            "feat_1_title": "Smart Delegation",
            "feat_1_desc": "Heavy assets (images, videos) are automatically offloaded to NGINX via X-Accel-Redirect. Zero Java heap exhaustion.",
            "feat_2_title": "ModSecurity CRS",
            "feat_2_desc": "Toggle OWASP Core Rule Set on a per-route basis. Isolate malicious traffic in a dedicated C++ engine before it hits your app.",
            "feat_3_title": "On-The-Fly Rewriting",
            "feat_3_desc": "Native engine parses and manipulates JSON payloads and HTML anchors dynamically to fix broken backend paths seamlessly.",
            "docs_title": "Documentation",
            "tab_install": "Installation",
            "tab_arch": "Architecture",
            "tab_config": "Configuration",
            "doc_inst_title": "Getting Started with Docker",
            "doc_inst_p1": "The easiest and recommended way to run eWAF is using the provided Docker Compose file. This ensures NGINX, ModSecurity, and the Java Core are perfectly synchronized.",
            "doc_inst_p2": "Once running, access the Admin Dashboard at http://localhost/login",
            "doc_arch_title": "The Hybrid Control Plane",
            "doc_arch_p1": "eWAF does not process everything in a single thread. It acts as an orchestrator:",
            "doc_arch_li1": "<strong>API/HTML Traffic:</strong> Handled by Java Native Engine for deep content inspection and URL rewriting.",
            "doc_arch_li2": "<strong>Static Assets:</strong> Handled by NGINX. Java replies with X-Accel-Redirect.",
            "doc_arch_li3": "<strong>WAF Routes:</strong> Traffic is shifted to a blind corridor, passing through OWASP CRS before reaching the backend.",
            "doc_cfg_title": "Proxy Rules Management",
            "doc_cfg_p1": "Routes can be managed in real-time without restarting the server. In the Admin Dashboard, configure:",
            "doc_cfg_li1": "<strong>Engine Selection:</strong> Choose between NATIVE, NGINX, or SPRING.",
            "doc_cfg_li2": "<strong>ModSecurity Toggle:</strong> Enable the C++ WAF with a simple switch (NGINX engine only).",
            "doc_cfg_li3": "<strong>Cache TTL:</strong> Define in seconds how long NGINX should hold the resource in disk."
        },
        "es": {
            "nav_features": "Características",
            "nav_docs": "Documentación",
            "hero_title_1": "Enrutamiento Híbrido.",
            "hero_title_2": "Seguridad Empresarial.",
            "hero_desc": "eWAF combina el enrutamiento dinámico inteligente de Java con la fuerza bruta en C++ de NGINX y ModSecurity. Construye una arquitectura Zero-Trust en segundos.",
            "feat_title": "Diseñado para Infraestructura Moderna",
            "feat_1_title": "Delegación Inteligente",
            "feat_1_desc": "Los recursos pesados (imágenes, videos) se delegan automáticamente a NGINX vía X-Accel-Redirect. Cero agotamiento de memoria RAM en Java.",
            "feat_2_title": "ModSecurity CRS",
            "feat_2_desc": "Activa el conjunto de reglas OWASP por ruta. Aísla el tráfico malicioso en un motor C++ dedicado antes de que toque tu aplicación.",
            "feat_3_title": "Reescritura en Vuelo",
            "feat_3_desc": "El motor nativo analiza y manipula cargas JSON y anclajes HTML dinámicamente para arreglar rutas rotas de forma transparente.",
            "docs_title": "Documentación",
            "tab_install": "Instalación",
            "tab_arch": "Arquitectura",
            "tab_config": "Configuración",
            "doc_inst_title": "Empezando con Docker",
            "doc_inst_p1": "La forma más fácil y recomendada de ejecutar eWAF es usando el archivo Docker Compose incluido. Esto asegura que NGINX, ModSecurity y el núcleo de Java estén sincronizados.",
            "doc_inst_p2": "Una vez en ejecución, accede al Panel de Administración en http://localhost/login",
            "doc_arch_title": "El Plano de Control Híbrido",
            "doc_arch_p1": "eWAF no procesa todo en un solo hilo. Actúa como un orquestador:",
            "doc_arch_li1": "<strong>Tráfico API/HTML:</strong> Manejado por el motor nativo de Java para inspección profunda y reescritura de URLs.",
            "doc_arch_li2": "<strong>Archivos Estáticos:</strong> Manejados por NGINX mediante respuestas X-Accel-Redirect.",
            "doc_arch_li3": "<strong>Rutas WAF:</strong> El tráfico se desvía a un pasillo ciego, pasando por OWASP CRS antes de llegar al backend.",
            "doc_cfg_title": "Gestión de Reglas de Proxy",
            "doc_cfg_p1": "Las reglas se manejan en tiempo real sin reiniciar. Desde el Panel, puedes configurar:",
            "doc_cfg_li1": "<strong>Motor:</strong> Elige entre NATIVE, NGINX o SPRING.",
            "doc_cfg_li2": "<strong>ModSecurity:</strong> Activa el WAF C++ con un simple interruptor (Solo en motor NGINX).",
            "doc_cfg_li3": "<strong>Caché TTL:</strong> Define en segundos cuánto tiempo NGINX debe guardar el recurso en disco."
        }
    };

    // --- LÓGICA DE IDIOMA ---
    const langToggle = document.getElementById('langToggle');
    langToggle.addEventListener('change', (e) => {
        const lang = e.target.checked ? 'es' : 'en';
        document.querySelectorAll('[data-i18n]').forEach(el => {
            const key = el.getAttribute('data-i18n');
            if (i18n[lang][key]) {
                el.innerHTML = i18n[lang][key];
            }
        });
    });

    // --- LÓGICA DE TEMA (head_setup.jsp) ---
    const themeToggleBtn = document.getElementById('themeToggleBtn');
    const themeIcon = document.getElementById('themeIcon');

    // Sincronizar el icono inicial según lo que decidió head_setup.jsp
    const currentTheme = document.documentElement.getAttribute('data-bs-theme') || 'dark';
    themeIcon.className = currentTheme === 'dark' ? 'bi bi-moon-stars-fill text-warning' : 'bi bi-sun-fill text-warning';

    // Escuchar clics en el botón de tema
    themeToggleBtn.addEventListener('click', () => {
        const htmlTag = document.documentElement;
        const newTheme = htmlTag.getAttribute('data-bs-theme') === 'dark' ? 'light' : 'dark';

        // Cambiar atributo y guardar en localStorage
        htmlTag.setAttribute('data-bs-theme', newTheme);
        localStorage.setItem('bs-theme', newTheme);

        // Cambiar icono
        themeIcon.className = newTheme === 'dark' ? 'bi bi-moon-stars-fill text-warning' : 'bi bi-sun-fill text-warning';
    });

    // --- COPIAR AL PORTAPAPELES ---
    document.querySelectorAll('.btn-copy').forEach(btn => {
        btn.addEventListener('click', function() {
            const code = this.nextElementSibling.innerText;
            navigator.clipboard.writeText(code).then(() => {
                const originalIcon = this.innerHTML;
                this.innerHTML = '<i class="bi bi-check2"></i>';
                setTimeout(() => { this.innerHTML = originalIcon; }, 2000);
            });
        });
    });
</script>
</body>
</html>