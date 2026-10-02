"""Writes test counts and coverage to the GitHub job summary. Standard library only.

Usage:
  ci_summary.py backend  <surefire-reports-dir> <jacoco.csv>
  ci_summary.py frontend <junit.xml> <coverage-summary.json>
"""
import csv
import glob
import json
import os
import sys
import xml.etree.ElementTree as ET


def junit_counts(paths):
    tests = failures = errors = skipped = 0
    seconds = 0.0
    for path in paths:
        root = ET.parse(path).getroot()
        suites = [root] if root.tag == "testsuite" else root.findall("testsuite")
        for s in suites:
            tests += int(s.get("tests", 0))
            failures += int(s.get("failures", 0))
            errors += int(s.get("errors", 0))
            skipped += int(s.get("skipped", 0))
            seconds += float(s.get("time", 0) or 0)
    return tests, failures, errors, skipped, seconds


def pct(covered, total):
    return f"{100 * covered / total:.1f}%" if total else "n/a"


def backend(reports_dir, jacoco_csv):
    tests, failures, errors, skipped, seconds = junit_counts(glob.glob(os.path.join(reports_dir, "TEST-*.xml")))
    rows = list(csv.DictReader(open(jacoco_csv)))
    cov = {}
    for kind in ("LINE", "BRANCH", "INSTRUCTION"):
        covered = sum(int(r[f"{kind}_COVERED"]) for r in rows)
        missed = sum(int(r[f"{kind}_MISSED"]) for r in rows)
        cov[kind] = pct(covered, covered + missed)
    return [
        "## Backend",
        "",
        "| Tests | Failed | Errors | Skipped | Test time | Line coverage | Branch coverage |",
        "|---|---|---|---|---|---|---|",
        f"| {tests} | {failures} | {errors} | {skipped} | {seconds:.0f} s | {cov['LINE']} | {cov['BRANCH']} |",
    ]


def frontend(junit_xml, coverage_json):
    tests, failures, errors, skipped, seconds = junit_counts([junit_xml])
    total = json.load(open(coverage_json))["total"]
    return [
        "## Frontend",
        "",
        "| Tests | Failed | Errors | Skipped | Test time | Line coverage | Branch coverage | Statements | Functions |",
        "|---|---|---|---|---|---|---|---|---|",
        f"| {tests} | {failures} | {errors} | {skipped} | {seconds:.1f} s | {total['lines']['pct']}% "
        f"| {total['branches']['pct']}% | {total['statements']['pct']}% | {total['functions']['pct']}% |",
    ]


if __name__ == "__main__":
    mode, *args = sys.argv[1:]
    lines = backend(*args) if mode == "backend" else frontend(*args)
    text = "\n".join(lines) + "\n"
    print(text)
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a") as f:
            f.write(text)
