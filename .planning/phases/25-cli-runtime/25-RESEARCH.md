# Phase 25: CLI & Runtime - Research

**Researched:** 2026-01-25
**Domain:** CLI pass-through commands, Docker container runtime, environment variable handling, config directory mounting
**Confidence:** HIGH

## Summary

This research covers implementing `aishell codex` and `aishell gemini` commands that run Codex and Gemini CLI tools in Docker containers with proper configuration mounting and environment variable passthrough. The implementation extends the existing patterns established for `aishell claude` and `aishell opencode` commands.

Key findings:
- Codex uses `~/.codex/` for configuration and authentication (auth.json, config.toml)
- Gemini uses `~/.gemini/` for configuration (settings.json) and `.gemini/.env` for API keys
- CODEX_API_KEY is only supported in `codex exec` mode (non-interactive); OPENAI_API_KEY works for login
- Gemini supports both GEMINI_API_KEY and GOOGLE_API_KEY, plus Vertex AI credentials
- GOOGLE_APPLICATION_CREDENTIALS must be handled specially (file mount, not just env var)
- Both harnesses follow the same pass-through pattern as claude/opencode (no special flags needed)
- harness_args config support requires adding "codex" and "gemini" to known-harnesses set

**Primary recommendation:** Extend the existing CLI dispatch and run infrastructure by adding codex/gemini cases that mirror the opencode pattern (simple pass-through), mount ~/.codex and ~/.gemini directories, and pass through the appropriate API key environment variables.

## Standard Stack

The established libraries/tools for this domain:

### Core
| Component | Purpose | Why Standard |
|-----------|---------|--------------|
| babashka.cli | CLI routing and dispatch | Already used for claude/opencode commands |
| babashka.process | Docker process execution (p/exec) | Already used for container invocation |
| aishell.run | Run command orchestration | Existing pattern for harness execution |
| aishell.docker.run | Docker argument construction | Existing pattern for mounts/env vars |

### Supporting
| Component | Purpose | When to Use |
|-----------|---------|-------------|
| aishell.config | harness_args loading | Default arguments from config.yaml |
| aishell.state | Build state verification | Check harness was installed |
| aishell.output | Error/warning display | User feedback for missing harnesses |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Pass-through dispatch | Separate dispatch table entry | Pass-through ensures all harness args/flags work; dispatch intercepts --help etc. |
| Single mount function | Per-harness mount functions | Single function is simpler, conditional paths handle all harnesses |

## Architecture Patterns

### Recommended Code Changes

The implementation follows the existing architecture exactly. Changes are additive to these files:

```
src/aishell/
├── cli.clj           # Add codex/gemini to dispatch (pass-through)
├── run.clj           # Add codex/gemini cases to verify-harness-available and container-cmd
├── config.clj        # Add "codex" and "gemini" to known-harnesses set
└── docker/
    └── run.clj       # Add config mounts and env vars for codex/gemini
```

### Pattern 1: CLI Pass-Through Dispatch
**What:** Route `aishell codex [args]` and `aishell gemini [args]` to run-container before standard CLI dispatch
**When to use:** For all harness commands that need full argument pass-through
**Example:**
```clojure
;; Source: Existing cli.clj pattern (lines 243-246)
(case (first clean-args)
  "claude" (run/run-container "claude" (vec (rest clean-args)) {:unsafe unsafe?})
  "opencode" (run/run-container "opencode" (vec (rest clean-args)) {:unsafe unsafe?})
  "codex" (run/run-container "codex" (vec (rest clean-args)) {:unsafe unsafe?})
  "gemini" (run/run-container "gemini" (vec (rest clean-args)) {:unsafe unsafe?})
  ;; ... rest of dispatch
```

### Pattern 2: Harness Verification
**What:** Check that harness was installed before attempting to run
**When to use:** At start of run-container for harness commands
**Example:**
```clojure
;; Source: Existing run.clj pattern (lines 18-27, 83-86)
(defn- verify-harness-available
  [harness-name state-key state]
  (when-not (get state state-key)
    (output/error
      (str (case harness-name
             "claude" "Claude Code"
             "opencode" "OpenCode"
             "codex" "Codex CLI"
             "gemini" "Gemini CLI")
           " not installed. Run: aishell build --with-"
           harness-name))))

;; In run-container:
(case cmd
  "claude" (verify-harness-available "claude" :with-claude state)
  "opencode" (verify-harness-available "opencode" :with-opencode state)
  "codex" (verify-harness-available "codex" :with-codex state)
  "gemini" (verify-harness-available "gemini" :with-gemini state)
  nil)
```

