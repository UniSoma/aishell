---
id: aix-01m57xyfsykv
title: Add omp (oh-my-pi) harness with a GitHub-release binary install kind
status: closed
type: feature
priority: 2
mode: afk
created: '2026-10-18T16:36:33.213097600Z'
updated: '2026-10-22T21:18:19.237137026Z'
closed: '2026-10-22T21:18:19.237137026Z'
tags:
- ready-for-agent
acceptance:
- title: '`aishell setup --with-omp` and `--with-omp=18.12.1` install `omp` into the harness volume, verified against the release''s `SHA256SUMS.txt`, and `aishell omp` launches it in the Sandbox.'
  done: true
- title: A tampered or truncated download (checksum mismatch) fails the volume build with a clear error.
  done: true
- title: '`aishell update` refreshes an unpinned omp to the newest release.'
  done: true
- title: '`clj-kondo --lint src test` is clean and the test suite passes.'
  done: true
- title: With only omp enabled, a host `PI_CODING_AGENT_DIR` or `PI_CONFIG_DIR` is not passed into the Sandbox.
  done: true
---

## Description

Add omp (oh-my-pi, https://omp.sh/docs, MIT, a fork of pi by Stencil Labs) as a first-class Harness. omp's npm package needs Bun, which the Foundation image doesn't have, so this ticket also adds a generic `:github-release` install kind that downloads a release binary into the harness volume. See ADR 0009.

## Design

### Settled decisions

**Registry entry** (`src/aishell/harness.clj`)
- `:id :omp`, `:subcommand "omp"`, `:label "oh-my-pi (omp)"`, `:state-key :with-omp`, `:version-key :omp-version`.
- `:interactive? true`, `:pre-start? true`, `:accepts-config-defaults? true`, `:volume-participant? true`, `:alias {:always? false}`. These match pi.
- `:install {:kind :github-release :repo "can1357/oh-my-pi" :asset "omp-linux-{arch}" :binary "omp" :tag-prefix "v" :checksums "SHA256SUMS.txt"}`.
- `:config-paths [{:path [".omp"] :type :dir}]` mounts all of `~/.omp`, including `agent.db` credentials and `wt/` worktrees.
- `:env-passthrough` is `OMP_PROFILE` plus the main provider keys, opencode-style: `ANTHROPIC_API_KEY`, `OPENAI_API_KEY`, `GEMINI_API_KEY`, `OPENROUTER_API_KEY` and similar. It deliberately leaves out `PI_CODING_AGENT_DIR` and `PI_CONFIG_DIR`, which omp reads under the same names as pi, so that a host variable meant for pi does not reach an omp-only Sandbox. This does not help when both are enabled: passthrough is the combined list of every enabled harness for the whole Sandbox (`build-api-env-args`, `src/aishell/docker/run.clj`), so pi's entry still brings `PI_CODING_AGENT_DIR` in and omp will read it. Note that in `docs/HARNESSES.md`; scoping env per launched harness is out of scope.
- No `:skip-permissions-flag`, because omp already defaults to yolo. No `:runtime-env` or `:fixed-args`, because the update check can be turned off only in a config file (`startup.checkUpdate: false`) and ADR 0008 says to declare nothing in that case. The check only shows a notice, and `/tools` is mounted read-only.

**Install kind** (`src/aishell/docker/volume.clj`, `build-install-commands`)
- Add a second branch next to npm that emits one snippet per enabled `:github-release` harness.
- Choose the arch with `uname -m`: `x86_64` becomes `x64` and `aarch64` becomes `arm64`. Anything else fails with a clear error. Always use the glibc asset.
- A pinned version downloads from `https://github.com/<repo>/releases/download/<tag-prefix><version>/<asset>`. "latest" downloads from `https://github.com/<repo>/releases/latest/download/<asset>`.
- Download `<checksums>` from the same release and run `sha256sum -c` on the asset. A mismatch fails the volume build. Install the result as `/tools/bin/<binary>`, executable.
- Add `/tools/bin` to PATH next to `/tools/npm/bin` (`src/aishell/docker/templates.clj` ~534-540 and ~665-668).

**Glossary:** `CONTEXT.md` already lists omp as a separate Harness ("Avoid: pi (for omp)").

### Out of scope (each becomes its own ticket if wanted)
- An omp equivalent of `pi_packages` for omp's plugin marketplace.
- A preinstalled Chromium for omp's browser tool.
- An `--unisoma`-style model whitelist for omp.
- Egress allowlist hosts.

### Touch list (modelled on Copilot commit 733bdc7)
- `src/aishell/harness.clj`, `src/aishell/docker/volume.clj`, `src/aishell/docker/templates.clj`.
- The `src/aishell/state.clj` harness-key docstring, which is already stale because it omits copilot.
- The `cli.clj` help examples, if they list harnesses.
- Tests: each harness table in `test/aishell/{harness,info,check,cli,output}_test.clj` and `test/aishell/docker/{run,volume}_test.clj`. Add volume tests for the github-release snippet: pinned vs latest URLs, arch mapping, checksum step, PATH.
- Docs: `docs/HARNESSES.md` (mention that omp is yolo by default and the `startup.checkUpdate: false` tip), `docs/CONFIGURATION.md`, `docs/ARCHITECTURE.md`, `README.md`, `llm.txt`, `CHANGELOG.md`.

## Notes

**2026-10-22T21:18:19.237137026Z**

omp harness landed (691701a, 49838b1, 3676f85); Jonas verified setup and launch end to end on 2026-10-22.
