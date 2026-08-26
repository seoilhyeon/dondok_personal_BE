# Local observability baseline

This is a disposable, local-only baseline for point and settlement investigation. The Compose CPU/memory limits approximate a t3.small envelope (2 vCPU/2 GiB); they do **not** reproduce EC2, RDS, S3, or network behaviour.

## Start and smoke-test

With Docker running and `backend-personal/.env` populated from `.env.example`, run:

```sh
GRAFANA_ADMIN_PASSWORD='your-existing-or-new-local-password' ./scripts/observability-smoke.sh
```

Grafana stores its administrator password in the local `grafana-data` volume on its first start. On a fresh volume, the value above initializes that password; with an existing volume, pass its existing password. The smoke script preflights this non-destructively and never resets volumes. If the old local password is unavailable and discarding local Prometheus/Grafana history is intentional, explicitly reset the monitoring volumes before rerunning:

```sh
GRAFANA_ADMIN_PASSWORD='new-local-password' docker compose -f monitoring/compose.yaml down -v
```

If `localhost:3000` is already in use, keep the default configuration and override only this local run's Grafana host port. `GRAFANA_PORT_BIND` configures Docker publishing; `GRAFANA_PORT` lets the smoke script use the same address:

```sh
GRAFANA_PORT_BIND=127.0.0.1:3001 \
GRAFANA_PORT=3001 \
GRAFANA_ADMIN_PASSWORD='your-existing-or-new-local-password' \
./scripts/observability-smoke.sh
```

Grafana is then available at <http://localhost:3001>.

The script creates the local-only shared Docker network when absent, validates both Compose files, starts MySQL/Redis/LocalStack/the Boot app and the existing Prometheus/Grafana stack, waits for `/api/actuator/health/readiness`, confirms Prometheus reports `dondok-api-local` as `UP`, creates non-mutating point-history and settlement-detail smoke traffic, checks the generic scrape, and verifies the provisioned dashboard.

- App readiness: <http://localhost:8080/api/actuator/health/readiness>
- Prometheus targets: <http://localhost:9090/targets>
- Grafana: <http://localhost:3000/d/point-settlement-baseline>

The app runs with `local,observability`. That profile exposes only `health,prometheus` at `/api/actuator`; it does not expose `info`, env, metrics, or mutating Actuator endpoints. The Prometheus `dondok-api-blue` and `dondok-api-green` jobs remain deployment targets; `dondok-api-local` is additive.

## Manual start for load-test preparation

Use the smoke script above for the complete reproducible check. To keep the local topology running while preparing a later load test, start the application/dependencies and monitoring stack separately:

```sh
# The two Compose projects use this external network.
docker network inspect "${APP_NETWORK:-dondok-network}" >/dev/null 2>&1 \
  || docker network create "${APP_NETWORK:-dondok-network}"

# MySQL, Redis, LocalStack, and the Boot application
docker compose -f compose.yaml -f compose.observability.yaml \
  --profile observability up -d --build

# Prometheus and Grafana on the same shared Docker network
GRAFANA_ADMIN_PASSWORD='your-existing-or-new-local-password' \
  docker compose -f monitoring/compose.yaml up -d
```

If `localhost:3000` is already in use, use the same manual start command with an alternate host binding:

```sh
GRAFANA_PORT_BIND=127.0.0.1:3001 \
GRAFANA_ADMIN_PASSWORD='your-existing-or-new-local-password' \
  docker compose -f monitoring/compose.yaml up -d
```

`GRAFANA_PORT` is only needed when running `observability-smoke.sh`, because it tells that script where to call Grafana.

The two Compose projects share `dondok-network`; Prometheus scrapes the local application at `app:8080`. This PR provides smoke traffic only. Add and run a load generator in its later scoped PR.

## Dashboard evidence

`Point & Settlement Baseline` is provisioned from source and contains HTTP RPS/errors/p95/p99, process CPU/JVM total memory, JVM heap/GC, Hikari active/idle/pending/max, datasource connection evidence, and a slow-query evidence panel. No database exporter is installed. Inspect the local MySQL evidence without exporting its password from `.env`:

```sh
docker compose -f compose.yaml -f compose.observability.yaml exec mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e "SHOW VARIABLES; SHOW GLOBAL STATUS;"' | grep -E 'slow_query_log|long_query_time|Slow_queries'
```

Correlate this with Hikari pressure before adding a platform.

The smoke proves topology plus deterministic point/settlement domain metrics. It intentionally does not run a load generator, tune resources, or make a capacity claim. Record workload, data scale, timestamp, image/configuration, raw results, and dashboard/query evidence before comparing performance.

## Stop

```sh
docker compose -f compose.yaml -f compose.observability.yaml --profile observability down
docker compose -f monitoring/compose.yaml down
```

## PR2 local domain-metric smoke

`GRAFANA_ADMIN_PASSWORD=... ./scripts/observability-smoke.sh` starts the app only with
`local,observability,load-test`; ordinary Compose keeps `local,observability` exactly. The
load-test ingress and payment double do not exist outside that non-production profile. Its fixture
uses the `load-test` namespace only; do not use it to clear normal local bucket or database data.
PR3 load work must start from this passing metric/dashboard smoke baseline.

## PR3 reproducible k6 load scenarios

Run one phase at a time from a clean local fixture namespace. The runner starts the existing
observability topology, holds an exclusive local lock, verifies readiness and the Prometheus
target, exports the current host UID/GID for the k6 results mount, and resets only after the
foreground k6 process has exited:

Create the ignored local runner environment file once:

```sh
(
umask 077
cat > .env.load-test <<'EOF'
GRAFANA_ADMIN_USER=admin
GRAFANA_ADMIN_PASSWORD=your-existing-or-new-local-password
EOF
)
```

The runner loads that file automatically and fixes `SPRING_PROFILES_ACTIVE` to
`local,observability,load-test`:

```sh
./scripts/run-load-test.sh point-smoke
```

When invoking the k6 Compose service without the runner, export the real host IDs first so the
container can write `summary.json` to the bind-mounted results directory:

```sh
export HOST_UID="$(id -u)"
export HOST_GID="$(id -g)"
```

If Grafana uses a non-default local host port, pass both the published binding and the runner
address so the runner preserves the existing container configuration:

```sh
GRAFANA_PORT_BIND=127.0.0.1:3001 GRAFANA_PORT=3001 ./scripts/run-load-test.sh point-smoke
```

Available phases are `point-smoke`, `point-baseline`, `point-limit-10`, `point-limit-20`,
`point-limit-40`, `settlement-smoke`, `settlement-baseline`, `settlement-limit-250`,
`settlement-limit-500`, and `settlement-limit-1000`. Point baseline/limit phases keep the
same 20-account zero-history fixture pool and vary only offered request rate/VUs. Settlement
phases refuse to run when ordinary local final-batch candidates exist, prepare N successful
fixtures, wait 5 minutes plus one scrape interval, then trigger the batch once.

Each run writes an ignored bundle under `load-test/results/<run-id>/<phase>/` with the k6
summary, bounded manifest, Prometheus counter samples, exact settlement counter delta validation,
Grafana URL, and datasource query responses. Do not commit these bundles: they are local measurement evidence and intentionally
exclude credentials, JWTs, request bodies, and entity identifiers. A dashboard screenshot is an
optional manual attachment using the saved URL; no renderer is installed.
