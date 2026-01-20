# Stack Research: Babashka CLI Rewrite

**Project:** aishell rewrite from Bash to Babashka
**Researched:** 2026-01-20
**Babashka Version:** v1.12.214 (released 2026-01-13)

---

## Summary

Babashka provides a complete replacement for the current 1,655 LOC Bash implementation with built-in libraries that cover all required functionality: CLI argument parsing (`babashka.cli`), process execution (`babashka.process`), file system operations (`babashka.fs`), config parsing (EDN native, YAML via built-in `clj-yaml`), and JSON handling (built-in `cheshire`). The cross-platform story is strong - Babashka binaries exist for Linux, macOS, and Windows, and the `babashka.process` library abstracts platform differences. **No external dependencies (pods) are needed; all functionality is built-in.**

---

## Recommended Stack

### Core (Babashka Built-ins)

| Need | Babashka Solution | Notes |
|------|-------------------|-------|
| CLI argument parsing | `babashka.cli` | Built-in since v0.9.160. Supports subcommands, coercion, validation, help generation |
| Process execution (docker) | `babashka.process` | `shell` for streaming output, `process` for fine control. Cross-platform |
| File system operations | `babashka.fs` | Temp files, deletion, glob, path manipulation. Cross-platform via java.nio |
| Config file parsing | `clojure.edn` + `clj-yaml.core` | Both built-in. EDN is native; YAML via built-in clj-yaml |
| JSON handling | `cheshire.core` | Built-in. For Docker inspect output parsing |
| String manipulation | `clojure.string` | Built-in. For path manipulation, output parsing |
| Regex validation | `clojure.core` | `re-matches`, `re-pattern` built-in. Same as Clojure |
| Hash computation | `java.security.MessageDigest` | Built-in. For Dockerfile hash detection |
| Environment variables | `System/getenv`, `System/getProperty` | Built-in Java interop |
| Cleanup on exit | `Runtime/addShutdownHook` | Java interop. Equivalent to Bash `trap EXIT` |

### External Dependencies

**None required.** All functionality is available in Babashka's built-in libraries.

| Library | Version | Purpose | Required? |
|---------|---------|---------|-----------|
| N/A | - | All needs covered by built-ins | No |

---

## Bash-to-Babashka Translation

### Argument Parsing

| Bash Pattern | Babashka Idiom |
|--------------|----------------|
| `case $1 in --flag)` | `babashka.cli/parse-opts` |
| `shift` / `$@` | Automatic via `parse-args` returns `:args` |
| `getopts` | `babashka.cli` with `:alias` for short opts |

```clojure
;; Bash: case/esac argument parsing
;; while [[ $# -gt 0 ]]; do case $1 in --with-claude) WITH_CLAUDE=true;; esac; done

;; Babashka equivalent:
(require '[babashka.cli :as cli])

(def cli-spec
  {:spec {:with-claude    {:coerce :boolean :desc "Include Claude Code"}
          :with-opencode  {:coerce :boolean :desc "Include OpenCode"}
          :claude-version {:desc "Claude Code version (X.Y.Z)"}
          :opencode-version {:desc "OpenCode version (X.Y.Z)"}
          :verbose        {:coerce :boolean :alias :v :desc "Verbose output"}
          :help           {:coerce :boolean :alias :h :desc "Show help"}
          :no-cache       {:coerce :boolean :desc "Force rebuild"}}})

(defn parse-args [args]
  (cli/parse-opts args cli-spec))
;; (parse-args ["--with-claude" "--verbose"])
;; => {:with-claude true, :verbose true}
```

### Subcommand Dispatch

| Bash Pattern | Babashka Idiom |
|--------------|----------------|
| First positional arg check | `babashka.cli/dispatch` |
| `case "$HARNESS_CMD" in build)` | Multi-method or table dispatch |

```clojure
;; Bash: HARNESS_CMD dispatch
;; case "$HARNESS_CMD" in build) do_build "${HARNESS_ARGS[@]}";; esac

;; Babashka equivalent:
(def table
  [{:cmds ["build"]   :fn do-build   :spec build-spec}
   {:cmds ["update"]  :fn do-update  :spec update-spec}
   {:cmds ["claude"]  :fn do-claude  :spec run-spec}
   {:cmds ["opencode"] :fn do-opencode :spec run-spec}
   {:cmds []          :fn do-shell   :spec run-spec}])  ; default

(defn -main [& args]
  (cli/dispatch table args))
```

