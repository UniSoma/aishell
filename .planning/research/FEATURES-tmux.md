# Feature Landscape: tmux Integration in Docker-Based AI Harness

**Domain:** Docker-based AI coding agent sandbox with tmux session management
**Researched:** 2026-01-31
**Confidence:** MEDIUM (ecosystem patterns well-documented, AI-specific workflows emerging)

## Executive Summary

The tmux-in-Docker pattern for AI coding agents is an emerging workflow in 2026, driven by the need for observability and session persistence. The core value proposition is replacing hidden background processes with visible, manageable tmux sessions that developers can inspect and interact with. For aishell's use case, this means:

1. **Auto-started sessions** provide immediate observability when harnesses run
2. **Named containers** enable multiple simultaneous agents with clear identity
3. **Attach workflows** allow developers to "peek in" on running agents or co-debug
4. **Project isolation** prevents name collisions across different codebases

The feature set divides cleanly into table stakes (expected tmux+Docker behavior), differentiators (aishell-specific improvements), and anti-features (complexity to avoid).

## Table Stakes

Features users expect from tmux-in-Docker workflows. Missing these means the feature feels incomplete.

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| tmux installed in base image | Core requirement for any tmux workflow | Low | Single RUN command in Dockerfile |
| Named tmux session creation | Default session names (0, 1, 2) unusable for multi-agent workflows | Low | `tmux new-session -s name` pattern |
| Attach to named session | Must reconnect to existing sessions by name | Low | `tmux attach -t session-name` |
| List running sessions | Users need to discover what's running | Low | `tmux ls` inside container |
| Session persistence during detach | Sessions must survive disconnect/reconnect | Low | Built-in tmux behavior |
| Named Docker containers | Anonymous containers unusable with multiple agents | Low | `docker run --name` flag |
| Container listing by project | Users need to see their running containers | Medium | Filter by name prefix pattern |
| Exec into running container | Must be able to enter container without disrupting PID 1 | Low | `docker exec -it` standard pattern |
| Project isolation in naming | Multiple projects must coexist without collision | Medium | Hash-based name prefix |
| Conflict detection | Prevent starting duplicate container names | Low | `docker ps` check before run |
| Harness-specific session naming | Different harnesses get different session names for clarity | Low | Session name = harness or "main" |
| Non-interactive attach works | Scripts/automation must attach without TTY | Low | `docker exec` without `-t` flag |

## Differentiators

Features that set aishell apart from manual tmux+Docker workflows. Not expected, but highly valued.

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Auto-start harness in tmux | Observability without extra steps | Medium | Requires entrypoint modification to wrap commands |
| Default session name = "main" | Convention over configuration | Low | Simple default for common case |
| Per-harness session customization | Claude in "claude" session, OpenCode in "opencode" session | Low | Map command → session name |
| `aishell attach <name>` CLI | Simpler than `docker exec` + `tmux attach` | Low | Single command abstracts two operations |
| `aishell attach <name> --session <session>` | Direct session targeting without manual tmux commands | Low | Pass session name through to tmux |
| `aishell ps` for current project | Scoped listing more useful than global `docker ps` | Medium | Filter containers by project hash prefix |
| Show session list on attach | If user forgets session name, show available sessions | Medium | Error case improvement (tmux ls output) |
| Graceful handling of no-tmux mode | Shell mode doesn't force tmux on users | Low | Only auto-start for harness commands |
| Multiple containers per project | Different named containers for parallel experimentation | Medium | Supported by naming scheme, needs docs |
| Visual indicator in PS output | Show which containers have active tmux sessions | High | Requires querying tmux state per container |

## Anti-Features

Features to explicitly NOT build. Common mistakes in this domain or unnecessary complexity.

| Anti-Feature | Why Avoid | What to Do Instead |
|--------------|-----------|-------------------|
| Persistent tmux sessions across container restarts | Violates ephemeral container design principle | Document that containers are ephemeral; use git for persistence |
| Automatic tmux session recording/logging | Privacy concern; users expect agent output in terminal only | Let users configure tmux logging if they want it |
| Built-in tmux configuration management | Scope creep; users have existing tmux configs | Mount `~/.tmux.conf` if users want custom config |
| Tmux plugin installation | Version drift, complexity, slow builds | Provide vanilla tmux; users extend via `.aishell/Dockerfile` |
| Window/pane management features | Over-engineering; single session per harness sufficient | One session per harness, let users split manually if needed |
| Session sharing between containers | Complex, requires external tmux server | Each container has isolated tmux server |
| Tmux detach on container exit | Confusing behavior; container should exit when harness exits | Keep current behavior: exit = cleanup |
| Background harness execution | Defeats observability purpose | Always run harness in foreground within tmux |
| Tmux multiplexing across projects | Unnecessary; Docker already provides isolation | One tmux server per container, not shared |
| Auto-attach on container start | Breaks scripting/automation use cases | Explicit attach command keeps flexibility |
| Session resurrection after kill | Users expect clean slate on restart | Ephemeral design: new container = new sessions |
| Fancy status bar configuration | Maintenance burden, subjective preferences | Vanilla tmux status bar |

