#!/bin/bash
# verify-images.sh -- Verify Docker image sizes, non-root users, and health checks
# Usage: ./docker/verify-images.sh (run from project root)
set -e

BACKEND_SERVICES="discovery-service api-gateway user-service association-service payment-service notification-service"
ALL_SERVICES="$BACKEND_SERVICES frontend"
MAX_BACKEND_SIZE_MB=200
MAX_FRONTEND_SIZE_MB=50
ERRORS=0

echo "============================================"
echo "  Docker Image Verification"
echo "============================================"

# -- 1. Check images exist --
echo ""
echo "=== Checking images exist ==="
for SERVICE in $ALL_SERVICES; do
  if docker image inspect "familyhobbies/$SERVICE:latest" > /dev/null 2>&1; then
    echo "  [OK] familyhobbies/$SERVICE:latest"
  else
    echo "  [FAIL] familyhobbies/$SERVICE:latest -- image not found"
    ERRORS=$((ERRORS + 1))
  fi
done

# -- 2. Check image sizes --
echo ""
echo "=== Checking image sizes ==="
for SERVICE in $BACKEND_SERVICES; do
  if docker image inspect "familyhobbies/$SERVICE:latest" > /dev/null 2>&1; then
    SIZE=$(docker image inspect "familyhobbies/$SERVICE:latest" --format '{{.Size}}')
    SIZE_MB=$((SIZE / 1024 / 1024))
    if [ "$SIZE_MB" -lt "$MAX_BACKEND_SIZE_MB" ]; then
      echo "  [OK] $SERVICE: ${SIZE_MB} MB (< ${MAX_BACKEND_SIZE_MB} MB)"
    else
      echo "  [WARN] $SERVICE: ${SIZE_MB} MB (target: < ${MAX_BACKEND_SIZE_MB} MB)"
      ERRORS=$((ERRORS + 1))
    fi
  fi
done

if docker image inspect "familyhobbies/frontend:latest" > /dev/null 2>&1; then
  SIZE=$(docker image inspect "familyhobbies/frontend:latest" --format '{{.Size}}')
  SIZE_MB=$((SIZE / 1024 / 1024))
  if [ "$SIZE_MB" -lt "$MAX_FRONTEND_SIZE_MB" ]; then
    echo "  [OK] frontend: ${SIZE_MB} MB (< ${MAX_FRONTEND_SIZE_MB} MB)"
  else
    echo "  [WARN] frontend: ${SIZE_MB} MB (target: < ${MAX_FRONTEND_SIZE_MB} MB)"
    ERRORS=$((ERRORS + 1))
  fi
fi

# -- 3. Check non-root user --
echo ""
echo "=== Checking non-root user ==="
for SERVICE in $BACKEND_SERVICES; do
  if docker image inspect "familyhobbies/$SERVICE:latest" > /dev/null 2>&1; then
    USER=$(docker inspect --format '{{.Config.User}}' "familyhobbies/$SERVICE:latest")
    if [ "$USER" = "appuser" ]; then
      echo "  [OK] $SERVICE runs as: $USER"
    else
      echo "  [FAIL] $SERVICE runs as: '$USER' (expected: appuser)"
      ERRORS=$((ERRORS + 1))
    fi
  fi
done

if docker image inspect "familyhobbies/frontend:latest" > /dev/null 2>&1; then
  USER=$(docker inspect --format '{{.Config.User}}' "familyhobbies/frontend:latest")
  if [ "$USER" = "nginx" ]; then
    echo "  [OK] frontend runs as: $USER"
  else
    echo "  [FAIL] frontend runs as: '$USER' (expected: nginx)"
    ERRORS=$((ERRORS + 1))
  fi
fi

# -- 4. Check HEALTHCHECK is configured --
echo ""
echo "=== Checking HEALTHCHECK instructions ==="
for SERVICE in $ALL_SERVICES; do
  if docker image inspect "familyhobbies/$SERVICE:latest" > /dev/null 2>&1; then
    HC=$(docker inspect --format '{{.Config.Healthcheck}}' "familyhobbies/$SERVICE:latest")
    if [ "$HC" != "<nil>" ] && [ -n "$HC" ]; then
      echo "  [OK] $SERVICE has HEALTHCHECK configured"
    else
      echo "  [FAIL] $SERVICE has no HEALTHCHECK"
      ERRORS=$((ERRORS + 1))
    fi
  fi
done

# -- 5. Check exposed ports --
echo ""
echo "=== Checking exposed ports ==="
declare -A EXPECTED_PORTS
EXPECTED_PORTS[discovery-service]="8761/tcp"
EXPECTED_PORTS[api-gateway]="8080/tcp"
EXPECTED_PORTS[user-service]="8081/tcp"
EXPECTED_PORTS[association-service]="8082/tcp"
EXPECTED_PORTS[payment-service]="8083/tcp"
EXPECTED_PORTS[notification-service]="8084/tcp"
EXPECTED_PORTS[frontend]="80/tcp"

for SERVICE in $ALL_SERVICES; do
  if docker image inspect "familyhobbies/$SERVICE:latest" > /dev/null 2>&1; then
    EXPECTED="${EXPECTED_PORTS[$SERVICE]}"
    PORTS=$(docker inspect --format '{{json .Config.ExposedPorts}}' "familyhobbies/$SERVICE:latest")
    if echo "$PORTS" | grep -q "$EXPECTED"; then
      echo "  [OK] $SERVICE exposes $EXPECTED"
    else
      echo "  [FAIL] $SERVICE does not expose $EXPECTED (found: $PORTS)"
      ERRORS=$((ERRORS + 1))
    fi
  fi
done

# -- Summary --
echo ""
echo "============================================"
if [ "$ERRORS" -eq 0 ]; then
  echo "  All checks passed!"
else
  echo "  $ERRORS check(s) failed or warned."
fi
echo "============================================"

exit $ERRORS
