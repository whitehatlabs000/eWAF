<div align="center">
  <img src="src/main/webapp/images/favicon-96x96.png" alt="eWAF Logo" width="100"/>
  <h1>eWAF (Enterprise Web Application Firewall)</h1>
  <p><strong>Enrutamiento Híbrido Inteligente & Seguridad de Grado Empresarial</strong></p>

[![License: AGPL v3](https://img.shields.io/badge/License-AGPL_v3-blue.svg)](https://www.gnu.org/licenses/agpl-3.0)
  [![Java: 17](https://img.shields.io/badge/Java-17-orange.svg)](https://www.oracle.com/java/)
  [![Docker: Ready](https://img.shields.io/badge/Docker-Ready-2496ED.svg?logo=docker&logoColor=white)](https://www.docker.com/)
  [![ModSecurity: v3](https://img.shields.io/badge/ModSecurity-v3-red.svg)](https://github.com/SpiderLabs/ModSecurity)
</div>

---

*🇺🇸 [Read in English](README.md)*

**eWAF** es un Proxy Inverso Híbrido y Web Application Firewall de alto rendimiento. Combina el enrutamiento dinámico inteligente y la inspección de cargas útiles de un Motor Nativo en Java con la fuerza bruta en C++ de NGINX y el conjunto de reglas OWASP ModSecurity Core Rule Set (CRS).

## ✨ Características Principales

* **🛡️ Delegación Inteligente:** Los recursos estáticos pesados (imágenes, videos) se delegan automáticamente a NGINX a través de `X-Accel-Redirect`. Cero agotamiento de memoria RAM en Java.
* **🚦 Integración ModSecurity CRS:** Activa o desactiva el OWASP Core Rule Set de forma individual para cada ruta desde la interfaz. El tráfico malicioso es aislado en un motor C++ dedicado antes de que toque tus aplicaciones internas.
* **🔄 Reescritura en Vuelo:** El motor Nativo en Java analiza y manipula cargas JSON y anclajes HTML de forma dinámica para reparar rutas rotas del backend de manera transparente.
* **⚡ Enrutamiento Multimotor:** Dirige tu tráfico a través de motores NATIVE (Tomcat), NGINX o SPRING (Cloud Gateway) dependiendo de las necesidades del endpoint.
* **📦 Base de Datos Zero-Config:** MySQL está completamente contenerizado. El esquema y el administrador por defecto se autoconfiguran en el primer arranque.

## 🏗️ Flujo de Arquitectura

eWAF actúa como un orquestador, distribuyendo la carga de trabajo de manera óptima:
1. **Tráfico API/HTML:** Manejado por el Motor Nativo de Java para inspección profunda de contenido y reescritura de URLs.
2. **Recursos Estáticos:** Manejados por NGINX. Java responde con `X-Accel-Redirect` y delega el flujo de descarga al motor C++.
3. **Rutas WAF:** El tráfico se desvía a un pasillo ciego de NGINX (`/internal-nginx-proxy-modsec`), pasando por el OWASP CRS antes de llegar al backend.

---

## 🚀 Guía de Despliegue

### Prerrequisitos
* [Docker & Docker Compose](https://docs.docker.com/get-docker/)
* Java 17 & Maven (Para compilar la aplicación principal)

### Paso 1: Compilar el Núcleo
Antes de levantar los contenedores, empaqueta la aplicación Java. Esto generará el archivo `target/eWAF.war` requerido por Tomcat.
```bash
git clone [https://github.com/whitehatlabs000/eWAF.git](https://github.com/whitehatlabs000/eWAF.git)
cd eWAF
mvn clean install
```

### Paso 2: Elige tu Entorno

eWAF está diseñado con un sistema de perfiles híbrido para adaptarse a tu flujo de trabajo.

#### Opción A: Desarrollo Local (Windows / Mac / IDE)
En este modo, Docker ejecuta la infraestructura (MySQL, NGINX, Spring Gateway), pero deja a Tomcat apagado para que puedas correr el código Java directamente desde tu IDE (IntelliJ/Eclipse) para depurar.
```bash
docker compose up -d --build
```
*Ejecuta tu servidor Tomcat desde tu IDE en el puerto `8080`. Se conectará automáticamente al MySQL contenerizado a través del puerto `3308`.*

#### Opción B: Despliegue en Producción (Linux / Servidor)
En este modo, el ecosistema completo (incluyendo el Núcleo Java) se ejecuta contenerizado dentro de la red aislada de Docker.
```bash
docker compose --profile prod up -d --build
```

### Paso 3: Acceder al Panel
Una vez desplegado, navega a `http://localhost:8081/login`. 
La base de datos se autoconfigura con las siguientes credenciales por defecto:
* **Usuario:** `admin`
* **Contraseña:** `admin`

*(Se recomienda encarecidamente cambiar esta contraseña o crear un nuevo administrador inmediatamente después de tu primer inicio de sesión).*

---

## 🛠️ Hoja de Trucos de Administración (Docker)

Gestiona tu despliegue de eWAF usando estos comandos estándar de Docker Compose:

```bash
# Ver logs en tiempo real para todos los servicios
docker compose logs -f

# Ver logs para un motor específico (ej., bloqueos de ModSecurity)
docker compose logs -f nginx

# Reiniciar NGINX para aplicar cambios manuales de reglas sin tirar la BD
docker compose restart nginx

# Detener todo el ecosistema de forma segura
docker compose stop

# Destruir el ecosistema (Mantiene los datos de la base de datos a salvo en volúmenes)
docker compose down

# REINICIO COMPLETO: Destruir ecosistema y borrar volúmenes de BD (PELIGRO: Borra todos los datos)
docker compose down -v
```

---

## 🤝 Contribuciones

Las contribuciones son lo que hace que la comunidad open source sea un lugar tan increíble para aprender, inspirarse y crear.

1. Haz un Fork del Proyecto
2. Crea tu Rama de Característica (`git checkout -b feature/CaracteristicaIncreible`)
3. Haz Commit a tus Cambios (`git commit -m 'Añadir alguna CaracteristicaIncreible'`)
4. Haz Push a la Rama (`git push origin feature/CaracteristicaIncreible`)
5. Abre un Pull Request

## 📝 Licencia

Distribuido bajo la Licencia GNU AGPLv3. Consulta el archivo `LICENSE` para más información.