## Feature Dependencies

```
Foundation layer (must exist first):
├─ tmux in base image
└─ Named Docker containers
   └─ Project-based naming with hash prefix
      ├─ Container conflict detection
      └─ Container listing (ps command)

Harness integration layer (builds on foundation):
├─ Auto-start harness in tmux session
│  ├─ Session name = harness name or "main"
│  └─ Shell mode without auto-start tmux
└─ Attach command
   ├─ Basic attach to default session
   ├─ Named session targeting (--session flag)
   └─ Session discovery on error

Parallel agent support (enabled by naming):
└─ Multiple containers per project
   └─ Unique container names via --name flag
```

## Detailed Feature Specifications

### Auto-Start Harness in tmux

**What:** When running `aishell claude`, `aishell opencode`, etc., the harness starts inside a tmux session named after the harness (or "main" as default).

**Why:** Observability is the primary motivation. Developers can attach later to see what the agent is doing, inspect stuck processes, or co-debug issues.

**How:**
- Modify entrypoint to detect harness commands
- Wrap harness invocation: `tmux new-session -s <session-name> <harness-command>`
- Shell mode (`aishell`) does NOT auto-start tmux (users may not want it)

**Edge cases:**
- If tmux session already exists (re-entry?), attach instead of creating
- Pass through harness exit code correctly
- Handle Ctrl+C gracefully (should exit harness and container)

**Dependency on existing features:**
- Uses existing entrypoint command detection
- Preserves existing ephemeral lifecycle

### Named Containers with Project Isolation

**What:** Container names follow pattern: `aishell-{project-hash}-{name}` where name defaults to harness but can be overridden with `--name` flag.

**Why:**
- Multiple containers per project (parallel agents or experimentation)
- Project isolation (avoid collisions across projects)
- Human-readable names for `docker ps` output

**How:**
- Compute project hash from absolute path (existing pattern in codebase)
- Default name = harness being run (claude, opencode, codex, gemini)
- `--name` flag overrides default
- Full name = `aishell-{hash}-{name}`

**Examples:**
```bash
# Default name = harness
aishell claude
# → container name: aishell-a1b2c3d4-claude

# Override with --name
aishell claude --name experiment
# → container name: aishell-a1b2c3d4-experiment

# Multiple containers same project
aishell claude --name agent1 &
aishell claude --name agent2 &
# → aishell-a1b2c3d4-agent1
# → aishell-a1b2c3d4-agent2
```

**Edge cases:**
- Conflict detection before starting container
- Name validation (Docker name rules: [a-zA-Z0-9][a-zA-Z0-9_.-]*)

**Dependency on existing features:**
- Extends existing hash computation
- Changes existing ephemeral run behavior (adds --name)

### Attach Command

**What:** `aishell attach <name>` enters a running container and attaches to its tmux session.

**Why:** Simpler UX than remembering `docker exec` + `tmux attach` commands.

**How:**
```bash
# Basic attach (default session)
aishell attach <name>
# → docker exec -it aishell-{hash}-{name} tmux attach

# Specific session
aishell attach <name> --session <session-name>
# → docker exec -it aishell-{hash}-{name} tmux attach -t <session-name>
```

**Error handling:**
- Container not found: "Container 'name' not running. Use 'aishell ps' to list containers."
- Tmux session not found: Show available sessions (`tmux ls` output)
- No tmux sessions: "No tmux sessions in container. Did you start a harness?"

**Edge cases:**
- If only one session exists, attach to it automatically
- Non-interactive environments: warn that attach requires TTY

**Dependency on existing features:**
- Uses existing project hash computation
- New command in CLI

### PS Command

**What:** `aishell ps` lists running containers for current project.

**Why:** Project-scoped listing more useful than global `docker ps`.

**How:**
```bash
aishell ps
# → docker ps --filter name=aishell-{hash}-
# → Format output: NAME, STATUS, CREATED
```

