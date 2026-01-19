---
status: diagnosed
trigger: "Diagnose why version validation in aishell is not working - malicious version string '1.0.0; echo pwned' accepted"
created: 2026-01-19T00:00:00Z
updated: 2026-01-19T00:00:00Z
symptoms_prefilled: true
goal: find_root_cause_only
---

## Current Focus

hypothesis: CONFIRMED - do_build() case statement silently ignores unknown arguments
test: traced code path for ./aishell build --version "1.0.0; echo pwned"
expecting: found that unknown args fall through without error
next_action: report root cause

## Symptoms

expected: Invalid version strings with shell metacharacters should be rejected before any npm/curl operations
actual: Script accepted "1.0.0; echo pwned" and completed the build
errors: None - validation silently bypassed
reproduction: ./aishell build --version "1.0.0; echo pwned"
started: Unknown - possible implementation gap

## Eliminated

## Evidence

- timestamp: 2026-01-19T00:01:00Z
  checked: validate_version() function (lines 111-128)
  found: Function correctly blocks dangerous chars and validates semver format
  implication: The function itself is correct

- timestamp: 2026-01-19T00:02:00Z
  checked: do_build() argument parsing (lines 836-874)
  found: case statement handles --claude-version=* and --opencode-version=* but has no default case
  implication: Unknown arguments like "--version" are silently ignored

- timestamp: 2026-01-19T00:03:00Z
  checked: Code path for "./aishell build --version 1.0.0; echo pwned"
  found: parse_args() captures "build" as HARNESS_CMD, passes remaining args to do_build()
  implication: do_build() receives ["--version", "1.0.0; echo pwned"]

- timestamp: 2026-01-19T00:04:00Z
  checked: do_build() case patterns
  found: "--version" does not match any pattern; "1.0.0; echo pwned" does not match any pattern
  implication: Both arguments are silently ignored, build proceeds with defaults

- timestamp: 2026-01-19T00:05:00Z
  checked: do_update() (lines 1347-1383)
  found: Same issue - no default case to reject unknown arguments
  implication: Vulnerability exists in both build and update commands

## Resolution

root_cause: do_build() and do_update() case statements lack a default case to reject unknown arguments. The command "./aishell build --version X" silently ignores both "--version" and "X" because neither matches any defined pattern. The validate_version() function is never called because the expected syntax is "--claude-version=X.Y.Z" (equals sign), not "--version X" (space separated).
fix:
verification:
files_changed: []
