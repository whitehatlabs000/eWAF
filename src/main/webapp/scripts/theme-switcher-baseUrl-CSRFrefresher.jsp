<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<script>
  // Usamos una bandera para asegurarnos de que este script solo se configure una vez.
  if (!window.themeSwitcherAttached) {
    document.addEventListener('DOMContentLoaded', function () {
      const mobileSwitch = document.getElementById('themeSwitch');
      const desktopSwitch = document.getElementById('themeSwitchDesktop');

      // 1. Función central que maneja el cambio de tema.
      const handleThemeChange = (isChecked) => {
        const theme = isChecked ? 'dark' : 'light';
        document.documentElement.setAttribute('data-bs-theme', theme);
        try {
          localStorage.setItem('bs-theme', theme);
        } catch (e) {
          console.error("Could not save theme to localStorage", e);
        }

        // Sincroniza el OTRO interruptor, solo si ambos existen.
        if (mobileSwitch && desktopSwitch) {
          if (mobileSwitch.checked !== isChecked) mobileSwitch.checked = isChecked;
          if (desktopSwitch.checked !== isChecked) desktopSwitch.checked = isChecked;
        }
      };

      // 2. Sincroniza el estado inicial de los interruptores que existan.
      const currentTheme = document.documentElement.getAttribute('data-bs-theme') || 'light';
      const isDark = currentTheme === 'dark';
      if (mobileSwitch) mobileSwitch.checked = isDark;
      if (desktopSwitch) desktopSwitch.checked = isDark;

      // 3. Añade el listener a los interruptores que existan.
      if (mobileSwitch) {
        mobileSwitch.addEventListener('change', (event) => handleThemeChange(event.target.checked));
      }
      if (desktopSwitch) {
        desktopSwitch.addEventListener('change', (event) => handleThemeChange(event.target.checked));
      }
    });
    window.themeSwitcherAttached = true;
  }
</script>

<script>
  // Se define la variable global usando el valor que cargó el Listener
  // Esto lo usa csrf-refresher.js
  const contextPath = "${appBaseUrl}";
</script>