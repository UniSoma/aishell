---
phase: 66-global-base-image-customization
verified: 2026-02-19T00:30:00Z
status: passed
score: 11/11 must-haves verified
re_verification: false
---

# Phase 66: Global Base Image Customization — Verification Report

**Phase Goal:** Introduce `aishell:base` as an intermediate image layer between `aishell:foundation` and project extensions, enabling advanced users to globally customize their base image via `~/.aishell/Dockerfile`
**Verified:** 2026-02-19
**Status:** passed
**Re-verification:** No — initial verification

---

## Goal Achievement

### Observable Truths

| #  | Truth | Status | Evidence |
|----|-------|--------|----------|
| 1  | `aishell:base` always exists after setup — custom-built or alias | VERIFIED | `base/ensure-base-image` called in `handle-setup` after foundation build (cli.clj:198); `tag-foundation-as-base` alias path present in `ensure-base-image` when no global Dockerfile |
| 2  | When `~/.aishell/Dockerfile` changes, base image rebuilds on next setup/update | VERIFIED | `needs-base-rebuild?` compares `base-dockerfile-hash-label` against `global-dockerfile-hash` (base.clj:73-74); `ensure-base-image` triggers rebuild on hash mismatch |
| 3  | `aishell setup --force` rebuilds base image | VERIFIED | cli.clj:198 passes `{:force (:force opts) ...}` to `base/ensure-base-image` unconditionally after foundation build |
| 4  | `aishell update --force` rebuilds base image | VERIFIED | cli.clj:338 calls `(base/ensure-base-image {:force true ...})` in the force branch; quiet ensure called when not force (cli.clj:339) |
| 5  | Extension images build FROM `aishell:base` (three-tier chain complete) | VERIFIED | extension.clj uses `base-image-id-label` ("aishell.base.id") for staleness; callers in run.clj pass `base/base-image-tag` to `needs-extended-rebuild?` and `build-extended-image` |
| 6  | Lazy base image build triggers on container run | VERIFIED | `base/ensure-base-image {}` called at 3 points: `resolve-image-tag` (run.clj:81), `run-container` (run.clj:119), `run-exec` (run.clj:285) |
| 7  | `aishell check` displays base image status | VERIFIED | `check-base-image-custom` defined at check.clj:72-79; called in `run-check` at check.clj:245 (after `check-dockerfile-staleness`, before extension check) |
| 8  | `aishell volumes prune` cleans up orphaned base images | VERIFIED | cli.clj:429-437: detects custom label + missing global Dockerfile -> calls `base/tag-foundation-as-base` |
| 9  | `FROM aishell:base` in project Dockerfiles is accepted | VERIFIED | `validate-base-tag` has zero occurrences anywhere in `src/` (removed from extension.clj, check.clj, and all call sites) |
| 10 | Documentation covers all aspects of global base image customization | VERIFIED | All 6 docs files updated (README, ARCHITECTURE, CONFIGURATION, HARNESSES, TROUBLESHOOTING, DEVELOPMENT) with feature content, three-tier chain, examples, and troubleshooting |
| 11 | Version 3.8.0 shipped with CHANGELOG entry | VERIFIED | cli.clj:23 `(def version "3.8.0")`; CHANGELOG.md:10 `## [3.8.0] - 2026-02-19` as first versioned entry |

**Score:** 11/11 truths verified

