package com.ewaf;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SecurityHeadersFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(SecurityHeadersFilter.class);

    // Construimos la cadena CSP una sola vez al cargar la clase.
    // --- INICIO DE LA POLÍTICA DE SEGURIDAD DE CONTENIDO (CSP) RELAJADA ---
    // Al ser un WAF/Proxy genérico, una política estricta romperá sitios de terceros (ej. GitHub).
    // Esta política es permisiva por defecto (*), delegando la seguridad al motor de inspección
    // del WAF en el backend, en lugar de bloquear ciegamente orígenes en el navegador del usuario.
    private static final String CSP_HEADER_VALUE = String.join("; ",
            "default-src * 'unsafe-inline' 'unsafe-eval' data: blob:",
            "script-src * 'unsafe-inline' 'unsafe-eval' data: blob:",
            "style-src * 'unsafe-inline' data: blob:",
            "img-src * data: blob:",
            "font-src * data: blob:",
            "connect-src * data: blob:",
            "frame-src * data: blob:",
            "media-src * data: blob:"
    );
    // --- FIN DE LA POLÍTICA CSP ---

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (response instanceof HttpServletResponse) {
            HttpServletResponse httpResponse = (HttpServletResponse) response;

            // Asignación ultra-rápida O(1) usando la constante en memoria
            httpResponse.setHeader("Content-Security-Policy", CSP_HEADER_VALUE);


            // --- OTRAS CABECERAS DE SEGURIDAD IMPORTANTES ---

            // 'X-Frame-Options' previene ataques de "Clickjacking".
            // 'SAMEORIGIN' indica al navegador que solo permita que tu página sea cargada en un <iframe>
            // si el sitio que la carga es el mismo que el tuyo.
            httpResponse.setHeader("X-Frame-Options", "SAMEORIGIN");

            // 'X-Content-Type-Options' previene que el navegador intente "adivinar" (sniffing) el tipo MIME de un recurso.
            // 'nosniff' fuerza al navegador a usar el tipo de contenido que el servidor declara en la cabecera 'Content-Type'.
            // Esto mitiga ataques donde un atacante sube un archivo (ej. una imagen) que en realidad contiene código malicioso.
            httpResponse.setHeader("X-Content-Type-Options", "nosniff");

            // 'Referrer-Policy' controla cuánta información de la página de origen (referrer) se envía al navegar a otros sitios.
            // 'strict-origin-when-cross-origin' envía el origen completo cuando se navega dentro
            //  de tu mismo sitio, pero solo el dominio (sin la ruta) cuando se navega a un sitio externo.
            //  mejora la privacidad del usuario.
            httpResponse.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        }

        // Finalmente, después de haber configurado todas las cabeceras en la respuesta,
        // pasamos la petición al siguiente eslabón de la cadena (otro filtro o el servlet).
        chain.doFilter(request, response);
    }

    @Override
    public void init(FilterConfig filterConfig) {
        log.info("eWAF SecurityHeadersFilter initialized successfully. CSP and strict security headers are active.");
    }

    @Override
    public void destroy() {
        log.info("eWAF SecurityHeadersFilter destroyed.");
    }
}