### Process Execution (Docker Commands)

| Bash Pattern | Babashka Idiom |
|--------------|----------------|
| `docker build ...` | `(p/shell "docker" "build" ...)` |
| `$(docker inspect ...)` | `(-> (p/shell {:out :string} ...) :out)` |
| `command -v docker` | `(fs/which "docker")` |
| `$?` exit code | `(:exit (p/shell {:continue true} ...))` |
| `> "$build_log" 2>&1` | `{:out :string :err :string}` |

```clojure
;; Bash: docker build with captured output
;; if ! docker build "${build_args[@]}" -t "$target_tag" "$build_dir" > "$build_log" 2>&1; then

;; Babashka equivalent:
(require '[babashka.process :as p])

(defn docker-build [{:keys [build-args target-tag build-dir verbose]}]
  (let [cmd (concat ["docker" "build"]
                    build-args
                    ["-t" target-tag build-dir])]
    (if verbose
      ;; Streaming output (like original verbose mode)
      (apply p/shell cmd)
      ;; Captured output (like original quiet mode)
      (let [result (apply p/shell {:out :string :err :string :continue true} cmd)]
        (when-not (zero? (:exit result))
          (throw (ex-info "Build failed" {:error (:err result)})))
        result))))

;; Check if docker exists
(defn check-docker []
  (when-not (fs/which "docker")
    (error "Docker is not installed. Please install Docker and try again."))
  (let [result (p/shell {:out :string :err :string :continue true} "docker" "info")]
    (when-not (zero? (:exit result))
      (error "Docker is not running. Please start Docker and try again."))))
```

### Temp Files and Cleanup

| Bash Pattern | Babashka Idiom |
|--------------|----------------|
| `mktemp -d` | `(fs/create-temp-dir)` |
| `trap cleanup EXIT` | `Runtime/addShutdownHook` |
| `rm -rf "$dir"` | `(fs/delete-tree dir)` |
| Array of cleanup paths | `atom` with paths |

```clojure
;; Bash: trap cleanup EXIT with temp files
;; declare -a CLEANUP_FILES=()
;; trap cleanup EXIT
;; register_cleanup() { CLEANUP_FILES+=("$1"); }

;; Babashka equivalent:
(require '[babashka.fs :as fs])

(def cleanup-paths (atom []))

(defn register-cleanup [path]
  (swap! cleanup-paths conj (str path)))

(defn cleanup []
  (doseq [path @cleanup-paths]
    (when (fs/exists? path)
      (fs/delete-tree path))))

;; Register shutdown hook ONCE at startup (like Bash trap EXIT)
(defn setup-cleanup-hook []
  (-> (Runtime/getRuntime)
      (.addShutdownHook (Thread. cleanup))))

;; Usage:
(defn with-build-dir [f]
  (let [build-dir (fs/create-temp-dir {:prefix "aishell-"})]
    (register-cleanup build-dir)
    (f build-dir)))
```

### Config File Parsing (run.conf to run.edn)

| Bash Pattern | Babashka Idiom |
|--------------|----------------|
| `source config.sh` | `(edn/read-string (slurp ...))` |
| Shell variable syntax | EDN maps |
| Line-by-line parsing | Native EDN parsing |
| `IFS= read -r line` | Not needed |

**Config format change: `run.conf` (shell) to `run.edn` (EDN)**

```clojure
;; Bash run.conf format:
;; MOUNTS="$HOME/.ssh $HOME/.config/git"
;; ENV="EDITOR DEBUG_MODE=1"
;; PORTS="3000:3000 8080:80"
;; DOCKER_ARGS="--cap-add=SYS_PTRACE"
;; PRE_START="redis-server --daemonize yes"

;; Babashka run.edn format (new):
;; {:mounts ["$HOME/.ssh" "$HOME/.config/git"]
;;  :env {"EDITOR" :passthrough   ; nil or :passthrough = inherit from host
;;        "DEBUG_MODE" "1"}       ; string = literal value
;;  :ports ["3000:3000" "8080:80"]
;;  :docker-args ["--cap-add=SYS_PTRACE"]
;;  :pre-start "redis-server --daemonize yes"}

(require '[clojure.edn :as edn])

(defn expand-home [s]
  (let [home (or (System/getenv "HOME")
                 (System/getProperty "user.home"))]
    (-> s
        (str/replace "$HOME" home)
        (str/replace "${HOME}" home)
        (str/replace #"^~" home))))

(defn parse-run-conf [project-dir]
  (let [config-file (fs/path project-dir ".aishell" "run.edn")]
    (if (fs/exists? config-file)
      (edn/read-string (slurp (str config-file)))
      {})))

;; Backward compatibility: also support YAML
(require '[clj-yaml.core :as yaml])

(defn parse-run-conf-yaml [project-dir]
  (let [config-file (fs/path project-dir ".aishell" "run.yaml")]
    (if (fs/exists? config-file)
      (yaml/parse-string (slurp (str config-file)) :keywords true)
      {})))
```

