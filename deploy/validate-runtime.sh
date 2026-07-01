#!/usr/bin/env bash
set -euo pipefail

# 배포 전에 Lightsail/server 런타임 필수 조건을 검증한다.
# Nginx site/TLS는 서버에서 직접 관리하지만, CD는 nginx reload 권한이 필요하다.

ENV_FILE="${1:-/opt/dondok/.env}"

# .env에 선택 AWS credential key가 있는지 확인한다.
has_env_value() {
  key="$1"
  [ -f "${ENV_FILE}" ] || return 1

  awk -v key="${key}" '
    /^[[:space:]]*($|#)/ { next }
    {
      line = $0
      sub(/\r$/, "", line)
      split(line, parts, "=")
      env_key = parts[1]
      gsub(/^[[:space:]]+|[[:space:]]+$/, "", env_key)

      if (env_key == key) {
        sub(/^[^=]*=/, "", line)
        if (length(line) > 0) {
          found = 1
        }
        exit
      }
    }
    END {
      if (!found) {
        exit 1
      }
    }
  ' "${ENV_FILE}"
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "[ERROR] required command is not installed: $1" >&2
    exit 1
  }
}

fetch_imds_token_from_host() {
  curl \
    --silent \
    --max-time 2 \
    -X PUT \
    -H "X-aws-ec2-metadata-token-ttl-seconds: 60" \
    "http://169.254.169.254/latest/api/token" || true
}

fetch_iam_role_from_host() {
  token="$1"
  curl \
    --silent \
    --max-time 2 \
    -H "X-aws-ec2-metadata-token: ${token}" \
    "http://169.254.169.254/latest/meta-data/iam/security-credentials/" || true
}

validate_container_imds_access() {
  curl_image="${IMDS_CHECK_IMAGE:-curlimages/curl:8.11.1}"

  # 인스턴스 IAM Role을 컨테이너에서 쓰려면 Docker bridge network 안에서도 IMDS에 접근 가능해야 한다.
  # 이 검증이 실패하면 metadata option의 http-put-response-hop-limit 값을 2 이상으로 조정한다.
  container_token="$(
    docker run --rm --network bridge --entrypoint curl "${curl_image}" \
      --silent \
      --max-time 2 \
      -X PUT \
      -H "X-aws-ec2-metadata-token-ttl-seconds: 60" \
      "http://169.254.169.254/latest/api/token" || true
  )"

  if [ -z "${container_token}" ]; then
    echo "[ERROR] Docker container cannot access instance IMDS token endpoint. Set instance metadata http-put-response-hop-limit to 2 or higher." >&2
    exit 1
  fi

  container_role="$(
    docker run --rm --network bridge --entrypoint curl "${curl_image}" \
      --silent \
      --max-time 2 \
      -H "X-aws-ec2-metadata-token: ${container_token}" \
      "http://169.254.169.254/latest/meta-data/iam/security-credentials/" || true
  )"

  if [ -z "${container_role}" ]; then
    echo "[ERROR] Docker container cannot read instance IAM Role credentials from IMDS. Check IAM Role attachment and IMDS hop limit." >&2
    exit 1
  fi

  echo "[INFO] Docker container can access instance IAM Role via IMDS: ${container_role}"
}

require_command docker
require_command nginx
require_command curl

run_as_root_dry_run() {
  if [ "$(id -u)" -eq 0 ]; then
    "$@"
  else
    sudo -n "$@"
  fi
}

# 배포 사용자는 컨테이너 pull/run/remove를 수행할 수 있어야 한다.
docker info >/dev/null 2>&1 || {
  echo "[ERROR] current server user cannot access Docker. Add the user to the docker group and reconnect." >&2
  exit 1
}

# CD는 Nginx 설정 검증과 reload를 위해 nginx 명령만 필요로 한다.
nginx -v >/dev/null 2>&1 || {
  echo "[ERROR] nginx command is required so CD can run nginx -t and reload after upstream switch. Nginx site and HTTPS certificates must still be managed directly on server." >&2
  exit 1
}

run_as_root_dry_run nginx -t >/dev/null 2>&1 || {
  echo "[ERROR] current server user cannot run nginx -t with non-interactive sudo. Configure NOPASSWD sudo for nginx before running CD." >&2
  exit 1
}

# S3 접근은 instance IAM Role을 우선 사용한다. 정적 AWS key는 fallback으로만 허용한다.
metadata_token="$(fetch_imds_token_from_host)"

iam_role=""
if [ -n "${metadata_token}" ]; then
  iam_role="$(fetch_iam_role_from_host "${metadata_token}")"
fi

if [ -z "${iam_role}" ]; then
  if has_env_value AWS_ACCESS_KEY_ID && has_env_value AWS_SECRET_ACCESS_KEY; then
    echo "[WARN] instance IAM role was not detected. Falling back to AWS access keys from env."
  else
    echo "[ERROR] AWS credentials were not found. Attach an instance IAM role for S3 access or set AWS_ACCESS_KEY_ID/AWS_SECRET_ACCESS_KEY." >&2
    exit 1
  fi
else
  echo "[INFO] instance IAM role detected for AWS access: ${iam_role}"
  validate_container_imds_access
fi

echo "[INFO] runtime validation passed"
