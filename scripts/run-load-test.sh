#!/usr/bin/env bash
set -euo pipefail

: "${GRAFANA_ADMIN_PASSWORD:?Set GRAFANA_ADMIN_PASSWORD for Grafana evidence queries.}"

root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root_dir"
source "$root_dir/scripts/load-test-lifecycle.sh"

phase="${1:?Usage: $0 point-smoke|point-baseline|point-limit-10|point-limit-20|point-limit-40|settlement-smoke|settlement-baseline|settlement-limit-250|settlement-limit-500|settlement-limit-1000}"
network="${APP_NETWORK:-dondok-network}"
app_url="http://localhost:${APP_PORT:-8080}"
prometheus_url="http://localhost:${PROMETHEUS_PORT:-9090}"
if [[ -n "${GRAFANA_PORT:-}" ]]; then
  grafana_port="$GRAFANA_PORT"
elif [[ -n "${GRAFANA_PORT_BIND:-}" ]]; then
  grafana_port="${GRAFANA_PORT_BIND##*:}"
else
  grafana_port=3000
fi
export GRAFANA_PORT_BIND="${GRAFANA_PORT_BIND:-127.0.0.1:$grafana_port}"
grafana_url="http://localhost:$grafana_port"
required_profiles="local,observability,load-test"
requested_profiles="${SPRING_PROFILES_ACTIVE:-}"

if [[ ",$requested_profiles," == *,prod,* ]] || { [[ -n "$requested_profiles" ]] && [[ "$requested_profiles" != "$required_profiles" ]]; }; then
  echo "SPRING_PROFILES_ACTIVE must be exactly $required_profiles (and never prod)." >&2
  exit 1
fi
export SPRING_PROFILES_ACTIVE="$required_profiles"

case "$phase" in
  point-smoke) rate=1; duration=1s; pre_vus=1; max_vus=1; accounts=1; scenario=point-charge.js ;;
  point-baseline) rate=5; duration=6m; pre_vus=5; max_vus=20; accounts=20; scenario=point-charge.js ;;
  point-limit-10) rate=10; duration=6m; pre_vus=20; max_vus=50; accounts=20; scenario=point-charge.js ;;
  point-limit-20) rate=20; duration=6m; pre_vus=40; max_vus=100; accounts=20; scenario=point-charge.js ;;
  point-limit-40) rate=40; duration=6m; pre_vus=80; max_vus=200; accounts=20; scenario=point-charge.js ;;
  settlement-smoke) settlements=1; scenario=settlement-batch.js ;;
  settlement-baseline) settlements=100; scenario=settlement-batch.js ;;
  settlement-limit-250) settlements=250; scenario=settlement-batch.js ;;
  settlement-limit-500) settlements=500; scenario=settlement-batch.js ;;
  settlement-limit-1000) settlements=1000; scenario=settlement-batch.js ;;
  *) echo "Unknown phase: $phase" >&2; exit 2 ;;
esac

run_id="$(date -u +%Y%m%dt%H%M%S)-$(git rev-parse --short HEAD)"
results_root="load-test/results/$run_id"
phase_dir="$results_root/$phase"
lock_dir="load-test/.suite-lock"
child_pid=""
reset_ready=false

cleanup() {
  status=$?
  set +e
  load_test_cleanup "$status" "$child_pid" "$reset_ready" "$lock_dir" "$app_url"
  exit "$status"
}
trap cleanup EXIT INT TERM

if ! load_test_acquire_suite_lock "$lock_dir" "pid=$$ run_id=$run_id"; then
  echo "A load-test suite lock already exists at $lock_dir; inspect it instead of stealing it." >&2
  exit 1
fi
mkdir -p "$phase_dir"

docker network inspect "$network" >/dev/null 2>&1 || docker network create "$network" >/dev/null
docker compose -f compose.yaml -f compose.observability.yaml --profile observability --profile k6 config -q
GRAFANA_ADMIN_PASSWORD="$GRAFANA_ADMIN_PASSWORD" docker compose -f monitoring/compose.yaml config -q
docker compose -f compose.yaml -f compose.observability.yaml --profile observability up -d --build mysql redis localstack app
GRAFANA_ADMIN_PASSWORD="$GRAFANA_ADMIN_PASSWORD" docker compose -f monitoring/compose.yaml up -d