### Input Validation (Regex)

| Bash Pattern | Babashka Idiom |
|--------------|----------------|
| `[[ "$v" =~ $REGEX ]]` | `(re-matches regex v)` |
| `readonly REGEX='...'` | `(def regex #"...")` |

```clojure
;; Bash: VERSION_REGEX validation
;; readonly VERSION_REGEX='^[0-9]+\.[0-9]+\.[0-9]+(-[a-zA-Z0-9.-]+)?$'
;; if [[ ! "$version" =~ $VERSION_REGEX ]]; then error "Invalid version"; fi

;; Babashka equivalent:
(def version-regex
  #"^[0-9]+\.[0-9]+\.[0-9]+(-[a-zA-Z0-9.-]+)?(\+[a-zA-Z0-9.-]+)?$")

(def dangerous-chars-regex
  #"[;&|`$(){}[\]<>!\\]")

(defn validate-version [version name]
  (cond
    (nil? version) nil  ; empty is OK (means "latest")

    (re-find dangerous-chars-regex version)
    (throw (ex-info (str "Invalid " name " version: contains shell metacharacters")
                    {:version version}))

    (not (re-matches version-regex version))
    (throw (ex-info (str "Invalid " name " version format: " version)
                    {:version version
                     :expected "X.Y.Z or X.Y.Z-prerelease (e.g., 2.0.22, 1.0.0-beta.1)"}))

    :else version))
```

### Environment Variables

| Bash Pattern | Babashka Idiom |
|--------------|----------------|
| `${VAR:-default}` | `(or (System/getenv "VAR") "default")` |
| `[[ -n "$VAR" ]]` | `(some? (System/getenv "VAR"))` |
| `export VAR=val` | `:extra-env` in process call |

```clojure
;; Bash: build_api_env function
;; for var in "${api_vars[@]}"; do
;;     [[ -n "${!var:-}" ]] && echo "-e $var=${!var}"
;; done

;; Babashka equivalent:
(def api-vars
  ["ANTHROPIC_API_KEY" "OPENAI_API_KEY" "GEMINI_API_KEY"
   "GROQ_API_KEY" "GITHUB_TOKEN" "AWS_ACCESS_KEY_ID"
   "AWS_SECRET_ACCESS_KEY" "AWS_REGION" "AWS_PROFILE"
   "AZURE_OPENAI_API_KEY" "AZURE_OPENAI_ENDPOINT"
   "GOOGLE_CLOUD_PROJECT" "GOOGLE_APPLICATION_CREDENTIALS"])

(defn build-api-env []
  (into {"DISABLE_AUTOUPDATER" "1"}
        (for [v api-vars
              :let [val (System/getenv v)]
              :when val]
          [v val])))

;; Pass to docker via :extra-env (not as -e flags)
;; Note: babashka.process handles this properly
```

### Heredoc / Embedded Files

| Bash Pattern | Babashka Idiom |
|--------------|----------------|
| `cat << 'EOF'` | Multi-line string literal |
| Variable substitution | `str` or `format` |

```clojure
;; Bash: write_dockerfile with heredoc
;; cat > "${target_dir}/Dockerfile" << 'DOCKERFILE_EOF'
;; ...
;; DOCKERFILE_EOF

