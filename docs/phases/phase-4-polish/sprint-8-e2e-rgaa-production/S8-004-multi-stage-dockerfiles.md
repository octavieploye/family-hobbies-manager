# Story S8-004: Multi-stage Dockerfiles for All Services

> 5 points | Priority: P1 | Service: all services + frontend
> Sprint file: [Back to Sprint Index](./_index.md)

---

## Context

Every microservice and the Angular frontend need production-grade Docker images. Multi-stage builds keep images small by separating the build toolchain (JDK, Maven, Node, npm) from the runtime layer (JRE, nginx). All images must run as a non-root user (`appuser`) for security, use Alpine-based runtimes to minimize attack surface and image size, and include health checks so orchestrators can detect unhealthy containers. Target image sizes: under 200 MB for backend services, under 50 MB for the frontend. The Dockerfiles use dependency-layer caching (copy `pom.xml` / `package.json` before source code) so that rebuilds only re-download dependencies when they actually change. A shared `.dockerignore` in `backend/` and `frontend/` prevents `node_modules`, `target/`, `.git`, and IDE files from bloating the build context.

## Tasks

| # | Task | File Path | What To Create | How To Verify |
|---|------|-----------|----------------|---------------|
| 1 | Backend Dockerfile template (discovery-service) | `backend/discovery-service/Dockerfile` | Multi-stage Dockerfile: Maven build + JRE Alpine runtime, non-root user, health check | `docker build -t discovery-service backend/discovery-service && docker image inspect --format '{{.Size}}' discovery-service` < 200 MB |
| 2 | Backend Dockerfile (api-gateway) | `backend/api-gateway/Dockerfile` | Same pattern, port 8080 | Image builds, container starts, health check passes |
| 3 | Backend Dockerfile (user-service) | `backend/user-service/Dockerfile` | Same pattern, port 8081 | Image builds, container starts, health check passes |
| 4 | Backend Dockerfile (association-service) | `backend/association-service/Dockerfile` | Same pattern, port 8082 | Image builds, container starts, health check passes |
| 5 | Backend Dockerfile (payment-service) | `backend/payment-service/Dockerfile` | Same pattern, port 8083 | Image builds, container starts, health check passes |
| 6 | Backend Dockerfile (notification-service) | `backend/notification-service/Dockerfile` | Same pattern, port 8084 | Image builds, container starts, health check passes |
| 7 | Frontend Dockerfile | `frontend/Dockerfile` | Node build + nginx Alpine runtime, SPA routing, non-root | Image builds, container starts, `curl localhost:80` returns index.html |
| 8 | nginx.conf for frontend | `frontend/nginx.conf` | SPA-friendly nginx config with gzip, try_files, security headers | `nginx -t` inside container passes |
| 9 | Backend .dockerignore | `backend/.dockerignore` | Exclude target, .git, IDE files, docs | Build context is minimal |
| 10 | Frontend .dockerignore | `frontend/.dockerignore` | Exclude node_modules, dist, .git, IDE files | Build context is minimal |
| 11 | Build and verify all images | -- | Shell script or manual verification | All 7 images build, all containers start, all health checks pass |

---

## Task 1 Detail: Backend Dockerfile Template (discovery-service)

- **What**: Multi-stage Dockerfile for Spring Boot microservices. Stage 1 uses Maven to build the JAR. Stage 2 copies only the JAR into a minimal JRE Alpine image. Creates a non-root user `appuser`. Includes a `HEALTHCHECK` instruction that probes the Spring Actuator health endpoint.
- **Where**: `backend/discovery-service/Dockerfile`
- **Why**: This is the template that all other backend services follow. The discovery-service is built first because it has no database dependency and is the simplest to validate.

