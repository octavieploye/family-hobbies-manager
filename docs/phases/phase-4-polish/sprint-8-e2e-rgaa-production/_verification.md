# Sprint 8 Verification Checklist

> Run these commands IN ORDER after all stories are implemented.
> Sprint file: [Back to Sprint Index](./_index.md)

---

## Prerequisites Check

Before running any Sprint 8 verification, confirm all previous work is complete:

```bash
# All backend modules compile
cd backend && mvn clean compile -q
echo "Exit code: $?"

# All backend unit tests pass
mvn test -q
echo "Exit code: $?"

# All Liquibase migrations run
docker compose -f docker/docker-compose.yml up -d postgresql
sleep 10
# Each service auto-runs migrations on startup

# Frontend compiles
cd frontend && npm install && npx ng build --configuration=production
echo "Exit code: $?"

# Frontend unit tests pass
npm test -- --watch=false
echo "Exit code: $?"

# Docker Compose starts all services
cd .. && docker compose -f docker/docker-compose.yml up -d
sleep 120
docker compose -f docker/docker-compose.yml ps
# Expected: all services "healthy"
```

- [ ] All backend modules compile without errors
- [ ] All backend unit tests pass (0 failures)
- [ ] All Liquibase migrations run without error
- [ ] Frontend production build succeeds
- [ ] Frontend unit tests pass (0 failures)
- [ ] Docker Compose starts all services and all reach "healthy" status

---

## Story S8-001: Playwright E2E Suite

**Reference**: [S8-001-playwright-e2e-suite.md](./S8-001-playwright-e2e-suite.md) | [S8-001-playwright-e2e-suite-tests.md](./S8-001-playwright-e2e-suite-tests.md)

### Setup

```bash
# Install Playwright and dependencies
cd e2e
npm install
npx playwright install --with-deps

# Start the E2E Docker Compose environment
cd ..
docker compose -f docker/docker-compose.e2e.yml up -d

# Wait for all services to be healthy (~120 seconds)
docker compose -f docker/docker-compose.e2e.yml ps
# Expected: all services "healthy"
```

### Run Tests

```bash
cd e2e

# Run all E2E tests across all browsers
npx playwright test

# Run tests for a specific spec file
npx playwright test specs/auth.spec.ts
npx playwright test specs/family.spec.ts
npx playwright test specs/association-search.spec.ts
npx playwright test specs/subscription.spec.ts
npx playwright test specs/attendance.spec.ts
npx playwright test specs/payment.spec.ts
npx playwright test specs/notification.spec.ts
npx playwright test specs/rgpd.spec.ts
```

### Verify Results

```bash
# Generate and open HTML report
npx playwright show-report

# Check test count
npx playwright test --list
```

### Checklist

- [ ] `npm install` in `e2e/` succeeds without errors
- [ ] `npx playwright install` downloads all browsers (Chromium, Firefox, WebKit)
- [ ] `docker-compose.e2e.yml` starts all services with health checks
- [ ] All 8 spec files are listed by `npx playwright test --list`
- [ ] All E2E tests pass on Chromium
- [ ] All E2E tests pass on Firefox
- [ ] All E2E tests pass on WebKit
- [ ] HTML report generates without errors
- [ ] Failure screenshots are saved to `e2e/test-results/` on any failure
- [ ] `playwright.config.ts` has retry logic (retries: 2)
- [ ] Page Object Model files exist for all 11 pages

### Cleanup

```bash
docker compose -f docker/docker-compose.e2e.yml down -v
```

---

## Story S8-002: RGAA Accessibility Remediation

**Reference**: [S8-002-rgaa-accessibility.md](./S8-002-rgaa-accessibility.md)

### Manual Verification

Open the Angular application in a browser and verify each RGAA category:

#### Skip-to-Content and Semantic Landmarks

```
1. Load http://localhost:4200
2. Press Tab once
3. Verify "Skip to main content" link appears and is visible on focus
4. Press Enter on the skip link
5. Verify focus moves to the <main> element
```