### Pattern 3: Container Command Construction
**What:** Build the command vector to execute inside the container
**When to use:** For each harness, construct appropriate command with merged args
**Example:**
```clojure
;; Source: Existing run.clj pattern (lines 151-163)
container-cmd (case cmd
                "claude"
                (into ["claude" "--dangerously-skip-permissions"]
                      merged-args)

                "opencode"
                (into ["opencode"] merged-args)

                "codex"
                (into ["codex"] merged-args)

                "gemini"
                (into ["gemini"] merged-args)

                "gitleaks"
                (into ["gitleaks"] harness-args)

                ;; Default: bash shell
                ["/bin/bash"])
```

### Pattern 4: Config Directory Mounting
**What:** Mount harness config directories from host to container
**When to use:** For directories that contain auth tokens, settings, and cache
**Example:**
```clojure
;; Source: Existing docker/run.clj build-harness-config-mounts pattern (lines 132-143)
(defn- build-harness-config-mounts
  []
  (let [home (util/get-home)
        config-paths [;; Claude Code
                      [(str home "/.claude") (str home "/.claude")]
                      [(str home "/.claude.json") (str home "/.claude.json")]
                      ;; OpenCode
                      [(str home "/.config/opencode") (str home "/.config/opencode")]
                      [(str home "/.local/share/opencode") (str home "/.local/share/opencode")]
                      ;; Codex CLI - uses ~/.codex/ for all config/auth
                      [(str home "/.codex") (str home "/.codex")]
                      ;; Gemini CLI - uses ~/.gemini/ for settings
                      [(str home "/.gemini") (str home "/.gemini")]]]
    (->> config-paths
         (filter (fn [[src _]] (fs/exists? src)))
         (mapcat (fn [[src dst]] ["-v" (str src ":" dst)])))))
```

### Pattern 5: API Key Environment Variable Passthrough
**What:** Pass API keys from host environment to container
**When to use:** For all authentication environment variables
**Example:**
```clojure
;; Source: Existing docker/run.clj api-key-vars (lines 145-159)
(def api-key-vars
  ["ANTHROPIC_API_KEY"
   "OPENAI_API_KEY"
   ;; Codex uses CODEX_API_KEY for exec mode, but also OPENAI_API_KEY for login
   "CODEX_API_KEY"
   ;; Gemini supports multiple API key sources
   "GEMINI_API_KEY"
   "GOOGLE_API_KEY"
   ;; Vertex AI credentials
   "GOOGLE_CLOUD_PROJECT"
   "GOOGLE_CLOUD_LOCATION"
   "GOOGLE_APPLICATION_CREDENTIALS"
   ;; Existing keys (keep all)
   "GROQ_API_KEY"
   "GITHUB_TOKEN"
   "AWS_ACCESS_KEY_ID"
   "AWS_SECRET_ACCESS_KEY"
   "AWS_REGION"
   "AWS_PROFILE"
   "AZURE_OPENAI_API_KEY"
   "AZURE_OPENAI_ENDPOINT"])
```

### Pattern 6: GOOGLE_APPLICATION_CREDENTIALS File Mounting
**What:** Mount the service account JSON file referenced by GOOGLE_APPLICATION_CREDENTIALS
**When to use:** When GOOGLE_APPLICATION_CREDENTIALS is set and points to a file
**Example:**
```clojure
;; New pattern needed in build-docker-args
;; GOOGLE_APPLICATION_CREDENTIALS points to a file path, not just a string value
;; Must mount the file AND pass the env var
(defn- build-gcp-credentials-mount
  "Mount GCP service account credentials file if GOOGLE_APPLICATION_CREDENTIALS is set."
  []
  (when-let [creds-path (System/getenv "GOOGLE_APPLICATION_CREDENTIALS")]
    (when (fs/exists? creds-path)
      ["-v" (str creds-path ":" creds-path ":ro")])))
```

### Anti-Patterns to Avoid
- **Intercepting harness flags in CLI dispatch:** Don't route through dispatch-table for harness commands; use direct pass-through so `aishell codex --help` shows Codex help, not aishell help
- **Hardcoding API key values:** Always use passthrough (`-e KEY`) not literal values (`-e KEY=value`) to avoid logging credentials
- **Forgetting file mounts for credentials:** GOOGLE_APPLICATION_CREDENTIALS requires file mount, not just env var
- **Creating config directories in container:** Don't create ~/.codex or ~/.gemini if they don't exist on host; user needs to authenticate on host first

