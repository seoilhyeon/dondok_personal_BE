#!/usr/bin/env bash

load_test_acquire_suite_lock() {
  local lock_dir="$1"
  local owner="$2"
  mkdir "$lock_dir" 2>/dev/null || return 1
  printf '%s\n' "$owner" > "$lock_dir/owner"
}

load_test_evidence_delay() {
  [[ "$1" = settlement-batch.js ]] && printf '31\n' || printf '16\n'
}

load_test_reset() {
  local app_url="$1"
  curl --fail --silent --show-error --connect-timeout 5 --max-time 30 --output /dev/null \
    --request POST "$app_url/api/load-test/reset"
}

load_test_cleanup() {
  local requested_status="$1"
  local child_pid="$2"
  local reset_ready="$3"
  local lock_dir="$4"
  local app_url="$5"
  local cleanup_status="$requested_status"
  local release_lock=true

  if [[ -n "$child_pid" ]] && kill -0 "$child_pid" 2>/dev/null; then
    kill -TERM "$child_pid" 2>/dev/null || true
    wait "$child_pid" 2>/dev/null || true
  fi
  if [[ "$reset_ready" = true ]] && ! load_test_reset "$app_url"; then
    release_lock=false
    [[ "$cleanup_status" -ne 0 ]] || cleanup_status=1
  elif [[ "$reset_ready" = failed ]]; then
    release_lock=false
    [[ "$cleanup_status" -ne 0 ]] || cleanup_status=1
  fi
  if [[ "$release_lock" = true ]]; then
    if ! rm -f "$lock_dir/owner"; then
      echo "Failed to remove suite lock owner: $lock_dir/owner" >&2
      [[ "$cleanup_status" -ne 0 ]] || cleanup_status=1
    elif ! rmdir "$lock_dir" 2>/dev/null; then
      echo "Failed to remove suite lock directory: $lock_dir" >&2
      [[ "$cleanup_status" -ne 0 ]] || cleanup_status=1
    fi
  fi
  return "$cleanup_status"
}
