# Phase 24: Dockerfile & Build Infrastructure - Research

**Researched:** 2026-01-25
**Domain:** Docker image building, CLI installation, build state management
**Confidence:** HIGH

## Summary

This research covers extending the aishell Docker image and Clojure build system to support OpenAI Codex CLI and Google Gemini CLI installation alongside the existing Claude Code and OpenCode harnesses. Both new CLIs use npm for installation, which aligns perfectly with the existing Node.js 24 already present in the base image.

Key findings:
- Codex CLI installs via `npm install -g @openai/codex` (latest stable 0.89.0)
- Gemini CLI installs via `npm install -g @google/gemini-cli` (latest stable 0.24.0)
- Both support version pinning via npm's `@version` syntax
- Both require Node.js 20+ (Gemini) / 18+ (Codex), which is satisfied by Node.js 24 in the current image
- Build state tracking and version change detection follow existing patterns for Claude/OpenCode

**Primary recommendation:** Extend the existing build infrastructure by adding `--with-codex` and `--with-gemini` build flags, mirroring the existing `--with-claude` and `--with-opencode` patterns. Use npm installation (matching Claude Code) since Node.js is already in the image.

## Standard Stack

The established libraries/tools for this domain:

### Core
| Component | Version | Purpose | Why Standard |
|-----------|---------|---------|--------------|
| @openai/codex | 0.89.0 (stable) | OpenAI's Codex CLI agent | Official npm package from OpenAI |
| @google/gemini-cli | 0.24.0 (stable) | Google's Gemini CLI agent | Official npm package from Google |
| Node.js | 24-bookworm-slim | Runtime for npm packages | Already in base image, meets requirements |
| npm | (bundled with Node.js) | Package installation | Already in base image |

### Supporting
| Component | Version | Purpose | When to Use |
|-----------|---------|---------|-------------|
| Homebrew | N/A | Alternative installer | macOS host, not for Docker |
| npx | (bundled) | Run without install | Local development only |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| npm install | brew install codex | Homebrew not in Debian image, would add complexity |
| npm install | npx @google/gemini-cli | npx downloads each run, defeats caching |
| Global install | Curl install scripts | No official curl scripts for these CLIs |

**Installation:**
```bash
# In Dockerfile (conditional on build args)
npm install -g @openai/codex
npm install -g @google/gemini-cli

# With version pinning
npm install -g @openai/codex@0.89.0
npm install -g @google/gemini-cli@0.24.0
```

## Architecture Patterns

### Existing Build Flag Pattern
```clojure
;; In cli.clj - existing pattern
(def build-spec
  {:with-claude   {:desc "Include Claude Code (optional: =VERSION)"}
   :with-opencode {:desc "Include OpenCode (optional: =VERSION)"}
   ;; Add new flags following same pattern:
   :with-codex    {:desc "Include Codex CLI (optional: =VERSION)"}
   :with-gemini   {:desc "Include Gemini CLI (optional: =VERSION)"}
   ...})
```

### Pattern 1: Conditional npm Installation in Dockerfile
**What:** Use build args to conditionally install npm packages
**When to use:** Optional harness installation at build time

```dockerfile
# In templates.clj - base-dockerfile string
ARG WITH_CODEX=false
ARG WITH_GEMINI=false
ARG CODEX_VERSION=""
ARG GEMINI_VERSION=""

# Install Codex CLI if requested
RUN if [ "$WITH_CODEX" = "true" ]; then \
        if [ -n "$CODEX_VERSION" ]; then \
            npm install -g @openai/codex@"$CODEX_VERSION"; \
        else \
            npm install -g @openai/codex; \
        fi \
    fi

# Install Gemini CLI if requested
RUN if [ "$WITH_GEMINI" = "true" ]; then \
        if [ -n "$GEMINI_VERSION" ]; then \
            npm install -g @google/gemini-cli@"$GEMINI_VERSION"; \
        else \
            npm install -g @google/gemini-cli; \
        fi \
    fi
```

### Pattern 2: Build State Extension
**What:** Extend state.edn schema to track new harnesses
**When to use:** Build state persistence for version tracking

```clojure
;; Extended state schema in state.clj
{:with-claude true
 :with-opencode false
 :with-codex true        ; NEW
 :with-gemini true       ; NEW
 :claude-version "2.0.22"
 :opencode-version nil
 :codex-version "0.89.0" ; NEW
 :gemini-version nil     ; NEW (nil means latest)
 :image-tag "aishell:base"
 :build-time "2026-01-25..."
 :dockerfile-hash "abc123def456"}
```

### Pattern 3: Version Change Detection
**What:** Trigger rebuild when harness version changes
**When to use:** Cache invalidation for version updates

