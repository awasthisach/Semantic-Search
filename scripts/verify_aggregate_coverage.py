#!/usr/bin/env python3
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

MINIMUM = 80.0


def instruction_totals(path: Path):
    root = ET.parse(path).getroot()
    counter = root.find("./counter[@type='INSTRUCTION']")
    if counter is None:
        raise RuntimeError(f"Report-level INSTRUCTION counter missing in {path}")
    return int(counter.attrib["covered"]), int(counter.attrib["missed"])


def main():
    if len(sys.argv) != 3:
        raise SystemExit("usage: verify_aggregate_coverage.py <jvm-report.xml> <instrumented-report.xml>")
    reports = [Path(sys.argv[1]), Path(sys.argv[2])]
    totals = [instruction_totals(path) for path in reports]
    covered = sum(item[0] for item in totals)
    missed = sum(item[1] for item in totals)
    total = covered + missed
    percent = covered * 100.0 / total if total else 0.0
    print(f"Aggregate JVM + instrumented instruction coverage: {percent:.2f}% ({covered}/{total})")
    if percent < MINIMUM:
        raise SystemExit(f"Coverage gate failed: {percent:.2f}% < {MINIMUM:.2f}%")


if __name__ == "__main__":
    main()
