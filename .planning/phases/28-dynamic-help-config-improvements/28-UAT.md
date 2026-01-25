---
status: diagnosed
phase: 28-dynamic-help-config-improvements
source: 28-01-SUMMARY.md, 28-02-SUMMARY.md
started: 2026-01-25T17:30:00Z
updated: 2026-01-25T17:45:00Z
---

## Current Test

[testing complete]

## Tests

### 1. Pre-start List Format
expected: Define pre_start as a YAML list in config.yaml. When container starts, commands execute joined by ` && `.
result: pass

### 2. Pre-start String Format (Backwards Compatible)
expected: Define pre_start as a string in config.yaml. Behavior unchanged from v2.4.0 - string passed directly.
result: pass

### 3. Build Without Gitleaks Flag
expected: Run `aishell build --without-gitleaks`. Build completes successfully and state.edn shows `:with-gitleaks false`.
result: pass

### 4. Dynamic Help - No State File
expected: Delete ~/.aishell/state.edn and run `aishell --help`. Help shows ALL harnesses (claude, opencode, codex, gemini, gitleaks) for discoverability.
result: pass

### 5. Dynamic Help - With Built State
expected: After building with specific harnesses, run `aishell --help`. Help shows only the installed harnesses plus gitleaks (always shown).
result: pass

### 6. Gitleaks Always Visible in Help
expected: Even if built with `--without-gitleaks`, `aishell --help` still shows the gitleaks command (may work via host installation).
result: issue
reported: "gitleaks command shown in help even when not installed in container, but there's no host mounting mechanism - command will fail. Should be consistent with harnesses: omit from help when absent from container"
severity: major

## Summary

total: 6
passed: 5
issues: 1
pending: 0
skipped: 0

## Gaps

- truth: "Gitleaks command should be hidden from help when not installed in container"
  status: failed
  reason: "User reported: gitleaks command shown in help even when not installed in container, but there's no host mounting mechanism - command will fail. Should be consistent with harnesses: omit from help when absent from container"
  severity: major
  test: 6
  root_cause: "installed-harnesses function (cli.clj:72-84) doesn't track gitleaks, and gitleaks print (line 104) is outside conditional block"
  artifacts:
    - path: "src/aishell/cli.clj"
      issue: "installed-harnesses missing gitleaks tracking; print-help gitleaks line unconditional"
  missing:
    - "Add gitleaks to installed-harnesses based on :with-gitleaks state"
    - "Wrap gitleaks print in (when (contains? installed \"gitleaks\"))"
