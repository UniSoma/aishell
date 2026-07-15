---
id: aix-01kr1qr59100
title: Wire aishell check --json
status: open
type: feature
priority: 2
mode: afk
created: '2026-05-07T17:28:44.064243075Z'
updated: '2026-07-15T02:44:33.217991534Z'
tags:
- ready-for-agent
- json
acceptance:
- title: aishell check --json emits one compact JSON object with keys passed, summary, checks, sensitiveFiles
  done: false
- title: Each entry in checks has exactly the keys section, status (ok|warn|fail), label; label has no ANSI color codes
  done: false
- title: summary counts {ok, warn, fail} match the entries in checks
  done: false
- title: passed is true iff summary.fail is 0; exit code 1 iff passed is false (warn never causes exit 1)
  done: false
- title: sensitiveFiles.findings is a JSON array of {path, kind, severity}; total / high / medium / low counts match the array contents
  done: false
- title: When detection is disabled or no findings exist, sensitiveFiles.findings is [] and counts are 0
  done: false
- title: Existing human aishell check output is unchanged (same banners, glyphs, sections, exit code)
  done: false
- title: check.clj is refactored so each per-check helper returns data; the human path consumes the same data via print-status (no logic duplication)
  done: false
- title: Pure-fn clojure.test coverage for build-check-data, sensitive-file finding serialization, and the passed/exit-code mapping
  done: false
- title: bb test passes; clojure -M:clj-kondo --lint src test passes
  done: false
deps:
- aix-01kr1qp6deb1
---

## Description

## What to build

Add JSON output for aishell check. Heaviest refactor of the four — check.clj currently prints status lines as it runs; we move it to a collect-then-render model so both human and JSON paths share the same data.

Behavior:
- aishell check --json emits a single compact JSON object on stdout with this shape:
  {
    passed: true|false,
    summary: {ok: <int>, warn: <int>, fail: <int>},
    checks: [
      {section: "Docker", status: "ok"|"warn"|"fail", label: "Docker is available"},
      {section: "Setup", status: "warn", label: "Image may be stale. Run aishell update to rebuild"},
      ...
    ],
    sensitiveFiles: {
      total: <int>,
      high: <int>, medium: <int>, low: <int>,
      findings: [{path, kind, severity}, ...]
    }
  }
- passed is true iff summary.fail == 0 (warn does not fail). Exit code preserved: exit 1 when passed is false, else exit 0. This matches the existing critical-fail semantics; warn-level results never trigger a non-zero exit.
- label strings are stripped of ANSI color (force-disabled in JSON mode anyway, so this is a natural side effect).
- sensitiveFiles.findings carries the structured per-file detail that today is printed as additional indented lines below the summary status line. Each finding has {path, kind, severity} extracted from what aishell.detection.formatters/format-finding-line already has access to. When detection is disabled or there are no findings, findings is [] and total/high/medium/low are 0.
- Gitleaks-freshness, sensitive-file detection, mount checks, security checks, harness-installed checks, dockerfile-staleness, base-image-custom, extension freshness, docker availability, setup-state-found — all the existing checks become entries in the checks array with the same status enum and label text the human path uses.
- Human path (aishell check without --json) is unchanged: same banners, separators, ✓/✗/! glyphs, exit code.

Mechanics:
- Refactor each check-* helper in src/aishell/check.clj to return a {section, status, label} map (or a vector of maps for multi-result helpers like check-harnesses) instead of calling print-status directly. Keep the print-status helper for the human renderer to consume the maps.
- Add a build-check-data function that runs all the checks in the existing order and returns {checks, sensitiveFiles, passed, summary}. The JSON path emits this; the human path iterates over checks and calls print-status, then prints the sensitive-files detail block, then prints the final summary line.
- Exit code lives in run-check (or a renamed pure caller) — derived from build-check-data's passed field.
- For sensitive files, expose per-finding {path, kind, severity} in the data; the human path keeps using format-finding-line.

## Blocked by

- aix-01kr1qp6deb1 (Wire --json infrastructure and aishell ps --json)