- [ ] Skip-to-content link is the first focusable element
- [ ] Skip-to-content link is visible on focus (not permanently hidden)
- [ ] Pressing Enter on skip link moves focus to `<main>`
- [ ] Page uses semantic HTML: `<header>`, `<nav>`, `<main>`, `<footer>`
- [ ] `<main>` has `role="main"` or is the only `<main>` element
- [ ] Navigation has `aria-label` identifying its purpose

#### Form Accessibility

```
1. Navigate to /auth/register
2. Tab through all form fields
3. Verify each field has a visible label and aria-describedby for hints/errors
4. Submit the form empty
5. Verify error messages are associated with fields via aria-describedby
6. Verify errors are announced by screen reader (LiveAnnouncer)
```

- [ ] All form inputs have associated `<label>` elements (via `for`/`id` or wrapping)
- [ ] Required fields are marked with `aria-required="true"`
- [ ] Error messages use `aria-describedby` to associate with the field
- [ ] Error messages are announced via `LiveAnnouncer` on form submission
- [ ] Form groups use `<fieldset>` and `<legend>` where appropriate

#### Table Markup

```
1. Navigate to a page with tabular data (e.g., /attendance, /payments)
2. Inspect the table HTML
```

- [ ] Data tables use `<table>`, `<thead>`, `<tbody>`, `<th>`, `<td>`
- [ ] `<th>` elements have `scope="col"` or `scope="row"`
- [ ] Tables have a `<caption>` or `aria-label` describing the table's purpose
- [ ] No layout tables (tables used only for visual layout)

#### Keyboard Navigation

```
1. Start at the top of any page
2. Tab through every interactive element
3. Verify focus order is logical (left-to-right, top-to-bottom)
4. Verify all interactive elements are reachable via Tab
5. Verify dialogs trap focus
```

- [ ] All interactive elements are keyboard-accessible
- [ ] Focus order follows visual reading order
- [ ] Modal dialogs trap focus (Tab does not leave the dialog)
- [ ] Focus returns to the trigger element when a dialog closes
- [ ] No keyboard traps (user can always Tab away from any element)
- [ ] Custom components (dropdowns, date pickers) support arrow key navigation

#### Color Contrast

```
1. Use browser DevTools or axe-core extension
2. Check all text meets WCAG 2.1 AA contrast ratios
```

- [ ] Normal text (< 18px): contrast ratio >= 4.5:1
- [ ] Large text (>= 18px or >= 14px bold): contrast ratio >= 3:1
- [ ] UI components and graphical objects: contrast ratio >= 3:1
- [ ] Focus indicators: contrast ratio >= 3:1 against adjacent colors
- [ ] No information conveyed by color alone (icons, text labels, or patterns supplement color)

#### Focus Indicators

- [ ] All focusable elements have a visible focus indicator
- [ ] Focus indicator has sufficient contrast (>= 3:1)
- [ ] Focus style is consistent across the application
- [ ] No `outline: none` without a replacement focus style

#### Loading States

- [ ] Loading spinners have `aria-busy="true"` on the container
- [ ] Loading state is announced via `aria-live="polite"` region
- [ ] "Loading" text is provided via `aria-label` or visually hidden text

---

## Story S8-003: Playwright Accessibility Tests

**Reference**: [S8-003-playwright-accessibility-tests.md](./S8-003-playwright-accessibility-tests.md)

### Setup

```bash
cd e2e
npm install
# axe-core is bundled via @axe-core/playwright
```

### Run Tests

```bash
# Start E2E environment if not already running
docker compose -f docker/docker-compose.e2e.yml up -d

# Run accessibility test specs
cd e2e
npx playwright test specs/a11y/

# Run with verbose output
npx playwright test specs/a11y/ --reporter=list
```

### Verify Results

```bash
# Check for zero critical and serious violations
npx playwright test specs/a11y/ 2>&1 | grep -E "(passed|failed|critical|serious)"
```

