# Architecture Research: Multi-Harness Support

**Domain:** Adding OpenAI Codex CLI and Google Gemini CLI to existing aishell harness sandbox
**Researched:** 2026-01-24
**Confidence:** HIGH

## Current Architecture Summary

The aishell Babashka CLI follows a modular namespace pattern:

**Core namespaces:**
- `aishell.cli` - CLI routing and argument parsing using babashka.cli dispatch tables
- `aishell.run` - Run command orchestration (handles shell, claude, opencode execution)
- `aishell.docker.build` - Image building with embedded Dockerfile templates
- `aishell.docker.run` - Docker run argument construction
- `aishell.docker.templates` - Embedded Dockerfile, entrypoint.sh, bashrc.aishell
- `aishell.state` - Build state persistence (~/.aishell/state.edn)
- `aishell.config` - YAML config loading with merge strategy

**Key patterns:**
1. **Embedded Dockerfile** - Dockerfile stored as multiline string in `templates.clj`, written to temp dir during build
2. **Conditional harness installation** - Build args (WITH_CLAUDE, WITH_OPENCODE) control npm/curl installations
3. **Pass-through commands** - CLI routes `aishell claude` to `run-container "claude" args` which execs docker with `["claude" ...args]`
4. **Config mounting** - `build-harness-config-mounts` in docker/run.clj mounts ~/.claude, ~/.config/opencode if they exist
5. **Version pinning** - `--with-claude=2.0.22` syntax parsed by `parse-with-flag`, stored in state.edn
6. **State tracking** - state.edn stores {:with-claude true :claude-version "2.0.22" ...}

**Harness installation locations:**
- Claude Code: `npm install -g @anthropic-ai/claude-code` → /usr/local/bin/claude
- OpenCode: `curl -fsSL https://opencode.ai/install | bash` → /root/.opencode/bin/opencode (copied to /usr/local/bin)

## Integration Points

### 1. Dockerfile Changes

**File:** `src/aishell/docker/templates.clj` → `base-dockerfile` string

**Add build args:**
```dockerfile
ARG WITH_CODEX=false
ARG WITH_GEMINI=false
ARG CODEX_VERSION=""
ARG GEMINI_VERSION=""
```

**Add installation blocks (after OpenCode section, before entrypoint):**
```dockerfile
# Install OpenAI Codex CLI if requested (npm global)
RUN if [ "$WITH_CODEX" = "true" ]; then \\
        if [ -n "$CODEX_VERSION" ]; then \\
            npm install -g @openai/codex@"$CODEX_VERSION"; \\
        else \\
            npm install -g @openai/codex; \\
        fi \\
    fi

# Install Google Gemini CLI if requested (npm global)
RUN if [ "$WITH_GEMINI" = "true" ]; then \\
        if [ -n "$GEMINI_VERSION" ]; then \\
            npm install -g @google/gemini-cli@"$GEMINI_VERSION"; \\
        else \\
            npm install -g @google/gemini-cli; \\
        fi \\
    fi
```

**Rationale:** Both harnesses install via npm like Claude Code. This follows the established pattern exactly.

### 2. CLI Routing

**File:** `src/aishell/cli.clj`

**Changes:**

1. **Build spec** - Add new flags to `build-spec` (line 62):
```clojure
(def build-spec
  {:with-claude   {:desc "Include Claude Code (optional: =VERSION)"}
   :with-opencode {:desc "Include OpenCode (optional: =VERSION)"}
   :with-codex    {:desc "Include OpenAI Codex CLI (optional: =VERSION)"}  ; ADD
   :with-gemini   {:desc "Include Google Gemini CLI (optional: =VERSION)"}  ; ADD
   :force         {:coerce :boolean :desc "Force rebuild (bypass Docker cache)"}
   :verbose       {:alias :v :coerce :boolean :desc "Show full Docker build output"}
   :help          {:alias :h :coerce :boolean :desc "Show build help"}})
```

