# orbisv2-polygon-creator

> **Polygon Calculation Background Service**

---

## Overview

`orbisv2-polygon-creator` is a dedicated Java Spring Boot background service responsible for computing and persisting polygon data for the Orbis platform. It is intentionally designed as a standalone worker process — not an API server.

> ⚠️ **Important:** This service does **not** expose a public-facing REST API. It runs on a separate port from the main Orbis Java backend and should never be confused with or replace the primary application server. Its sole responsibility is to run scheduled polygon calculation jobs in the background.

---

## Architecture & Port Isolation

The service is architected for strict separation of concerns:

- The main Orbis backend API runs on its own port (typically `8080`).
- `orbisv2-polygon-creator` runs on a **different port** to avoid conflicts.
- Both services connect independently to the same MongoDB instance.
- The polygon creator operates in a fire-and-forget loop, paginating through records and computing polygons according to its schedule configuration.

Configure the service port in `application.properties` or your environment-specific YAML file:

```properties
server.port=8081   # Use any port not occupied by the main backend
```

---

## Tech Stack

- Java (Spring Boot)
- MongoDB (via Spring Data MongoDB)
- Docker & Docker Compose (deployment)
- YAML-based environment configuration

---

## Prerequisites

To build and run locally you need:

- JDK 11 or later
- Gradle (wrapper included)
- A running MongoDB instance
- Docker + Docker Compose (for containerized deployment)

---

## Configuration

Configuration is managed through Spring Boot YAML profiles. Below is the reference for the production-local profile (`application-prod-local.yml`):

```yaml
spring:
  data:
    mongodb:
      database: orbis_clone
      uri: mongodb://localhost:27017/?retryWrites=true

logging:
  level:
    org:
      springframework:
        data:
          mongodb:
            core:
              MongoTemplate: DEBUG
            repository:
              query: DEBUG

app:
  polygon-calculation:
    enable: true
    test-controller-enable: false
    delay-in-sec: 1
    start-page: 0
    page-size: 3
```

### `app.polygon-calculation` properties

| Property | Default | Description |
|---|---|---|
| `enable` | `true` | Master switch. Set to `false` to disable all polygon calculation jobs. |
| `test-controller-enable` | `false` | Exposes a test HTTP endpoint when `true`. Keep `false` in production. |
| `delay-in-sec` | `1` | Pause in seconds between processing iterations. |
| `start-page` | `0` | Pagination start page when scanning records. |
| `page-size` | `3` | Number of records processed per page/batch. |

---

## Running Locally

Clone the repository and build the JAR:

```bash
git clone <repository-url>
cd orbisv2-polygon-creator
./gradlew bootJar
```

Run with a specific Spring profile:

```bash
java -jar build/libs/orbisv2-polygon-creator-0.0.1-SNAPSHOT.jar \
     --spring.profiles.active=prod-local
```

Make sure MongoDB is reachable at the URI defined in your active profile. Adjust `server.port` if the default conflicts with the main backend.

---

## Deployment

### 1 — Build the JAR

```bash
./gradlew bootJar
```

### 2 — Transfer to the server

```bash
scp build/libs/orbisv2-polygon-creator-0.0.1-SNAPSHOT.jar \
    root@66.228.60.78:/home/orbis/artifactory/
```

### 3 — SSH into the server and navigate to the Docker directory

```bash
ssh root@66.228.60.78
cd /home/orbis/docker
```

### 4 — Deploy with Docker Compose

```bash
sudo docker-compose --file orbis-polygon-creator.yml up -d --force-recreate
```

The `--force-recreate` flag ensures the container is rebuilt from the latest JAR even if the image definition hasn't changed.

---

## Docker Compose Notes

The `orbis-polygon-creator.yml` Compose file should:

- Mount or COPY the JAR from the artifactory path.
- Set the correct Spring profile via the `SPRING_PROFILES_ACTIVE` environment variable.
- Map a port that does not conflict with the main backend service.
- Connect to the same Docker network as MongoDB if both are containerized.

Example snippet:

```yaml
services:
  polygon-creator:
    image: eclipse-temurin:17-jre
    volumes:
      - /home/orbis/artifactory/orbisv2-polygon-creator-0.0.1-SNAPSHOT.jar:/app/app.jar
    command: java -jar /app/app.jar
    environment:
      - SPRING_PROFILES_ACTIVE=prod-local
    ports:
      - "8081:8081"
    restart: unless-stopped
```

---

## Logs & Monitoring

To stream logs from the running container:

```bash
sudo docker-compose --file orbis-polygon-creator.yml logs -f
```

MongoDB query-level logging is enabled by default in the prod-local profile (`MongoTemplate: DEBUG`). Switch to `INFO` in production to reduce verbosity.

---

## Deploying on Any Server

This service has no OS-specific dependencies beyond a JRE (or Docker). To deploy on any Linux server:

1. Ensure Java 11+ is installed, or use a Docker JRE image.
2. Copy the JAR to a target directory on the server.
3. Create an application YAML for the target environment (database URI, port, profile).
4. Run the JAR directly with `java -jar` or wrap it in a systemd service / Docker Compose stack.
5. Confirm the chosen port is open in any firewall rules and does not clash with the main API.

Systemd service example for non-Docker deployments:

```ini
[Unit]
Description=Orbis Polygon Creator
After=network.target

[Service]
ExecStart=java -jar /opt/orbis/orbisv2-polygon-creator-0.0.1-SNAPSHOT.jar
Environment=SPRING_PROFILES_ACTIVE=prod-local
Restart=on-failure

[Install]
WantedBy=multi-user.target
```

---

*orbisv2-polygon-creator — Internal background service. Not a public API.*