for _ in $(seq 1 60); do
  if curl --fail --silent "$app_url/api/actuator/health/readiness" | grep -q '"status":"UP"'; then break; fi
  sleep 2
done
curl --fail --silent "$app_url/api/actuator/health/readiness" | grep -q '"status":"UP"'

for _ in $(seq 1 30); do
  if curl --fail --silent "$prometheus_url/api/v1/targets" | python3 -c 'import json,sys; raise SystemExit(not any(t["labels"].get("job") == "dondok-api-local" and t["health"] == "up" for t in json.load(sys.stdin)["data"]["activeTargets"]))'; then break; fi
  sleep 2
done
curl --fail --silent "$prometheus_url/api/v1/targets" | python3 -c 'import json,sys; assert any(t["labels"].get("job") == "dondok-api-local" and t["health"] == "up" for t in json.load(sys.stdin)["data"]["activeTargets"])'
for _ in $(seq 1 30); do
  if curl --fail --silent -u "${GRAFANA_ADMIN_USER:-admin}:$GRAFANA_ADMIN_PASSWORD" "$grafana_url/api/user" >/dev/null; then break; fi
  sleep 2
done
curl --fail --silent -u "${GRAFANA_ADMIN_USER:-admin}:$GRAFANA_ADMIN_PASSWORD" "$grafana_url/api/user" >/dev/null
reset_ready=true
curl --fail --silent --output /dev/null --request POST "$app_url/api/load-test/reset"

post_json() {
  curl --fail --silent --show-error --request POST "$app_url$1" --header 'Content-Type: application/json' --data "$2"
}
query_counter() {
  python3 - "$prometheus_url" "$1" <<'PYTHON'
import json, sys
from urllib.parse import urlencode
from urllib.request import urlopen
with urlopen(sys.argv[1] + '/api/v1/query?' + urlencode({'query': sys.argv[2]}), timeout=10) as response:
    payload = json.load(response)
assert payload['status'] == 'success'
print(json.dumps(payload['data']['result']))
PYTHON
}

if [[ "$scenario" = point-charge.js ]]; then
  post_json "/api/load-test/runs/point/prepare" "{\"runId\":\"$run_id\",\"accounts\":$accounts}" > "$phase_dir/prepare.json"
else
  post_json "/api/load-test/runs/settlement/final/preflight" "{\"runId\":\"$run_id\"}" > "$phase_dir/preflight.json"
  post_json "/api/load-test/runs/settlement/final/prepare" "{\"runId\":\"$run_id\",\"settlements\":$settlements}" > "$phase_dir/prepare.json"
  sleep 315
  query_counter 'sum(dondok_settlement_batch_execution_seconds_count{job="dondok-api-local",batch_type="final",outcome="success"})' > "$phase_dir/prometheus-before-success.json"
  query_counter 'sum(dondok_settlement_batch_execution_seconds_count{job="dondok-api-local",batch_type="final",outcome="failure"})' > "$phase_dir/prometheus-before-failure.json"
fi

stage_started_ms="$(python3 -c 'import time; print(int(time.time()*1000))')"
set +e
docker compose -f compose.yaml -f compose.observability.yaml --profile k6 run --rm \
  -e RUN_ID="$run_id" -e RESULT_DIR="/results/$run_id/$phase" \
  -e RATE="${rate:-}" -e DURATION="${duration:-}" -e PRE_ALLOCATED_VUS="${pre_vus:-}" -e MAX_VUS="${max_vus:-}" -e SETTLEMENTS="${settlements:-}" \
  k6 run "/scripts/$scenario" > "$phase_dir/k6.log" 2>&1 &