### Checklist

- [ ] `@axe-core/playwright` is listed in `e2e/package.json` dependencies
- [ ] Accessibility spec files exist in `e2e/specs/a11y/`
- [ ] Tests run axe-core analysis on every major page route
- [ ] Zero critical violations reported
- [ ] Zero serious violations reported
- [ ] Minor and moderate violations are documented (not blocking)
- [ ] Each page route tested: `/auth/login`, `/auth/register`, `/dashboard`, `/families`, `/associations/search`, `/subscriptions`, `/attendance`, `/payments`, `/notifications`, `/settings`
- [ ] Tests verify WCAG 2.1 AA standard (not just default axe-core rules)

---

## Story S8-004: Multi-stage Dockerfiles

**Reference**: [S8-004-multi-stage-dockerfiles.md](./S8-004-multi-stage-dockerfiles.md)

### Build All Images

```bash
# Backend services (build from backend/ context)
SERVICES="discovery-service api-gateway user-service association-service payment-service notification-service"

for SERVICE in $SERVICES; do
  echo "=== Building $SERVICE ==="
  docker build \
    -t "familyhobbies/$SERVICE:latest" \
    -f "backend/$SERVICE/Dockerfile" \
    backend/
  echo "Exit code: $?"
done

# Frontend
echo "=== Building frontend ==="
docker build -t familyhobbies/frontend:latest frontend/
echo "Exit code: $?"
```

### Verify Image Sizes

```bash
echo "=== Image sizes ==="
for SERVICE in discovery-service api-gateway user-service association-service payment-service notification-service; do
  SIZE=$(docker image inspect "familyhobbies/$SERVICE:latest" --format '{{.Size}}')
  SIZE_MB=$((SIZE / 1024 / 1024))
  echo "  $SERVICE: ${SIZE_MB} MB (target: < 200 MB)"
done

FRONTEND_SIZE=$(docker image inspect "familyhobbies/frontend:latest" --format '{{.Size}}')
FRONTEND_MB=$((FRONTEND_SIZE / 1024 / 1024))
echo "  frontend: ${FRONTEND_MB} MB (target: < 50 MB)"
```

### Verify Non-Root User

```bash
echo "=== Non-root user check ==="
for SERVICE in discovery-service api-gateway user-service association-service payment-service notification-service; do
  USER=$(docker inspect --format '{{.Config.User}}' "familyhobbies/$SERVICE:latest")
  echo "  $SERVICE: runs as '$USER' (expected: 'appuser')"
done

FRONTEND_USER=$(docker inspect --format '{{.Config.User}}' "familyhobbies/frontend:latest")
echo "  frontend: runs as '$FRONTEND_USER' (expected: 'nginx')"
```

### Verify Health Checks

```bash
# Start a test container (discovery-service -- no DB dependency)
docker run -d --name test-discovery -p 8761:8761 familyhobbies/discovery-service:latest
echo "Waiting 90s for startup..."
sleep 90

# Check actuator health
curl -s http://localhost:8761/actuator/health | python3 -m json.tool
# Expected: {"status":"UP"}

# Check Docker health status
docker inspect --format '{{.State.Health.Status}}' test-discovery
# Expected: healthy

# Cleanup
docker stop test-discovery && docker rm test-discovery

# Test frontend health
docker run -d --name test-frontend -p 8888:80 familyhobbies/frontend:latest
sleep 5
curl -s http://localhost:8888/health
# Expected: {"status":"UP"}

# Verify nginx config
docker exec test-frontend nginx -t
# Expected: syntax is ok, test is successful

# Cleanup
docker stop test-frontend && docker rm test-frontend
```

### Checklist

