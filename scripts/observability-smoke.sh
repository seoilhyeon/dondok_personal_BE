#!/usr/bin/env bash
set -euo pipefail

: "${GRAFANA_ADMIN_PASSWORD:?Set GRAFANA_ADMIN_PASSWORD to run the local observability smoke.}"

root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root_dir"
network="${APP_NETWORK:-dondok-network}"
app_url="http://localhost:${APP_PORT:-8080}"
prometheus_url="http://localhost:${PROMETHEUS_PORT:-9090}"
grafana_url="http://localhost:${GRAFANA_PORT:-3000}"

# The monitoring Compose file intentionally consumes this pre-existing app network.
docker network inspect "$network" >/dev/null 2>&1 || docker network create "$network" >/dev/null

required_profiles="local,observability,load-test"
requested_profiles="${SPRING_PROFILES_ACTIVE:-}"
if [[ ",$requested_profiles," == *,prod,* ]]; then
  echo "Refusing to run the load-test smoke with the prod profile." >&2
  exit 1
fi
if [[ -n "$requested_profiles" && "$requested_profiles" != "$required_profiles" ]]; then
  echo "SPRING_PROFILES_ACTIVE must be $required_profiles for this smoke test." >&2
  exit 1
fi
export SPRING_PROFILES_ACTIVE="$required_profiles"
docker compose -f compose.yaml -f compose.observability.yaml --profile observability config -q
GRAFANA_ADMIN_PASSWORD="$GRAFANA_ADMIN_PASSWORD" docker compose -f monitoring/compose.yaml config -q

docker compose -f compose.yaml -f compose.observability.yaml --profile observability up -d --build
GRAFANA_ADMIN_PASSWORD="$GRAFANA_ADMIN_PASSWORD" docker compose -f monitoring/compose.yaml up -d

prometheus_reloaded=false
for _ in $(seq 1 30); do
  if curl --fail --silent "$prometheus_url/-/ready" >/dev/null \
    && curl --fail --silent --request POST "$prometheus_url/-/reload" >/dev/null; then
    prometheus_reloaded=true
    break
  fi
  sleep 2
done
if [ "$prometheus_reloaded" != true ]; then
  echo "Prometheus did not become ready to reload its bind-mounted configuration." >&2
  exit 1
fi

grafana_status=""
for _ in $(seq 1 30); do
  grafana_status="$(curl --silent --output /dev/null --write-out '%{http_code}' -u "${GRAFANA_ADMIN_USER:-admin}:$GRAFANA_ADMIN_PASSWORD" "$grafana_url/api/user" || true)"
  if [ "$grafana_status" = "200" ]; then
    break
  fi
  if [ "$grafana_status" = "401" ]; then
    cat >&2 <<'EOF'
Grafana rejected GRAFANA_ADMIN_PASSWORD. Its admin password is persisted in the local grafana-data volume and is only initialized on first start. Re-run with that existing local password; this script will not reset or delete volumes.
EOF
    exit 1
  fi
  sleep 2
done
if [ "$grafana_status" != "200" ]; then
  echo "Grafana did not become ready for authenticated checks (last HTTP status: $grafana_status)." >&2
  exit 1
fi

for _ in $(seq 1 60); do
  if curl --fail --silent "$app_url/api/actuator/health/readiness" | grep -q '"status":"UP"'; then
    break
  fi
  sleep 2
done
curl --fail --silent "$app_url/api/actuator/health/readiness" | grep -q '"status":"UP"'
curl --fail --silent "$app_url/api/actuator/prometheus" | python3 -c '
import sys
text=sys.stdin.read()
for forbidden in ("user_id=", "crew_id=", "member_uuid=", "payment_id=", "order_id=", "settlement_id=", "amount=", "userId=", "crewId="):
    assert forbidden not in text, forbidden
'

for _ in $(seq 1 30); do
  if curl --fail --silent "$prometheus_url/api/v1/targets" | python3 -c '
import json, sys
for target in json.load(sys.stdin)["data"]["activeTargets"]:
    if target["labels"].get("job") == "dondok-api-local" and target["health"] == "up":
        raise SystemExit(0)
raise SystemExit(1)
'; then
    break
  fi
  sleep 2