child_pid=$!
wait "$child_pid"
k6_status=$?
child_pid=""
set -e
if [[ "$k6_status" -ne 0 ]]; then
  echo "k6 exited with status $k6_status; collecting completed-run evidence before reset." >&2
  if [[ ! -s "$phase_dir/summary.json" ]]; then
    echo "k6 did not produce summary.json; preserving partial artifacts." >&2
    exit "$k6_status"
  fi
fi

sleep "$(load_test_evidence_delay "$scenario")"
to_ms="$(python3 -c 'import time; print(int(time.time()*1000))')"
if [[ "$scenario" = point-charge.js && "$phase" != point-smoke ]]; then
  from_ms="$((to_ms - 60000))"
else
  from_ms="$stage_started_ms"
fi
if [[ "$scenario" = settlement-batch.js ]]; then
  query_counter 'sum(dondok_settlement_batch_execution_seconds_count{job="dondok-api-local",batch_type="final",outcome="success"})' > "$phase_dir/prometheus-after-success.json"
  query_counter 'sum(dondok_settlement_batch_execution_seconds_count{job="dondok-api-local",batch_type="final",outcome="failure"})' > "$phase_dir/prometheus-after-failure.json"
  python3 scripts/verify-settlement-counter-delta.py "$settlements" \
    "$phase_dir/prometheus-before-success.json" \
    "$phase_dir/prometheus-after-success.json" \
    "$phase_dir/prometheus-before-failure.json" \
    "$phase_dir/prometheus-after-failure.json" \
    "$phase_dir/prometheus-counter-delta.json"
fi

GRAFANA_URL="$grafana_url" GRAFANA_ADMIN_USER="${GRAFANA_ADMIN_USER:-admin}" GRAFANA_ADMIN_PASSWORD="$GRAFANA_ADMIN_PASSWORD" \
  python3 - "$from_ms" "$to_ms" "$phase_dir" "$phase" "$scenario" <<'PYTHON'
import base64, json, os, re, sys
from pathlib import Path
from urllib.request import Request, urlopen

from_ms, to_ms, output, phase, scenario = sys.argv[1:]
base = os.environ['GRAFANA_URL']
auth = base64.b64encode(f"{os.environ['GRAFANA_ADMIN_USER']}:{os.environ['GRAFANA_ADMIN_PASSWORD']}".encode()).decode()
headers = {'Authorization': f'Basic {auth}', 'Content-Type': 'application/json'}
with urlopen(Request(base + '/api/dashboards/uid/point-settlement-baseline', headers=headers), timeout=10) as response:
    dashboard = json.load(response)['dashboard']
Path(output, 'dashboard-url.txt').write_text(base + f'/d/point-settlement-baseline?from={from_ms}&to={to_ms}&refresh=\n')
evidence = []
for panel in dashboard.get('panels', []):
    title = panel.get('title', '')
    for target in panel.get('targets', []):
        expression = target.get('expr')
        if not expression:
            continue
        ref_id = target.get('refId', 'A')
        body = {'from': from_ms, 'to': to_ms, 'queries': [{'refId': ref_id, 'datasource': {'type': 'prometheus', 'uid': 'prometheus'}, 'expr': expression, 'format': 'time_series', 'range': True, 'instant': False, 'intervalMs': 15000, 'maxDataPoints': 1000}]}
        request = Request(base + '/api/ds/query', data=json.dumps(body).encode(), headers=headers, method='POST')
        with urlopen(request, timeout=10) as response:
            result = json.load(response)
        frame = result.get('results', {}).get(ref_id, {})
        if frame.get('error'):
            raise RuntimeError(f'Grafana query failed: {title}: {frame["error"]}')
        slug = re.sub(r'[^a-z0-9]+', '-', title.lower()).strip('-')
        filename = f'grafana-{slug}-{ref_id}.json'
        Path(output, filename).write_text(json.dumps(result, indent=2) + '\n')
        point_panels = {'HTTP RPS', 'HTTP errors (4xx/5xx)', 'HTTP p95', 'HTTP p99', 'Process CPU', 'JVM total memory used', 'JVM heap used / max', 'JVM GC pause activity', 'Hikari active / idle', 'Hikari pending / max', 'Point charge success/failure rate', 'Point charge p95'}
        settlement_panels = {'HTTP RPS', 'HTTP errors (4xx/5xx)', 'HTTP p95', 'HTTP p99', 'Process CPU', 'JVM total memory used', 'JVM heap used / max', 'JVM GC pause activity', 'Hikari active / idle', 'Hikari pending / max', 'Settlement batch success/failure rate', 'Settlement batch p95'}
        authoritative = (scenario == 'point-charge.js' and phase != 'point-smoke' and title in point_panels) or (scenario == 'settlement-batch.js' and title in settlement_panels)
        numeric = [value for grafana_frame in frame.get('frames', []) for field, column in zip(grafana_frame.get('schema', {}).get('fields', []), grafana_frame.get('data', {}).get('values', [])) if field.get('type') == 'number' for value in column if isinstance(value, (int, float)) and not isinstance(value, bool)]
        if authoritative and not numeric:
            raise RuntimeError(f'Grafana query has no numeric data: {title}')
        evidence.append({'panel': title, 'refId': ref_id, 'path': filename, 'querySha256': __import__('hashlib').sha256(expression.encode()).hexdigest(), 'empty': not bool(frame.get('frames')), 'selectorWindowIsolated': authoritative, 'authoritativeForComparison': authoritative})
