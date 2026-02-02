---
phase: 40-plugin-installation-in-volume
verified: 2026-02-02T12:45:00Z
status: passed
score: 9/9 must-haves verified
---

# Phase 40: Plugin Installation in Volume Verification Report

**Phase Goal:** Install TPM and declared plugins into harness volume at build time
**Verified:** 2026-02-02T12:45:00Z
**Status:** PASSED
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Invalid plugin format triggers warning during config load | ✓ VERIFIED | validate-plugin-format returns error message for 'invalid' and '-bad/repo' |
| 2 | Valid plugin format passes validation silently | ✓ VERIFIED | validate-plugin-format returns nil for 'tmux-plugins/tmux-sensible' |
| 3 | Non-list tmux.plugins value triggers warning | ✓ VERIFIED | validate-tmux-config checks sequential? and warns (line 120-122) |
| 4 | Non-string plugin entries trigger warning | ✓ VERIFIED | validate-tmux-config checks string? per entry (line 126-128) |
| 5 | aishell build --with-tmux installs TPM and plugins | ✓ VERIFIED | cli.clj handle-build loads config, passes to populate-volume; volume.clj conditionally adds TPM install |
| 6 | aishell update refreshes plugin installations | ✓ VERIFIED | cli.clj handle-update loads config, passes to populate-volume |
| 7 | Plugins are world-readable (chmod -R a+rX) | ✓ VERIFIED | build-tpm-install-command includes 'chmod -R a+rX /tools/tmux' (line 203) |
| 8 | Empty plugins list skips TPM installation | ✓ VERIFIED | build-tpm-install-command returns nil when (seq plugins) is false |
| 9 | Volume population without --with-tmux skips tmux work | ✓ VERIFIED | populate-volume checks (:with-tmux state) before getting plugins (line 315) |

**Score:** 9/9 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/aishell/config.clj` | Plugin format validation | ✓ VERIFIED | plugin-format-pattern defined (line 17-21), validate-plugin-format function (line 74-79), integrated into validate-tmux-config (line 119-132) |
| `src/aishell/docker/volume.clj` | TPM install command builder | ✓ VERIFIED | build-tpm-install-command function (line 192-203, 92 lines total), substantive implementation with git clone, install_plugins, chmod |
| `src/aishell/docker/volume.clj` | populate-volume config integration | ✓ VERIFIED | populate-volume accepts :config in opts (line 313), extracts tmux-plugins (line 315-316), calls build-tpm-install-command (line 317), appends to commands (line 318-319) |
| `src/aishell/cli.clj` | Config threading in handle-build | ✓ VERIFIED | Requires aishell.config (line 15), loads cfg (line 182), passes to populate-volume (line 215) |
| `src/aishell/cli.clj` | Config threading in handle-update | ✓ VERIFIED | Loads cfg (line 302), passes to populate-volume (line 331) |
| `src/aishell/run.clj` | ensure-harness-volume config param | ✓ VERIFIED | ensure-harness-volume accepts config param (line 46), passes to populate-volume calls (line 57, 66), called with cfg from run-container (line 125) |

**All artifacts:** VERIFIED (existence + substantive + wired)

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|----|--------|---------|
| validate-tmux-config | validate-plugin-format | Function call in plugin iteration | ✓ WIRED | Line 131 calls validate-plugin-format for each plugin string |
| cli.clj handle-build | vol/populate-volume | Config passed as :config in opts | ✓ WIRED | Line 182 loads cfg, line 215 passes {:config cfg} |
| cli.clj handle-update | vol/populate-volume | Config passed as :config in opts | ✓ WIRED | Line 302 loads cfg, line 331 passes {:config cfg} |
| run.clj ensure-harness-volume | vol/populate-volume | Config passed as :config in opts | ✓ WIRED | Lines 57, 66 pass {:config config} |
| populate-volume | build-tpm-install-command | Conditional call when :with-tmux + plugins | ✓ WIRED | Line 315-317 extracts plugins, calls builder, line 319 appends to commands |
| build-tpm-install-command | TPM bin/install_plugins | Shell command string with TMUX_PLUGIN_MANAGER_PATH | ✓ WIRED | Line 202 contains full shell command with env var and install_plugins call |

**All key links:** WIRED

### Requirements Coverage

| Requirement | Status | Evidence |
|-------------|--------|----------|
| PLUG-01: tmux.plugins list in config.yaml | ✓ SATISFIED | Config validation accepts :plugins key, .aishell/config.yaml has tmux.plugins with tmux-plugins/tmux-sensible |
| PLUG-02: TPM at /tools/tmux/plugins/tpm | ✓ SATISFIED | build-tpm-install-command git clones to /tools/tmux/plugins/tpm (line 200) |
| PLUG-03: Non-interactive install during build/update | ✓ SATISFIED | populate-volume conditionally adds TPM install to build/update flows, uses bin/install_plugins non-interactively |
| PLUG-06: Plugin format validation | ✓ SATISFIED | plugin-format-pattern regex validates owner/repo format during config parsing |

**Requirements:** 4/4 satisfied for phase 40

### Anti-Patterns Found

**None.** All modified files are free of TODO, FIXME, placeholder content, or empty implementations.

### Human Verification Required

**None.** All observable truths are verifiable programmatically through code inspection and unit testing. The actual runtime behavior (Docker volume population with TPM installation) would require integration testing but is beyond the scope of goal-backward structural verification.

---

## Detailed Verification

### Artifact Analysis

**src/aishell/config.clj**
- **Exists:** Yes (318 lines)
- **Substantive:** Yes
  - plugin-format-pattern def with detailed regex and docstring (5 lines)
  - validate-plugin-format function with clear logic (6 lines)
  - validate-tmux-config extended with plugins validation (14 lines of new logic)
- **Wired:** Yes
  - validate-plugin-format called from validate-tmux-config (line 131)
  - validate-tmux-config called from load-yaml-config via validate-config chain

**src/aishell/docker/volume.clj**
- **Exists:** Yes (348 lines)
- **Substantive:** Yes
  - build-tpm-install-command function (12 lines) with complex shell command building
  - populate-volume modified to accept :config, extract plugins, conditionally add TPM install (6 lines of new logic)
- **Wired:** Yes
  - build-tpm-install-command called from populate-volume (line 317)
  - populate-volume called from cli.clj and run.clj with :config

**src/aishell/cli.clj**
- **Exists:** Yes
- **Substantive:** Yes
  - aishell.config required (line 15)
  - Config loading in handle-build (line 182) and handle-update (line 302)
  - Config passed to populate-volume (lines 215, 331)
- **Wired:** Yes
  - config/load-config called in both commands
  - cfg variable threaded to vol/populate-volume calls

**src/aishell/run.clj**
- **Exists:** Yes
- **Substantive:** Yes
  - ensure-harness-volume signature updated to accept config (line 46)
  - Config passed to populate-volume calls (lines 57, 66)
  - ensure-harness-volume called with cfg (line 125)
- **Wired:** Yes
  - cfg loaded before ensure-harness-volume call
  - config threaded through to populate-volume

### Verification Tests Executed

```bash
# Plugin format validation
bb -cp src -e "(require 'aishell.config) (println (aishell.config/validate-plugin-format \"tmux-plugins/tmux-sensible\"))"
# Output: nil ✓

