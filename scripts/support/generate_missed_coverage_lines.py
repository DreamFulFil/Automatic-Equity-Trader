#!/usr/bin/env python3
"""Generate missed_coverage_lines.csv from JaCoCo XML.

Output format matches the repo root missed_coverage_lines.csv:
  path,missed_lines
where path is relative to src/main/java.

We aggregate missed lines at the *source file* level (package + sourcefile),
combining misses across outer/inner classes.
"""

from __future__ import annotations

import csv
import sys
import xml.etree.ElementTree as ET
from collections import defaultdict
from pathlib import Path


def ranges_from_numbers(nums: list[int]) -> str:
    nums = sorted(set(nums))
    if not nums:
        return ""
    parts: list[str] = []
    start = prev = nums[0]
    for n in nums[1:]:
        if n == prev + 1:
            prev = n
            continue
        parts.append(f"{start}-{prev}" if start != prev else str(start))
        start = prev = n
    parts.append(f"{start}-{prev}" if start != prev else str(start))
    return ",".join(parts)


def main() -> int:
    jacoco_xml = Path("target/site/jacoco/jacoco.xml")
    if not jacoco_xml.exists():
        print("ERROR: target/site/jacoco/jacoco.xml not found; run mvn test first", file=sys.stderr)
        return 2

    tree = ET.parse(jacoco_xml)
    root = tree.getroot()

    missed_by_source: dict[str, set[int]] = defaultdict(set)

    for pkg in root.findall("package"):
        pkg_name = pkg.get("name") or ""
        for sourcefile in pkg.findall("sourcefile"):
            src_name = sourcefile.get("name")
            if not src_name:
                continue
            key = f"{pkg_name}/{src_name}" if pkg_name else src_name
            for line in sourcefile.findall("line"):
                nr_s = line.get("nr")
                mi_s = line.get("mi")
                if nr_s is None or mi_s is None:
                    continue
                nr = int(nr_s)
                mi = int(mi_s)
                # Track missed *instructions* only (aligns with pom.xml JaCoCo check: INSTRUCTION COVEREDRATIO)
                if mi > 0:
                    missed_by_source[key].add(nr)

    out_csv = Path("missed_coverage_lines.csv")
    rows = []
    for path, nums in missed_by_source.items():
        missed = ranges_from_numbers(list(nums))
        if missed:
            rows.append((path, missed, len(nums)))

    rows.sort(key=lambda r: (-r[2], r[0]))

    with out_csv.open("w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["path", "missed_lines"])
        for path, missed, _ in rows:
            w.writerow([path, missed])

    print(f"Wrote {out_csv} with {len(rows)} rows")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