2. **handle-build function** - Extend parsing and validation (line 107):
```clojure
(defn handle-build [{:keys [opts]}]
  (if (:help opts)
    (print-build-help)
    (let [;; Parse flags
          claude-config (parse-with-flag (:with-claude opts))
          opencode-config (parse-with-flag (:with-opencode opts))
          codex-config (parse-with-flag (:with-codex opts))      ; ADD
          gemini-config (parse-with-flag (:with-gemini opts))    ; ADD

          ;; Validate versions before build
          _ (validate-version (:version claude-config) "Claude Code")
          _ (validate-version (:version opencode-config) "OpenCode")
          _ (validate-version (:version codex-config) "OpenAI Codex CLI")   ; ADD
          _ (validate-version (:version gemini-config) "Google Gemini CLI") ; ADD

          ;; ... rest of build logic
          result (build/build-base-image
                   {:with-claude (:enabled? claude-config)
                    :with-opencode (:enabled? opencode-config)
                    :with-codex (:enabled? codex-config)         ; ADD
                    :with-gemini (:enabled? gemini-config)       ; ADD
                    :claude-version (:version claude-config)
                    :opencode-version (:version opencode-config)
                    :codex-version (:version codex-config)       ; ADD
                    :gemini-version (:version gemini-config)     ; ADD
                    :verbose (:verbose opts)
                    :force (:force opts)})]

      ;; Persist state
      (state/write-state
        {:with-claude (:enabled? claude-config)
         :with-opencode (:enabled? opencode-config)
         :with-codex (:enabled? codex-config)         ; ADD
         :with-gemini (:enabled? gemini-config)       ; ADD
         :claude-version (:version claude-config)
         :opencode-version (:version opencode-config)
         :codex-version (:version codex-config)       ; ADD
         :gemini-version (:version gemini-config)     ; ADD
         :image-tag (:image result)
         :build-time (str (java.time.Instant/now))
         :dockerfile-hash (hash/compute-hash templates/base-dockerfile)}))))
```

3. **dispatch function** - Add pass-through commands (line 214):
```clojure
(defn dispatch [args]
  (let [unsafe? (boolean (some #{"--unsafe"} args))
        clean-args (vec (remove #{"--unsafe"} args))]
    (case (first clean-args)
      "claude" (run/run-container "claude" (vec (rest clean-args)) {:unsafe unsafe?})
      "opencode" (run/run-container "opencode" (vec (rest clean-args)) {:unsafe unsafe?})
      "codex" (run/run-container "codex" (vec (rest clean-args)) {:unsafe unsafe?})     ; ADD
      "gemini" (run/run-container "gemini" (vec (rest clean-args)) {:unsafe unsafe?})   ; ADD
      "gitleaks" (run/run-container "gitleaks" (vec (rest clean-args)) {:unsafe unsafe? :skip-pre-start true})
      ;; Standard dispatch for other commands
      (if unsafe?
        (run/run-container nil [] {:unsafe true})
        (cli/dispatch dispatch-table args {:error-fn handle-error :restrict true})))))
```

4. **print-help function** - Update commands list (line 69):
```clojure
(println (str "  " output/CYAN "claude" output/NC "     Run Claude Code"))
(println (str "  " output/CYAN "opencode" output/NC "   Run OpenCode"))
(println (str "  " output/CYAN "codex" output/NC "      Run OpenAI Codex CLI"))     ; ADD
(println (str "  " output/CYAN "gemini" output/NC "     Run Google Gemini CLI"))    ; ADD
(println (str "  " output/CYAN "gitleaks" output/NC "   Run Gitleaks secret scanner"))
```

**Rationale:** Follows exact same pattern as existing claude/opencode commands. No new abstractions needed.

### 3. Run Commands

**File:** `src/aishell/run.clj`

**Changes:**

1. **verify-harness-available function** - Add new cases (line 18):
```clojure
(defn- verify-harness-available
  [harness-name state-key state]
  (when-not (get state state-key)
    (output/error
      (str (case harness-name
             "claude" "Claude Code"
             "opencode" "OpenCode"
             "codex" "OpenAI Codex CLI"      ; ADD
             "gemini" "Google Gemini CLI")   ; ADD
           " not installed. Run: aishell build --with-"
           harness-name))))
```

2. **run-container function** - Add verification and command cases (line 83, 151):
```clojure
;; Verify harness if requested (around line 83)
(case cmd
  "claude" (verify-harness-available "claude" :with-claude state)
  "opencode" (verify-harness-available "opencode" :with-opencode state)
  "codex" (verify-harness-available "codex" :with-codex state)       ; ADD
  "gemini" (verify-harness-available "gemini" :with-gemini state)    ; ADD
  nil)

;; Determine command to run in container (around line 151)
container-cmd (case cmd
                "claude"
                (into ["claude" "--dangerously-skip-permissions"]
                      merged-args)

                "opencode"
                (into ["opencode"] merged-args)

                "codex"                             ; ADD
                (into ["codex"] merged-args)        ; ADD

                "gemini"                            ; ADD
                (into ["gemini"] merged-args)       ; ADD

                "gitleaks"
                (into ["gitleaks"] harness-args)

                ;; Default: bash shell
                ["/bin/bash"])
```