- [ ] All 6 backend Dockerfiles build without errors (`docker build` exits 0)
- [ ] Frontend Dockerfile builds without errors
- [ ] All backend images use multi-stage builds (Maven build + JRE Alpine runtime)
- [ ] Frontend image uses multi-stage build (Node build + nginx Alpine runtime)
- [ ] All backend images are under 200 MB
- [ ] Frontend image is under 50 MB
- [ ] All backend images run as `appuser` (non-root)
- [ ] Frontend image runs as `nginx` (non-root)
- [ ] All backend images have `HEALTHCHECK` probing `/actuator/health`
- [ ] Frontend image has `HEALTHCHECK` probing `/`
- [ ] `backend/.dockerignore` excludes `target/`, `.git`, IDE files
- [ ] `frontend/.dockerignore` excludes `node_modules/`, `dist/`, `.git`
- [ ] Dependency layer caching works (changing source code does not re-download deps)
- [ ] `nginx -t` passes inside the frontend container
- [ ] Frontend SPA routing works (deep link to `/dashboard` returns `index.html`)

---

## Story S8-005: OpenShift Deployment Manifests

**Reference**: [S8-005-openshift-manifests.md](./S8-005-openshift-manifests.md)

### Validate Base Manifests

```bash
# Validate individual manifest directories
for DIR in discovery-service api-gateway user-service association-service payment-service notification-service frontend postgresql kafka secrets; do
  echo "=== Validating k8s/base/$DIR ==="
  oc apply --dry-run=client -f "k8s/base/$DIR/" 2>&1
  echo "Exit code: $?"
done

# Validate namespace
oc apply --dry-run=client -f k8s/base/namespace.yaml
```

### Validate Kustomize Builds

```bash
# Build base
echo "=== Kustomize build: base ==="
kustomize build k8s/base > /dev/null 2>&1
echo "Exit code: $?"

# Build dev overlay
echo "=== Kustomize build: dev overlay ==="
kustomize build k8s/overlays/dev > /dev/null 2>&1
echo "Exit code: $?"

# Build prod overlay
echo "=== Kustomize build: prod overlay ==="
kustomize build k8s/overlays/prod > /dev/null 2>&1
echo "Exit code: $?"

# Inspect dev output
echo "=== Dev overlay namespace ==="
kustomize build k8s/overlays/dev | grep "namespace:" | head -5

# Inspect prod output
echo "=== Prod overlay namespace ==="
kustomize build k8s/overlays/prod | grep "namespace:" | head -5

# Verify prod replicas
echo "=== Prod overlay replicas ==="
kustomize build k8s/overlays/prod | grep "replicas:" | head -10
```

### Validate YAML Syntax

```bash
# Use yamllint or python to check YAML validity
pip install yamllint 2>/dev/null
find k8s/ -name "*.yaml" -exec yamllint -d relaxed {} \;
```

### Checklist

- [ ] `k8s/base/kustomization.yaml` references all resources
- [ ] `k8s/base/namespace.yaml` creates `family-hobbies-dev` namespace
- [ ] `k8s/base/secrets/secrets.yaml` has base64 placeholders (no real secrets)
- [ ] Every backend service has: `deployment.yaml`, `service.yaml`, `configmap.yaml`
- [ ] API Gateway and Frontend have `route.yaml` with edge TLS
- [ ] PostgreSQL has `statefulset.yaml`, `service.yaml`, `pvc.yaml` (5Gi)
- [ ] Kafka has `statefulset.yaml`, `service.yaml`
- [ ] All Deployments have resource requests and limits
- [ ] All backend Deployments have readiness probes (`/actuator/health/readiness`)
- [ ] All backend Deployments have liveness probes (`/actuator/health/liveness`)
- [ ] Frontend Deployment has probes at `/health`
- [ ] Services use correct port mappings (8761, 8080, 8081, 8082, 8083, 8084, 80)
- [ ] `oc apply --dry-run=client` succeeds for all base manifests
- [ ] `kustomize build k8s/overlays/dev` succeeds
- [ ] `kustomize build k8s/overlays/prod` succeeds
- [ ] Dev overlay: 1 replica, `:latest` tags
- [ ] Prod overlay: 2 replicas, pinned version tags, production domain routes
- [ ] Deployment guide with `oc apply` commands is documented