```dockerfile
# ============================================================
# Stage 1: Build
# ============================================================
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /workspace

# -- Copy parent POM and module POMs for dependency caching --
COPY pom.xml ./pom.xml
COPY error-handling/pom.xml ./error-handling/pom.xml
COPY common/pom.xml ./common/pom.xml
COPY discovery-service/pom.xml ./discovery-service/pom.xml

# -- Download dependencies (cached unless POMs change) --
RUN mvn dependency:go-offline -pl discovery-service -am -B -q

# -- Copy source code --
COPY error-handling/src ./error-handling/src
COPY common/src ./common/src
COPY discovery-service/src ./discovery-service/src

# -- Build the JAR (skip tests -- tests run in CI, not in Docker build) --
RUN mvn package -pl discovery-service -am -DskipTests -B -q

# ============================================================
# Stage 2: Runtime
# ============================================================
FROM eclipse-temurin:17-jre-alpine AS runtime

# -- Security: create non-root user --
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# -- Copy JAR from build stage --
COPY --from=build /workspace/discovery-service/target/*.jar app.jar

# -- Set ownership to non-root user --
RUN chown -R appuser:appgroup /app

# -- Switch to non-root user --
USER appuser

# -- Expose service port --
EXPOSE 8761

# -- Health check: probe Actuator health endpoint --
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8761/actuator/health || exit 1

# -- JVM tuning for containers --
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

**Build context note**: This Dockerfile is run from the `backend/` directory as the build context, not from within `discovery-service/`. This allows access to the parent POM and shared modules (`error-handling`, `common`).

**Build command**:
```bash
docker build -t familyhobbies/discovery-service:latest -f backend/discovery-service/Dockerfile backend/
```

- **Verify**: Image size < 200 MB; container starts and `/actuator/health` returns `{"status":"UP"}`

---

## Task 2 Detail: Backend Dockerfile (api-gateway)

- **Where**: `backend/api-gateway/Dockerfile`
- **Why**: The API gateway is the single entry point for all client traffic. Same multi-stage pattern as discovery-service, but exposes port 8080.

```dockerfile
# ============================================================
# Stage 1: Build
# ============================================================
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /workspace

COPY pom.xml ./pom.xml
COPY error-handling/pom.xml ./error-handling/pom.xml
COPY common/pom.xml ./common/pom.xml
COPY api-gateway/pom.xml ./api-gateway/pom.xml

RUN mvn dependency:go-offline -pl api-gateway -am -B -q

COPY error-handling/src ./error-handling/src
COPY common/src ./common/src
COPY api-gateway/src ./api-gateway/src

RUN mvn package -pl api-gateway -am -DskipTests -B -q

# ============================================================
# Stage 2: Runtime
# ============================================================
FROM eclipse-temurin:17-jre-alpine AS runtime

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