**Output format:**
```
NAME                          STATUS          CREATED
agent1                        Up 5 minutes    5 minutes ago
experiment                    Up 2 hours      2 hours ago
```

**Edge cases:**
- No containers running: "No containers running for this project."
- Show full container names with `--verbose` flag

**Dependency on existing features:**
- Uses existing project hash computation
- New command in CLI

### Conflict Detection

**What:** Before starting container with name that already exists, error with clear message.

**Why:** Prevents confusing behavior where old container is stopped/replaced.

**How:**
```bash
# Check before docker run
if docker ps -a --filter name=aishell-{hash}-{name} | grep -q aishell; then
  error "Container '{name}' already exists. Use 'aishell ps' to list containers."
  exit 1
fi
```

**User workflow:**
```bash
$ aishell claude
# ... container runs ...

# In another terminal
$ aishell claude
Error: Container 'claude' already exists for this project.

Running containers:
  claude        Up 2 minutes

Use 'aishell attach claude' to connect, or 'aishell claude --name other' to start another container.
```

**Edge cases:**
- Container exists but stopped: Offer to remove it
- Container from different project with same name: No conflict (different hash)

**Dependency on existing features:**
- Extends existing container launch logic

## MVP Recommendation

For MVP (v2.6.0), prioritize foundational workflows:

### Phase 1: Foundation (Must Have)
1. tmux in base Docker image
2. Named container support with project isolation
3. Auto-start harness in tmux session "main"
4. Basic `aishell attach <name>` command
5. `aishell ps` for listing containers
6. Conflict detection

**Rationale:** These features provide immediate value and establish the core workflow. Users can run multiple agents, list them, and attach to observe them.

### Phase 2: Polish (Should Have)
7. Per-harness session naming (claude/opencode/etc instead of "main")
8. `aishell attach <name> --session <session>` for specific sessions
9. Session discovery on attach error (show tmux ls)
10. Shell mode without auto-start tmux

**Rationale:** These improve UX but aren't blockers. Can iterate based on user feedback.

### Defer to Post-MVP
- Visual session indicators in ps output (HIGH complexity)
- Advanced tmux configuration options
- Session management utilities beyond basic attach

## Integration with Existing Features

| Existing Feature | Integration Point | Changes Required |
|------------------|-------------------|------------------|
| Ephemeral lifecycle | Container still ephemeral, just named | Name doesn't persist after exit |
| Pre-start commands | Still run before harness, tmux doesn't affect them | No changes needed |
| Sensitive file detection | Still runs before container start | No changes needed |
| Config mounting | Same mounts apply to named containers | No changes needed |
| Exec command | Continues to work, but separate from attach | Document difference |
| Gitleaks integration | Still works, unaffected by tmux | No changes needed |

**Critical consideration:** Named containers change the cleanup story. Current behavior is `--rm` flag (auto-remove on exit). Named containers typically don't use `--rm` because name must be available. Solution: Document that containers are ephemeral but require manual cleanup, OR implement auto-cleanup on next run with same name.

## Complexity Assessment

| Feature Category | Implementation Risk | Testing Complexity | Documentation Need |
|------------------|--------------------|--------------------|-------------------|
| tmux installation | Low | Low | Low |
| Named containers | Low | Medium (collision cases) | High (workflow change) |
| Auto-start in tmux | Medium (entrypoint logic) | Medium | Medium |
| Attach command | Low | Low | Low |
| PS command | Low | Low | Low |
| Per-harness sessions | Low | Low | Low |
| Conflict detection | Low | Medium | Medium |

**Overall complexity:** MEDIUM. No individual feature is hard, but the combination requires careful entrypoint design and clear documentation of the new workflow.

## User Workflows

### Workflow 1: Single Agent Observation

```bash
# Start agent
$ aishell claude
# Harness runs in tmux session "main" inside container "claude"

# In another terminal, attach to observe
$ aishell attach claude
# Connected to tmux, sees harness output in real-time
# Detach with Ctrl+b d

# List containers
$ aishell ps
NAME     STATUS         CREATED
claude   Up 5 minutes   5 minutes ago
```

### Workflow 2: Parallel Agents

```bash
# Start two agents for same project
$ aishell claude --name backend-refactor
$ aishell claude --name frontend-refactor

# List them
$ aishell ps
NAME                STATUS          CREATED
backend-refactor    Up 2 minutes    2 minutes ago
frontend-refactor   Up 1 minute     1 minute ago

# Attach to each
$ aishell attach backend-refactor
$ aishell attach frontend-refactor
```