Path(output, 'grafana-evidence.json').write_text(json.dumps(evidence, indent=2) + '\n')
PYTHON

app_image_id="$(docker compose -f compose.yaml -f compose.observability.yaml images -q app | head -1)"
k6_image_id="$(docker image inspect --format '{{.Id}}' grafana/k6:0.54.0)"
config_hash="$(docker compose -f compose.yaml -f compose.observability.yaml --profile observability --profile k6 config | shasum -a 256 | awk '{print $1}')"
python3 - "$phase_dir/manifest.json" "$run_id" "$phase" "$from_ms" "$to_ms" "$scenario" "${rate:-}" "${duration:-}" "${pre_vus:-}" "${max_vus:-}" "${accounts:-}" "${settlements:-}" "$app_image_id" "$k6_image_id" "$config_hash" "$k6_status" <<'PYTHON'
import json, platform, subprocess, sys
(path, run_id, phase, from_ms, to_ms, scenario, rate, duration, pre_vus, max_vus, accounts, settlements, app_image, k6_image, config_hash, k6_status) = sys.argv[1:]
payload = {
  'schemaVersion': 1, 'runId': run_id, 'phase': phase, 'scenario': scenario,
  'fromMs': int(from_ms), 'toMs': int(to_ms), 'rate': rate or None, 'duration': duration or None,
  'preAllocatedVUs': pre_vus or None, 'maxVUs': max_vus or None, 'accounts': accounts or None,
  'settlements': settlements or None, 'profiles': ['local', 'observability', 'load-test'],
  'appCpuLimit': '2.0', 'appMemoryLimit': '2G', 'scrapeInterval': '15s',
  'gitSha': subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip(),
  'gitDirty': bool(subprocess.check_output(['git', 'status', '--porcelain'], text=True).strip()),
  'appImageId': app_image, 'k6ImageRef': 'grafana/k6:0.54.0', 'k6ImageId': k6_image,
  'k6ExitStatus': int(k6_status),
  'composeConfigSha256': config_hash, 'os': platform.system(), 'arch': platform.machine(),
  'selectorWindowIsolated': scenario == 'point-charge.js' and phase != 'point-smoke',
}
open(path, 'w').write(json.dumps(payload, indent=2) + '\n')
PYTHON

curl --fail --silent --output /dev/null --request POST "$app_url/api/load-test/reset"
reset_ready=false
if [[ "$k6_status" -ne 0 ]]; then
  echo "Load-test phase completed with k6 status $k6_status (artifact: $phase_dir)" >&2
  exit "$k6_status"
fi
echo "Load-test phase passed: $phase (artifact: $phase_dir)"
