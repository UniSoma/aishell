---
status: diagnosed
phase: 23-context-config
source: 23-01-SUMMARY.md, 23-02-SUMMARY.md
started: 2026-01-24T02:10:00Z
updated: 2026-01-24T02:20:00Z
---

## Current Test

[testing complete]

## Tests

### 1. High-severity gitignore risk annotation
expected: When a high-severity file exists and is NOT in .gitignore, warning shows "(risk: may be committed)" suffix
result: pass

### 2. High-severity file protected by gitignore
expected: When a high-severity file exists and IS in .gitignore, no "(risk: may be committed)" annotation appears
result: pass

### 3. Medium-severity no gitignore annotation
expected: Medium-severity findings (e.g., .env files) never show gitignore annotations regardless of .gitignore status
result: pass

### 4. Custom pattern detection
expected: Adding detection.custom_patterns in .aishell/config.yaml with a pattern and severity causes matching files to be detected with that severity
result: issue
reported: "Severity is being ignored, showing medium irrespective of what I put on the yaml. Example: detection.custom_patterns '*.frubas': high shows MEDIUM instead of HIGH"
severity: major

### 5. Allowlist suppresses findings
expected: Adding a file path to detection.allowlist in config.yaml hides that file from warnings (not shown as "allowed", just gone)
result: issue
reported: "Allowlist silences ALL warnings, not just the specified file. With allowlist for '.env' and custom_patterns for '*.frubas', the frubas warnings are also silenced. Removing allowlist makes frubas warnings appear."
severity: major

### 6. Detection kill switch
expected: Setting detection.enabled: false in config.yaml suppresses ALL filename-based detection warnings
result: pass

## Summary

total: 6
passed: 4
issues: 2
pending: 0
skipped: 0

## Gaps

- truth: "Custom pattern severity from config.yaml is applied to detected files"
  status: failed
  reason: "User reported: Severity is being ignored, showing medium irrespective of what I put on the yaml. Example: detection.custom_patterns '*.frubas': high shows MEDIUM instead of HIGH"
  severity: major
  test: 4
  root_cause: "detect-custom-patterns assumes opts is always a map, but shorthand YAML syntax '*.frubas': high passes string not map. Line 281 calls (:severity opts) on string which returns nil, defaulting to medium."
  artifacts:
    - path: "src/aishell/detection/patterns.clj"
      issue: "Line 281: severity extraction doesn't handle shorthand syntax where opts is a string/keyword"
  missing:
    - "Check if opts is a map before extracting :severity; if string/keyword, use it directly as severity"

- truth: "Allowlist only suppresses the specific files listed, not all warnings"
  status: failed
  reason: "User reported: Allowlist silences ALL warnings, not just the specified file. With allowlist for '.env' and custom_patterns for '*.frubas', the frubas warnings are also silenced. Removing allowlist makes frubas warnings appear."
  severity: major
  test: 5
  root_cause: "file-allowlisted? doesn't guard against nil file paths. Summary findings from group-findings have :path nil. When checked against allowlist, nil path causes incorrect match, filtering out all summaries."
  artifacts:
    - path: "src/aishell/detection/core.clj"
      issue: "Line 35: file-allowlisted? doesn't guard against nil file paths from summary findings"
  missing:
    - "Add early return in file-allowlisted? when file-path is nil (summary findings should never be allowlisted)"