COPY --from=build /workspace/api-gateway/target/*.jar app.jar

RUN chown -R appuser:appgroup /app

USER appuser

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

**Build command**:
```bash
docker build -t familyhobbies/api-gateway:latest -f backend/api-gateway/Dockerfile backend/
```

---

## Task 3 Detail: Backend Dockerfile (user-service)

- **Where**: `backend/user-service/Dockerfile`

```dockerfile
# ============================================================
# Stage 1: Build
# ============================================================
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /workspace

COPY pom.xml ./pom.xml
COPY error-handling/pom.xml ./error-handling/pom.xml
COPY common/pom.xml ./common/pom.xml
COPY user-service/pom.xml ./user-service/pom.xml

RUN mvn dependency:go-offline -pl user-service -am -B -q

COPY error-handling/src ./error-handling/src
COPY common/src ./common/src
COPY user-service/src ./user-service/src

RUN mvn package -pl user-service -am -DskipTests -B -q

# ============================================================
# Stage 2: Runtime
# ============================================================
FROM eclipse-temurin:17-jre-alpine AS runtime

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

COPY --from=build /workspace/user-service/target/*.jar app.jar

RUN chown -R appuser:appgroup /app

USER appuser

EXPOSE 8081

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8081/actuator/health || exit 1

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

**Build command**:
```bash
docker build -t familyhobbies/user-service:latest -f backend/user-service/Dockerfile backend/
```

---

## Task 4 Detail: Backend Dockerfile (association-service)

- **Where**: `backend/association-service/Dockerfile`

```dockerfile
# ============================================================
# Stage 1: Build
# ============================================================
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /workspace

COPY pom.xml ./pom.xml
COPY error-handling/pom.xml ./error-handling/pom.xml
COPY common/pom.xml ./common/pom.xml
COPY association-service/pom.xml ./association-service/pom.xml

RUN mvn dependency:go-offline -pl association-service -am -B -q

COPY error-handling/src ./error-handling/src
COPY common/src ./common/src
COPY association-service/src ./association-service/src

RUN mvn package -pl association-service -am -DskipTests -B -q

# ============================================================
# Stage 2: Runtime
# ============================================================
FROM eclipse-temurin:17-jre-alpine AS runtime

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

COPY --from=build /workspace/association-service/target/*.jar app.jar

RUN chown -R appuser:appgroup /app

USER appuser

EXPOSE 8082

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8082/actuator/health || exit 1

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

**Build command**:
```bash
docker build -t familyhobbies/association-service:latest -f backend/association-service/Dockerfile backend/
```

---

## Task 5 Detail: Backend Dockerfile (payment-service)

- **Where**: `backend/payment-service/Dockerfile`

```dockerfile
# ============================================================
# Stage 1: Build
# ============================================================
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /workspace

COPY pom.xml ./pom.xml
COPY error-handling/pom.xml ./error-handling/pom.xml
COPY common/pom.xml ./common/pom.xml
COPY payment-service/pom.xml ./payment-service/pom.xml

RUN mvn dependency:go-offline -pl payment-service -am -B -q

COPY error-handling/src ./error-handling/src
COPY common/src ./common/src
COPY payment-service/src ./payment-service/src

RUN mvn package -pl payment-service -am -DskipTests -B -q

# ============================================================
# Stage 2: Runtime
# ============================================================
FROM eclipse-temurin:17-jre-alpine AS runtime

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

COPY --from=build /workspace/payment-service/target/*.jar app.jar

RUN chown -R appuser:appgroup /app

USER appuser

EXPOSE 8083

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8083/actuator/health || exit 1

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

**Build command**:
```bash
docker build -t familyhobbies/payment-service:latest -f backend/payment-service/Dockerfile backend/
```

---

## Task 6 Detail: Backend Dockerfile (notification-service)

- **Where**: `backend/notification-service/Dockerfile`

```dockerfile
# ============================================================
# Stage 1: Build
# ============================================================
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /workspace

COPY pom.xml ./pom.xml
COPY error-handling/pom.xml ./error-handling/pom.xml
COPY common/pom.xml ./common/pom.xml
COPY notification-service/pom.xml ./notification-service/pom.xml

RUN mvn dependency:go-offline -pl notification-service -am -B -q

COPY error-handling/src ./error-handling/src
COPY common/src ./common/src
COPY notification-service/src ./notification-service/src

RUN mvn package -pl notification-service -am -DskipTests -B -q

# ============================================================
# Stage 2: Runtime
# ============================================================
FROM eclipse-temurin:17-jre-alpine AS runtime

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

COPY --from=build /workspace/notification-service/target/*.jar app.jar

RUN chown -R appuser:appgroup /app

USER appuser

EXPOSE 8084

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8084/actuator/health || exit 1

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

**Build command**:
```bash
docker build -t familyhobbies/notification-service:latest -f backend/notification-service/Dockerfile backend/
```

---

## Task 7 Detail: Frontend Dockerfile

- **What**: Multi-stage Dockerfile for the Angular 17+ application. Stage 1 uses Node 18 Alpine to install dependencies and build the production bundle. Stage 2 uses nginx Alpine to serve the static files with SPA routing.
- **Where**: `frontend/Dockerfile`
- **Why**: The Angular build output is a set of static files. nginx serves them efficiently with gzip compression and SPA-friendly `try_files` routing. Non-root execution uses the `nginx` user already present in the Alpine image.

```dockerfile
# ============================================================
# Stage 1: Build
# ============================================================
FROM node:18-alpine AS build

WORKDIR /app

# -- Copy dependency manifests for layer caching --
COPY package.json package-lock.json ./

# -- Install dependencies (ci = clean install, deterministic) --
RUN npm ci --prefer-offline

# -- Copy source code --
COPY . .

# -- Build production bundle --
RUN npx ng build --configuration=production

# ============================================================
# Stage 2: Runtime
# ============================================================
FROM nginx:1.25-alpine AS runtime

# -- Remove default nginx config --
RUN rm /etc/nginx/conf.d/default.conf

# -- Copy custom nginx config with SPA routing --
COPY nginx.conf /etc/nginx/conf.d/default.conf

# -- Copy built Angular app from build stage --
COPY --from=build /app/dist/frontend/browser /usr/share/nginx/html

# -- Security: run as non-root nginx user --
# nginx Alpine image already has user 'nginx' (uid 101)
# Adjust permissions for nginx to run as non-root
RUN chown -R nginx:nginx /usr/share/nginx/html && \
    chown -R nginx:nginx /var/cache/nginx && \
    chown -R nginx:nginx /var/log/nginx && \
    touch /var/run/nginx.pid && \
    chown -R nginx:nginx /var/run/nginx.pid

USER nginx

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:80/ || exit 1

CMD ["nginx", "-g", "daemon off;"]
```

**Build command**:
```bash
docker build -t familyhobbies/frontend:latest frontend/
```

- **Verify**: Image size < 50 MB; `curl http://localhost:80` returns the Angular `index.html`

---

## Task 8 Detail: nginx.conf for Frontend

- **What**: nginx configuration file optimized for serving an Angular SPA with gzip compression, security headers, and `try_files` fallback to `index.html` for client-side routing.
- **Where**: `frontend/nginx.conf`
- **Why**: Without `try_files`, refreshing a deep-linked Angular route (e.g., `/dashboard/family/123`) returns a 404 because nginx looks for a physical file at that path. The `try_files` directive falls back to `index.html`, letting Angular's router handle the URL.

```nginx
server {
    listen 80;
    server_name _;

    root /usr/share/nginx/html;
    index index.html;

    # ── Gzip compression ────────────────────────────────────
    gzip on;
    gzip_vary on;
    gzip_proxied any;
    gzip_comp_level 6;
    gzip_min_length 256;
    gzip_types
        text/plain
        text/css
        text/javascript
        application/javascript
        application/json
        application/xml
        image/svg+xml
        font/woff2;

    # ── Security headers ────────────────────────────────────
    add_header X-Frame-Options "SAMEORIGIN" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header X-XSS-Protection "1; mode=block" always;
    add_header Referrer-Policy "strict-origin-when-cross-origin" always;
    add_header Content-Security-Policy "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'; connect-src 'self' http://localhost:8080 https://api.helloasso-sandbox.com;" always;

    # ── SPA routing: fallback to index.html ─────────────────
    location / {
        try_files $uri $uri/ /index.html;
    }

    # ── Cache static assets aggressively ────────────────────
    location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff|woff2|ttf|eot)$ {
        expires 1y;
        add_header Cache-Control "public, immutable";
        access_log off;
    }

    # ── API proxy (optional: forward /api to gateway) ───────
    # Uncomment if frontend container needs to proxy API calls
    # location /api/ {
    #     proxy_pass http://api-gateway:8080/;
    #     proxy_set_header Host $host;
    #     proxy_set_header X-Real-IP $remote_addr;
    #     proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    #     proxy_set_header X-Forwarded-Proto $scheme;
    # }

    # ── Disable access to hidden files ──────────────────────
    location ~ /\. {
        deny all;
        access_log off;
        log_not_found off;
    }

    # ── Health check endpoint ───────────────────────────────
    location /health {
        access_log off;
        return 200 '{"status":"UP"}';
        add_header Content-Type application/json;
    }
}
```

- **Verify**: `docker exec <container> nginx -t` -> `syntax is ok`

---

## Task 9 Detail: Backend .dockerignore

- **What**: `.dockerignore` file for the `backend/` build context to exclude unnecessary files from Docker builds.
- **Where**: `backend/.dockerignore`
- **Why**: Reduces build context size and prevents sensitive or irrelevant files from entering images.

```dockerignore
# Build output
**/target/