## Don't Hand-Roll

Problems that look simple but have existing solutions:

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| CLI argument parsing | Custom arg parsing | Existing pass-through pattern | Already handles all edge cases (--help, --, etc.) |
| Harness verification | Custom check functions | Extend verify-harness-available | Single place for error messages |
| Config merging | Manual harness_args handling | Existing merge-harness-args | Already handles global/project precedence |
| Environment filtering | Custom env filter | Extend api-key-vars list | Consistent with existing keys |
| Mount existence check | Per-harness checks | Existing filter pattern | Already handles non-existent paths gracefully |

**Key insight:** This phase is pure extension. Every pattern already exists in the codebase for claude/opencode. The work is adding cases to existing switch statements and adding paths to existing lists.

## Common Pitfalls

### Pitfall 1: Missing Authentication Directory
**What goes wrong:** `aishell codex` fails with authentication error even though user has authenticated on host
**Why it happens:** ~/.codex/ or ~/.gemini/ not mounted because directory doesn't exist
**How to avoid:** Mount directories only if they exist (already in pattern), document that users must authenticate on host first
**Warning signs:** "Please log in" or "API key required" errors inside container

### Pitfall 2: GOOGLE_APPLICATION_CREDENTIALS as Env Only
**What goes wrong:** Gemini/Vertex AI fails with "could not read credentials file"
**Why it happens:** GOOGLE_APPLICATION_CREDENTIALS path passed as env var but file not mounted
**How to avoid:** Implement file mount pattern (Pattern 6 above)
**Warning signs:** "FileNotFoundError" or "credentials file does not exist" from Gemini CLI

### Pitfall 3: CODEX_API_KEY in Interactive Mode
**What goes wrong:** CODEX_API_KEY is set but Codex still prompts for login
**Why it happens:** Per OpenAI docs, CODEX_API_KEY only works with `codex exec`, not interactive mode
**How to avoid:** Document that users should either authenticate on host (auth.json mounted) or use OPENAI_API_KEY for login flow
**Warning signs:** "CODEX_API_KEY is only supported in codex exec" warnings

### Pitfall 4: Dispatch Table Interception
**What goes wrong:** `aishell codex --help` shows aishell help instead of Codex help
**Why it happens:** Using dispatch-table instead of direct pass-through for harness commands
**How to avoid:** Add to case statement before dispatch-table call (existing pattern)
**Warning signs:** Harness flags not working as expected, --version showing wrong version

### Pitfall 5: known-harnesses Not Updated
**What goes wrong:** Config validation warns "Unknown harness names: codex, gemini"
**Why it happens:** known-harnesses set in config.clj not updated
**How to avoid:** Add "codex" and "gemini" to known-harnesses set
**Warning signs:** Warnings when using harness_args in config.yaml

## Code Examples

Verified patterns from existing codebase:

### CLI Dispatch Extension
```clojure
;; Source: cli.clj dispatch function pattern
(defn dispatch [args]
  (let [unsafe? (boolean (some #{"--unsafe"} args))
        clean-args (vec (remove #{"--unsafe"} args))]
    (case (first clean-args)
      "claude" (run/run-container "claude" (vec (rest clean-args)) {:unsafe unsafe?})
      "opencode" (run/run-container "opencode" (vec (rest clean-args)) {:unsafe unsafe?})
      "codex" (run/run-container "codex" (vec (rest clean-args)) {:unsafe unsafe?})
      "gemini" (run/run-container "gemini" (vec (rest clean-args)) {:unsafe unsafe?})
      "gitleaks" (run/run-container "gitleaks" (vec (rest clean-args)) {:unsafe unsafe? :skip-pre-start true})
      ;; Standard dispatch for other commands
      (if unsafe?
        (run/run-container nil [] {:unsafe true})
        (cli/dispatch dispatch-table args {:error-fn handle-error :restrict true})))))
```