;; Babashka equivalent:
(def dockerfile-template
  "# Aishell Base Image
FROM debian:bookworm-slim

ARG WITH_CLAUDE=false
ARG WITH_OPENCODE=false
ARG CLAUDE_VERSION=\"\"
ARG OPENCODE_VERSION=\"\"
ARG BABASHKA_VERSION=1.12.214

ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update && apt-get install -y --no-install-recommends \\
    bash ca-certificates curl file git htop jq less \\
    ripgrep sqlite3 sudo tree unzip vim watch \\
    && rm -rf /var/lib/apt/lists/*

# ... rest of Dockerfile
")

(defn write-dockerfile [target-dir]
  (spit (str (fs/path target-dir "Dockerfile")) dockerfile-template))
```

### Color Output

| Bash Pattern | Babashka Idiom |
|--------------|----------------|
| `echo -e "${RED}Error${NC}"` | ANSI escape codes |
| `tput colors` | Check `TERM` env var |
| `[[ -t 1 ]]` | `(System/console)` |

```clojure
;; Bash: color support detection
;; supports_color() { [[ ! -t 1 ]] && return 1; ... }

;; Babashka equivalent:
(def colors-enabled?
  (and (some? (System/console))  ; TTY check
       (nil? (System/getenv "NO_COLOR"))
       (or (some? (System/getenv "FORCE_COLOR"))
           (not= "dumb" (System/getenv "TERM")))))

(def red (if colors-enabled? "\u001b[0;31m" ""))
(def yellow (if colors-enabled? "\u001b[0;33m" ""))
(def nc (if colors-enabled? "\u001b[0m" ""))

(defn error [msg]
  (binding [*out* *err*]
    (println (str red "Error:" nc " " msg)))
  (System/exit 1))

(defn warn [msg]
  (binding [*out* *err*]
    (println (str yellow "Warning:" nc " " msg))))
```

### SHA256 Hash Computation

| Bash Pattern | Babashka Idiom |
|--------------|----------------|
| `sha256sum file \| cut -c1-12` | `MessageDigest` |

```clojure
;; Bash: get_dockerfile_hash
;; hash=$(sha256sum "${temp_dir}/Dockerfile" | cut -c1-12)

;; Babashka equivalent:
(import '[java.security MessageDigest])

(defn sha256-hex [s]
  (let [md (MessageDigest/getInstance "SHA-256")
        bytes (.digest md (.getBytes s "UTF-8"))]
    (apply str (map #(format "%02x" %) bytes))))

(defn get-dockerfile-hash []
  (subs (sha256-hex dockerfile-template) 0 12))
```

### State File Management

| Bash Pattern | Babashka Idiom |
|--------------|----------------|
| Shell-style key=value | EDN format |
| Atomic write via mv | `spit` (atomic on POSIX) |

```clojure
;; Bash state file format:
;; BUILD_WITH_CLAUDE=true
;; BUILD_IMAGE_TAG="aishell:claude-2.0.22"

;; Babashka state file format (EDN):
;; {:with-claude true
;;  :with-opencode false
;;  :claude-version "2.0.22"
;;  :image-tag "aishell:claude-2.0.22"
;;  :timestamp "2026-01-20T12:00:00Z"}

(defn get-state-file [project-dir]
  (let [state-base (or (System/getenv "XDG_STATE_HOME")
                       (str (System/getProperty "user.home") "/.local/state"))
        hash (subs (sha256-hex (str (fs/canonicalize project-dir))) 0 12)]
    (fs/path state-base "aishell" "builds" (str hash ".edn"))))

(defn write-state [state-file state]
  (fs/create-dirs (fs/parent state-file))
  (spit (str state-file) (pr-str state)))

(defn read-state [state-file]
  (when (fs/exists? state-file)
    (edn/read-string (slurp (str state-file)))))
```

---

## Cross-Platform Considerations

### Windows-Specific

| Issue | Solution |
|-------|----------|
| No shebang support | Distribute `aishell.bat` wrapper or use `bb.exe` directly |
| Path separators | Use `babashka.fs` functions (handles `\` vs `/` automatically) |
| Executable extensions | `babashka.process` auto-resolves `.exe`, `.cmd`, `.bat` |
| Case-sensitive env vars | Use exact case in `:extra-env` (e.g., `"Path"` not `"PATH"`) |
| HOME directory | `(System/getProperty "user.home")` works cross-platform |
| `$HOME` expansion | Use Babashka function, not shell expansion |

```clojure
;; Cross-platform HOME resolution
(defn get-home []
  (or (System/getenv "HOME")
      (System/getProperty "user.home")))

;; Cross-platform path joining (NOT string concat)
(fs/path (get-home) ".aishell" "run.edn")

;; Windows wrapper (aishell.bat):
;; @echo off
;; bb "%~dp0aishell.bb" %*
```

### macOS-Specific

| Issue | Solution |
|-------|----------|
| Apple Silicon | Babashka provides `aarch64` builds |
| `realpath` differences | Use `(fs/canonicalize path)` |
| Docker Desktop SSH socket | Different path than Linux |

```clojure
;; SSH agent socket detection (cross-platform)
(defn get-ssh-agent-socket []
  (cond
    ;; macOS Docker Desktop magic socket
    (and (= "Mac OS X" (System/getProperty "os.name"))
         (fs/exists? "/run/host-services/ssh-auth.sock"))
    "/run/host-services/ssh-auth.sock"

    ;; Linux/standard SSH_AUTH_SOCK
    (System/getenv "SSH_AUTH_SOCK")
    (System/getenv "SSH_AUTH_SOCK")

    :else nil))
```

### Linux-Specific

| Issue | Solution |
|-------|----------|
| Static binary for containers | Use `babashka-*-linux-amd64-static.tar.gz` |
| Alpine/musl | Static binary works; no glibc dependency |
| Docker socket permissions | User must be in `docker` group |

---

## Project Structure

Recommended structure for the Babashka rewrite:

```
aishell                 ; Main script (shebang: #!/usr/bin/env bb)
bb.edn                  ; Dependencies, paths, tasks
src/
  aishell/
    core.clj            ; Main entry, CLI dispatch
    cli.clj             ; Argument parsing with babashka.cli
    config.clj          ; Config file parsing (run.edn)
    docker.clj          ; Docker build/run operations
    state.clj           ; Build state management
    validation.clj      ; Input validation (versions, paths)
    output.clj          ; Color output, spinners, logging
    templates.clj       ; Embedded Dockerfile, entrypoint, bashrc
```

### bb.edn Configuration

```clojure
{:paths ["src"]
 :min-bb-version "1.12.214"
 :tasks
 {build   {:doc "Build the container image"
           :task (exec 'aishell.core/build)}
  update  {:doc "Rebuild with latest versions"
           :task (exec 'aishell.core/update)}
  claude  {:doc "Run Claude Code"
           :task (exec 'aishell.core/claude)}
  opencode {:doc "Run OpenCode"
            :task (exec 'aishell.core/opencode)}}}
```

### Single-File Alternative

For simpler distribution, the entire CLI can be a single `.bb` file:

```clojure
#!/usr/bin/env bb
;; aishell - AI Shell Container Launcher

(ns aishell.core
  (:require [babashka.cli :as cli]
            [babashka.fs :as fs]
            [babashka.process :as p]
            [clojure.edn :as edn]
            [clojure.string :as str]))

;; ... all code in single file ...

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
```

---

## Migration Strategy

### Phase 1: Core Structure
1. Set up `bb.edn` with paths
2. Create `aishell.core` with main entry
3. Port CLI parsing using `babashka.cli`
4. Port output functions (error, warn, verbose)

### Phase 2: Docker Operations
1. Port `check-docker` function
2. Port `docker-build` with captured/streaming output
3. Port `docker-run` with all mount/env logic
4. Test on Linux first

### Phase 3: Config and State
1. Design `run.edn` format (simpler than `run.conf`)
2. Port state file management (use EDN)
3. Port Dockerfile hash detection
4. Maintain backward compat with `run.conf` if needed

### Phase 4: Cross-Platform
1. Test on macOS
2. Add Windows support (wrapper script)
3. Handle platform-specific SSH agent sockets

---

## Confidence Assessment

| Area | Confidence | Reasoning |
|------|------------|-----------|
| Built-in libraries | HIGH | Verified via Babashka book and v1.12.214 release notes |
| babashka.process | HIGH | Verified via babashka/process GitHub repo and API docs |
| babashka.fs | HIGH | Verified via babashka/fs API.md |
| babashka.cli | HIGH | Verified via babashka/cli GitHub repo |
| clj-yaml built-in | HIGH | Confirmed: "No installation required. Clj-yaml is built into babashka" |
| Cross-platform Linux/macOS | HIGH | Documented in Babashka book |
| Windows support | MEDIUM | Documented but less battle-tested than Unix |
| Docker integration | HIGH | Just shell commands via babashka.process |

---

## Sources

- [Babashka Book](https://book.babashka.org/) - Official documentation
- [babashka/babashka GitHub](https://github.com/babashka/babashka) - v1.12.214 release
- [babashka/process GitHub](https://github.com/babashka/process) - Process execution API
- [babashka/fs API](https://github.com/babashka/fs/blob/master/API.md) - File system functions
- [babashka/cli GitHub](https://github.com/babashka/cli) - CLI argument parsing
- [clj-yaml in Babashka](https://github.com/babashka/babashka/blob/master/feature-yaml/babashka/impl/yaml.clj) - Built-in YAML
