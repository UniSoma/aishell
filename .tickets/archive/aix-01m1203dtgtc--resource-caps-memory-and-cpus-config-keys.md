---
id: aix-01m1203dtgtc
title: 'Resource caps: memory and cpus config keys'
status: closed
type: feature
priority: 3
mode: afk
created: '2026-08-27T16:15:58.288259919Z'
updated: '2026-11-26T14:58:57.314114903Z'
closed: '2026-11-26T14:58:57.314114903Z'
acceptance:
- title: resources.memory and resources.cpus reach docker run; absent keys add no flags
  done: true
- title: Invalid values exit 1 with the key named
  done: true
- title: aishell info shows the caps; CONFIGURATION.md documents them
  done: true
tags:
- security
- settled
---

## Description


Add a `resources` map to `.aishell/config.yaml` (and the global `~/.aishell/config.yaml`) with two optional keys, `memory` and `cpus`, that cap the Sandbox container: `resources: {memory: "8g", cpus: "4"}` puts `--memory 8g --cpus 4` on the `docker run` that starts the Sandbox, on the interactive path and the exec path alike. aishell sets no cap of its own: an absent key adds no flag, and no profile or mode supplies a default (ADR 0006: OOM-killing a build is worse than no cap). A value Docker's own CLI would refuse stops the launch with exit 1 and a message that names the key (`resources.memory` or `resources.cpus`) and the bad value. `aishell info` shows the active caps for the current project, or that none are set. `docs/CONFIGURATION.md` documents the key, its merge rule, and its precedence against `docker_args`.

### Decisions

Carried forward from the old description: no default cap (ADR 0006); the `--memory` / `--cpus` mapping; the `aishell info` and `docs/CONFIGURATION.md` surfaces. Retired: "no default in either security profile" now reads "no profile supplies a default", because security profiles do not exist yet (they are `aix-01m1203dkknf`, still open); the rule is the same.

- The flags are built by a new fn in `aishell.docker.run` beside `build-port-args`, called from `build-docker-args-internal`, so both `build-docker-args` and `build-docker-args-for-exec` (and the vscode launch, which calls `build-docker-args`) get the caps. Setup containers (`docker/volume.clj`, `pi.clj`, `docker/build.clj`) take no caps.
- The validator lives in the same fn and fails through `output/error` (exit 1), the way `build-port-args` refuses a bad port. `config/validate-config` stays warn-only: it adds `resources` to `known-keys` and to the "Valid keys" message, and warns on an unknown key under `resources` (for example `swap`) without exiting.
- `memory` follows Docker's client grammar (`go-units` `RAMInBytes`): digits with an optional decimal part, an optional space, an optional unit `b k m g t p` in either case, an optional `i`, and an optional `b` (`512m`, `8g`, `1.5G`, `8GiB`, `1073741824`). A YAML integer is read as bytes. Zero is refused: an absent key is the only way to say "no cap".
- `cpus` is a positive decimal number, given as a YAML number or a string (`4`, `"4"`, `1.5`), with at most 9 fractional digits (Docker's "value is too precise" bound). Zero and negatives are refused.
- Host-dependent limits (the daemon's 6 MB memory floor, `cpus` above the host's core count) are left to the Docker daemon; aishell checks the grammar only.
- The flags are emitted as `--memory <value>` and `--cpus <value>` with the value as the user wrote it (numbers stringified), placed just before `docker_args`. `docker_args` keeps its documented place as the last word: a `--memory` there wins over `resources.memory`.
- Merge: `resources` shallow-merges like `env` (project key overrides global key, other keys kept); `extends: none` drops the global map. It needs its own branch in `merge-configs`, not `normalize-env-to-map`.
- `aishell info` gets a `Resources` section after `Project`, loading the config for the working directory with `config/load-config`, listing `Memory:` and `CPUs:` lines for the keys that are set, or `None (no caps)`. It reads the caps through the same `aishell.docker.run` fn the launch uses, so an invalid value exits 1 from `info` with the same message.
- `docs/CONFIGURATION.md`: a `resources` section in the full reference, a row in the merge-strategy table, and the existing `docker_args` resource-limit examples point at the new key.
- Tests: `test/aishell/docker/run_test.clj` asserts the flags in the `build-docker-args` and `build-docker-args-for-exec` vectors, no flags when the key is absent, and exit 1 naming the key for each refused form (`8x`, `0`, `-1`, `abc`, `1.0000000001` cpus); `test/aishell/config_test.clj` covers the merge; `test/aishell/info_test.clj` covers the section lines.
- No door sweep: the check is a format check on the user's own config, not an access or ingress rule (`~/.agents/ticket-shapes/door-sweep.md` trigger).
- Depth: one new fn in the module that already builds every other run flag (seam placement passes); no new seam or protocol. `run.clj` stays one concept (docker run argument construction).
- `aix-01m1203dkknf` also edits `build-docker-args-internal` and `info.clj`; whichever lands second rebases. No dependency: neither uses code the other adds.

## Notes

**2026-11-26T14:55:29.083150838Z**

Proposed amendment (decision wording only): the memory Decision paraphrases go-units RAMInBytes as 'an optional unit b k m g t p, an optional i, and an optional b'. go-units itself (docker/go-units size.go parseSize) takes either a lone b, or one of kmgtp followed by nothing, b or ib; it refuses 8i, 8Gi, 8bb, 8bi. The code follows go-units, as the Decision names it, and also refuses a size that truncates to 0 bytes (0.5, 0.9b), which Docker would read as no limit. Suggest rewording the Decision to match.

**2026-11-26T14:58:57.314114903Z**

Added resources.memory and resources.cpus config keys: validated and emitted as --memory/--cpus before docker_args on launch, exec and vscode runs; shallow-merged like env; shown in aishell info; documented in CONFIGURATION.md.
