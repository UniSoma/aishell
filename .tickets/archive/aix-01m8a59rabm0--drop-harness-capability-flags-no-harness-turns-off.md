---
id: aix-01m8a59rabm0
title: Drop harness capability flags no harness turns off
status: closed
type: chore
priority: 3
mode: afk
created: '2026-11-25T20:10:36.740335675Z'
updated: '2026-11-25T20:38:16.873649607Z'
closed: '2026-11-25T20:38:16.873649607Z'
tags:
- codebase-design
- settled
- ready-for-agent
links:
- aix-01m8a3nye1d7
acceptance:
- title: No descriptor, source or test under src/ and test/ mentions :interactive?, :pre-start?, :accepts-config-defaults? or :volume-participant?
  done: true
- title: launch-argv always places config defaults before CLI args; the run-path 'Ignoring harness_args' warning and the config 'Ignored harness_args' warning are gone
  done: true
- title: A harness launch takes neither skip-pre-start nor skip-interactive from a descriptor; exec still skips both and --base-image still skips pre_start, each pinned by a test
  done: true
- title: :version-key is a required descriptor field and no code path handles its absence (setup flag coercion, setup boolean options, summaries, check, info)
  done: true
- title: Volume hash membership and the enabled-harness list are unchanged for every harness, pinned by the existing tests
  done: true
- title: The descriptor schema comment, the namespace docstring and DEVELOPMENT.md's adding-a-harness guide list only the remaining fields
  done: true
- title: bb test passes and clj-kondo --lint src test is clean
  done: true
---

## Description


Every harness descriptor sets `:interactive?`, `:pre-start?`, `:accepts-config-defaults?` and `:volume-participant?` to true and carries a `:version-key`; Gitleaks (aix-01m8a3nye1d7) was the only descriptor that did otherwise. The code paths for the other value run only under tests that fake a descriptor. Remove the four booleans, the branches that read them, the version-less branches, and the tests that fake a descriptor to reach them. Every real harness launches exactly as before: same argv, same docker args, same volume hash, same setup flags and summaries. A future harness that needs one of these behaviours brings the capability back with its first real user.

`exec` keeps its own skip-pre-start / skip-interactive behaviour, and `--base-image` still skips pre_start: neither comes from a descriptor.

### Decisions

Settled by the user (2026-11-25):

- `:volume-participant?` is dropped; `harness/volume-participants` goes and its callers (`docker/volume.clj`, `cli/enabled-harness-list`, `volume-harnesses-enabled?`, `volume-config`) read `registry`. CONTEXT.md's **Harness descriptor** entry drops "harness-volume participation" from its capabilities ("Q1 a", user, 2026-11-25).
- `:version-key` stays as a required descriptor field; the version-less branches and `harness/versioned` are deleted, not replaced by deriving the key from `:id` ("Q2 a", user, 2026-11-25).

Assumed:

- `launch-argv` always concatenates config defaults then CLI args. `run/report-harness-defaults!` keeps only its verbose note. `config/defaultless-harnesses` and its half of `validate-harness-names` go.
- `cli.clj`'s harness pass-through passes `launch-opts` unchanged. `run/skip-pre-start?` collapses to the `:base-image` check; its `:skip-pre-start` test case goes.
- `run-container` stops passing `:skip-interactive`; `docker.run/build-docker-args` drops the key, `build-docker-args-internal` keeps it for `exec`. The run_test caller of `build-docker-args` drops the key with its assertions unchanged.
- Tests deleted: `argv-without-config-defaults`, `harness-args-for-a-harness-without-config-defaults-warn`, `launch-shape-capabilities`, the "volume participant missing its version key" completeness rule. `always-required` gains `:version-key` and loses the booleans; `config_test`'s known-harness test iterates the whole registry.
- `harness/setup-flag-desc` always advertises =VERSION; `info`, `cli` setup options, setup summaries and `check` read `registry` with no version-less branch (`cli/setup-boolean-options` keeps only UniSoma, `harness-setup-spec` loses its `:coerce :boolean` branch). Deleting `versioned` and `volume-participants` passes the depth checks: each fails the deletion test once every descriptor qualifies.
- No CHANGELOG entry: no user-visible behaviour changes. No door sweep: the slice removes a warning and adds no rule that refuses or allows.

## Notes

**2026-11-25T20:38:16.873649607Z**

Dropped :interactive?, :pre-start?, :accepts-config-defaults? and :volume-participant? from harness descriptors with the branches and faked-descriptor tests that read them; :version-key is now required and the version-less branches are gone. Added an exec-vs-launch docker args test and a volume-config membership test.
