#!/usr/bin/env python3
"""Fail closed unless three Point load-test bundles are comparable and healthy."""

import json
import math
import re
import stat
import subprocess
import sys
from pathlib import Path


PHASE = "point-limit-40"
REQUIRED_PANELS = {
    "HTTP RPS",
    "HTTP errors (4xx/5xx)",
    "HTTP p95",
    "HTTP p99",
    "Process CPU",
    "JVM total memory used",
    "JVM heap used / max",
    "JVM GC pause activity",
    "Hikari active / idle",
    "Hikari pending / max",
    "Datasource connection evidence",
    "Point charge success/failure rate",
    "Point charge p95",
}
DATASOURCE_PANEL = "Datasource connection evidence"
HASH = re.compile(r"[0-9a-f]{64}\Z")
MAX_COUNTER = (1 << 53) - 1
MANIFEST_VALUES = {
    "schemaVersion": 1,
    "phase": PHASE,
    "scenario": "point-charge.js",
    "rate": "40",
    "duration": "6m",
    "preAllocatedVUs": "80",
    "maxVUs": "200",
    "accounts": "20",
    "settlements": None,
    "profiles": ["local", "observability", "load-test"],
    "appCpuLimit": "2.0",
    "appMemoryLimit": "2G",
    "scrapeInterval": "15s",
    "gitDirty": False,
    "k6ImageRef": "grafana/k6:0.54.0",
    "k6ExitStatus": 0,
    "resetExitStatus": 0,
    "selectorWindowIsolated": True,
    "status": "passed",
    "failureStages": [],
}
IDENTITY_FIELDS = ("runId", "gitSha", "appImageId", "k6ImageId", "composeConfigSha256", "os", "arch")
COMPARABLE_FIELDS = (
    "gitSha",
    "appImageId",
    "k6ImageRef",
    "k6ImageId",
    "composeConfigSha256",
    "os",
    "arch",
    "profiles",
    "appCpuLimit",
    "appMemoryLimit",
    "scrapeInterval",
    "phase",
    "scenario",
    "rate",
    "duration",
    "preAllocatedVUs",
    "maxVUs",
    "accounts",
    "settlements",
)


class Detector(Exception):
    def __init__(self, scope, name):
        self.scope = scope
        self.name = name


def fail_run(index, name):
    raise Detector(f"run {index}", name)


def require_regular_json(path, index, name):
    try:
        mode = path.lstat().st_mode
    except OSError:
        fail_run(index, name)
    if not stat.S_ISREG(mode) or path.suffix != ".json":
        fail_run(index, name)
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError, RecursionError):
        fail_run(index, name)
    return value


def number(value):
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        return False
    try:
        return math.isfinite(value)
    except OverflowError:
        return False


def nonnegative(value):
    return number(value) and value >= 0


def counter(value):
    return isinstance(value, int) and not isinstance(value, bool) and 0 <= value <= MAX_COUNTER


def value_at(mapping, *keys):
    current = mapping
    for key in keys:
        if not isinstance(current, dict) or key not in current:
            return None
        current = current[key]
    return current


def verify_artifact(directory, index):
    verifier = Path(__file__).resolve().with_name("verify-load-test-artifact-safety.py")
    try:
        completed = subprocess.run(
            [sys.executable, str(verifier), str(directory)],
            capture_output=True,
            check=False,
        )
    except OSError:
        fail_run(index, "artifact-safety")
    if completed.returncode != 0:
        fail_run(index, "artifact-safety")


def validate_manifest(manifest, index):
    if not isinstance(manifest, dict):
        fail_run(index, "manifest-shape")
    for key, expected in MANIFEST_VALUES.items():
        if manifest.get(key) != expected:
            fail_run(index, "manifest-contract")
    if (
        isinstance(manifest["schemaVersion"], bool)
        or not isinstance(manifest["schemaVersion"], int)
        or not isinstance(manifest["gitDirty"], bool)
        or not isinstance(manifest["selectorWindowIsolated"], bool)
        or any(
            isinstance(manifest[key], bool) or not isinstance(manifest[key], int)
            for key in ("k6ExitStatus", "resetExitStatus")
        )
    ):
        fail_run(index, "manifest-contract")
    for key in IDENTITY_FIELDS:
        if not isinstance(manifest.get(key), str) or not manifest[key].strip():
            fail_run(index, "manifest-identity")
    from_ms, to_ms = manifest.get("fromMs"), manifest.get("toMs")
    if isinstance(from_ms, bool) or isinstance(to_ms, bool) or not isinstance(from_ms, int) or not isinstance(to_ms, int) or to_ms - from_ms != 60000:
        fail_run(index, "manifest-window")


def metric(summary, key, index):
    values = value_at(summary, "metrics", key, "values")
    if not isinstance(values, dict):
        fail_run(index, "summary-metric")
    return values


def validate_summary(summary, index):
    if not isinstance(summary, dict) or "setup_data" in summary:
        fail_run(index, "summary-shape")
    if any(not isinstance(summary.get(key), dict) for key in ("metrics", "options", "root_group", "state")):
        fail_run(index, "summary-shape")
    requests = metric(summary, "http_reqs{target:point_charge}", index).get("count")
    iterations = metric(summary, "iterations", index).get("count")
    control = metric(summary, "http_reqs{target:control}", index).get("count")
    duration = metric(summary, "http_req_duration{target:point_charge}", index)
    failures = metric(summary, "http_req_failed{target:point_charge}", index)
    checks = metric(summary, "checks{target:point_charge}", index)
    dropped = metric(summary, "dropped_iterations{scenario:point_charge}", index).get("count")
    if not all(counter(value) for value in (requests, iterations, control, failures.get("passes"), checks.get("fails"), dropped)) or not all(
        nonnegative(value)
        for value in (duration.get("p(95)"), duration.get("max"), failures.get("rate"), checks.get("rate"))
    ):
        fail_run(index, "summary-metric")
    if requests <= 0 or requests != iterations or control != 1:
        fail_run(index, "summary-count")
    if failures["rate"] != 0 or failures["passes"] != 0:
        fail_run(index, "summary-http-failure")
    if checks["rate"] != 1 or checks["fails"] != 0:
        fail_run(index, "summary-check")
    if dropped != 0:
        fail_run(index, "summary-dropped")
    return {"requests": requests, "p95": duration["p(95)"], "max": duration["max"], "httpFailures": failures["passes"], "checkFailures": checks["fails"], "dropped": dropped}


