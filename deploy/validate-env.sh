#!/usr/bin/env bash
set -euo pipefail

# 운영 .env 파일을 shell source 없이 검증한다.
# JDBC URL처럼 '&'가 포함된 값이 shell 문법으로 깨지는 문제를 피하기 위함이다.

ENV_FILE="${1:-/opt/dondok/.env}"

if [ ! -f "${ENV_FILE}" ]; then
  echo "[ERROR] env file not found: ${ENV_FILE}" >&2
  exit 1
fi

# env 파일에서 단일 KEY=VALUE 값을 읽는다.
# ENV_PROD는 "export KEY=VALUE"가 아니라 순수 KEY=VALUE 형식을 사용해야 한다.
get_env_value() {
  awk -v key="$1" '
    /^[[:space:]]*($|#)/ { next }
    {
      line = $0
      sub(/\r$/, "", line)
      split(line, parts, "=")
      env_key = parts[1]
      gsub(/^[[:space:]]+|[[:space:]]+$/, "", env_key)

      if (env_key == key) {
        sub(/^[^=]*=/, "", line)
        gsub(/^[[:space:]]+|[[:space:]]+$/, "", line)

        if ((line ~ /^".*"$/) || (line ~ /^'\''.*'\''$/)) {
          line = substr(line, 2, length(line) - 2)
          gsub(/^[[:space:]]+|[[:space:]]+$/, "", line)
        }

        print line
        found = 1
        exit
      }
    }
    END {
      if (!found) {
        exit 1
      }
    }
  ' "${ENV_FILE}" || true
}

# prod 컨테이너가 부팅되고 readiness를 통과하기 위해 필요한 필수 값이다.
required_vars=(
  SPRING_PROFILES_ACTIVE
  MYSQL_URL
  MYSQL_USER
  MYSQL_PASSWORD
  AWS_REGION
  AWS_S3_BUCKET
  JWT_SECRET
  CORS_ALLOWED_ORIGINS
)

missing=()
for var_name in "${required_vars[@]}"; do
  value="$(get_env_value "${var_name}")"
  if [ -z "${value}" ]; then
    missing+=("${var_name}")
  fi
done

if [ "${#missing[@]}" -gt 0 ]; then
  echo "[ERROR] missing required environment variables:" >&2
  printf '  - %s\n' "${missing[@]}" >&2
  exit 1
fi

# 실수로 prod가 아닌 profile을 배포하지 않도록 막는다.
SPRING_PROFILES_ACTIVE_VALUE="$(get_env_value SPRING_PROFILES_ACTIVE)"
COOKIE_SECURE_VALUE="$(get_env_value COOKIE_SECURE)"
COOKIE_SAME_SITE_VALUE="$(get_env_value COOKIE_SAME_SITE)"

COOKIE_SECURE_VALUE="${COOKIE_SECURE_VALUE:-true}"
COOKIE_SAME_SITE_VALUE="${COOKIE_SAME_SITE_VALUE:-None}"

if [ "${SPRING_PROFILES_ACTIVE_VALUE}" != "prod" ]; then
  echo "[ERROR] SPRING_PROFILES_ACTIVE must be prod" >&2
  exit 1
fi

# 최신 브라우저에서 SameSite=None 쿠키는 Secure=true일 때만 정상 동작한다.
if [ "${COOKIE_SAME_SITE_VALUE}" = "None" ] && [ "${COOKIE_SECURE_VALUE}" != "true" ]; then
  echo "[ERROR] COOKIE_SAME_SITE=None requires COOKIE_SECURE=true" >&2
  exit 1
fi

echo "[INFO] env validation passed: ${ENV_FILE}"
