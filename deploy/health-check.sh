#!/usr/bin/env bash
set -euo pipefail

# URL이 HTTP 200을 반환할 때까지 재시도한다.
# 컨테이너 readiness, Nginx entrypoint health, 선택 smoke test에서 공통으로 사용한다.

URL="${1:?usage: health-check.sh <url> [max_attempts] [interval_seconds] [timeout_seconds]}"
MAX_ATTEMPTS="${2:-24}"
INTERVAL_SECONDS="${3:-5}"
TIMEOUT_SECONDS="${4:-3}"

for attempt in $(seq 1 "${MAX_ATTEMPTS}"); do
  # curl 오류는 상태 코드 000처럼 처리해 재시도를 계속할 수 있게 한다.
  status_code="$(
    curl \
      --silent \
      --show-error \
      --output /dev/null \
      --write-out '%{http_code}' \
      --max-time "${TIMEOUT_SECONDS}" \
      "${URL}" || true
  )"

  if [ "${status_code}" = "200" ]; then
    echo "[INFO] health check passed: ${URL}"
    exit 0
  fi

  echo "[INFO] health check waiting (${attempt}/${MAX_ATTEMPTS}): ${URL} returned ${status_code}"
  sleep "${INTERVAL_SECONDS}"
done

echo "[ERROR] health check failed after ${MAX_ATTEMPTS} attempts: ${URL}" >&2
echo "[ERROR] If this is an HTTPS entrypoint, check DNS, TLS certificate, security group, and Nginx routing." >&2
exit 1
