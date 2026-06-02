<div align="center">
  <img src="src/main/webapp/images/favicon-96x96.png" alt="eWAF Logo" width="100"/>
  <h1>eWAF (Enterprise Web Application Firewall)</h1>
  <p><strong>Smart Hybrid Routing & Enterprise Grade Security</strong></p>

[![License: AGPL v3](https://img.shields.io/badge/License-AGPL_v3-blue.svg)](https://www.gnu.org/licenses/agpl-3.0)
[![Java: 24](https://img.shields.io/badge/Java-24-orange.svg)](https://www.oracle.com/java/)
  [![Docker: Ready](https://img.shields.io/badge/Docker-Ready-2496ED.svg?logo=docker&logoColor=white)](https://www.docker.com/)
  [![ModSecurity: v3](https://img.shields.io/badge/ModSecurity-v3-red.svg)](https://github.com/SpiderLabs/ModSecurity)
</div>

---

*🇪🇸 [Leer en Español](README-es.md)*

**eWAF** is a high-performance Hybrid Reverse Proxy and Web Application Firewall. It combines the intelligent dynamic routing and payload inspection of a Java Native Engine with the raw C++ power of NGINX and the OWASP ModSecurity Core Rule Set (CRS).

## ✨ Core Features

* **🛡️ Smart Delegation:** Heavy static assets (images, videos) are automatically offloaded to NGINX via `X-Accel-Redirect`. Zero Java heap exhaustion.
* **🚦 ModSecurity CRS Integration:** Toggle the OWASP Core Rule Set on a per-route basis directly from the UI. Malicious traffic is isolated in a dedicated C++ engine before it hits your internal apps.
* **🔄 On-The-Fly Rewriting:** The Java Native engine dynamically parses and manipulates JSON payloads and HTML anchors to fix broken backend paths seamlessly.
* **⚡ Multi-Engine Routing:** Route your traffic through NATIVE (Tomcat), NGINX, or SPRING (Cloud Gateway) engines depending on the endpoint needs.
* **📦 Zero-Config Database:** MySQL is fully containerized. The schema and default administrator are auto-provisioned on the first boot.

## 🏗️ Architecture Flow

eWAF acts as an orchestrator, distributing the workload optimally:
1. **API/HTML Traffic:** Handled by the Java Native Engine for deep content inspection and URL rewriting.
2. **Static Assets:** Handled by NGINX. Java replies with `X-Accel-Redirect` and delegates the download stream to the C++ engine.
3. **WAF Routes:** Traffic is shifted to a blind NGINX corridor (`/internal-nginx-proxy-modsec`), passing through the OWASP CRS before reaching the backend.

---

## 🚀 Deployment Guide

### Prerequisites
* [Docker & Docker Compose](https://docs.docker.com/get-docker/)
* Java 24 & Maven (To build the core application)

### Step 1: Build the Core
Before spinning up the containers, package the Java application. This will generate the `target/eWAF.war` file required by Tomcat.

```bash
git clone [https://github.com/whitehatlabs000/eWAF.git](https://github.com/whitehatlabs000/eWAF.git)
cd eWAF
mvn clean install
```

### Step 2: Choose your Environment

eWAF is designed with a hybrid profile system to adapt to your workflow.

#### Option A: Local Development (Windows / Mac / IDE)
In this mode, Docker runs the infrastructure (MySQL, NGINX, Spring Gateway), but leaves Tomcat offline so you can run the Java code directly from your IDE (IntelliJ/Eclipse) for debugging.

```bash
docker compose up -d --build
```
*Run your Tomcat server from your IDE on port `8080`. It will automatically connect to the containerized MySQL on port `3308`.*

#### Option B: Production Deployment (Linux / Server)
In this mode, the entire ecosystem (including the Java Core) runs containerized within the isolated Docker network.

```bash
docker compose --profile prod up -d --build
```

### Step 3: Access the Dashboard
Once deployed, navigate to `http://localhost:8081/login`. 
The database is auto-provisioned with the following default credentials:
* **Username:** `admin`
* **Password:** `admin`

*(It is highly recommended to change this password or create a new administrator immediately after your first login).*

---

## 🛠️ Docker Administration Cheatsheet

Manage your eWAF deployment using these standard Docker Compose commands:

```bash
# View real-time logs for all services
docker compose logs -f

# View logs for a specific engine (e.g., ModSecurity blocks)
docker compose logs -f nginx

# Restart NGINX to apply manual rule changes without dropping the DB
docker compose restart nginx

# Stop the entire ecosystem safely
docker compose stop

# Tear down the ecosystem (Keeps database data safe in volumes)
docker compose down

# FULL RESET: Tear down and delete database volumes (DANGER: Wipes all data)
docker compose down -v
```

---

## 🤝 Contributing

Contributions are what make the open source community such an amazing place to learn, inspire, and create. 

1. Fork the Project
2. Create your Feature Branch (`git checkout -b feature/AmazingFeature`)
3. Commit your Changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the Branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## 📝 License

Distributed under the GNU AGPLv3 License. See `LICENSE` for more information.