# IDE files
**/.idea/
**/.vscode/
**/*.iml
**/.project
**/.classpath
**/.settings/

# Git
.git
.gitignore

# Docker files (prevent recursive copy)
**/Dockerfile
**/.dockerignore

# Documentation
**/README.md
**/docs/

# CI/CD
**/.github/

# Logs
**/*.log

# OS files
**/.DS_Store
**/Thumbs.db

# Test results
**/surefire-reports/
**/failsafe-reports/
```

---

## Task 10 Detail: Frontend .dockerignore

- **What**: `.dockerignore` file for the `frontend/` build context.
- **Where**: `frontend/.dockerignore`
- **Why**: `node_modules` alone can be hundreds of MB. Excluding it from the build context is critical for fast builds.

```dockerignore
# Dependencies (installed fresh via npm ci)
node_modules/

# Build output (built inside container)
dist/

# IDE files
.idea/
.vscode/
*.iml

# Git
.git
.gitignore

# Docker files
Dockerfile
.dockerignore

# Test files
coverage/
e2e/
*.spec.ts

# Documentation
README.md
docs/

# Environment files
.env
.env.*

# OS files
.DS_Store
Thumbs.db

# Angular cache
.angular/
```

---

## Task 11 Detail: Build and Verify All Images

- **What**: Build all 7 Docker images and verify they meet size targets, start successfully, and pass health checks.
- **Why**: End-to-end verification that all Dockerfiles work together with Docker Compose.

### Build Script

```bash
#!/bin/bash
# build-all-images.sh -- Build and verify all Docker images
set -e

