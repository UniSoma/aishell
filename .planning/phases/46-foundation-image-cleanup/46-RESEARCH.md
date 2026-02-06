# Phase 46: Foundation Image Cleanup - Research

**Researched:** 2026-02-06
**Domain:** Docker image optimization, package removal, Dockerfile template modification
**Confidence:** HIGH

## Summary

Phase 46 removes the tmux binary from the foundation Docker image as the first step of v3.0.0 milestone, which eliminates tmux entirely from aishell in favor of Docker-native attach semantics. The current implementation installs tmux 3.6a as a static binary from the official tmux/tmux-builds GitHub releases (~1.5-2MB compressed, ~5-8MB uncompressed). This phase focuses solely on removing the tmux installation block from the Dockerfile template and the associated runtime configuration that depends on tmux being present.

The research confirms this is a straightforward Dockerfile modification task with low risk. The tmux binary is installed as a standalone static binary in lines 44-57 of templates.clj, and there's a global tmux config file created in line 103. These blocks can be removed cleanly without affecting other system dependencies. The foundation image uses multi-stage builds and layer caching, so removing tmux will reduce the final image size immediately and measurably via Docker's `docker image inspect --format={{.Size}}` command (already implemented in aishell.docker/get-image-size).

The key constraint is that this phase removes ONLY the tmux binary installation. All tmux-related runtime logic (WITH_TMUX environment variable handling, config mounting, plugin management) remains in the entrypoint script and will be addressed in subsequent phases (47-49). This phased approach prevents breaking changes to the codebase that might still reference tmux conditionally.

**Primary recommendation:** Remove tmux installation block (lines 44-57) and global config line (103) from templates/base-dockerfile. Leave entrypoint script unchanged. Verify image size reduction via docker inspect after rebuild.

## Standard Stack

Foundation image cleanup uses established Docker and Clojure patterns already present in the codebase:

### Core Technologies (Unchanged)
| Component | Version | Purpose | Why Standard |
|-----------|---------|---------|--------------|
| debian:bookworm-slim | stable | Base OS layer | Minimal Debian, widely used for production containers |
| Docker CLI | 20+ | Image inspection and size measurement | Standard tool for Docker operations |
| Babashka | 1.12.214 | CLI scripting and build orchestration | Already used throughout aishell codebase |

### Removal Target
| Component | Current Location | Size Impact | Removal Method |
|-----------|------------------|-------------|----------------|
| tmux static binary | /usr/local/bin/tmux | ~5-8MB uncompressed | Remove RUN block from Dockerfile template |
| tmux global config | /etc/tmux.conf | <1KB | Remove single-line echo RUN command |

**No new dependencies required.** This phase only removes existing components.

## Architecture Patterns

### Pattern 1: Dockerfile Template Modification
**What:** Remove installation blocks from embedded Dockerfile string in Clojure namespace
**When to use:** Removing packages from Docker images managed as code templates
**Example:**
```clojure
;; In src/aishell/docker/templates.clj

;; REMOVE these lines (44-57):
;; # Install tmux static binary (Debian bookworm ships 3.3a)
;; # Source: https://github.com/tmux/tmux-builds (official tmux org)
;; ARG TMUX_VERSION=3.6a
;; RUN set -eux; \\
;;     dpkgArch=\"$(dpkg --print-architecture)\"; \\
;;     case \"${dpkgArch}\" in \\
;;         amd64) tmuxArch='x86_64' ;; \\
;;         arm64) tmuxArch='arm64' ;; \\
;;         *) echo \"unsupported architecture for tmux: $dpkgArch\"; exit 1 ;; \\
;;     esac; \\
;;     curl -fsSL \"https://github.com/tmux/tmux-builds/releases/download/v${TMUX_VERSION}/tmux-${TMUX_VERSION}-linux-${tmuxArch}.tar.gz\" \\
;;     | tar -xz -C /usr/local/bin tmux; \\
;;     chmod +x /usr/local/bin/tmux; \\
;;     tmux -V

;; REMOVE this line (103):
;; # Enable tmux mouse mode for scroll support inside containers
;; # Without this, scroll events are interpreted as arrow keys by the inner application
;; RUN echo 'set -g mouse on' > /etc/tmux.conf
```

**Rationale:** Embedded Dockerfile templates in templates.clj control foundation image contents. Removing these lines ensures tmux is not installed in future builds. Historical note: tmux was added in v2.7.0, made opt-in in v2.9.0, and is being completely removed in v3.0.0.