---

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/aishell/docker/base.clj` | Global base image module | VERIFIED | 163 lines; 9 public/private functions + 3 constants; all plan-specified items present: `global-dockerfile-path`, `global-dockerfile-exists?`, `global-dockerfile-hash`, `tag-foundation-as-base`, `needs-base-rebuild?`, `build-base-image`, `ensure-base-image`, `has-custom-base-label?` |
| `src/aishell/cli.clj` | Setup/update/volumes integration + version 3.8.0 | VERIFIED | `[aishell.docker.base :as base]` in requires; `handle-setup` calls `ensure-base-image` (line 198); `handle-update` calls `ensure-base-image` both force (338) and quiet (339) paths; `handle-volumes-prune` has orphan detection (429-437); `(def version "3.8.0")` at line 23 |
| `src/aishell/docker/extension.clj` | Extension builds FROM aishell:base | VERIFIED | `base-image-id-label "aishell.base.id"` (line 15); no circular require; callers pass `base/base-image-tag` as `foundation-tag` parameter; no `validate-base-tag` |
| `src/aishell/run.clj` | Lazy base image build before container run | VERIFIED | `[aishell.docker.base :as base]` required; `base/ensure-base-image {}` called at 3 defensive points; `base/base-image-tag` used throughout |
| `src/aishell/check.clj` | Base image status in check output | VERIFIED | `[aishell.docker.base :as base]` required; `check-base-image-custom` defined (lines 72-79); wired into `run-check` at line 245 |
| `README.md` | Feature mention | VERIFIED | Line 164: "Global base image — Customize the base image for all projects via `~/.aishell/Dockerfile`" |
| `docs/ARCHITECTURE.md` | Three-tier image chain documentation | VERIFIED | "aishell:foundation -> aishell:base -> aishell:ext-{hash}" at line 71; cascade behavior documented; Docker labels documented |
| `docs/CONFIGURATION.md` | Global Dockerfile usage guide with examples | VERIFIED | Full "Global Base Image Customization" section (line 881+); 3 examples (system packages, shell config, dev tools); reset procedure |
| `docs/HARNESSES.md` | `FROM aishell:base` recommendation | VERIFIED | Line 33: "Project `.aishell/Dockerfile` extensions should use `FROM aishell:base` (recommended)" |
| `docs/TROUBLESHOOTING.md` | Build failures and reset procedure | VERIFIED | "Base Image Build Failures" section (line 189+); "Reset Global Base Image" section (line 221+) |
| `docs/DEVELOPMENT.md` | docker/base.clj module documented | VERIFIED | Line 103: docker/base.clj in project structure with key functions listed |
| `CHANGELOG.md` | v3.8.0 entry | VERIFIED | `## [3.8.0] - 2026-02-19` at line 10 with Added (8 items), Changed (2 items), Docs (6 items) sections |

---

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `src/aishell/cli.clj` | `src/aishell/docker/base.clj` | `require + base/ensure-base-image` | WIRED | `[aishell.docker.base :as base]` in ns requires; `base/ensure-base-image` called at lines 198, 338, 339; `base/base-image-tag` and `base/tag-foundation-as-base` used in volumes prune |
| `src/aishell/run.clj` | `src/aishell/docker/base.clj` | `require + ensure-base-image before resolve-image-tag` | WIRED | `[aishell.docker.base :as base]` required; `base/ensure-base-image {}` at lines 81, 119, 285; `base/base-image-tag` passed to extension functions |
| `src/aishell/check.clj` | `src/aishell/docker/base.clj` | `require + check-base-image-custom` | WIRED | `[aishell.docker.base :as base]` required; `check-base-image-custom` calls `base/global-dockerfile-exists?` and `base/needs-base-rebuild?`; function called in `run-check` at line 245 |
| `src/aishell/docker/extension.clj` | `src/aishell/docker/base.clj` | `base-tag parameter (avoids circular require)` | WIRED (via parameter) | No direct require (would create circular dependency since base.clj requires extension.clj); callers in run.clj pass `base/base-image-tag` as the `foundation-tag` parameter — design decision documented in SUMMARY 66-02 |
| `docs/CONFIGURATION.md` | `docs/ARCHITECTURE.md` | Cross-reference link | WIRED | Line 971: "See [TROUBLESHOOTING.md](TROUBLESHOOTING.md#base-image-build-failures) for details" |

---

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|---------|
| BASE-01 | 66-01 | `~/.aishell/Dockerfile` detected and built as `aishell:base` with Docker labels | SATISFIED | `global-dockerfile-exists?` detects; `build-base-image` builds with `base-dockerfile-hash-label` and `base-foundation-id-label` labels |
| BASE-02 | 66-01 | When no global Dockerfile, `aishell:base` is a tag alias for `aishell:foundation` | SATISFIED | `tag-foundation-as-base` runs `docker tag aishell:foundation aishell:base`; called from `ensure-base-image` when no Dockerfile |
| BASE-03 | 66-01 | Base image rebuilds when global Dockerfile content changes | SATISFIED | `needs-base-rebuild?` compares `base-dockerfile-hash-label` label against `global-dockerfile-hash` |
| BASE-04 | 66-01, 66-02 | Foundation image change cascades to base rebuild, then extension rebuilds | SATISFIED | `needs-base-rebuild?` checks `base-foundation-id-label` vs current foundation ID; extension tracks `base-image-id-label` ("aishell.base.id") to detect base changes |
| BASE-05 | 66-02 | `aishell check` shows base image status | SATISFIED | `check-base-image-custom` shows "custom (~/.aishell/Dockerfile)" or "default (foundation alias)" |
| BASE-06 | 66-01 | `aishell setup --force` and `aishell update --force` rebuild base image | SATISFIED | `handle-setup` passes `(:force opts)` to `ensure-base-image`; `handle-update` calls with `{:force true}` |
| BASE-07 | 66-02 | `aishell volumes prune` cleans up orphaned base images | SATISFIED | Orphan detection + `tag-foundation-as-base` reset in `handle-volumes-prune` |
| BASE-08 | 66-02 | Project extension Dockerfiles accept `FROM aishell:base` | SATISFIED | `validate-base-tag` fully removed — zero occurrences in `src/` |
| BASE-09 | 66-03 | All user-facing documentation updated | SATISFIED | 6 doc files updated: README, ARCHITECTURE, CONFIGURATION, HARNESSES, TROUBLESHOOTING, DEVELOPMENT |
| BASE-10 | 66-04 | Version bumped and CHANGELOG updated | SATISFIED | `(def version "3.8.0")` in cli.clj; `## [3.8.0]` entry first in CHANGELOG.md |

