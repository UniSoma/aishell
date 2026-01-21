---
status: diagnosed
phase: 17-validation-polish
source: 17-01-SUMMARY.md, 17-02-SUMMARY.md, 17-03-SUMMARY.md
started: 2026-01-21T02:00:00Z
updated: 2026-01-21T03:00:00Z
gap_closure: true
---

## Current Test

[testing complete]

## Tests

### 1. Vector docker_args security warning (fix verification)
expected: Adding `docker_args: ["--privileged"]` to config.yaml and running `./aishell` shows a security warning about privileged mode, but execution continues (no crash).
result: issue
reported: "Warning displays correctly but still crashes with: clojure.lang.LazySeq cannot be cast to java.lang.CharSequence"
severity: blocker

### 2. Version change triggers cache invalidation (fix verification)
expected: After building with `./aishell build --with-claude=1.0.0`, changing to `./aishell build --with-claude=1.0.1` should detect the version change and rebuild (not say "base image is up to date").
result: pass

## Summary

total: 2
passed: 1
issues: 1
pending: 0
skipped: 0

## Gaps

- truth: "Security warning displays for vector docker_args and execution continues without crash"
  status: failed
  reason: "User reported: Warning displays correctly but still crashes with: clojure.lang.LazySeq cannot be cast to java.lang.CharSequence"
  severity: blocker
  test: 1
  root_cause: "tokenize-docker-args in run.clj assumes docker_args is a string, but YAML parser returns a vector when config uses array syntax, causing str/trim to crash"
  artifacts:
    - path: "src/aishell/docker/run.clj"
      issue: "tokenize-docker-args (lines 121-126) expects string, receives vector"
  missing:
    - "Handle both string and vector formats in tokenize-docker-args using sequential? check"
  debug_session: ""