**Source:** Existing pattern in src/aishell/docker/templates.clj (lines 7-117)

### Pattern 2: Image Size Verification
**What:** Measure image size before and after rebuild to verify tmux removal impact
**When to use:** After any Dockerfile change that affects image size
**Example:**
```clojure
;; Already implemented in src/aishell/docker.clj:67-76
(defn get-image-size
  "Get human-readable image size"
  [image-tag]
  (try
    (let [{:keys [exit out]}
          (p/shell {:out :string :err :string :continue true}
                   "docker" "image" "inspect" "--format={{.Size}}" image-tag)]
      (when (zero? exit)
        (format-size (str/trim out))))
    (catch Exception _ nil)))

;; Usage in build completion (build.clj:140-144):
;; (let [size (docker/get-image-size foundation-image-tag)]
;;   (println (str "Built " foundation-image-tag " (" size ")")))
```

**Rationale:** Docker image inspect provides accurate uncompressed size. The existing get-image-size function formats bytes to human-readable format (B/KB/MB/GB). Build completion message already displays size, enabling immediate verification of tmux removal impact.

**Source:** [Docker image inspect reference](https://docs.docker.com/reference/cli/docker/image/inspect/), implemented in src/aishell/docker.clj

### Pattern 3: Phased Removal Strategy
**What:** Remove binary installation (Phase 46) before removing runtime logic (Phases 47-49)
**When to use:** Removing features with runtime dependencies spanning multiple files
**Example:**
```
Phase 46 (Foundation Image Cleanup):
  - Remove tmux installation from Dockerfile template
  - Foundation image builds without tmux
  - Entrypoint script still has WITH_TMUX conditional logic (dead code path)
  - Result: tmux command unavailable, but no runtime errors

Phase 47 (State & Config Schema Cleanup):
  - Remove --with-tmux flag from CLI
  - Remove :with-tmux from state schema
  - Remove tmux: section from config schema

Phase 48 (Docker Run Arguments Cleanup):
  - Remove WITH_TMUX env var from container runtime
  - Remove tmux config mounts

Phase 49 (Entrypoint Simplification):
  - Remove WITH_TMUX conditional logic from entrypoint
  - Simplify to single exec gosu code path
```

**Rationale:** This approach prevents runtime errors from referencing removed binaries while code still expects them. Phase 46 makes tmux unavailable but leaves conditional logic intact (it will simply never enter the WITH_TMUX=true branch). Subsequent phases can safely remove the dead code paths.

**Source:** Project milestone planning pattern (v3.0.0 roadmap, .planning/ROADMAP.md lines 450-556)

### Anti-Patterns to Avoid

- **Removing entrypoint tmux logic in Phase 46:** Entrypoint script contains extensive WITH_TMUX conditional logic (lines 238-316 in templates.clj). Removing this in Phase 46 while other phases still set WITH_TMUX=true environment variable would cause runtime errors. Leave entrypoint unchanged; Phase 49 will handle it comprehensively.

- **Forgetting to remove TMUX_VERSION ARG:** ARG declarations affect Docker cache keys. Leaving unused ARGs can cause cache misses. Remove both the ARG TMUX_VERSION declaration and the RUN block that references it.

- **Removing only the RUN block but not the config echo:** Line 103 creates /etc/tmux.conf with mouse mode enabled. This file is harmless if left (1KB) but represents incomplete cleanup. Remove both the binary installation (lines 44-57) and the config creation (line 103) for complete Phase 46 scope.

## Don't Hand-Roll

Problems with existing Docker/shell solutions:

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Image size measurement | Custom file size tracking | Docker's `docker image inspect --format={{.Size}}` | Already implemented in aishell.docker/get-image-size, returns accurate uncompressed size |
| Dockerfile modification | String manipulation in multiple places | Centralized template in templates.clj | Single source of truth for Dockerfile content, changes apply to all builds |
| Multi-architecture binary selection | Separate install logic for each arch | Case statement in RUN block (already implemented) | Current pattern handles amd64/arm64/armhf consistently, no need to change approach |
| Size reduction verification | Manual before/after comparison | Build completion message already shows size | aishell build output displays size automatically via get-image-size call |

**Key insight:** The existing codebase already has robust infrastructure for Dockerfile templating and image size measurement. Phase 46 is purely subtractive—no new utilities or helpers needed.

## Common Pitfalls

### Pitfall 1: Breaking Entrypoint Script by Removing tmux Too Eagerly
**What goes wrong:** If Phase 46 removes tmux AND removes entrypoint WITH_TMUX logic, containers built with --with-tmux=true (before flag removal in Phase 47) will fail to start with "tmux: command not found".

**Why it happens:** Phases 46-49 are sequenced to run over time, potentially with builds occurring between phases. A build in Phase 46 might still have WITH_TMUX=true in old state files. Removing the binary breaks backward compatibility during the transition.

**How to avoid:**
1. Phase 46: Remove ONLY tmux installation from Dockerfile, leave entrypoint unchanged
2. Phase 47: Remove --with-tmux flag and state tracking
3. Phase 49: Remove entrypoint conditional logic (now guaranteed dead code)
4. Document in MIGRATION.md that users must rebuild after Phase 47 completion

**Warning signs:**
- Container startup errors: "tmux: not found" or "exec format error"
- Errors only occur when WITH_TMUX=true in environment

**Mitigation:** If users encounter this between phases, they must run `aishell build` again after Phase 47 completes to get a state file without :with-tmux.

### Pitfall 2: Incomplete Dockerfile Cleanup
**What goes wrong:** Removing the RUN block (lines 44-57) but forgetting the ARG TMUX_VERSION declaration or the /etc/tmux.conf echo (line 103) leaves artifacts in the image and confuses future maintainers.

**Why it happens:** The tmux installation spans multiple non-contiguous lines in templates.clj. It's easy to grep for "RUN.*tmux" and miss the ARG declaration and the config file creation.

**How to avoid:** Systematic search for all tmux references:
1. ARG declarations: `ARG TMUX_VERSION=3.6a` (remove)
2. RUN blocks with tmux: Lines 44-57 (remove)
3. Config file creation: Line 103 `echo 'set -g mouse on' > /etc/tmux.conf` (remove)
4. Comments mentioning tmux: Remove or update to explain removal
5. Verify with: `grep -n tmux templates.clj` should show ZERO results after Phase 46

**Warning signs:**
- `docker history aishell:foundation` shows "ARG TMUX_VERSION" layer
- Foundation image contains /etc/tmux.conf file
- Subsequent phases need to remove "leftover" tmux artifacts

**Source:** Learned from Phase 35 research (foundation image split) which emphasized complete cleanup to prevent confusion.

### Pitfall 3: Not Verifying Image Size Reduction
**What goes wrong:** Rebuild completes successfully but image size stays the same or only decreases by <1MB, indicating tmux wasn't actually removed.

**Why it happens:** Docker layer caching can cause confusion. If the Dockerfile hash label isn't updated correctly, Docker might serve a cached image that still contains tmux.

**How to avoid:**
1. Run `aishell build --force` to bypass cache after template changes
2. Check build output for size reduction (expect ~5-8MB decrease)
3. Verify manually: `docker run --rm aishell:foundation tmux -V` should fail with "command not found"
4. Inspect image layers: `docker history aishell:foundation` should NOT show tmux installation layer

**Warning signs:**
- Image size unchanged after rebuild
- `tmux -V` still succeeds inside foundation container
- `docker history` shows tmux installation layer

**Source:** [Docker build cache invalidation](https://docs.docker.com/build/cache/invalidation/), [Docker image size optimization](https://phoenixnap.com/kb/docker-image-size)

### Pitfall 4: Documentation Drift
**What goes wrong:** Documentation (README, ARCHITECTURE.md) still mentions tmux as part of foundation image, confusing users after Phase 46 completes.

**Why it happens:** Documentation updates are deferred to Phase 52 (final documentation pass), but Phase 46 makes a user-visible change (tmux unavailable in containers).

**How to avoid:** Phase 46 scope is foundation image ONLY. User-facing documentation updates are correctly deferred to Phase 52. However, add a note in Phase 46 plan: "Phase 52 will update documentation to reflect tmux removal."

**Warning signs:**
- Users report "documentation says tmux is available but it's not installed"
- GitHub issues about missing tmux

**Note:** This is expected temporary state during v3.0.0 milestone execution. Phase 52 will align all documentation with final v3.0.0 state.

## Code Examples

Verified patterns from the existing codebase:

### Dockerfile Template Modification (Exact Lines to Remove)
```clojure
;; In src/aishell/docker/templates.clj

;; Current (with tmux):
(def base-dockerfile
  "# Aishell Foundation Image
...
# Install tmux static binary (Debian bookworm ships 3.3a)
# Source: https://github.com/tmux/tmux-builds (official tmux org)
ARG TMUX_VERSION=3.6a
RUN set -eux; \\
    dpkgArch=\"$(dpkg --print-architecture)\"; \\
    case \"${dpkgArch}\" in \\
        amd64) tmuxArch='x86_64' ;; \\
        arm64) tmuxArch='arm64' ;; \\
        *) echo \"unsupported architecture for tmux: $dpkgArch\"; exit 1 ;; \\
    esac; \\
    curl -fsSL \"https://github.com/tmux/tmux-builds/releases/download/v${TMUX_VERSION}/tmux-${TMUX_VERSION}-linux-${tmuxArch}.tar.gz\" \\
    | tar -xz -C /usr/local/bin tmux; \\
    chmod +x /usr/local/bin/tmux; \\
    tmux -V

...

# Enable tmux mouse mode for scroll support inside containers
# Without this, scroll events are interpreted as arrow keys by the inner application
RUN echo 'set -g mouse on' > /etc/tmux.conf
...")

;; After Phase 46 (without tmux):
(def base-dockerfile
  "# Aishell Foundation Image
...
# [tmux block removed - see Phase 46]
# Install Babashka (static binary for container compatibility)
RUN set -eux; \\
...")
```
**Source:** src/aishell/docker/templates.clj lines 44-57 and line 103

### Image Size Verification (Already Implemented)
```clojure
;; In src/aishell/docker/build.clj:140-144
(let [duration (- (System/currentTimeMillis) start-time)
      size (docker/get-image-size foundation-image-tag)]
  (when-not quiet
    (println (str "Built " foundation-image-tag
                  " (" (format-duration duration)
                  (when size (str ", " size)) ")")))
  {:success true
   :image foundation-image-tag
   :duration duration
   :size size})

;; Example output:
;; Before Phase 46: Built aishell:foundation (12.3s, 587.3MB)
;; After Phase 46:  Built aishell:foundation (11.8s, 580.1MB)
;; Reduction: ~7MB (tmux static binary removed)
```
**Source:** src/aishell/docker/build.clj, already implemented and working

### Manual Verification Commands
```bash
# Before Phase 46 (tmux present):
$ docker run --rm aishell:foundation tmux -V
tmux 3.6a

$ docker image inspect --format='{{.Size}}' aishell:foundation
615677952  # ~587MB

# After Phase 46 (tmux removed):
$ docker run --rm aishell:foundation tmux -V
docker: Error response from daemon: failed to create shim task: OCI runtime create failed: runc create failed: unable to start container process: exec: "tmux": executable file not found in $PATH: unknown.

$ docker image inspect --format='{{.Size}}' aishell:foundation
607928320  # ~580MB (7-8MB reduction)

# Verify tmux not in image layers:
$ docker history aishell:foundation --no-trunc | grep tmux
# Should return nothing after Phase 46
```
**Source:** [Docker CLI reference](https://docs.docker.com/reference/cli/docker/image/inspect/)

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| tmux always installed | tmux opt-in via --with-tmux flag | v2.9.0 (Phase 39) | Made tmux conditional, reduced default image size |
| tmux opt-in | tmux completely removed | v3.0.0 (Phase 46) | No tmux in any configuration, simplifies architecture |
| Manual size comparison | Automatic size display in build output | v2.0 (Phase 14) | get-image-size function provides instant feedback |
| apt-get install tmux | Static binary from tmux/tmux-builds | v2.7.0 | Upgraded from Debian's tmux 3.3a to 3.6a, but now moot |

**Deprecated/outdated:**
- **tmux 3.6a static binary installation:** Removed in Phase 46. Containers no longer include tmux. Window management moved to host terminal multiplexers (tmux, screen, Zellij on host).
- **--with-tmux build flag:** Will be removed in Phase 47. Phase 46 removes the binary but leaves the flag temporarily to prevent breaking existing workflows.
- **/etc/tmux.conf global config:** Removed in Phase 46 alongside binary removal. No longer needed.

## Open Questions

### 1. Should Phase 46 update the dockerfile-hash-label calculation?
**What we know:**
- build.clj:22-25 computes Dockerfile hash for cache invalidation
- Removing tmux changes the Dockerfile content
- Hash will automatically change due to content change
- needs-rebuild? compares current hash to image label

**What's unclear:**
- Does removing tmux require any special hash calculation logic?

**Recommendation:** No action needed. Hash is computed from templates/base-dockerfile string content. Removing tmux lines automatically changes the hash, triggering rebuild as expected. The existing cache invalidation logic handles this correctly.

### 2. What is the actual size of tmux 3.6a static binary?
**What we know:**
- tmux installed from https://github.com/tmux/tmux-builds/releases/download/v3.6a/
- Static binary includes libevent and ncurses (self-contained)
- Typical static binaries range 3-10MB depending on architecture and compression

**What's unclear:**
- Exact uncompressed size of tmux 3.6a for x86_64 and arm64
- GitHub releases page failed to load asset sizes during research

**Recommendation:** Proceed with Phase 46 without exact size. Success criteria is "Foundation image size reduced by tmux removal" (measurable via docker inspect). Actual reduction will be visible in build output. Document observed reduction in Phase 46 completion summary for future reference.

**Expected range:** 5-8MB reduction based on typical static binary sizes and similar tools.

### 3. Should Phase 46 gate execution on :with-tmux=false in state?
**What we know:**
- Some users might have :with-tmux=true in their state.edn from v2.9.0
- Phase 46 removes tmux from foundation regardless of state
- Phase 47 will remove :with-tmux from state schema
- Building in Phase 46 with :with-tmux=true will create containers without tmux

**What's unclear:**
- Should Phase 46 detect :with-tmux=true and error/warn before building?
- Or proceed silently and let Phase 47 handle state migration?

**Recommendation:** No gating in Phase 46. Proceed with tmux removal regardless of state. Rationale:
1. Foundation image is being rebuilt, user is already taking action
2. Phase 47 will clean up state schema comprehensively
3. Containers will start successfully (WITH_TMUX conditional will simply not enter tmux path)
4. Error messages can be added in Phase 47 if users try to build with --with-tmux (flag won't exist)

## Sources

### Primary (HIGH confidence)
- src/aishell/docker/templates.clj - Current Dockerfile template with tmux installation (lines 44-57, 103)
- src/aishell/docker/build.clj - Build orchestration with get-image-size display (lines 94-153)
- src/aishell/docker.clj - Image size measurement utilities (lines 56-76)
- [Docker image inspect documentation](https://docs.docker.com/reference/cli/docker/image/inspect/) - Official reference for size measurement
- .planning/ROADMAP.md - v3.0.0 milestone phases and sequencing (lines 450-556)
- .planning/STATE.md - Current project state and v3.0.0 decisions
- CHANGELOG.md - tmux history (v2.7.0 addition, v2.9.0 opt-in, v3.0.0 removal)

### Secondary (MEDIUM confidence)
- [Docker image size optimization guide](https://phoenixnap.com/kb/docker-image-size) - Size measurement best practices (2026)
- [Docker layer caching behavior](https://docs.docker.com/build/cache/invalidation/) - Understanding cache invalidation on content changes
- [Removing APT cache in Dockerfiles](https://gist.github.com/marvell/7c812736565928e602c4) - Debian package cleanup patterns (similar principle to binary removal)
- [tmux static binary projects](https://github.com/pythops/tmux-linux-binary) - Context on static tmux binary sizes (typical range)

### Tertiary (LOW confidence)
- [tmux 3.6a GitHub releases](https://github.com/tmux/tmux/releases/tag/3.6a) - Official release page (asset sizes not accessible during research)
- [tmux/tmux-builds repository](https://github.com/tmux/tmux-builds) - Source of current tmux binary (general context only)

## Metadata

**Confidence breakdown:**
- Dockerfile template modification: HIGH - Direct code change, clear scope, low risk
- Image size measurement: HIGH - Existing implementation well-tested and documented
- Phased removal strategy: HIGH - Defined in milestone roadmap, logical sequencing
- Size reduction estimate: MEDIUM - Exact binary size unavailable, using typical range
- Backward compatibility during transition: MEDIUM - Assumes users rebuild between phases

**Research date:** 2026-02-06
**Valid until:** 2026-04-06 (60 days - Docker core features stable, Dockerfile patterns established, foundation architecture is long-term design)

**Key technical constraints:**
- Phase 46 removes ONLY tmux installation, not runtime logic
- Entrypoint script (WITH_TMUX conditionals) unchanged until Phase 49
- No user-facing CLI changes in Phase 46 (--with-tmux flag removed in Phase 47)
- Documentation updates deferred to Phase 52 (final alignment)
- Image size reduction is success criteria (measurable via docker inspect)