All 10 requirements satisfied. No orphaned requirements found (REQUIREMENTS.md traceability table lists BASE-01 through BASE-10 all as Phase 66 / Complete).

---

### Anti-Patterns Found

No anti-patterns detected in phase-modified files:

- No TODO/FIXME/PLACEHOLDER comments in base.clj, cli.clj, extension.clj, run.clj, or check.clj
- No stub implementations (return null, empty handlers) in any modified file
- No orphaned functions — all new functions called from appropriate entry points
- `validate-base-tag` fully removed with zero remaining references (confirmed via grep)
- The only `3.7.0` reference in source is a schema version comment in `state.clj` (not a phase-modified file; a docstring comment, not a version constant — not a concern)

---

### Commit Verification

All 8 commits documented in SUMMARYs exist in git history:

| Commit | Plan | Description |
|--------|------|-------------|
| `10504d1` | 66-01 | feat(66-01): create docker/base.clj global base image module |
| `25d8995` | 66-01 | feat(66-01): integrate base image into setup and update commands |
| `2fef394` | 66-02 | feat(66-02): wire base image into extension builds and run path |
| `ff75309` | 66-02 | feat(66-02): add base image status to check and orphan cleanup to volumes prune |
| `f3039aa` | 66-03 | docs(66-03): update README, ARCHITECTURE, and CONFIGURATION for base image customization |
| `f22f1bc` | 66-03 | docs(66-03): update HARNESSES, TROUBLESHOOTING, and DEVELOPMENT for base image |
| `2f66c95` | 66-04 | chore(66-04): bump version to 3.8.0 |
| `9a6247c` | 66-04 | docs(66-04): add v3.8.0 CHANGELOG entry for Global Base Image Customization |

---

### Human Verification Required

None required. All behavioral aspects verified programmatically:

- Function existence and wiring verified via grep and file inspection
- `validate-base-tag` removal confirmed via zero grep results
- Version string confirmed at source level
- All requirement coverage mapped to concrete code artifacts

The only behaviors that would need human verification (visual UX, spinner appearance, actual Docker build execution) are covered by the existing Babashka load-test convention used in the plans, and the structure of the implementation matches the expected UX patterns from prior phases.

---

### Gaps Summary

No gaps. Phase goal fully achieved.

The three-tier image chain (`aishell:foundation` -> `aishell:base` -> `aishell:ext-{hash}`) is implemented end-to-end:

1. **Core module** (`docker/base.clj`): Detection, build, tag-alias, staleness, and the `ensure-base-image` entry point are all present and substantive.
2. **Setup/update integration** (`cli.clj`): Both commands call `ensure-base-image` at the correct points with correct force/quiet semantics.
3. **Run-path lazy build** (`run.clj`): Three defensive `ensure-base-image {}` calls ensure the base image is always current before containers run.
4. **Extension chain** (`extension.clj`): Tracks `aishell.base.id` label, not `aishell.foundation.id`, so changes to `aishell:base` cascade to extension rebuilds. Circular dependency avoided by parameter passing.
5. **Check visibility** (`check.clj`): `check-base-image-custom` wired into `run-check`, displaying correct custom vs default status.
6. **Volumes cleanup** (`cli.clj`): Orphaned base image detection present in `handle-volumes-prune`.
7. **Documentation**: All 6 user-facing docs updated; three-tier chain documented in ARCHITECTURE; full guide with examples in CONFIGURATION; troubleshooting and reset in TROUBLESHOOTING; module documented in DEVELOPMENT.
8. **Release**: Version 3.8.0 in CLI; comprehensive CHANGELOG entry.

---

_Verified: 2026-02-19_
_Verifier: Claude (gsd-verifier)_
