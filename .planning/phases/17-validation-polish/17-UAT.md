---
status: diagnosed
phase: 17-validation-polish
source: 17-01-SUMMARY.md, 17-02-SUMMARY.md
started: 2026-01-21T02:00:00Z
updated: 2026-01-21T02:20:00Z
---

## Current Test

[testing complete]

## Tests

### 1. Build with --force flag
expected: Running `./aishell build --force` rebuilds the image without using Docker cache. Build output shows fresh layer builds.
result: pass

### 2. Update command preserves configuration
expected: After `./aishell build --with-claude=latest`, running `./aishell update` rebuilds with the same configuration (--with-claude=latest) preserved.
result: pass

### 3. Invalid version string rejected
expected: Running `./aishell build --with-claude="1.0; rm -rf /"` shows clear error rejecting the invalid version.
result: pass

### 4. Dangerous docker_args warning
expected: Adding `docker_args: ["--privileged"]` to config.yaml and running `./aishell` shows a security warning about privileged mode, but execution continues.
result: issue
reported: "./aishell.clj Error: Unexpected error: clojure.lang.LazySeq cannot be cast to java.lang.CharSequence. Config: env: {FOO: frubas}, docker_args: [\"--privileged\"]"
severity: blocker

### 5. Stale image warning
expected: After modifying embedded Dockerfile and running `./aishell`, a warning shows the image may be stale and suggests `./aishell update`.
result: pass

### 6. Version change triggers rebuild (discovered)
expected: Changing --with-claude version (e.g., 1.0.0 to 1.0.1) should indicate image needs rebuild, not report "base image is up to date".
result: issue
reported: "Changing the claude code version, I still get a message saying the base image is up to date. Version change should invalidate cache."
severity: major

## Summary

total: 6
passed: 4
issues: 2
pending: 0
skipped: 0

## Gaps

- truth: "Security warning displays for dangerous docker_args and execution continues"
  status: failed
  reason: "User reported: ./aishell.clj Error: Unexpected error: clojure.lang.LazySeq cannot be cast to java.lang.CharSequence. Config: env: {FOO: frubas}, docker_args: [\"--privileged\"]"
  severity: blocker
  test: 4
  root_cause: "check-dangerous-args in validation.clj expects a string but receives a vector. YAML parses docker_args: [\"--privileged\"] as a vector. str/blank? and str/includes? on lines 23 and 28 require strings, not vectors."
  artifacts:
    - path: "src/aishell/validation.clj"
      issue: "check-dangerous-args uses str/blank? and str/includes? which require strings, but receives vector from config"
  missing:
    - "Join vector to string before pattern matching: (str/join \" \" docker-args)"
  debug_session: ""

- truth: "Version change in build flags triggers rebuild detection"
  status: failed
  reason: "User reported: Changing the claude code version, I still get a message saying the base image is up to date. Version change should invalidate cache."
  severity: major
  test: 6
  root_cause: "Cache check only compares dockerfile hash, not build flags (--with-claude version). State stores version but cache-valid? doesn't compare stored vs requested versions."
  artifacts:
    - path: "src/aishell/docker/build.clj"
      issue: "cache-valid? only checks dockerfile hash, ignores version changes in build flags"
    - path: "src/aishell/state.clj"
      issue: "State stores :with-claude version but it's not used for cache invalidation"
  missing:
    - "Compare requested --with-claude/--with-opencode versions against stored state"
    - "Invalidate cache when versions differ"
  debug_session: ""