**Rationale:** Direct pass-through like OpenCode. No special flags needed (unlike Claude's --dangerously-skip-permissions).

### 4. Config Mounting

**File:** `src/aishell/docker/run.clj`

**Changes:**

**build-harness-config-mounts function** - Add config paths (line 132):
```clojure
(defn- build-harness-config-mounts
  []
  (let [home (util/get-home)
        config-paths [[(str home "/.claude") (str home "/.claude")]
                      [(str home "/.claude.json") (str home "/.claude.json")]
                      [(str home "/.config/opencode") (str home "/.config/opencode")]
                      [(str home "/.local/share/opencode") (str home "/.local/share/opencode")]
                      [(str home "/.config/codex") (str home "/.config/codex")]           ; ADD (Codex config)
                      [(str home "/.local/share/codex") (str home "/.local/share/codex")] ; ADD (Codex data)
                      [(str home "/.config/gemini-cli") (str home "/.config/gemini-cli")] ; ADD (Gemini config)
                      [(str home "/.local/share/gemini-cli") (str home "/.local/share/gemini-cli")]]] ; ADD (Gemini data)
    (->> config-paths
         (filter (fn [[src _]] (fs/exists? src)))
         (mapcat (fn [[src dst]] ["-v" (str src ":" dst)])))))
```

**Rationale:**
- Codex likely uses ~/.config/codex (XDG standard, similar to OpenCode)
- Gemini CLI likely uses ~/.config/gemini-cli (per npm package name pattern)
- Both may use ~/.local/share for auth/cache (standard XDG data location)
- Mounting conditionally (only if exists) prevents mount errors

**Note:** These paths are inferred from standard XDG conventions and npm package patterns. During implementation, verify actual config locations by:
1. Installing harness locally
2. Running `codex` or `gemini` command
3. Checking `ls -la ~/.*` and `ls -la ~/.config/` for created directories
4. Adjust paths in implementation if different

### 5. Build State

**File:** `src/aishell/state.clj`

**Changes:**

**State schema documentation** - Update comment (line 25):
```clojure
"Write state to file, creating directory if needed.

   State schema:
   {:with-claude true            ; boolean
    :with-opencode false         ; boolean
    :with-codex false            ; boolean  (ADD)
    :with-gemini false           ; boolean  (ADD)
    :claude-version \"2.0.22\"   ; string or nil
    :opencode-version nil        ; string or nil
    :codex-version nil           ; string or nil  (ADD)
    :gemini-version nil          ; string or nil  (ADD)
    :image-tag \"aishell:base\"  ; string
    :build-time \"2026-01-20...\" ; ISO-8601 string
    :dockerfile-hash \"abc123def456\"} ; 12-char SHA-256 hash"
```

**Rationale:** State schema is just documentation. The map is flexible (EDN accepts any keys).

### 6. Version Pinning

**Already implemented!** The existing `parse-with-flag` function (cli.clj line 44) handles `--with-X=VERSION` syntax generically.

**Usage examples:**
```bash
# Latest versions
aishell build --with-codex --with-gemini

# Pinned versions
aishell build --with-codex=1.2.0 --with-gemini=0.24.0

# Mixed
aishell build --with-claude=2.0.22 --with-codex
```

**Rationale:** No new code needed. Existing flag parser works for any `--with-X` flag.

### 7. Docker Build Args

**File:** `src/aishell/docker/build.clj`

**Changes:**

1. **build-docker-args function** - Add new build args (line 60):
```clojure
(defn- build-docker-args
  [{:keys [with-claude with-opencode with-codex with-gemini
           claude-version opencode-version codex-version gemini-version]} dockerfile-hash]
  (cond-> []
    with-claude (conj "--build-arg" "WITH_CLAUDE=true")
    with-opencode (conj "--build-arg" "WITH_OPENCODE=true")
    with-codex (conj "--build-arg" "WITH_CODEX=true")         ; ADD
    with-gemini (conj "--build-arg" "WITH_GEMINI=true")       ; ADD
    claude-version (conj "--build-arg" (str "CLAUDE_VERSION=" claude-version))
    opencode-version (conj "--build-arg" (str "OPENCODE_VERSION=" opencode-version))
    codex-version (conj "--build-arg" (str "CODEX_VERSION=" codex-version))      ; ADD
    gemini-version (conj "--build-arg" (str "GEMINI_VERSION=" gemini-version))   ; ADD
    true (conj "--label" (str dockerfile-hash-label "=" dockerfile-hash))))
```

2. **version-changed? function** - Add new version checks (line 38):
```clojure
(defn version-changed?
  [opts state]
  (or
    ;; Claude version changed
    (and (:with-claude opts)
         (not= (:claude-version opts) (:claude-version state)))
    ;; OpenCode version changed
    (and (:with-opencode opts)
         (not= (:opencode-version opts) (:opencode-version state)))
    ;; Codex version changed (ADD)
    (and (:with-codex opts)
         (not= (:codex-version opts) (:codex-version state)))
    ;; Gemini version changed (ADD)
    (and (:with-gemini opts)
         (not= (:gemini-version opts) (:gemini-version state)))
    ;; Harness added that wasn't in previous build
    (and (:with-claude opts) (not (:with-claude state)))
    (and (:with-opencode opts) (not (:with-opencode state)))
    (and (:with-codex opts) (not (:with-codex state)))       ; ADD
    (and (:with-gemini opts) (not (:with-gemini state)))))   ; ADD
```

3. **build-base-image function** - Add to build summary output (line 165):
```clojure
(when-not quiet
  (println (str "Built " base-image-tag
                " (" (format-duration duration)
                (when size (str ", " size)) ")"))
  (when (:with-claude opts)
    (println (format-harness-line "Claude Code" (:claude-version opts))))
  (when (:with-opencode opts)
    (println (format-harness-line "OpenCode" (:opencode-version opts))))
  (when (:with-codex opts)                                                      ; ADD
    (println (format-harness-line "OpenAI Codex CLI" (:codex-version opts))))  ; ADD
  (when (:with-gemini opts)                                                     ; ADD
    (println (format-harness-line "Google Gemini CLI" (:gemini-version opts)))) ; ADD
```

4. **build-base-image return map** - Add to result (line 169):
```clojure
{:success true
 :image base-image-tag
 :duration duration
 :size size
 :with-claude (:with-claude opts)
 :with-opencode (:with-opencode opts)
 :with-codex (:with-codex opts)         ; ADD
 :with-gemini (:with-gemini opts)       ; ADD
 :claude-version (:claude-version opts)
 :opencode-version (:opencode-version opts)
 :codex-version (:codex-version opts)   ; ADD
 :gemini-version (:gemini-version opts)} ; ADD
```

**Rationale:** Mechanically adds new harnesses to existing build pipeline. No logic changes.

### 8. Config YAML Support

**File:** `src/aishell/config.clj`

**Changes:**

1. **known-harnesses set** - Add new harness names (line 13):
```clojure
(def known-harnesses
  #{"claude" "opencode" "codex" "gemini"})  ; ADD codex and gemini
```

**Rationale:** Enables `harness_args` validation for new harnesses in config.yaml:
```yaml
harness_args:
  codex:
    - --model=gpt-4
  gemini:
    - --temperature=0.7
```

## Suggested Build Order

Implement changes in this order to maintain working state:

1. **Dockerfile template** (templates.clj)
   - Add WITH_CODEX, WITH_GEMINI build args
   - Add npm install blocks for both harnesses
   - Test: `aishell build --with-codex --with-gemini` (should build successfully)

2. **Build orchestration** (docker/build.clj)
   - Add codex/gemini to build-docker-args
   - Add to version-changed? checks
   - Add to build summary output
   - Test: Rebuild should detect version changes

3. **CLI routing** (cli.clj)
   - Add :with-codex and :with-gemini to build-spec
   - Extend handle-build with parsing and validation
   - Add "codex" and "gemini" cases to dispatch
   - Update help text
   - Test: `aishell build --help` shows new flags, `aishell --help` shows new commands

4. **State management** (state.clj)
   - Update state schema documentation
   - Test: Build state persists new fields

5. **Run commands** (run.clj)
   - Add codex/gemini to verify-harness-available
   - Add to container-cmd case
   - Test: `aishell codex` fails with "not installed" if not built
   - Test: `aishell build --with-codex && aishell codex` runs successfully

6. **Config mounting** (docker/run.clj)
   - Add codex/gemini paths to build-harness-config-mounts
   - Test: Install harnesses locally, verify config dirs are mounted
   - Adjust paths if actual locations differ from XDG standard assumptions

7. **Config YAML support** (config.clj)
   - Add to known-harnesses set
   - Test: harness_args validation works for new harnesses

**Testing progression:**
- After step 1: Can build image with new harnesses
- After step 3: Can invoke new run commands
- After step 5: Full integration working
- After step 6: Config persistence working
- After step 7: YAML config support complete

## Files to Modify

| File | Lines Changed | Purpose | Complexity |
|------|---------------|---------|------------|
| src/aishell/docker/templates.clj | ~20 | Add Dockerfile build args and npm install blocks | Low |
| src/aishell/docker/build.clj | ~15 | Add to build-docker-args, version-changed?, output | Low |
| src/aishell/cli.clj | ~30 | Add build flags, dispatch cases, help text | Low |
| src/aishell/state.clj | ~5 | Update state schema docs | Trivial |
| src/aishell/run.clj | ~10 | Add harness verification and command cases | Low |
| src/aishell/docker/run.clj | ~5 | Add config mount paths | Low |
| src/aishell/config.clj | ~1 | Add to known-harnesses set | Trivial |

**Total estimated changes:** ~85 lines across 7 files

**Complexity assessment:** LOW - All changes follow established patterns mechanically. No new abstractions or logic needed.

## Architecture Patterns Followed

1. **Consistent harness treatment** - Codex and Gemini added exactly like Claude/OpenCode
2. **Version pinning via build args** - Same `--with-X=VERSION` pattern
3. **Conditional mounting** - Only mount config dirs if they exist (prevents errors)
4. **Pass-through commands** - CLI routes to docker exec without interception
5. **State-based verification** - Check build state before running harness
6. **XDG compliance** - Config paths follow XDG Base Directory spec

## Known Unknowns

Items requiring verification during implementation:

1. **Codex config location** - Assumed ~/.config/codex but needs verification
   - Verify: Install `@openai/codex` globally, run `codex`, check created directories
   - Update: docker/run.clj config paths if different

2. **Gemini CLI config location** - Assumed ~/.config/gemini-cli but needs verification
   - Verify: Install `@google/gemini-cli` globally, run `gemini`, check created directories
   - Update: docker/run.clj config paths if different

3. **Codex/Gemini permission flags** - Assumed none needed (unlike Claude's --dangerously-skip-permissions)
   - Verify: Run in container, check for permission prompts
   - Update: run.clj container-cmd if flags needed

4. **npm version syntax** - Assumed `@openai/codex@VERSION` works
   - Verify: Test `npm install -g @openai/codex@1.2.0` syntax
   - Update: templates.clj if syntax differs

## Migration from Claude/OpenCode Pattern

If implementation reveals differences from Claude/OpenCode patterns:

**If Codex/Gemini use native installers (not npm):**
- Update templates.clj to use curl installer like OpenCode
- Adjust PATH or symlink strategy accordingly

**If config locations differ from XDG standard:**
- Update docker/run.clj mount paths based on actual locations
- Document actual paths in code comments

**If special flags needed:**
- Add to run.clj container-cmd case like Claude's --dangerously-skip-permissions

**If authentication differs:**
- Update API key passthrough in docker/run.clj if needed
- Document in config.yaml examples

## Sources

**Official Documentation (HIGH confidence):**
- [OpenAI Codex CLI - npm package](https://www.npmjs.com/package/@openai/codex) - Installation method, version info
- [OpenAI Codex CLI Docs](https://developers.openai.com/codex/cli/) - Configuration, usage
- [Google Gemini CLI - npm package](https://www.npmjs.com/package/@google/gemini-cli) - Installation method, version 0.24.0
- [Google Gemini CLI Docs](https://geminicli.com/docs/) - Installation, configuration

**Existing Codebase (HIGH confidence):**
- Phase 03 Research (harness-integration/03-RESEARCH.md) - Claude/OpenCode installation patterns
- Current implementation analysis - Established architecture patterns

**Standards (HIGH confidence):**
- [XDG Base Directory Specification](https://specifications.freedesktop.org/basedir-spec/basedir-spec-latest.html) - Config path conventions

---

**Research completed:** 2026-01-24
**Implementation ready:** Yes - All integration points identified with line-level precision
