---
phase: 16-run-commands
plan: 01
subsystem: config
tags: [yaml, config, clj-yaml]

dependency-graph:
  requires: [13-foundation]
  provides: [config-loading, yaml-parsing, validation]
  affects: [16-02-args, 16-03-run-command]

tech-stack:
  added: []
  patterns:
    - project-first-global-fallback config lookup
    - yaml/parse-string for YAML parsing
    - validation with warnings for unknown keys

key-files:
  created:
    - src/aishell/config.clj
  modified: []

decisions:
  - id: config-yaml-over-run-conf
    context: "Config format choice"
    decision: "YAML config.yaml replaces bash run.conf"
    rationale: "Better structure, native Babashka support via clj-yaml"

metrics:
  duration: 1.4min
  completed: 2026-01-20
---

# Phase 16 Plan 01: Config Module Summary

**One-liner:** YAML config loading with project-first/global-fallback and unknown key warnings via clj-yaml.

## What Was Built

Created `src/aishell/config.clj` module that handles per-project configuration loading:

### Core Functions

| Function | Purpose |
|----------|---------|
| `load-config` | Load config.yaml with project-first, global-fallback strategy |
| `validate-config` | Warn on unknown keys, return config unchanged |
| `config-source` | Return :project, :global, or nil to indicate source |
| `project-config-path` | Path to PROJECT_DIR/.aishell/config.yaml |
| `global-config-path` | Path to ~/.aishell/config.yaml |

### Config Keys Supported

```clojure
#{:mounts :env :ports :docker_args :pre_start}
```

### Lookup Strategy

1. Check `PROJECT_DIR/.aishell/config.yaml` - use if exists
2. Fall back to `~/.aishell/config.yaml` if project config missing
3. Return `nil` if neither exists

### Error Handling

- Invalid YAML syntax: immediate error exit with clear message
- Unknown config keys: warning to stderr, continues execution

## Key Decisions

| Decision | Rationale |
|----------|-----------|
| YAML over run.conf | Better structure, native clj-yaml support |
| Warn don't fail on unknown keys | Forward compatibility for future config keys |
| nil return for no config | Caller decides behavior, matches pattern from other modules |

## Verification Results

All tests passed:
- Module loads without error
- Returns nil when no config exists
- Project config takes precedence over global
- Falls back to global when project missing
- config-source correctly identifies source
- Unknown keys trigger warning
- Invalid YAML causes error exit

## Deviations from Plan

None - plan executed exactly as written.

## Commits

| Hash | Message |
|------|---------|
| b272caf | feat(16-01): add config module for YAML loading |

## Files Changed

```
src/aishell/config.clj (created) - 78 lines
```

## Next Plan Readiness

Ready for 16-02: Run argument parsing. The config module provides:
- `load-config` for loading project/global config
- `config-source` for verbose output
- All config keys ready for docker run argument construction
