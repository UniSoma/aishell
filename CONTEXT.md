# aishell

Docker sandbox for AI coding harnesses: each project runs its harness (Claude Code, OpenCode, Codex, …) inside a per-project container, with shared base images and harness tooling.

## Language

**Harness**:
An AI coding CLI (Claude Code, OpenCode, Codex, Gemini, pi) that aishell installs and runs inside a sandbox container.
_Avoid_: agent, tool, AI

**Harness descriptor**:
The pure-data record of one Harness's facts: identity, canonical label, state and version keys, capabilities (subcommand, launch command, shell alias, harness-volume participation), install source, config paths, and passthrough environment variables.
_Avoid_: harness config, harness entry

**Harness registry**:
The ordered, closed collection of harness descriptors from which every harness-dependent behavior in aishell derives. Closed: users cannot add harnesses; new ones are added in aishell itself.
_Avoid_: harness list, harness table

**Distro image**:
The upstream OS image the Foundation image is built FROM — `debian:trixie-slim`. It carries a minimum glibc version as a declared property (`>= 2.39`), asserted at build time; when a tool outruns the floor, the distro image is bumped rather than the tool vendored per-case.
_Avoid_: base image, OS image

**Foundation image**:
`aishell:foundation` — the bottom image layer, built from a Dockerfile embedded in aishell itself and identical for every user and project. Holds the OS packages, runtimes and shared tooling. Users never edit it; it rebuilds when its embedded content hash changes.
_Avoid_: base (for this layer), system image

**Base image**:
`aishell:base` — the per-user layer between the Foundation image and a project's own extension, built from `~/.aishell/Dockerfile` when one exists and otherwise a plain tag alias of the Foundation image. This is where a user's own machine-wide customizations go.
_Avoid_: foundation, global image

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
