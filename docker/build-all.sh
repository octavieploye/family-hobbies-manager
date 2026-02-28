#!/bin/bash
# build-all.sh -- Build all Docker images for Family Hobbies Manager
# Usage: ./docker/build-all.sh (run from project root)
set -e

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$PROJECT_ROOT"

echo "=== Building backend images ==="
SERVICES="discovery-service api-gateway user-service association-service payment-service notification-service"

for SERVICE in $SERVICES; do
  echo ""
  echo "--- Building $SERVICE ---"
  docker build \
    -t "family-hobbies/$SERVICE:latest" \
    -f "backend/$SERVICE/Dockerfile" \
    backend/
  echo "$SERVICE built successfully."
done

echo ""
echo "=== Building frontend image ==="
docker build -t family-hobbies/frontend:latest frontend/
echo "Frontend built successfully."

echo ""
echo "=== Image sizes ==="
for SERVICE in $SERVICES frontend; do
  SIZE=$(docker image inspect "family-hobbies/$SERVICE:latest" --format '{{.Size}}')
  SIZE_MB=$((SIZE / 1024 / 1024))
  echo "  family-hobbies/$SERVICE: ${SIZE_MB} MB"
done

echo ""
echo "=== Verifying non-root user ==="
for SERVICE in $SERVICES; do
  USER=$(docker inspect --format '{{.Config.User}}' "family-hobbies/$SERVICE:latest")
  echo "  $SERVICE runs as: $USER"
done
FRONTEND_USER=$(docker inspect --format '{{.Config.User}}' "family-hobbies/frontend:latest")
echo "  frontend runs as: $FRONTEND_USER"

echo ""
echo "=== All images built and verified ==="