### Workflow 3: Co-Debugging

```bash
# Agent gets stuck
$ aishell claude

# In another terminal
$ aishell attach claude
# See agent is waiting for user input or stuck in loop
# Can interact with tmux, send Ctrl+C to harness, etc.
```

### Workflow 4: Shell Mode (No tmux)

```bash
# User wants plain shell, not harness
$ aishell
# tmux available but not auto-started
# User can manually run `tmux` if desired
```

## Migration Path

**For existing users:**
- Containers remain ephemeral (no behavior change)
- Shell and exec commands work exactly as before
- New `--name` flag is optional (defaults to harness name)
- No breaking changes to existing workflows

**New capabilities:**
- Can now attach to running containers
- Can run multiple containers per project
- Can observe harness output in real-time

**Documentation needs:**
- Clear explanation of named vs anonymous containers
- Examples of common workflows (single agent, parallel agents, attach)
- Explanation of project isolation via hash prefix

## Open Questions for Implementation

1. **Cleanup strategy:** Do named containers auto-remove on exit, or require manual cleanup?
   - **Recommendation:** Use `--rm` but with conflict detection + helpful error message

2. **Session naming:** Always "main" or per-harness (claude/opencode)?
   - **Recommendation:** Per-harness for clarity in multi-session scenarios

3. **Default behavior:** Should `aishell claude` use named container, or require explicit `--name`?
   - **Recommendation:** Always named (default = harness name) for consistency

4. **Multiple harnesses, one container:** Should we support `aishell claude` followed by `aishell attach <name>` then running `opencode`?
   - **Recommendation:** No, one harness per container. Users can run tmux manually in shell mode if they want custom layouts

## Sources

### Ecosystem Research
- [TmuxAI: AI-Powered, Non-Intrusive Terminal Assistant](https://tmuxai.dev/)
- [GitHub - joshuaswarren/agentyard-cli: Workflow orchestration for AI coding assistants](https://github.com/joshuaswarren/agentyard-cli)
- [Forking subagents in an AI coding session with tmux - Kaushik Gopal](https://kau.sh/blog/agent-forking/)
- [GitHub - laris-co/multi-agent-workflow-kit](https://github.com/laris-co/multi-agent-workflow-kit)
- [Building Software Faster with LLMs: Part 2 - Ergonomics and Observability](https://blog.laurentcharignon.com/post/2025-09-30-llm-workflow-part2-ergonomics/)
- [Building with Pi: Coding Agent (mariozechner.at)](https://mariozechner.at/posts/2025-11-30-pi-coding-agent/)

### Docker and Container Management
- [Docker Best Practices 2026](https://thinksys.com/devops/docker-best-practices/)
- [10 Docker Container Naming Convention Best Practices](https://devtodevops.com/blog/docker-container-naming-convention/)
- [Docker attach vs exec & When to Use What - Yasoob Khalid](https://yasoob.me/posts/docker-attach-vs-exec-when-to-use-what/)
- [Mastering Docker Attach vs Exec - TheLinuxCode](https://thelinuxcode.com/docker-attach-vs-exec/)
- [How to filter Docker containers by name | LabEx](https://labex.io/tutorials/docker-how-to-filter-docker-containers-by-name-417741)
- [List and Filter Docker Containers: A Cheat Sheet](https://howtodoinjava.com/devops/list-docker-containers/)

### Tmux Session Management
- [How to use tmux in 2026](https://www.hostinger.com/tutorials/how-to-use-tmux)
- [Using tmux for persistent server sessions](https://brainhack-princeton.github.io/handbook/content_pages/hack_pages/tmux.html)
- [Smart tmux sessions with sesh | Josh Medeski](https://www.joshmedeski.com/posts/smart-tmux-sessions-with-sesh/)
- [GitHub - Dicklesworthstone/ntm: Named Tmux Manager](https://github.com/Dicklesworthstone/ntm)
- [My tmux workflow | Carlos Becker](https://carlosbecker.com/posts/tmux-sessionizer/)

### Docker + tmux Integration
- [tmux in demonized docker container · GitHub](https://gist.github.com/ptrj/b54b8cdd34f632028e669a1e71bc03b8)
- [GitHub - dsummersl/intmux: Connect to multiple docker hosts "in tmux"](https://github.com/dsummersl/intmux)
- [Tmux Cheat Sheet & Quick Reference](https://tmuxcheatsheet.com/)