echo "=== Building backend images ==="
SERVICES="discovery-service api-gateway user-service association-service payment-service notification-service"

for SERVICE in $SERVICES; do
  echo "Building $SERVICE..."
  docker build \
    -t "familyhobbies/$SERVICE:latest" \
    -f "backend/$SERVICE/Dockerfile" \
    backend/
  echo "$SERVICE built successfully."
done

echo "=== Building frontend image ==="
docker build -t familyhobbies/frontend:latest frontend/
echo "Frontend built successfully."

echo ""
echo "=== Image sizes ==="
for SERVICE in $SERVICES frontend; do
  SIZE=$(docker image inspect "familyhobbies/$SERVICE:latest" --format '{{.Size}}')
  SIZE_MB=$((SIZE / 1024 / 1024))
  echo "  familyhobbies/$SERVICE: ${SIZE_MB} MB"
done

echo ""
echo "=== Verifying non-root user ==="
for SERVICE in $SERVICES; do
  USER=$(docker inspect --format '{{.Config.User}}' "familyhobbies/$SERVICE:latest")
  echo "  $SERVICE runs as: $USER"
done
FRONTEND_USER=$(docker inspect --format '{{.Config.User}}' "familyhobbies/frontend:latest")
echo "  frontend runs as: $FRONTEND_USER"

