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

load_test_cleanup() {
  local requested_status="$1"
  local child_pid="$2"
  local reset_ready="$3"
  local lock_dir="$4"
  local app_url="$5"

  if [[ -n "$child_pid" ]] && kill -0 "$child_pid" 2>/dev/null; then
    kill -TERM "$child_pid" 2>/dev/null || true
    wait "$child_pid" 2>/dev/null || true
  fi
  if [[ "$reset_ready" = true ]]; then
    curl --silent --output /dev/null --request POST "$app_url/api/load-test/reset" || true
  fi
  rm -f "$lock_dir/owner"
  rmdir "$lock_dir" 2>/dev/null || true
  return "$requested_status"
}