def contained_json_path(directory, raw, index):
    if not isinstance(raw, str) or not raw or "\\" in raw:
        fail_run(index, "grafana-path")
    relative = Path(raw)
    if relative.is_absolute() or ".." in relative.parts or relative.suffix != ".json":
        fail_run(index, "grafana-path")
    path = directory / relative
    try:
        resolved = path.resolve(strict=True)
        root = directory.resolve(strict=True)
    except (OSError, ValueError):
        fail_run(index, "grafana-path")
    try:
        regular = stat.S_ISREG(path.lstat().st_mode)
    except (OSError, ValueError):
        fail_run(index, "grafana-path")
    if root not in resolved.parents or not regular:
        fail_run(index, "grafana-path")
    return path


def has_numeric_data(response, ref_id):
    frame = value_at(response, "results", ref_id)
    if not isinstance(frame, dict) or not isinstance(frame.get("frames"), list):
        return False
    for grafana_frame in frame["frames"]:
        fields = value_at(grafana_frame, "schema", "fields")
        columns = value_at(grafana_frame, "data", "values")
        if not isinstance(fields, list) or not isinstance(columns, list):
            continue
        for field, column in zip(fields, columns):
            if isinstance(field, dict) and field.get("type") == "number" and isinstance(column, list) and any(number(value) for value in column):
                return True
    return False


def validate_evidence(directory, index):
    evidence = require_regular_json(directory / "grafana-evidence.json", index, "grafana-evidence")
    if not isinstance(evidence, list):
        fail_run(index, "grafana-evidence")
    entries = {}
    for entry in evidence:
        if not isinstance(entry, dict):
            fail_run(index, "grafana-evidence")
        panel, ref_id, query = entry.get("panel"), entry.get("refId"), entry.get("querySha256")
        if not isinstance(panel, str) or not isinstance(ref_id, str) or not isinstance(query, str) or not HASH.fullmatch(query):
            fail_run(index, "grafana-evidence")
        key = (panel, ref_id)
        if key in entries:
            fail_run(index, "grafana-evidence")
        path = contained_json_path(directory, entry.get("path"), index)
        if panel in REQUIRED_PANELS:
            if panel != DATASOURCE_PANEL and (entry.get("authoritativeForComparison") is not True or entry.get("selectorWindowIsolated") is not True or entry.get("empty") is not False):
                fail_run(index, "grafana-evidence")
            if not has_numeric_data(require_regular_json(path, index, "grafana-response"), ref_id):
                fail_run(index, "grafana-response")
        entries[key] = query
    if not REQUIRED_PANELS.issubset({panel for panel, _ in entries}):
        fail_run(index, "grafana-evidence")
    return {(panel, ref_id, query) for (panel, ref_id), query in entries.items()}


def compare(runs):
    reference = runs[0]["manifest"]
    for index, run in enumerate(runs[1:], start=2):
        for field in COMPARABLE_FIELDS:
            if run["manifest"][field] != reference[field]:
                raise Detector("comparison", "metadata")
        if run["evidence"] != runs[0]["evidence"]:
            raise Detector("comparison", "grafana-evidence")
    run_ids = [run["manifest"]["runId"] for run in runs]
    if len(set(run_ids)) != len(run_ids):
        raise Detector("comparison", "duplicate-run-id")


def main(arguments):
    if len(arguments) != 3:
        raise Detector("comparison", "arguments")
    runs = []
    for index, argument in enumerate(arguments, start=1):
        directory = Path(argument)
        verify_artifact(directory, index)
        manifest = require_regular_json(directory / "manifest.json", index, "manifest")
        validate_manifest(manifest, index)
        summary = require_regular_json(directory / "summary.json", index, "summary")
        metrics = validate_summary(summary, index)
        evidence = validate_evidence(directory, index)
        runs.append({"manifest": manifest, "metrics": metrics, "evidence": evidence})
    compare(runs)
    metrics = [run["metrics"] for run in runs]
    p95s = sorted(metric["p95"] for metric in metrics)
    aggregate = {
        "phase": PHASE,
        "runs": 3,
        "totalPointRequests": sum(metric["requests"] for metric in metrics),
        "totalHttpFailures": sum(metric["httpFailures"] for metric in metrics),
        "totalCheckFailures": sum(metric["checkFailures"] for metric in metrics),
        "totalDroppedIterations": sum(metric["dropped"] for metric in metrics),
        "p95MedianMs": p95s[1],
        "p95MaxMs": p95s[-1],
        "maxDurationMs": max(metric["max"] for metric in metrics),
        "observabilityPanelsVerified": len(REQUIRED_PANELS),
    }
    print(json.dumps(aggregate, separators=(",", ":"), allow_nan=False))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main(sys.argv[1:]))
    except Detector as error:
        print(f"{error.scope}: {error.name}", file=sys.stderr)
        raise SystemExit(1)