done
curl --fail --silent "$prometheus_url/api/v1/targets" | python3 -c '
import json, sys
assert any(t["labels"].get("job") == "dondok-api-local" and t["health"] == "up" for t in json.load(sys.stdin)["data"]["activeTargets"])
'

# Protected point-history and settlement-detail reads create generic HTTP meters; no domain data is mutated.
curl --silent --output /dev/null "$app_url/api/points/history"
curl --silent --output /dev/null "$app_url/api/settlements/1"
assert_prometheus_baseline_metrics() {
  python3 - "$prometheus_url" "$grafana_url" "${GRAFANA_ADMIN_USER:-admin}" "$GRAFANA_ADMIN_PASSWORD" <<'PYTHON'
import base64
import json
import sys
from urllib.parse import urlencode
from urllib.request import Request, urlopen

TIMEOUT = 10
prometheus_url, grafana_url, grafana_user, grafana_password = sys.argv[1:]
credentials = base64.b64encode(f"{grafana_user}:{grafana_password}".encode()).decode()
dashboard_request = Request(
    f"{grafana_url}/api/dashboards/uid/point-settlement-baseline",
    headers={"Authorization": f"Basic {credentials}"},
)
with urlopen(dashboard_request, timeout=TIMEOUT) as response:
    dashboard = json.load(response)["dashboard"]

assert dashboard["title"] == "Point & Settlement Baseline"
queries_checked = 0
for panel in dashboard["panels"]:
    if panel.get("title", "").startswith(("Point ", "Settlement ", "Retry batch")):
        continue
    for target in panel.get("targets", []):
        query = target.get("expr")
        if not query:
            continue
        with urlopen(f"{prometheus_url}/api/v1/query?{urlencode({'query': query})}", timeout=TIMEOUT) as response:
            payload = json.load(response)
        assert payload["status"] == "success", f"Prometheus query failed for {panel['title']}"
        assert payload["data"]["result"], f"Prometheus has no data for dashboard panel {panel['title']}"
        queries_checked += 1

assert queries_checked, "Dashboard has no Prometheus queries to validate"
PYTHON
}

for _ in $(seq 1 30); do
  if assert_prometheus_baseline_metrics 2>/dev/null; then
    break
  fi
  sleep 2
done
assert_prometheus_baseline_metrics

echo "Observability smoke passed: readiness UP, Prometheus local target UP, generic HTTP metrics scraped, dashboard provisioned."

# PR2 metric-to-panel contract: every run primes traffic before the first scrape, repeats it, then
# asks both Prometheus and Grafana to evaluate the provisioned panel expressions over that range.
load_test_paths=(
  "/api/load-test/reset"
  "/api/load-test/point-charge?paymentId=load-test-payment-success&orderId=load-test-order-success"
  "/api/load-test/point-charge?paymentId=load-test-payment-fail&orderId=load-test-order-fail"
  "/api/load-test/recovery"
  "/api/load-test/settlement/final"
  "/api/load-test/settlement/retry"
)
load_test_failure_path="${load_test_paths[2]}"

run_load_test_tuples() {
  for path in "${load_test_paths[@]}"; do
    if [ "$path" = "$load_test_failure_path" ]; then
      curl --silent --output /dev/null --request POST "$app_url$path"
    else
      curl --fail --silent --request POST "$app_url$path" >/dev/null
    fi
  done
}

run_load_test_tuples
python3 - "$prometheus_url" "$grafana_url" "${GRAFANA_ADMIN_USER:-admin}" "$GRAFANA_ADMIN_PASSWORD" "$app_url" "$load_test_failure_path" "${load_test_paths[@]}" <<'PYTHON'
import base64, json, math, sys, time
from urllib.parse import urlencode
from urllib.request import Request, urlopen

TIMEOUT = 10
prometheus, grafana, user, password, app, failure_path, *load_test_paths = sys.argv[1:]
auth = base64.b64encode(f"{user}:{password}".encode()).decode()
anchor = 'timestamp(dondok_point_charge_seconds_count{job="dondok-api-local",outcome="success",failure_code="none"})'

def query(expr, at=None):
    params = {"query": expr}
    if at is not None: params["time"] = str(at)
    with urlopen(f"{prometheus}/api/v1/query?{urlencode(params)}", timeout=TIMEOUT) as r:
        result = json.load(r)
    assert result["status"] == "success" and result["data"]["resultType"] == "vector", result
    return result["data"]["result"]

