#!/usr/bin/env python3
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

# Coverage gates are intentionally expressed at the same precision that CI
# publishes (two decimal places). Comparing the unrounded floating-point value
# against a two-decimal baseline can reject a report that is still exactly at
# the published baseline because the denominator changed by one line.
MIN_JVM_LINE = 2.55
MIN_INSTRUMENTED_LINE = 23.86


def line_totals(path: Path):
    root = ET.parse(path).getroot()
    counter = root.find("./counter[@type='LINE']")
    if counter is None:
        raise RuntimeError(f"Report-level LINE counter missing in {path}")
    return int(counter.attrib["covered"]), int(counter.attrib["missed"])


def percentage(covered: int, missed: int) -> float:
    total = covered + missed
    return covered * 100.0 / total if total else 0.0


def published_percentage(value: float) -> float:
    """Return the exact two-decimal metric published by the CI summary."""
    return float(f"{value:.2f}")


def main():
    if len(sys.argv) != 3:
        raise SystemExit(
            "usage: verify_aggregate_coverage.py <jvm-report.xml> <instrumented-report.xml>"
        )

    jvm = line_totals(Path(sys.argv[1]))
    instrumented = line_totals(Path(sys.argv[2]))
    jvm_pct = percentage(*jvm)
    instrumented_pct = percentage(*instrumented)
    jvm_gate_pct = published_percentage(jvm_pct)
    instrumented_gate_pct = published_percentage(instrumented_pct)

    print(
        f"JVM/Robolectric line coverage: {jvm_pct:.2f}% "
        f"({jvm[0]}/{jvm[0] + jvm[1]})"
    )
    print(
        f"Instrumented Android line coverage: {instrumented_pct:.2f}% "
        f"({instrumented[0]}/{instrumented[0] + instrumented[1]})"
    )
    print(
        "Coverage baselines: "
        f"JVM >= {MIN_JVM_LINE:.2f}%, "
        f"instrumented >= {MIN_INSTRUMENTED_LINE:.2f}%"
    )

    failures = []
    if jvm_gate_pct < MIN_JVM_LINE:
        failures.append(
            f"JVM line coverage regressed: {jvm_pct:.2f}% < {MIN_JVM_LINE:.2f}%"
        )
    if instrumented_gate_pct < MIN_INSTRUMENTED_LINE:
        failures.append(
            "Instrumented line coverage regressed: "
            f"{instrumented_pct:.2f}% < {MIN_INSTRUMENTED_LINE:.2f}%"
        )

    if failures:
        raise SystemExit("Coverage regression gate failed: " + "; ".join(failures))


if __name__ == "__main__":
    main()