```clojure
;; In build.clj - extend version-changed? function
(defn version-changed?
  [opts state]
  (or
    ;; Existing checks
    (and (:with-claude opts)
         (not= (:claude-version opts) (:claude-version state)))
    (and (:with-opencode opts)
         (not= (:opencode-version opts) (:opencode-version state)))
    ;; New checks for Codex and Gemini
    (and (:with-codex opts)
         (not= (:codex-version opts) (:codex-version state)))
    (and (:with-gemini opts)
         (not= (:gemini-version opts) (:gemini-version state)))
    ;; Harness added that wasn't in previous build
    (and (:with-claude opts) (not (:with-claude state)))
    (and (:with-opencode opts) (not (:with-opencode state)))
    (and (:with-codex opts) (not (:with-codex state)))
    (and (:with-gemini opts) (not (:with-gemini state)))))
```

### Pattern 4: Build Summary Display
**What:** Show installed harness versions in build output
**When to use:** After successful build completion

```clojure
;; In build.clj - extend format-harness-line usage
(when (:with-claude opts)
  (println (format-harness-line "Claude Code" (:claude-version opts))))
(when (:with-opencode opts)
  (println (format-harness-line "OpenCode" (:opencode-version opts))))
(when (:with-codex opts)
  (println (format-harness-line "Codex" (:codex-version opts))))
(when (:with-gemini opts)
  (println (format-harness-line "Gemini" (:gemini-version opts))))
```

### Anti-Patterns to Avoid
- **Using curl install scripts:** Codex and Gemini don't have official curl install scripts like Claude/OpenCode
- **Installing in separate RUN layers per harness:** Bloats image layers; combine npm installs when possible
- **Hardcoding versions in Dockerfile:** Use build args for flexibility

## Don't Hand-Roll

Problems that look simple but have existing solutions:

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Version validation | Custom regex | Existing semver-pattern in cli.clj | Already validates Claude/OpenCode versions |
| Build arg construction | New function | Extend build-docker-args | Same pattern, just more args |
| State persistence | New file format | Extend state.edn schema | Consistent with existing state management |
| Help text formatting | Manual strings | Extend build-spec | cli/format-opts handles display |

**Key insight:** This phase is primarily about extension, not invention. All the patterns for build flags, state tracking, and version management already exist. The implementation is adding new keys to existing data structures and new conditions to existing functions.

## Common Pitfalls

### Pitfall 1: npm Permission Issues
**What goes wrong:** npm install -g fails with EACCES permission denied
**Why it happens:** npm trying to write to /usr/local in Docker as non-root
**How to avoid:** Run npm install as root during Docker build (before gosu drops privileges)
**Warning signs:** Build fails with "permission denied" or "EACCES"

### Pitfall 2: Node.js Version Mismatch
**What goes wrong:** Gemini CLI fails at runtime with "unsupported engine" warning
**Why it happens:** Using Node.js < 20 (Gemini requires 20+)
**How to avoid:** Image already uses Node.js 24, verify with `node --version` in build output
**Warning signs:** Runtime warnings about Node.js version

### Pitfall 3: Version String Format
**What goes wrong:** Invalid version passed to npm causes install failure
**Why it happens:** User passes version with "v" prefix or invalid format
**How to avoid:** Reuse existing validate-version function from cli.clj
**Warning signs:** npm error "No matching version found"

### Pitfall 4: Empty Version Treated as Latest
**What goes wrong:** State shows nil version but user expected specific version
**Why it happens:** Flag parsed as boolean true, not version string
**How to avoid:** Follow existing parse-with-flag pattern that distinguishes true from "version"
**Warning signs:** State shows nil for version when user specified one

### Pitfall 5: Stale Build Detection False Negatives
**What goes wrong:** Version changed but rebuild not triggered
**Why it happens:** version-changed? function not checking new harnesses
**How to avoid:** Add all new harness checks to version-changed? function
**Warning signs:** `aishell build --with-codex=1.0.0` shows "up to date" when version differs

## Code Examples

Verified patterns from official sources and existing codebase:

### Dockerfile Installation Block
```dockerfile
# Source: Existing templates.clj pattern + npm documentation
ARG WITH_CODEX=false
ARG WITH_GEMINI=false
ARG CODEX_VERSION=""
ARG GEMINI_VERSION=""

# Install Codex CLI if requested (npm global)
# npm global installs to /usr/local/bin/codex
RUN if [ "$WITH_CODEX" = "true" ]; then \
        if [ -n "$CODEX_VERSION" ]; then \
            npm install -g @openai/codex@"$CODEX_VERSION"; \
        else \
            npm install -g @openai/codex; \
        fi \
    fi

# Install Gemini CLI if requested (npm global)
# npm global installs to /usr/local/bin/gemini
RUN if [ "$WITH_GEMINI" = "true" ]; then \
        if [ -n "$GEMINI_VERSION" ]; then \
            npm install -g @google/gemini-cli@"$GEMINI_VERSION"; \
        else \
            npm install -g @google/gemini-cli; \
        fi \
    fi
```

