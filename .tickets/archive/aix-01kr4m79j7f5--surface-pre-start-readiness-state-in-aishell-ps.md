---
id: aix-01kr4m79j7f5
title: Surface pre_start readiness state in aishell ps and ps --json
status: closed
type: feature
priority: 2
mode: afk
created: '2026-05-08T20:24:48.967118070Z'
updated: '2026-05-08T22:35:45.936393925Z'
closed: '2026-05-08T22:35:45.936393925Z'
tags:
- ready-for-agent
acceptance:
- title: bb test passes; clojure -M:clj-kondo --lint src test passes
  done: true
- title: 'aishell ps --json rows include a bootstrap field with one of: pending | ready | failed | none'
  done: true
- title: aishell ps (human) shows a BOOTSTRAP column derived from the same source as the JSON path (no logic duplication); the failed value is rendered as FAILED for emphasis (no ANSI color)
  done: true
- title: Entrypoint writes /tmp/pre-start.done on success and /tmp/pre-start.failed (containing the non-zero exit code) on failure of the backgrounded pre_start; no sentinel is written when PRE_START is unset
  done: true
- title: Entrypoint removes any stale /tmp/pre-start.done and /tmp/pre-start.failed at start, before launching pre_start, so a config change from pre_start to no pre_start surfaces as bootstrap none on the next start
  done: true
- title: Non-running containers (Exited / Created / Restarting) report bootstrap none without invoking docker exec; bootstrap none also covers the pre_start-unset case (the overload is intentional and documented in code)
  done: true
- title: When any row reports bootstrap failed, a single banner under the human table points the user at aishell exec <name> cat /tmp/pre-start.log for details
  done: true
- title: Pure-fn clojure.test coverage for derive-state (truth table covering none, pending, ready, failed, and the both-sentinels race resolving to failed) and for merge-bootstrap (running rows take their bootstrap from the probe map; non-running rows short-circuit to none regardless of the map)
  done: true
---

## Description

`aishell ps` and `aishell ps --json` only report Docker's raw container status, so a container shows `Up …` (and `status: "Up …"` in JSON) the moment the entrypoint exec's the foreground command — even though `pre_start` is still running detached in the background. Callers running `aishell --name main` and then driving the container have no first-class signal for "bootstrap finished".

Sources of the gap:
- `src/aishell/docker/templates.clj:323` — entrypoint launches `pre_start` backgrounded: `gosu … sh -c "$PRE_START" > /tmp/pre-start.log 2>&1 &`
- `src/aishell/docker/templates.clj:336` — entrypoint then `exec`s the foreground command without waiting on `pre_start`
- `src/aishell/docker/naming.clj:108-128` — `list-project-containers` only surfaces `:name/:status/:created` from `docker ps`
- `src/aishell/cli.clj:560-582` — `ps-row` / `format-ps-data` / `format-container` have no readiness field
- No sentinel file, no Docker `HEALTHCHECK`, no record of `pre_start` exit code; failures are swallowed into `/tmp/pre-start.log`.

Follow-up to the now-shipped JSON parent aix-01kr1qp6deb1 ("Wire --json infrastructure and aishell ps --json").

## Design

Design locked via `/grill-me` on 2026-05-08. JSON-key name moved from `:ready` to `:bootstrap`; the title still reads "readiness state" because that remains the user-facing concept being surfaced.

### Field & values

- JSON key on each `ps` row: `:bootstrap`.
- Values: `pending | ready | failed | none`.
- `none` is **intentionally overloaded**: it covers both "pre_start was never configured for this container" and "container is not running, so the sentinels can't be probed." Both cases collapse to the same value because the user-facing answer is the same — there is no readiness signal to report. The overload must be called out in a code comment in `aishell.docker.bootstrap`.

### Entrypoint changes (`src/aishell/docker/templates.clj`)

The pre_start block at templates.clj:317-324 grows two responsibilities:

1. **Unconditional stale-sentinel cleanup** before the `if [[ -n "${PRE_START:-}" ]]` guard:
   ```bash
   rm -f /tmp/pre-start.done /tmp/pre-start.failed
   ```
   Required because `/tmp` is on the container's writable layer (not a tmpfs), so sentinels persist across `docker stop`/`docker start`. Without this, removing `pre_start` from config would leave a stale `done` sentinel and the next probe would lie.

2. **Subshell wrapper** that captures the exit code and writes the appropriate sentinel:
   ```bash
   if [[ -n "${PRE_START:-}" ]]; then
       (
         set +e
         gosu "$USER_ID:$GROUP_ID" sh -c "$PRE_START" > /tmp/pre-start.log 2>&1
         ec=$?
         if [ $ec -eq 0 ]; then
           touch /tmp/pre-start.done
         else
           echo "$ec" > /tmp/pre-start.failed
         fi
       ) &
   fi
   ```
   Notes:
   - The subshell is backgrounded with `&` so it survives the entrypoint's `exec`. The shell that launched it is replaced; the subshell is re-parented to PID 1 (the foreground command) and continues to run, eventually writing its sentinel.
   - `set +e` inside the subshell is mandatory: the entrypoint script declares `set -e` at templates.clj:203, which is inherited into the subshell. Without `set +e`, a non-zero `gosu` exit would abort the subshell *before* the `if/else` runs, and no `failed` sentinel would ever be written.
   - Inline comment must explain both the `exec`-survival reasoning and the `set -e` interaction so a future reader doesn't "simplify" either.