### Help Text Update
```clojure
;; Source: cli.clj print-help pattern
(defn print-help []
  ;; ... existing header ...
  (println (str output/BOLD "Commands:" output/NC))
  (println (str "  " output/CYAN "build" output/NC "      Build the container image"))
  (println (str "  " output/CYAN "update" output/NC "     Rebuild with latest versions"))
  (println (str "  " output/CYAN "claude" output/NC "     Run Claude Code"))
  (println (str "  " output/CYAN "opencode" output/NC "   Run OpenCode"))
  (println (str "  " output/CYAN "codex" output/NC "      Run Codex CLI"))
  (println (str "  " output/CYAN "gemini" output/NC "     Run Gemini CLI"))
  (println (str "  " output/CYAN "gitleaks" output/NC "   Run Gitleaks secret scanner"))
  (println (str "  " output/CYAN "(none)" output/NC "     Enter interactive shell")))
```

### Known Commands Set Update
```clojure
;; Source: output.clj known-commands for typo suggestions
(def known-commands #{"build" "update" "claude" "opencode" "codex" "gemini" "gitleaks"})
```

### Known Harnesses Set Update
```clojure
;; Source: config.clj known-harnesses for config validation
(def known-harnesses
  "Valid harness names for harness_args validation."
  #{"claude" "opencode" "codex" "gemini"})
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| XDG paths assumed | Actual paths verified via docs | 2026-01-25 | Codex uses ~/.codex/, Gemini uses ~/.gemini/ |
| Generic env passthrough | Harness-specific env vars | Phase 25 | CODEX_API_KEY, GEMINI_API_KEY, GOOGLE_API_KEY added |
| Env-only credentials | File mount for GOOGLE_APPLICATION_CREDENTIALS | Phase 25 | Vertex AI authentication works |

**Deprecated/outdated:**
- XDG paths (~/.config/codex, ~/.config/gemini-cli) are NOT used by these CLIs; they use simpler ~/.<harness>/ paths

## Open Questions

Things that couldn't be fully resolved:

1. **Codex exec vs interactive mode API key behavior**
   - What we know: CODEX_API_KEY only works with `codex exec`, not interactive mode
   - What's unclear: Whether users will primarily use exec or interactive mode
   - Recommendation: Pass through both CODEX_API_KEY and OPENAI_API_KEY; document behavior difference

2. **Gemini .env file loading**
   - What we know: Gemini CLI searches for .env files in project directory hierarchy
   - What's unclear: Whether this works correctly when project is mounted in container
   - Recommendation: Mount ~/.gemini/ which includes .env support; test during implementation

3. **GOOGLE_CLOUD_LOCATION default**
   - What we know: Required for Vertex AI authentication
   - What's unclear: Whether a sensible default exists or if it must be user-configured
   - Recommendation: Pass through if set; don't set a default

## Sources

### Primary (HIGH confidence)
- [OpenAI Codex Configuration Reference](https://developers.openai.com/codex/config-reference/) - Config directory ~/.codex/, auth.json, config.toml
- [OpenAI Codex Authentication](https://developers.openai.com/codex/auth/) - CODEX_API_KEY usage, credential storage
- [OpenAI Codex Non-Interactive Mode](https://developers.openai.com/codex/noninteractive/) - CODEX_API_KEY only in exec mode
- [Gemini CLI Configuration](https://geminicli.com/docs/get-started/configuration/) - ~/.gemini/settings.json, .env files
- [Gemini CLI Authentication](https://github.com/google-gemini/gemini-cli/blob/main/docs/get-started/authentication.md) - GEMINI_API_KEY, GOOGLE_API_KEY, Vertex AI credentials
- Existing codebase: cli.clj, run.clj, docker/run.clj, config.clj, output.clj - Implementation patterns

### Secondary (MEDIUM confidence)
- [.planning/research/ARCHITECTURE.md](/home/jonasrodrigues/projects/harness/.planning/research/ARCHITECTURE.md) - Multi-harness integration patterns
- [Phase 24 Research](/home/jonasrodrigues/projects/harness/.planning/phases/24-dockerfile---build-infrastructure/24-RESEARCH.md) - Build infrastructure patterns

### Tertiary (LOW confidence)
- None - all claims verified with official documentation

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - Using existing proven patterns from codebase
- Architecture: HIGH - Direct extension of existing claude/opencode patterns
- Pitfalls: HIGH - Based on official documentation and existing implementation experience
- Environment variables: HIGH - Verified against official documentation

**Research date:** 2026-01-25
**Valid until:** 14 days (CLI tools may update authentication patterns)

---
*Phase: 25-cli-runtime*
*Research completed: 2026-01-25*