echo ""
echo "=== All images built and verified ==="
```

### Size Verification Targets

| Image | Target | Base Image |
|-------|--------|------------|
| familyhobbies/discovery-service | < 200 MB | eclipse-temurin:17-jre-alpine |
| familyhobbies/api-gateway | < 200 MB | eclipse-temurin:17-jre-alpine |
| familyhobbies/user-service | < 200 MB | eclipse-temurin:17-jre-alpine |
| familyhobbies/association-service | < 200 MB | eclipse-temurin:17-jre-alpine |
| familyhobbies/payment-service | < 200 MB | eclipse-temurin:17-jre-alpine |
| familyhobbies/notification-service | < 200 MB | eclipse-temurin:17-jre-alpine |
| familyhobbies/frontend | < 50 MB | nginx:1.25-alpine |

### Health Check Verification

```bash
#!/bin/bash
# verify-health-checks.sh -- Start containers and verify health
set -e

echo "=== Starting discovery-service ==="
docker run -d --name test-discovery -p 8761:8761 familyhobbies/discovery-service:latest

echo "Waiting 60s for startup..."
sleep 60

echo "=== Checking health ==="
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8761/actuator/health)
if [ "$HTTP_CODE" = "200" ]; then
  echo "  discovery-service: HEALTHY (HTTP $HTTP_CODE)"
else
  echo "  discovery-service: UNHEALTHY (HTTP $HTTP_CODE)"
  exit 1
fi

echo "=== Checking Docker health status ==="
HEALTH=$(docker inspect --format '{{.State.Health.Status}}' test-discovery)
echo "  Docker health status: $HEALTH"

echo "=== Cleanup ==="
docker stop test-discovery && docker rm test-discovery

echo "=== Health check verification passed ==="
```

### Docker Scout Security Scan

```bash
# Run Docker Scout on each image to check for vulnerabilities
for SERVICE in discovery-service api-gateway user-service association-service payment-service notification-service frontend; do
  echo "=== Scanning familyhobbies/$SERVICE ==="
  docker scout cves "familyhobbies/$SERVICE:latest" --only-severity critical,high
done
```

---

## Design Decisions

### Why `wget` Instead of `curl` for Health Checks

Alpine-based images include `wget` by default but not `curl`. Using `wget --spider` avoids adding an extra package to the runtime image, keeping images smaller.

### Why Build Context Is `backend/` Not `backend/{service}/`

All backend services depend on shared modules (`error-handling`, `common`). The parent `pom.xml` and shared module sources must be accessible during the Maven build. Setting the build context to `backend/` and using `-f backend/{service}/Dockerfile` solves this cleanly.

### Why `sh -c` in ENTRYPOINT

Using `ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]` instead of `ENTRYPOINT ["java", "-jar", "app.jar"]` allows the `JAVA_OPTS` environment variable to be expanded at runtime. This enables operators to tune JVM settings without rebuilding the image.

### Why `MaxRAMPercentage=75.0`

Container-aware JVM settings (`-XX:+UseContainerSupport`) combined with `MaxRAMPercentage=75.0` ensure the JVM heap stays within 75% of the container's memory limit, leaving 25% for metaspace, thread stacks, and OS overhead.

---

## Acceptance Criteria Checklist

- [ ] All 6 backend Dockerfiles use multi-stage builds (Maven build + JRE runtime)
- [ ] Frontend Dockerfile uses multi-stage build (Node build + nginx runtime)
- [ ] All runtime stages use Alpine-based images
- [ ] All images create and run as non-root user (`appuser` for backend, `nginx` for frontend)
- [ ] All backend images include `HEALTHCHECK` probing `/actuator/health`
- [ ] Frontend image includes `HEALTHCHECK` probing `/`
- [ ] Backend images are under 200 MB
- [ ] Frontend image is under 50 MB
- [ ] Dependency layer caching works (pom.xml/package.json copied before source)
- [ ] `backend/.dockerignore` excludes `target/`, `.git`, IDE files
- [ ] `frontend/.dockerignore` excludes `node_modules/`, `dist/`, `.git`
- [ ] All images build without errors: `docker build` exits 0
- [ ] All containers start and reach healthy state within 90 seconds
- [ ] No critical or high vulnerabilities reported by Docker Scout
- [ ] `nginx -t` passes inside frontend container
- [ ] Frontend SPA routing works (deep link returns `index.html`)