### Probe namespace (new: `src/aishell/docker/bootstrap.clj`)

A new sibling of `naming.clj` / `run.clj` / `templates.clj`. Layout:

- **`derive-state`** — pure. Input: `{:pre-start-configured? bool, :done? bool, :failed? bool}`. Output: one of `:none :pending :ready :failed`. Truth table:

  | `pre-start-configured?` | `done?` | `failed?` | result      |
  |---|---|---|---|
  | `false` | any   | any   | `:none`    |
  | `true`  | `false` | `false` | `:pending` |
  | `true`  | `true`  | `false` | `:ready`   |
  | `true`  | `false` | `true`  | `:failed`  |
  | `true`  | `true`  | `true`  | `:failed`  (race-resolution: failure takes precedence) |

- **`merge-bootstrap`** — pure. Input: `(containers, name->state-map)`. Output: containers with `:bootstrap` attached. Non-running containers (status not matching `^Up`) get `:bootstrap :none` *without consulting the state map*; running containers look up their state in the map (defaulting to `:none` if absent — defensive).

- **`probe-running!`** — impure. For each running container, in parallel via `pmap`:
  1. `docker inspect --format '{{range .Config.Env}}{{println .}}{{end}}' <name>` to read env vars; if `PRE_START` is empty/unset, return `:none` immediately (no exec).
  2. Otherwise, `docker exec <name> sh -c '...'` to read the sentinels and produce a `derive-state` input map; pass through `derive-state`.
  3. On any exec/inspect error, log a warning (suppressed in JSON mode via the existing `output/warn` invariant) and return `:pending`. Self-correcting on next `ps` call.
  Returns a `name->state-keyword` map.

- **`attach-bootstrap!`** — impure composition: `(merge-bootstrap containers (probe-running! containers))`. Called by `cli/handle-ps` after `naming/list-project-containers`.

### CLI surface (`src/aishell/cli.clj`)

- `ps-row` reads `(:bootstrap c)` directly (the work was done by `attach-bootstrap!` upstream). New JSON shape per row: `{name, fullName, status, created, bootstrap}` — additive, existing keys unchanged.
- `format-container` adds the `:BOOTSTRAP` cell: literal token for `:none|:pending|:ready`, capitalized `FAILED` for `:failed`. No ANSI color (would break `clojure.pprint/print-table` width math).
- `handle-ps` (human path only): if any row in the table has `:bootstrap :failed`, print a single banner *under* the table:
  ```
  One or more containers failed pre_start. Run aishell exec <name> cat /tmp/pre-start.log for details.
  ```
  No banner in JSON mode.

### Tests

`bb test` MUST run without Docker. Only the pure functions are unit-tested.

**`test/aishell/docker/bootstrap_test.clj` (new):**
- `derive-state`: five rows of the truth table above (the four primary cases + the `both-sentinels` race resolving to `:failed`). The `pre-start-configured? false` row covers two sub-cases (sentinels absent, sentinels present-but-shouldn't-be) — at least one of each.
- `merge-bootstrap`: a running container with each of the four enum values pulled from the map, a stopped container that gets `:none` even when the map says otherwise, and a running container missing from the map (defaults to `:none`).

**`test/aishell/cli_test.clj` (extend):**
- Existing `format-ps-data-extracts-short-name-and-keys` fixture grows a `:bootstrap` field on each input row and on each expected output row.
- Add a fixture row per enum value (`:none, :pending, :ready, :failed`) so the JSON shape is locked in by tests across the full taxonomy.

**Not in `bb test` (manual smoke checklist in PR description):**
- `probe-running!` and `attach-bootstrap!` — these shell out to docker. Verified by the human reviewer post-merge per the PR checklist:
  - Container with slow `pre_start` (e.g. `sleep 10 && true`): `BOOTSTRAP=pending` initially, transitions to `ready`.
  - Container with failing `pre_start` (e.g. `exit 7`): `BOOTSTRAP=FAILED`, banner appears, `/tmp/pre-start.failed` contains `7`.
  - Container with no `pre_start` in config: `BOOTSTRAP=none`.
  - Stopped container: `BOOTSTRAP=none`.
  - Config change from `pre_start: …` to no `pre_start`, then container restart: `BOOTSTRAP=none` (validates the stale-sentinel cleanup).
  - `aishell ps --json` matches the human table for all five cases.

### Out of scope (confirmed during grilling)

- `aishell ready <name>` waiter command — candidate follow-up ticket.
- Docker `HEALTHCHECK` — wrong tool for one-shot bootstrap outcome (it's for ongoing liveness).
- ANSI color in the human table.
- Changing `pre_start` execution semantics (still backgrounded, still gosu'd).
- Moving `/tmp/pre-start.log`.
- Exposing the failed `pre_start` exit code anywhere except inside `/tmp/pre-start.failed` (cell stays bare `FAILED`; JSON value stays bare `"failed"`).

## Notes

**2026-05-08T22:35:45.936393925Z**

Shipped: bootstrap probe (derive-state + merge-bootstrap pure fns, parallel docker probe), :bootstrap on aishell ps + ps --json, BOOTSTRAP column with FAILED emphasis and pre_start.log banner, sentinel-writing entrypoint with stale-cleanup. Side fix: foundation-content hash now covers all COPY'd files so entrypoint-only edits invalidate the build cache (was: only Dockerfile).