---

## Story S8-006: Final Documentation

**Reference**: [S8-006-final-documentation.md](./S8-006-final-documentation.md)

### Verify README

```bash
# Check README exists and has content
wc -l README.md
# Expected: > 100 lines

# Check for key sections (case-insensitive search)
grep -i "architecture" README.md
grep -i "quick start" README.md
grep -i "tech stack" README.md
grep -i "development setup" README.md
grep -i "api documentation" README.md
grep -i "project structure" README.md
grep -i "license" README.md
```

- [ ] README renders correctly on GitHub (push and check)
- [ ] CI badge links to GitHub Actions
- [ ] Architecture table lists all 7 services with correct ports
- [ ] Tech stack table is complete and accurate
- [ ] Quick start instructions use `docker compose` (not `docker-compose`)
- [ ] Quick start includes `cp .env.example .env` step
- [ ] Development setup covers both backend (Maven) and frontend (npm)
- [ ] API documentation links to Swagger UI per service
- [ ] Project structure tree matches actual repo layout
- [ ] Features section lists all key features
- [ ] License section present

### Verify Swagger UI

```bash
# Start all services
docker compose -f docker/docker-compose.yml up -d
sleep 120

# Check Swagger UI per service
for PORT in 8081 8082 8083 8084; do
  echo "=== Checking Swagger UI on port $PORT ==="
  HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:$PORT/swagger-ui.html")
  echo "  HTTP $HTTP_CODE (expected: 200 or 302)"
done

# Check OpenAPI spec JSON per service
for PORT in 8081 8082 8083 8084; do
  echo "=== Checking OpenAPI spec on port $PORT ==="
  curl -s "http://localhost:$PORT/v3/api-docs" | python3 -m json.tool | head -5
done
```

- [ ] Swagger UI accessible at `http://localhost:8081/swagger-ui.html` (user-service)
- [ ] Swagger UI accessible at `http://localhost:8082/swagger-ui.html` (association-service)
- [ ] Swagger UI accessible at `http://localhost:8083/swagger-ui.html` (payment-service)
- [ ] Swagger UI accessible at `http://localhost:8084/swagger-ui.html` (notification-service)
- [ ] All controllers have `@Tag` annotations (grouped in Swagger UI)
- [ ] All endpoints have `@Operation` summaries
- [ ] All endpoints have `@ApiResponse` codes

### Verify Postman Collection

```bash
# Check Postman files exist
ls -la docs/api/postman/
# Expected: 3 files
#   family-hobbies-manager.postman_collection.json
#   local.postman_environment.json
#   docker.postman_environment.json

# Validate JSON syntax
for FILE in docs/api/postman/*.json; do
  echo "=== Validating $FILE ==="
  python3 -m json.tool "$FILE" > /dev/null 2>&1
  echo "Exit code: $? (expected: 0)"
done

# Count endpoints in collection
grep -c '"method":' docs/api/postman/family-hobbies-manager.postman_collection.json
# Expected: 34
```

- [ ] `family-hobbies-manager.postman_collection.json` is valid JSON
- [ ] `local.postman_environment.json` is valid JSON
- [ ] `docker.postman_environment.json` is valid JSON
- [ ] Collection imports into Postman without errors
- [ ] Collection has 11 folders (Auth, Users, Families, RGPD, Associations, Activities, Sessions, Subscriptions, Attendance, Payments, Notifications)
- [ ] Collection has 34 requests total
- [ ] Pre-request scripts auto-refresh JWT tokens
- [ ] Local environment `base_url` = `http://localhost:8080`
- [ ] Docker environment routes all traffic through gateway

### Verify ADRs

- [ ] All ADR files in `docs/architecture/` opened and reviewed
- [ ] Service names, ports, and DB names match implementation
- [ ] Error handling module structure matches `backend/error-handling/`
- [ ] HelloAsso integration details match `association-service/` and `payment-service/`
- [ ] Kafka event names match `backend/common/` DTOs
- [ ] No references to removed or renamed components

