---
id: aix-01m1ms2ngp23
title: bbin shims share one fixed /tmp deps.edn path across UIDs
status: closed
type: bug
priority: 2
mode: afk
created: '2026-09-03T23:18:47.574071674Z'
updated: '2026-09-03T23:30:14.123417240Z'
closed: '2026-09-03T23:30:14.123417240Z'
tags:
- ready-for-agent
acceptance:
- title: Dockerfile template patches the five gensym "bbin" forms in /usr/local/bin/bbin to the per-user name, in the same RUN layer that downloads bbin
  done: true
- title: That RUN layer fails the build when the substitution does not match exactly five occurrences
  done: true
- title: Entrypoint removes /tmp/bbin* beside the existing sentinel rm -f, with a comment explaining why
  done: true
- title: Unit tests assert the rendered Dockerfile contains the patched form and the guard, and the rendered entrypoint contains the cleanup
  done: true
- title: Full test suite and clj-kondo --lint src test pass
  done: true
- title: CHANGELOG entry under Unreleased with the foundation-rebuild wording; version constant untouched
  done: true
links:
- aix-01m1msbc2hyt
---

## Description

Every bbin-generated shim (`/usr/local/share/bbin/bin/*`) writes its temp deps.edn to
`(fs/file (fs/temp-dir) (str (gensym "bbin")))`. `gensym`'s counter resets per process, so the
path is identical on every run (`/tmp/bbin190` on the current image). The shim then
`process/exec`s bb, which replaces the process, so `fs/delete-on-exit` never fires and the
file persists. Once a copy at that path is owned by another UID (root, or a container run
under a different host UID) every bbin script in the image fails with
`java.io.FileNotFoundException: /tmp/bbin190 (Permission denied)` until the file is removed.

### Findings

- The `gensym` form lives in the shim templates inside `/usr/local/bin/bbin` itself (five
  occurrences in 0.2.5), not in the shims. aishell downloads that file at build time
  (templates.clj ~L217-220), so a `sed` on it in the same RUN layer patches every shim generated
  afterwards, including ones a downstream project installs at runtime.
- Upstream `main` still uses `gensym` as of 2026-09-03; no released fix.
- `fs/create-temp-file` fixes the collision (verified with a patched copy) but, because
  delete-on-exit never fires, leaves one ~180-byte file per invocation. The per-user stable
  name below keeps today's overwrite behaviour with no cross-UID clash and no accumulation.
- `TMPDIR` and `JAVA_TOOL_OPTIONS` are ignored by bb; only `-Djava.io.tmpdir` works and it
  cannot reach the shim's own interpreter.

### Decisions (settled 2026-09-03)

- Scope is the bbin sed patch plus the entrypoint cleanup. Filing the upstream issue against
  babashka/bbin is a separate human task, not part of this ticket.
- Temp-file name is the per-user stable form, not `fs/create-temp-file`:
  `(fs/file (fs/temp-dir) (str (gensym (str "bbin-" (System/getProperty "user.name") "-"))))`
- The RUN layer must fail the build unless the substitution matches exactly five occurrences,
  so a future `BBIN_VERSION` bump surfaces as a build error instead of a silent no-op.
- Verification is by unit test on the rendered Dockerfile and entrypoint text in
  `src/aishell/docker/templates.clj`, plus the existing suite and clj-kondo. No Docker in the
  agent sandbox; the live behaviour check is a human follow-up.
- CHANGELOG entry goes under `## [Unreleased]` with the standard "one foundation rebuild on
  your next setup or update" wording. Do not touch the version constant (already 4.1.0).
  No ADR.

### Implementation notes

- Patch site: the RUN layer that curls `bbin` (templates.clj ~L217-220). Match the exact
  string `(fs/file (fs/temp-dir) (str (gensym \"bbin\")))` (it appears inside a Clojure string
  literal in bbin, so the quotes are escaped there).
- Cleanup site: beside the sentinel `rm -f /tmp/aishell.entrypoint-done ...` in the entrypoint
  (~L548). Add `rm -f /tmp/bbin*`. It runs as root before `exec gosu`; add a one-line comment
  saying why (bbin shims `exec` bb so delete-on-exit never fires; leftovers from another UID
  block every bbin script). The glob also covers the new `bbin-<user>-N` names.
- Use `clj-surgeon` for edits to templates.clj; the entrypoint is a heredoc string inside it.
- Lint before commit: `clj-kondo --lint src test`. No AI attribution in the commit.

Recovery in an affected sandbox today: `sudo rm -f /tmp/bbin*`.

## Notes

**2026-09-03T23:30:14.123417240Z**

Foundation build patches the five gensym forms in /usr/local/bin/bbin to bbin-<user>-N with a 5-occurrence guard; entrypoint removes /tmp/bbin* on every start. All sites tagged WORKAROUND(bbin-gensym-tmp) for removal once upstream fixes it (aix-01m1msbc2hyt tracks filing that).
