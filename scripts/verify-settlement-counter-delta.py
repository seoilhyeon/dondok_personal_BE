#!/usr/bin/env python3
import json
import sys
from decimal import Decimal
from pathlib import Path


def counter_value(path: str) -> Decimal:
    series = json.loads(Path(path).read_text())
    return sum((Decimal(item["value"][1]) for item in series), Decimal(0))


def exact_integer(value: Decimal, label: str) -> int:
    integer = int(value)
    if value != integer:
        raise ValueError(f"{label} counter is not an integer: {value}")
    return integer


def main() -> int:
    if len(sys.argv) != 7:
        print(
            "usage: verify-settlement-counter-delta.py EXPECTED "
            "BEFORE_SUCCESS AFTER_SUCCESS BEFORE_FAILURE AFTER_FAILURE OUTPUT",
            file=sys.stderr,
        )
        return 2

    expected = int(sys.argv[1])
    success_before = counter_value(sys.argv[2])
    success_after = counter_value(sys.argv[3])
    failure_before = counter_value(sys.argv[4])
    failure_after = counter_value(sys.argv[5])
    success_delta = exact_integer(success_after - success_before, "success")
    failure_delta = exact_integer(failure_after - failure_before, "failure")

    if success_delta != expected or failure_delta != 0:
        print(
            f"expected success delta {expected} and failure delta 0, "
            f"got success delta {success_delta} and failure delta {failure_delta}",
            file=sys.stderr,
        )
        return 1

    evidence = {
        "expectedSuccessDelta": expected,
        "successBefore": exact_integer(success_before, "success before"),
        "successAfter": exact_integer(success_after, "success after"),
        "successDelta": success_delta,
        "failureBefore": exact_integer(failure_before, "failure before"),
        "failureAfter": exact_integer(failure_after, "failure after"),
        "failureDelta": failure_delta,
    }
    Path(sys.argv[6]).write_text(json.dumps(evidence, indent=2) + "\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
