# Per-project Claude machine-state isolation via mount inversion

Mounting the host `~/.claude` wholesale into every sandbox shares Claude Code's supervisor state (`daemon.lock`, `daemon/roster.json`, `jobs/`) across containers, where its PIDs and socket paths are meaningless — this breaks Agent View. We introduce an opt-in `claude_isolation: project` config mode (default `shared`, today's behavior) that gives each sandbox its own Claude machine state under `{XDG state dir}/aishell/claude/{project-hash}/dot-claude/`, mounted at the container's `~/.claude`, with shared entries mounted on top. Each sandbox then runs its own Agent View supervisor, scoped to that project.

## Considered Options

- **Isolate by default, share an allowlist (chosen).** The per-project dir *is* `~/.claude`; shared config is mounted on top. New state files shipped by future Claude Code releases are isolated automatically; only new config needs allowlist maintenance, and its absence is immediately visible.
- **Share by default, overlay a state blocklist (rejected).** Every new state file Claude Code adds at the config-dir root would silently regress to cross-container sharing until someone extends the blocklist.
- **Symlinks into a shared mount for root-level files (rejected for now).** Would survive atomic-rename writes, but costs a reconciliation step on every start. We chose plain single-file bind mounts and accept that a rename-style write fails with `EBUSY` — the already-mounted `~/.claude.json` shows this works in practice. If credential refresh ever hits `EBUSY`, symlink-plus-reconcile is the upgrade path.

## Consequences

- **Share allowlist**: config dirs (`skills/`, `agents/`, `commands/`, `hooks/`, `plugins/`) as dir mounts; config files (`CLAUDE.md`, `settings.json`, `.credentials.json`) as single-file mounts; Claude project data (`projects/`, `history.jsonl`) always shared — transcripts are path-keyed, so sandboxes never contend, and resume/history work identically in both modes with no seeding or forking. `~/.claude.json` is unchanged. Users extend the list via `claude_shared_paths` (entries colliding with machine-state paths are rejected).
- **First-run bootstrapping**: missing host sources are pre-created before mounting (Docker would otherwise manufacture a *directory* at a missing file path). `settings.json` seeds as `{}`, `CLAUDE.md` empty. `.credentials.json` is mounted only when present on host; when absent, first login persists into the project state dir and is promoted to the host on a later start.
- **Lifecycle**: a `meta.edn` (project path, created-at) is written beside `dot-claude/` at creation, since project hashes don't reverse; prune tooling is deferred until needed.
- **Scope**: background sessions live and die with their container (daemon state survives on disk; stale PIDs are handled by Claude's own pid+procStart validation, as on a host reboot). Transcripts remain readable across sandboxes, as today. A unified all-projects Agent View is explicitly out of scope — it would require one PID namespace and one `/tmp`.