---

## End-to-End Smoke Test

After all individual stories pass, run the full integration test:

```bash
# Start the complete environment
docker compose -f docker/docker-compose.yml up -d
sleep 120

# Verify all services healthy
docker compose -f docker/docker-compose.yml ps
# Expected: all services "healthy"

# Run the full E2E suite against the running environment
cd e2e
npx playwright test

# Run accessibility tests
npx playwright test specs/a11y/

# Check results
echo "=== E2E Results ==="
npx playwright test --reporter=list 2>&1 | tail -5

echo "=== A11y Results ==="
npx playwright test specs/a11y/ --reporter=list 2>&1 | tail -5
```

- [ ] Docker Compose starts all services (discovery, gateway, user, association, payment, notification, frontend, postgresql, kafka)
- [ ] All services reach healthy status within 120 seconds
- [ ] Full E2E suite passes (all 8 spec files, all browsers)
- [ ] Accessibility suite passes (0 critical/serious violations)
- [ ] No flaky tests (run suite twice, same results)

---

## Production Readiness Checklist

Final check before declaring the project production-ready:

### Testing

- [ ] All backend unit tests pass (JUnit 5)
- [ ] All frontend unit tests pass (Jest)
- [ ] All E2E tests pass (Playwright, 3 browsers)
- [ ] All accessibility tests pass (axe-core, 0 critical/serious)
- [ ] Error handling module has 44 test cases passing
- [ ] Test coverage is acceptable (aim for > 80% on critical paths)

### Docker / Infrastructure

- [ ] All Docker images build without errors
- [ ] Backend images < 200 MB, frontend image < 50 MB
- [ ] All images run as non-root user
- [ ] All images have health checks configured
- [ ] Docker Compose starts and stabilizes within 120 seconds
- [ ] OpenShift manifests validated (`oc apply --dry-run=client`)
- [ ] Kustomize overlays (dev and prod) build without errors

### Security

- [ ] No secrets in codebase (search for passwords, tokens, keys)
- [ ] JWT secret loaded from environment variables / Kubernetes secrets
- [ ] HelloAsso credentials loaded from environment variables / Kubernetes secrets
- [ ] Database passwords loaded from environment variables / Kubernetes secrets
- [ ] SMTP credentials loaded from environment variables / Kubernetes secrets
- [ ] All Docker images use non-root user
- [ ] nginx security headers configured (X-Frame-Options, CSP, etc.)
- [ ] CORS configured on API gateway

### Accessibility (RGAA / WCAG 2.1 AA)

- [ ] Skip-to-content link present and functional
- [ ] Semantic HTML used throughout (`<header>`, `<nav>`, `<main>`, `<footer>`)
- [ ] All form inputs have associated labels
- [ ] All data tables have proper markup (`<th scope>`, `<caption>`)
- [ ] Color contrast meets WCAG 2.1 AA (4.5:1 normal, 3:1 large)
- [ ] All interactive elements keyboard-accessible
- [ ] Focus indicators visible and sufficient contrast
- [ ] Screen reader announcements for dynamic content (LiveAnnouncer)
- [ ] Zero critical/serious axe-core violations

### Documentation

- [ ] README is professional, complete, and renders correctly on GitHub
- [ ] Architecture docs match implementation
- [ ] Swagger UI accessible per service with complete API documentation
- [ ] Postman collection importable and functional
- [ ] ADRs reviewed and up-to-date
- [ ] Deployment guide with step-by-step instructions

### Performance

- [ ] All API endpoints respond within 2 seconds under normal load
- [ ] Frontend initial load under 3 seconds (gzip, code splitting)
- [ ] Docker Compose startup completes within 120 seconds
- [ ] No memory leaks observed during E2E test runs

---

**Sprint 8 is DONE when all checks above pass.**

**Phase 4 is DONE when Sprint 7 AND Sprint 8 verification pass.**

**The project is PRODUCTION-READY.**
