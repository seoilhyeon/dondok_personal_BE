#!/usr/bin/env bash
set -euo pipefail

# 백엔드 API Blue/Green 배포 스크립트다.
# inactive slot을 실행하고 readiness를 검증한 뒤 Nginx upstream을 전환한다.
# 전환 후 외부 entrypoint를 확인하고, 모든 검증이 끝나면 이전 slot을 종료한다.

APP_ROOT="${APP_ROOT:-/opt/dondok}"
ENV_FILE="${ENV_FILE:-${APP_ROOT}/.env}"
CONFIG_FILE="${CONFIG_FILE:-${APP_ROOT}/config/application-prod.yml}"
FIREBASE_CREDENTIALS_FILE="${FIREBASE_CREDENTIALS_FILE:-${APP_ROOT}/secrets/firebase-service-account.json}"
FIREBASE_CREDENTIALS_CONTAINER_PATH="${FIREBASE_CREDENTIALS_CONTAINER_PATH:-/app/secrets/firebase-service-account.json}"
IMAGE="${1:?usage: switch-blue-green.sh <docker-image> <commit-sha>}"
DEPLOY_SHA="${2:?usage: switch-blue-green.sh <docker-image> <commit-sha>}"

BLUE_PORT="${BLUE_PORT:-8081}"
GREEN_PORT="${GREEN_PORT:-8082}"
CONTAINER_PORT="${CONTAINER_PORT:-8080}"
APP_NETWORK="${APP_NETWORK:-dondok-network}"

NGINX_DIR="${APP_ROOT}/nginx"
ACTIVE_UPSTREAM="${NGINX_DIR}/active-upstream.conf"
BLUE_UPSTREAM="${NGINX_DIR}/blue-upstream.conf"
GREEN_UPSTREAM="${NGINX_DIR}/green-upstream.conf"

RELEASE_DIR="${APP_ROOT}/releases"
DEPLOYED_SHA_FILE="${RELEASE_DIR}/deployed-sha.txt"
PREVIOUS_SHA_FILE="${RELEASE_DIR}/previous-sha.txt"

HEALTH_CHECK="${APP_ROOT}/deploy/health-check.sh"
VALIDATE_ENV="${APP_ROOT}/deploy/validate-env.sh"

BACKUP_UPSTREAM=""
NEW_CONTAINER_STARTED=false
SWITCHED=false
NEXT_SLOT=""
NEXT_PORT=""
ACTIVE_SLOT=""

log() {
  echo "[INFO] $*"
}

run_as_root() {
  if [ "$(id -u)" -eq 0 ]; then
    "$@"
  else
    sudo "$@"
  fi
}

reload_nginx() {
  run_as_root nginx -s reload || run_as_root systemctl reload nginx
}

fail() {
  echo "[ERROR] $*" >&2
  exit 1
}

slot_port() {
  case "$1" in
    blue) echo "${BLUE_PORT}" ;;
    green) echo "${GREEN_PORT}" ;;
    *) fail "unknown slot: $1" ;;
  esac
}

container_running() {
  [ "$(docker inspect -f '{{.State.Running}}' "api-$1" 2>/dev/null || true)" = "true" ]
}

# active slot은 Nginx symlink가 가리키는 slot과 실제 실행 중인 컨테이너가 일치할 때만 인정한다.
detect_active_slot() {
  if [ ! -L "${ACTIVE_UPSTREAM}" ]; then
    echo ""
    return
  fi

  target="$(readlink -f "${ACTIVE_UPSTREAM}")"
  case "${target}" in
    "$(readlink -f "${BLUE_UPSTREAM}")")
      if container_running blue; then
        echo "blue"
      else
        echo ""
      fi
      ;;
    "$(readlink -f "${GREEN_UPSTREAM}")")
      if container_running green; then
        echo "green"
      else
        echo ""
      fi
      ;;
    *) echo "" ;;
  esac
}

# 실패 시 이전 upstream으로 복구한다.
# 최초 배포에서는 초기 정책대로 active-upstream을 blue 기준으로 유지한다.
rollback_upstream() {
  if [ -n "${BACKUP_UPSTREAM}" ]; then
    log "rollback upstream to ${BACKUP_UPSTREAM}"
    ln -sfn "${BACKUP_UPSTREAM}" "${ACTIVE_UPSTREAM}"
    if run_as_root nginx -t; then
      reload_nginx || true
    fi
  else
    log "rollback upstream to initial blue upstream"
    ln -sfn "${BLUE_UPSTREAM}" "${ACTIVE_UPSTREAM}"
  fi
}

# 컨테이너 실행 또는 Nginx 전환 이후 실패하면 새 slot을 정리하고 이전 upstream으로 복구한다.
cleanup_on_error() {
  exit_code=$?
  if [ "${exit_code}" -eq 0 ]; then
    return
  fi

  echo "[ERROR] deployment failed, starting cleanup" >&2

  if [ "${SWITCHED}" = true ]; then
    rollback_upstream
  fi

  if [ "${NEW_CONTAINER_STARTED}" = true ] && [ -n "${NEXT_SLOT}" ]; then
    docker rm -f "api-${NEXT_SLOT}" >/dev/null 2>&1 || true
  fi

  exit "${exit_code}"
}

trap cleanup_on_error EXIT

mkdir -p "${RELEASE_DIR}"

