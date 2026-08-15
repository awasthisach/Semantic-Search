#!/usr/bin/env python3
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

# Current verified baselines. These are deliberately non-regression gates;
# the long-term production target is higher and will be raised as real tests
# are added. The two reports are kept separate because they exercise the same
# production classes through different runtimes and must not be double-counted.
MIN_JVM_INSTRUCTION = 2.55
MIN_INSTRUMENTED_INSTRUCTION = 23.86


def instruction_totals(path: Path):
    root = ET.parse(path).getroot()
    counter = root.find("./counter[@type='INSTRUCTION']")
    if counter is None:
        raise RuntimeError(f"Report-level INSTRUCTION counter missing in {path}")
    return int(counter.attrib["covered"]), int(counter.attrib["missed"])


def percentage(covered: int, missed: int) -> float:
    total = covered + missed
    return covered * 100.0 / total if total else 0.0


def main():
    if len(sys.argv) != 3:
        raise SystemExit(
            "usage: verify_aggregate_coverage.py <jvm-report.xml> <instrumented-report.xml>"
        )

    jvm = instruction_totals(Path(sys.argv[1]))
    instrumented = instruction_totals(Path(sys.argv[2]))
    jvm_pct = percentage(*jvm)
    instrumented_pct = percentage(*instrumented)

    print(
        f"JVM/Robolectric instruction coverage: {jvm_pct:.2f}% "
        f"({jvm[0]}/{jvm[0] + jvm[1]})"
    )
    print(
        f"Instrumented Android instruction coverage: {instrumented_pct:.2f}% "
        f"({instrumented[0]}/{instrumented[0] + instrumented[1]})"
    )
    print(
        "Coverage baselines: "
        f"JVM >= {MIN_JVM_INSTRUCTION:.2f}%, "
        f"instrumented >= {MIN_INSTRUMENTED_INSTRUCTION:.2f}%"
    )

    failures = []
    if jvm_pct < MIN_JVM_INSTRUCTION:
        failures.append(
            f"JVM coverage regressed: {jvm_pct:.2f}% < {MIN_JVM_INSTRUCTION:.2f}%"
        )
    if instrumented_pct < MIN_INSTRUMENTED_INSTRUCTION:
        failures.append(
            "Instrumented coverage regressed: "
            f"{instrumented_pct:.2f}% < {MIN_INSTRUMENTED_INSTRUCTION:.2f}%"
        )

    if failures:
        raise SystemExit("Coverage regression gate failed: " + "; ".join(failures))


if __name__ == "__main__":
    main()