### CLI Spec Extension
```clojure
;; Source: Existing cli.clj pattern
(def build-spec
  {:with-claude   {:desc "Include Claude Code (optional: =VERSION)"}
   :with-opencode {:desc "Include OpenCode (optional: =VERSION)"}
   :with-codex    {:desc "Include Codex CLI (optional: =VERSION)"}
   :with-gemini   {:desc "Include Gemini CLI (optional: =VERSION)"}
   :force         {:coerce :boolean :desc "Force rebuild (bypass Docker cache)"}
   :verbose       {:alias :v :coerce :boolean :desc "Show full Docker build output"}
   :help          {:alias :h :coerce :boolean :desc "Show build help"}})
```

### Build Args Extension
```clojure
;; Source: Existing build.clj pattern
(defn- build-docker-args
  [{:keys [with-claude with-opencode with-codex with-gemini
           claude-version opencode-version codex-version gemini-version]}
   dockerfile-hash]
  (cond-> []
    with-claude (conj "--build-arg" "WITH_CLAUDE=true")
    with-opencode (conj "--build-arg" "WITH_OPENCODE=true")
    with-codex (conj "--build-arg" "WITH_CODEX=true")
    with-gemini (conj "--build-arg" "WITH_GEMINI=true")
    claude-version (conj "--build-arg" (str "CLAUDE_VERSION=" claude-version))
    opencode-version (conj "--build-arg" (str "OPENCODE_VERSION=" opencode-version))
    codex-version (conj "--build-arg" (str "CODEX_VERSION=" codex-version))
    gemini-version (conj "--build-arg" (str "GEMINI_VERSION=" gemini-version))
    true (conj "--label" (str dockerfile-hash-label "=" dockerfile-hash))))
```

### State Schema Extension
```clojure
;; Source: state.clj documentation comment - extended
;; State schema:
;; {:with-claude true
;;  :with-opencode false
;;  :with-codex true
;;  :with-gemini false
;;  :claude-version "2.0.22"
;;  :opencode-version nil
;;  :codex-version "0.89.0"
;;  :gemini-version nil
;;  :image-tag "aishell:base"
;;  :build-time "2026-01-25..."
;;  :dockerfile-hash "abc123def456"}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Curl install scripts | npm install for Codex/Gemini | Always (these CLIs) | Simpler, consistent with existing npm patterns |
| Separate version tracking | Unified state.edn schema | v2.0.0 | Single source of truth for all harnesses |
| Manual cache invalidation | Hash-based + version detection | v2.0.0 | Automatic rebuild on changes |

**Deprecated/outdated:**
- N/A for Codex/Gemini - these are new harness integrations
- Note: Claude Code's npm installation still works but native is preferred (already handled)

## Open Questions

Things that couldn't be fully resolved:

1. **Codex CLI Headless Authentication**
   - What we know: Codex supports `codex login --device-auth` for headless environments
   - What's unclear: Whether cached credentials in ~/.codex/auth.json need special handling
   - Recommendation: Mount ~/.codex directory similar to ~/.claude (Phase 25 concern)

2. **Gemini CLI Initial Login Flow**
   - What we know: First run prompts for browser-based Google login
   - What's unclear: Best UX for container-based authentication
   - Recommendation: Document that users should run `gemini` on host first to authenticate, then config is mounted (Phase 25 concern)

3. **npm Global Install Location**
   - What we know: npm installs to /usr/local/bin which is in PATH
   - What's unclear: Whether both CLIs have identical behavior
   - Recommendation: Test during implementation; add symlinks if needed (matching OpenCode pattern)

## Sources

### Primary (HIGH confidence)
- [OpenAI Codex CLI npm](https://www.npmjs.com/package/@openai/codex) - Package details, version info
- [OpenAI Codex Quickstart](https://developers.openai.com/codex/quickstart/) - Installation methods
- [OpenAI Codex Auth Docs](https://developers.openai.com/codex/auth/) - Authentication methods
- [OpenAI Codex Config Reference](https://developers.openai.com/codex/config-reference/) - Configuration options
- [OpenAI Codex Non-Interactive](https://developers.openai.com/codex/noninteractive/) - CODEX_API_KEY usage
- [Google Gemini CLI GitHub](https://github.com/google-gemini/gemini-cli) - Installation, Node.js requirements
- [Gemini CLI Authentication](https://github.com/google-gemini/gemini-cli/blob/main/docs/get-started/authentication.md) - Auth methods, env vars
- [Gemini CLI Configuration](https://geminicli.com/docs/get-started/configuration/) - Config file structure
- [OpenAI Codex Releases](https://github.com/openai/codex/releases) - Version history

### Secondary (MEDIUM confidence)
- Existing codebase: cli.clj, build.clj, state.clj, templates.clj - Implementation patterns
- Phase 3 Research - Prior harness integration patterns

### Tertiary (LOW confidence)
- None - all claims verified with official documentation

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - Official npm packages, official documentation
- Architecture: HIGH - Extends proven existing patterns from codebase
- Pitfalls: MEDIUM - Some based on general npm/Docker experience, not harness-specific issues

**Research date:** 2026-01-25
**Valid until:** 14 days (CLI tools update weekly, version pinning recommended)

---
*Phase: 24-dockerfile---build-infrastructure*
*Research completed: 2026-01-25*