# 컨테이너를 건드리기 전에 운영 env와 필수 런타임 입력을 검증한다.
"${VALIDATE_ENV}" "${ENV_FILE}"

if [ ! -f "${CONFIG_FILE}" ]; then
  fail "application-prod.yml not found: ${CONFIG_FILE}"
fi

if [ -z "${ENTRYPOINT_HEALTH_URL:-}" ]; then
  fail "ENTRYPOINT_HEALTH_URL is required to verify the Nginx/API entrypoint after traffic switch"
fi

ACTIVE_SLOT="$(detect_active_slot)"
case "${ACTIVE_SLOT}" in
  blue) NEXT_SLOT="green" ;;
  green) NEXT_SLOT="blue" ;;
  "") NEXT_SLOT="blue" ;;
  *) fail "invalid active slot: ${ACTIVE_SLOT}" ;;
esac

NEXT_PORT="$(slot_port "${NEXT_SLOT}")"

log "active slot: ${ACTIVE_SLOT:-none}"
log "next slot: ${NEXT_SLOT}"
log "image: ${IMAGE}"

# CD workflow가 전달한 SHA tag 이미지를 기준으로 배포한다.
if ! docker network inspect "${APP_NETWORK}" >/dev/null 2>&1; then
  log "create docker network: ${APP_NETWORK}"
  docker network create "${APP_NETWORK}" >/dev/null
fi

docker pull "${IMAGE}"

# host port 충돌을 막기 위해 inactive slot의 기존 컨테이너를 먼저 제거한다.
log "remove stale inactive container: api-${NEXT_SLOT}"
docker rm -f "api-${NEXT_SLOT}" >/dev/null 2>&1 || true

# 새 slot은 localhost에만 bind한다. 외부 트래픽은 반드시 Nginx를 통해 들어온다.
log "start new container: api-${NEXT_SLOT}"
docker run -d \
  --name "api-${NEXT_SLOT}" \
  --network "${APP_NETWORK}" \
  --env-file "${ENV_FILE}" \
  -e SPRING_CONFIG_ADDITIONAL_LOCATION=file:/app/config/ \
  -e DEPLOYED_SHA="${DEPLOY_SHA}" \
  -e FIREBASE_CREDENTIALS_PATH="${FIREBASE_CREDENTIALS_CONTAINER_PATH}" \
  -p "127.0.0.1:${NEXT_PORT}:${CONTAINER_PORT}" \
  -v "${CONFIG_FILE}:/app/config/application-prod.yml:ro" \
  -v "${FIREBASE_CREDENTIALS_FILE}:${FIREBASE_CREDENTIALS_CONTAINER_PATH}:ro" \
  --restart unless-stopped \
  --log-driver json-file \
  --log-opt max-size=10m \
  --log-opt max-file=3 \
  "${IMAGE}"

NEW_CONTAINER_STARTED=true

# readiness는 애플리케이션 상태와 필수 외부 의존성을 함께 검증한다.
READINESS_URL="http://127.0.0.1:${NEXT_PORT}/api/actuator/health/readiness"
"${HEALTH_CHECK}" "${READINESS_URL}" 24 5 3

# 이 시점 이후 실패하면 rollback할 수 있도록 현재 upstream을 백업한다.
if [ -L "${ACTIVE_UPSTREAM}" ]; then
  BACKUP_UPSTREAM="$(readlink -f "${ACTIVE_UPSTREAM}")"
fi

# active upstream symlink를 새 slot으로 교체한 뒤 Nginx 설정을 검증하고 reload한다.
log "switch nginx upstream to ${NEXT_SLOT}"
ln -sfn "${NGINX_DIR}/${NEXT_SLOT}-upstream.conf" "${ACTIVE_UPSTREAM}"
SWITCHED=true

run_as_root nginx -t
reload_nginx

# 트래픽 전환 후 실제 Nginx/API entrypoint 기준으로 health를 확인한다.
"${HEALTH_CHECK}" "${ENTRYPOINT_HEALTH_URL}" 12 5 3

# SMOKE_TEST_URL은 서버 내부에서 S3 업로드/다운로드/삭제를 수행하는 smoke endpoint를 가리킨다.
SMOKE_TEST_URL="${SMOKE_TEST_URL:-}"
if [ -n "${SMOKE_TEST_URL}" ]; then
  "${HEALTH_CHECK}" "${SMOKE_TEST_URL}" 6 5 3
else
  log "S3 upload/download/delete smoke test URL not configured; skipping"
fi

# 전환 이후 모든 검증이 성공한 뒤에만 이전 active slot을 종료한다.
if [ -n "${ACTIVE_SLOT}" ]; then
  log "stop old container: api-${ACTIVE_SLOT}"
  docker rm -f "api-${ACTIVE_SLOT}" >/dev/null 2>&1 || true
fi

# 수동 이전 SHA 재배포와 감사 추적을 위해 배포 SHA 기록을 남긴다.
if [ -f "${DEPLOYED_SHA_FILE}" ]; then
  cp "${DEPLOYED_SHA_FILE}" "${PREVIOUS_SHA_FILE}"
fi
echo "${DEPLOY_SHA}" > "${DEPLOYED_SHA_FILE}" || {
  echo "[WARN] failed to record deployed sha: ${DEPLOY_SHA}" >&2
}

SWITCHED=false
NEW_CONTAINER_STARTED=false
log "deployment completed: ${DEPLOY_SHA}"
