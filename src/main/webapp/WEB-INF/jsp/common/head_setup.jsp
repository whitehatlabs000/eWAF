<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>

<%-- Script de Tema (Dark/Light Mode) --%>
<script>
    (() => {
        // Función para establecer el tema en el HTML.
        const setTheme = (theme) => {
            document.documentElement.setAttribute('data-bs-theme', theme);
        };

        // --- PRIORIDAD 1: Verificar si el usuario ya guardó un tema.
        const storedTheme = localStorage.getItem('bs-theme');
        if (storedTheme) {
            setTheme(storedTheme);
            return;
        }

        // Si no hay tema guardado, el valor por defecto inicial es 'dark'.
        let defaultTheme = 'dark';

        // --- PRIORIDAD 2 preferencia del Sistema Operativo.


        if (window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches) {
            defaultTheme = 'dark'; // Si el SO está en modo oscuro, se convierte en el nuevo defecto.
        }

        setTheme(defaultTheme);

        //  Se guarda el tema por defecto para futuras páginas. ---
        localStorage.setItem('bs-theme', defaultTheme);
    })();
</script>

<%-- Favicons y Manifest --%>
<%-- Escudo eWAF inyectado dinámicamente como SVG puro --%>
<link rel="icon" type="image/svg+xml" href="data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 16 16' fill='%230d6efd'%3E%3Cpath fill-rule='evenodd' d='M8 14.933a.615.615 0 0 0 .1-.025c.076-.023.174-.061.294-.118.24-.113.547-.29.893-.533a10.726 10.726 0 0 0 2.287-2.233c1.527-1.997 2.807-5.031 2.253-9.188a.48.48 0 0 0-.328-.39c-.651-.213-1.75-.56-2.837-.855C9.552 1.29 8.531 1.067 8 1.067v13.866zM5.072.56C6.157.265 7.31 0 8 0s1.843.265 2.928.56c1.11.3 2.229.655 2.887.87a1.54 1.54 0 0 1 1.044 1.262c.596 4.477-.787 7.795-2.465 9.99a11.775 11.775 0 0 1-2.517 2.453 7.159 7.159 0 0 1-1.048.625c-.28.132-.581.24-.829.24s-.548-.108-.829-.24a7.158 7.158 0 0 1-1.048-.625 11.777 11.777 0 0 1-2.517-2.453C1.928 10.487.545 7.169 1.141 2.692A1.54 1.54 0 0 1 2.185 1.43 62.456 62.456 0 0 1 5.072.56z'/%3E%3C/svg%3E" />
<%-- Fallback para navegadores antiguos --%>
<link rel="alternate icon" type="image/svg+xml" href="data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 16 16' fill='%230d6efd'%3E%3Cpath fill-rule='evenodd' d='M8 14.933a.615.615 0 0 0 .1-.025c.076-.023.174-.061.294-.118.24-.113.547-.29.893-.533a10.726 10.726 0 0 0 2.287-2.233c1.527-1.997 2.807-5.031 2.253-9.188a.48.48 0 0 0-.328-.39c-.651-.213-1.75-.56-2.837-.855C9.552 1.29 8.531 1.067 8 1.067v13.866zM5.072.56C6.157.265 7.31 0 8 0s1.843.265 2.928.56c1.11.3 2.229.655 2.887.87a1.54 1.54 0 0 1 1.044 1.262c.596 4.477-.787 7.795-2.465 9.99a11.775 11.775 0 0 1-2.517 2.453 7.159 7.159 0 0 1-1.048.625c-.28.132-.581.24-.829.24s-.548-.108-.829-.24a7.158 7.158 0 0 1-1.048-.625 11.777 11.777 0 0 1-2.517-2.453C1.928 10.487.545 7.169 1.141 2.692A1.54 1.54 0 0 1 2.185 1.43 62.456 62.456 0 0 1 5.072.56z'/%3E%3C/svg%3E" />

<meta name="apple-mobile-web-app-title" content="eWAF" />
<link rel="manifest" href="${pageContext.request.contextPath}/images/site.webmanifest" />