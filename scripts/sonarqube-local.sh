#!/usr/bin/env bash
set -euo pipefail

# Minimal launcher for a local SonarQube server via Docker.
# - If the container is already running, it exits without changes.
# - If the container exists but is stopped, it starts it.
# - Otherwise, it runs a new container in the background.

CONTAINER_NAME=${CONTAINER_NAME:-sonarqube-local}
IMAGE=${IMAGE:-sonarqube:lts-community}
PORT=${PORT:-9000}

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker is required but not found in PATH. Please install Docker Desktop."
  exit 1
fi

# If already running, do nothing
if docker ps --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
  echo "SonarQube container '${CONTAINER_NAME}' is already running at http://localhost:${PORT}"
  exit 0
fi

# If exists but stopped, start it
if docker ps -a --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
  echo "Starting existing SonarQube container '${CONTAINER_NAME}'..."
  docker start "${CONTAINER_NAME}" >/dev/null
  echo "Started. Open http://localhost:${PORT}"
  exit 0
fi

echo "Launching new SonarQube container '${CONTAINER_NAME}' using image '${IMAGE}'..."
docker run -d \
  --name "${CONTAINER_NAME}" \
  -p "${PORT}:9000" \
  --restart unless-stopped \
  "${IMAGE}" >/dev/null

echo "SonarQube is starting at http://localhost:${PORT}"
echo "Default credentials: admin / admin (you'll be prompted to change the password on first login)"


