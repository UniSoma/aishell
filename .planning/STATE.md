# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-01-20)

**Core value:** Run agentic AI harnesses in isolated, reproducible environments without polluting the host system.
**Current focus:** Phase 17 - Validation & Polish

## Current Position

Phase: 17 of 18 (Validation & Polish)
Plan: 0 of ? in current phase
Status: Ready to plan
Last activity: 2026-01-21 — Completed 16-05-PLAN.md (pass-through args fix)

Progress: [██████████████░░░░░░] 70%

**Milestone v2.0:** IN PROGRESS (Phases 13-18)

## What We're Building

v2.0 Babashka Rewrite:
- Rewrite CLI in Clojure Babashka
- Cross-platform: Linux, macOS
- Feature parity with v1.2
- Leverage Babashka built-ins (YAML, EDN, better data structures)
- Parallel development until production-ready

## Performance Metrics

**Velocity:**
- Total plans completed: 15 (v2.0)
- Average duration: 2.1 min
- Total execution time: 31.4 min

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| 13-foundation | 2 | 5 min | 2.5 min |
| 14-docker-integration | 5 | 7 min | 1.4 min |
| 15-build-command | 3 | 6 min | 2.0 min |
| 16-run-commands | 5 | 13.4 min | 2.7 min |

*Updated after each plan completion*

## Accumulated Context

### Decisions

See PROJECT.md Key Decisions table (23 validated decisions from v1.0-v1.2).

**v2.0 Decisions:**

| Decision | Context | Phase |
|----------|---------|-------|
| Runtime require to avoid circular deps | core.clj uses dynamic require for cli.clj | 13-01 |
| Dynamic classpath in entry script | aishell.clj loads src/ at runtime | 13-01 |
| Color detection: console + NO_COLOR + TERM | Standard conventions for TTY detection | 13-01 |
| Levenshtein max distance 3 for suggestions | Catches typos without false positives | 13-02 |
| XDG_STATE_HOME support for state dir | Following XDG Base Directory Specification | 13-02 |
| restrict: true for unknown options | Catches --badopt with clear error | 13-02 |
| try/catch around all Docker shell calls | Handle missing docker binary gracefully | 14-01 |
| Go template index syntax for labels | Handles dots in label names properly | 14-01 |
| format-size accepts string or numeric | Flexible input from shell output | 14-01 |
| Native Java MessageDigest over clj-commons/digest | Zero deps for SHA-256 hashing | 14-02 |
| CI env var + System/console for TTY detection | Standard pattern for spinner display | 14-02 |
| 12-char hash truncation | Matches bash sha256sum \| cut -c1-12 | 14-02 |
| Templates as multiline strings with escaped quotes | Clojure has no heredocs, proper escaping | 14-03 |
| if-not early return for cache hit | Clean pattern for cache result without nesting | 14-03 |
| Temp directory cleanup in finally block | Ensures cleanup even on build failure | 14-03 |
| Dual cache invalidation (base ID + extension hash) | Rebuilds only when dependencies change | 14-04 |
| Return nil for missing extension Dockerfile | Caller decides behavior, matches bash impl | 14-04 |
| Docker check before image check in CLI | Fail fast on Docker unavailable | 14-05 |
| State at ~/.aishell/state.edn (global) | Per CONTEXT.md, not per-project | 15-01 |
| read-state returns nil for missing file | Caller decides behavior, not error | 15-01 |
| No :coerce :string for optional value flags | babashka.cli returns boolean true for flags without values | 15-02 |
| parse-with-flag handles both boolean and string | Check (true? value) before string checks | 15-02 |
| (str value) in parse-with-flag :else clause | Ensures validate-version receives string, not Double | 15-03 |
| apply to spread vectors to p/process and p/shell | babashka.process expects command parts as args, not vector | 15-03 |
| $HOME env var over fs/home for home directory | fs/home returns '?' in network login environments | 15-UAT |
| build-time as ISO-8601 string, not Instant | EDN can't read #object[java.time.Instant...] | 15-UAT |
| YAML config.yaml replaces bash run.conf | Better structure, native Babashka support via clj-yaml | 16-01 |
| Warn don't fail on unknown config keys | Forward compatibility for future config keys | 16-01 |
| cond-> threading for optional docker args | Cleaner than nested ifs for conditional arg inclusion | 16-02 |
| PRE_START as env var, entrypoint executes | Passed via -e, Phase 14 entrypoint runs in background | 16-02 |
| p/exec for process replacement | Proper Unix exec semantics, no zombie processes | 16-03 |
| Claude always --dangerously-skip-permissions | Container IS the sandbox, prompts redundant | 16-03 |
| Pass-through args for harness commands | No :restrict true for claude/opencode, allow arbitrary args | 16-03 |
| Dual format support for env config | map? check to detect format, normalize to [k v] pairs | 16-04 |
| :restrict false per-command override | Overrides global :restrict true for pass-through commands | 16-04 |
| Pre-dispatch command interception | Handle pass-through before cli/dispatch | 16-05 |

### Pending Todos

None.

### Roadmap Evolution

v1.0: Phases 1-8 (SHIPPED 2026-01-18)
v1.1: Phases 9-10 (SHIPPED 2026-01-19)
v1.2: Phases 11-12 (SHIPPED 2026-01-19)
v2.0: Phases 13-18 (IN PROGRESS)

### Blockers/Concerns

None.

## Session Continuity

Last session: 2026-01-21
Stopped at: Completed 16-05-PLAN.md - Phase 16 gap closure complete
Resume file: None