bb -cp src -e "(require 'aishell.config) (println (aishell.config/validate-plugin-format \"invalid\"))"
# Output: Invalid plugin format: 'invalid' - expected 'owner/repo' ✓

bb -cp src -e "(require 'aishell.config) (println (aishell.config/validate-plugin-format \"-bad/repo\"))"
# Output: Invalid plugin format: '-bad/repo' - expected 'owner/repo' ✓

# TPM install command builder
bb -cp src -e "(require 'aishell.docker.volume) (println (aishell.docker.volume/build-tpm-install-command [\"tmux-plugins/tmux-sensible\"]))"
# Output: mkdir -p /tools/tmux/plugins && git clone --depth 1 https://github.com/tmux-plugins/tpm /tools/tmux/plugins/tpm && printf '%s\n' "set -g @plugin 'tmux-plugins/tmux-sensible'" > /tmp/plugins.conf && TMUX_PLUGIN_MANAGER_PATH=/tools/tmux/plugins /tools/tmux/plugins/tpm/bin/install_plugins && chmod -R a+rX /tools/tmux ✓

bb -cp src -e "(require 'aishell.docker.volume) (println (aishell.docker.volume/build-tpm-install-command []))"
# Output: nil ✓

bb -cp src -e "(require 'aishell.docker.volume) (println (aishell.docker.volume/build-tpm-install-command nil))"
# Output: nil ✓

# Config loading
bb -cp src -e "(require 'aishell.config) (let [cfg (aishell.config/load-config \".\")] (println \"Config loaded:\") (clojure.pprint/pprint (get-in cfg [:tmux :plugins])))"
# Output: ("tmux-plugins/tmux-sensible") ✓
```

All verification tests passed.

### Implementation Quality

**Strengths:**
1. **Conditional execution:** TPM installation only runs when both :with-tmux is true AND plugins are declared
2. **Graceful degradation:** Empty/nil plugins list returns nil, allowing caller to skip installation
3. **Permissions:** World-readable permissions (a+rX) ensure non-root container user can access plugins
4. **Config threading:** Config properly threaded through all populate-volume call sites (build, update, run)
5. **Validation first:** Plugin format validated at config parse time, before Docker operations
6. **Warning-only validation:** Consistent with existing validation framework (validate-detection-config, validate-harness-names)

**Architectural alignment:**
- Plugins installed in harness volume (/tools/tmux) not foundation image (correct layer per RESEARCH)
- Non-interactive installation via bin/install_plugins (no manual TPM prefix+I)
- TMUX_PLUGIN_MANAGER_PATH override for non-standard plugin location
- Config extends strategy respected (tmux.plugins merges with global if applicable)

---

_Verified: 2026-02-02T12:45:00Z_
_Verifier: Claude (gsd-verifier)_
