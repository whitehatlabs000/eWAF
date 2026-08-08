document.addEventListener('DOMContentLoaded', () => {
    const shieldBtn = document.getElementById('wafGuardDog');
    const statusText = document.getElementById('watchdogStatus');

    // Frases del Easter Egg
    const alerts = [
        "Intrusion detected! WAF WAF WAF! 🐕",
        "Grrr... WAF! 🐕 (IP logged)",
        "Biting malicious packet... WAF WAF!",
        "Watchdog triggered! WAF WAF! 🚨"
    ];

    let clickCount = 0;
    let resetTimer;

    shieldBtn.addEventListener('click', () => {
        clickCount++;

        // Agregar la clase que activa la animación y pone el escudo rojo
        shieldBtn.classList.add('dog-barking');
        statusText.classList.add('typing-animation');
        statusText.classList.replace('text-secondary', 'text-warning');

        // Seleccionar un ladrido aleatorio
        const randomBark = alerts[Math.floor(Math.random() * alerts.length)];
        statusText.innerHTML = randomBark;

        // Limpiar animaciones rápidas para que se pueda clickear repetidamente
        setTimeout(() => {
            shieldBtn.classList.remove('dog-barking');
        }, 400); // 400ms dura la animación CSS

        // Si deja de molestar al perro por 3 segundos, vuelve a la normalidad
        clearTimeout(resetTimer);
        resetTimer = setTimeout(() => {
            statusText.innerHTML = "Silent monitoring...";
            statusText.classList.replace('text-warning', 'text-secondary');
            statusText.classList.remove('typing-animation');
            clickCount = 0;
        }, 3000);
    });
});