# First Principles: aishell CLI UX Review

**Problem/Belief:** aishell's CLI UX is well-designed for its target users (developers running AI coding agents in Docker sandboxes).

## Current Assumptions

- **Users want multiple AI agents in one tool**: Verified — multi-agent workflows are real, but most users likely use one agent 90%+ of the time.
- **Users need Docker isolation**: Verified — AI agents writing arbitrary code benefit from sandboxing.
- **`setup` then `run` is the right two-step flow**: Unverified — this mirrors package managers, but the gap between install and use creates friction. Why can't `aishell claude` auto-setup if not configured?
- **Users understand Docker concepts (images, volumes, containers)**: Unverified — the abstraction leaks: `docker stop aishell-<hash>-claude`, volume architecture details, Dockerfile extension. Users who want sandboxing may not be Docker experts.
- **YAML config with inheritance is the right config model**: Unverified — `extends: global` vs `extends: none`, shallow merge vs concatenate rules, map-of-lists merging — this is a lot of cognitive load for config. Is anyone actually using global + project config inheritance?
- **Sensitive file detection should block by default**: Unverified — high-severity findings requiring confirmation means the happy path has interrupts. If the project is already in git, `.gitignore` is the source of truth for what shouldn't be exposed.
- **Container naming with hashes is user-friendly**: False — `aishell-{project-hash}-{name}` requires users to either remember hashes or always use `--name`. `aishell ps` helps, but `docker stop aishell-a3f8b2c1-claude` is hostile.
- **tmux is the right multiplexer for detach/attach**: Unverified — tmux adds build-time complexity, plugin management, resurrect config. Docker itself supports `docker attach` / `docker exec`. Is tmux solving a real problem or adding a layer?
- **Path preservation matters**: Verified — AI agents referencing `/home/you/project/src/foo.ts` need that path to be real. This is a genuine insight.

## Fundamental Truths

- **AI coding agents execute untrusted code** — isolation is genuinely needed, not optional.
- **The user's mental model is "run claude/codex/etc but sandboxed"** — everything else is implementation detail they'd rather not think about.
- **File ownership and git identity must match the host** — or the tool creates more problems than it solves.
- **State lives in the project directory** — containers must be ephemeral; the project mount is the only thing that matters.
- **The fewer concepts a user must learn, the better** — Docker, volumes, images, tmux, YAML inheritance, detection, Gitleaks, Dockerfiles, pre_start hooks — that's a lot of surface area.

## Rebuilt Understanding

Starting from fundamentals, aishell's core value proposition is one line: **run an AI agent in a sandbox with zero config**. The UX should be optimized for that path.

Issues when rebuilt from scratch:

1. **Setup shouldn't be a separate step.** `aishell claude` should auto-setup if needed. The two-step ceremony (`setup` then `run`) exists because Docker builds are slow, but that's an implementation constraint, not a user need. At minimum, offer `aishell claude` as a single command that sets up on first run.

2. **Too many concepts exposed.** The docs describe foundation images, harness volumes, volume hashes, container naming schemes, tmux sessions, resurrect state directories. A user who just wants `aishell claude` shouldn't encounter any of this unless they go looking. The llm.txt/docs flatten everything into one layer.

3. **Config inheritance is over-engineered.** `extends: global` with different merge strategies per type (lists concatenate, maps shallow-merge, scalars replace, map-of-lists merge-then-concatenate) is the kind of thing that sounds logical in design but creates confusion in practice. Simpler: project config wins, period. If you want to share config, use a shared file with explicit `!include`.

4. **Container lifecycle leaks Docker.** `docker stop aishell-<hash>-claude` in the docs means the abstraction is incomplete. If aishell manages containers, it should provide `aishell stop` / `aishell kill` / `aishell rm`.

5. **Security UX is interruptive.** Three security layers (sensitive file detection, Gitleaks freshness, config validation) all run before launch. The happy path should be fast. Move security checks to `aishell check` (explicit) and first-run warnings, not every launch.

6. **`--unsafe` is a bad flag name.** It means "skip detection," but the name implies danger. `--no-detect` or `--skip-scan` communicates intent without moral judgment.

7. **Exit codes are too coarse.** 0 or 1 tells scripts nothing. Distinct codes for "not set up," "Docker not running," "detection blocked," "container conflict" would enable automation.

## New Possibilities

- **Zero-config first run:** `aishell claude` does everything — detects no setup, runs setup with defaults, launches. One command, no ceremony.
- **`aishell stop [name]`** — complete the lifecycle abstraction so Docker never leaks through.
- **Layered docs:** Quick start (3 lines), daily use (1 page), advanced config (full reference). The current llm.txt puts volume architecture next to `aishell claude`.
- **Drop tmux as a dependency** — use Docker's native attach/exec for simple cases, offer tmux as an opt-in power-user feature rather than a core concept.
- **Config as flat key-value with overrides** — `aishell config set env.DEBUG true` instead of editing YAML with merge semantics.
- **Progressive security** — first run: full scan with explanation. Subsequent runs in same project: silent unless new files detected. `aishell check` for on-demand deep scan.