def anchor_timestamp(previous=0):
    for _ in range(40):
        values = query(anchor)
        if values:
            value = float(values[0]["value"][1])
            if value > previous: return value
        time.sleep(2)
    raise AssertionError("expected a newer point-charge scrape sample")

first = anchor_timestamp()
# The shell function cannot be called from this process; reuse its route manifest.
for path in load_test_paths:
    ok = path != failure_path
    try:
        with urlopen(Request(app + path, method="POST"), timeout=TIMEOUT) as response:
            if not ok: raise AssertionError("deterministic failure unexpectedly succeeded")
    except Exception as error:
        if ok: raise
        status = getattr(error, "code", None)
        assert status == 400, f"expected deterministic point-charge HTTP 400, got {status}: {error}"
second = anchor_timestamp(first)
raw = urlopen(app + "/api/actuator/prometheus", timeout=TIMEOUT).read().decode()
for family in ("dondok_point_charge_seconds", "dondok_point_charge_recovery", "dondok_settlement_batch_execution_seconds"):
    assert family in raw, f"missing metric family: {family}"
for forbidden in ("user_id=", "crew_id=", "member_uuid=", "payment_id=", "order_id=", "settlement_id=", "amount=", "userId=", "crewId="):
    assert forbidden not in raw, f"forbidden metric label: {forbidden}"
failure_values = query('dondok_point_charge_seconds_count{job="dondok-api-local",outcome="failure",failure_code="PAYMENT_CONFIRM_FAILED"}', second)
assert failure_values and float(failure_values[0]["value"][1]) > 0, "point charge failure tuple missing"

with urlopen(Request(f"{grafana}/api/dashboards/uid/point-settlement-baseline", headers={"Authorization": f"Basic {auth}"}), timeout=TIMEOUT) as r:
    dashboard = json.load(r)["dashboard"]
required_titles = {
    "Point charge success/failure rate", "Point charge p95", "Point recovery retry/failure rate",
    "Settlement batch success/failure rate", "Settlement batch p95", "Retry batch attempt/failure rate",
}
panels_by_title = {panel.get("title"): panel for panel in dashboard["panels"]}
missing = required_titles - panels_by_title.keys()
assert not missing, f"required custom dashboard panels missing: {sorted(missing)}"
panels = [panels_by_title[title] for title in required_titles]
for panel in panels:
    for target in panel.get("targets", []):
        expr = target.get("expr")
        if not expr: continue
        values = query(expr, second)
        assert values, f"Prometheus empty: {panel['title']}"
        numeric = [float(value["value"][1]) for value in values]
        assert all(math.isfinite(value) for value in numeric), f"Prometheus non-finite values: {panel['title']}"
        assert any(value > 0 for value in numeric), f"Prometheus non-positive values: {panel['title']}"
        ref = target.get("refId", "A")
        body = {"from": str(int(first * 1000)), "to": str(int(second * 1000)), "queries": [{
            "refId": ref, "datasource": {"type": "prometheus", "uid": "prometheus"}, "expr": expr,
            "format": "time_series", "range": True, "instant": False, "intervalMs": 15000, "maxDataPoints": 1000,
        }]}
        request = Request(f"{grafana}/api/ds/query", data=json.dumps(body).encode(), method="POST", headers={"Authorization": f"Basic {auth}", "Content-Type": "application/json"})
        with urlopen(request, timeout=TIMEOUT) as response: payload = json.load(response)
        frame = payload.get("results", {}).get(ref, {})
        assert not frame.get("error") and frame.get("frames"), f"Grafana invalid/empty: {panel['title']} {frame}"
        numeric = []
        for grafana_frame in frame["frames"]:
            fields = grafana_frame.get("schema", {}).get("fields", [])
            values = grafana_frame.get("data", {}).get("values", [])
            for field, column in zip(fields, values):
                if field.get("type") != "number":
                    continue
                for value in column:
                    if isinstance(value, (int, float)) and not isinstance(value, bool): numeric.append(float(value))
        assert numeric, f"Grafana has no numeric values: {panel['title']}"
        assert all(math.isfinite(value) for value in numeric), f"Grafana non-finite values: {panel['title']}"
        assert any(value > 0 for value in numeric), f"Grafana non-positive values: {panel['title']}"
print(f"PR2 metric dashboard contract passed: first={first}, second={second}")
PYTHON
