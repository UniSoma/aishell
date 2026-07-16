# aishell

Docker sandbox for AI coding harnesses: each project runs its harness (Claude Code, OpenCode, Codex, …) inside a per-project container, with shared base images and harness tooling.

## Language

**Harness**:
An AI coding CLI (Claude Code, OpenCode, Codex, Gemini, pi) that aishell installs and runs inside a sandbox container.
_Avoid_: agent, tool, AI

**Project hash**:
The 8-character SHA-256 prefix of a project's canonicalized directory path. Keys everything aishell scopes per project (container names, per-project state).

**Sandbox**:
The Docker container a project's harness runs in, named `aishell-{project-hash}-{name}`.
_Avoid_: box, environment

**Claude config**:
The slowly-changing Claude Code files a user deliberately curates and wants identical in every sandbox: skills, agents, commands, hooks, plugins, memory (CLAUDE.md), settings, credentials.

**Claude machine state**:
The PID- and socket-bearing runtime data Claude Code writes as it runs: supervisor/daemon registry, background jobs, session locks. Meaningful only within one machine/PID namespace, so sharing it across sandboxes corrupts it.
_Avoid_: cache, state (unqualified)

**Claude project data**:
Portable per-project records Claude Code accumulates: session transcripts and prompt history. Keyed by project path, inert across sandboxes, and shared in every isolation mode.
_Avoid_: history (as a category name)

**Claude isolation**:
Per-project choice of whether a sandbox shares the user's Claude machine state (`shared`, the default — today's behavior) or gets its own keyed by project hash (`project`). Claude config and Claude project data are shared in both modes.
