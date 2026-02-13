---
phase: quick-7
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - src/aishell/vscode.clj
  - src/aishell/util.clj
  - src/aishell/cli.clj
  - src/aishell/output.clj
autonomous: true
must_haves:
  truths:
    - "Running `aishell vscode` opens VSCode attached to the container as `developer` user"
    - "If container is not running, it is started automatically before opening VSCode"
    - "If container is already running, VSCode opens without restarting it"
    - "VSCode per-image config is written so remoteUser=developer persists across sessions"
    - "Command works on Linux, macOS, Windows, and WSL2"
    - "Clear error if `code` CLI is not on PATH"
  artifacts:
    - path: "src/aishell/vscode.clj"
      provides: "VSCode subcommand implementation"
      exports: ["open-vscode"]
    - path: "src/aishell/util.clj"
      provides: "vscode-imageconfigs-dir utility function"
      contains: "vscode-imageconfigs-dir"
    - path: "src/aishell/cli.clj"
      provides: "CLI dispatch for vscode subcommand"
      contains: "\"vscode\""
  key_links:
    - from: "src/aishell/cli.clj"
      to: "src/aishell/vscode.clj"
      via: "case branch calling vscode/open-vscode"
      pattern: "vscode/open-vscode"
    - from: "src/aishell/vscode.clj"
      to: "src/aishell/docker/naming.clj"
      via: "container-running? and container-name"
      pattern: "naming/container-running\\?"
    - from: "src/aishell/vscode.clj"
      to: "src/aishell/util.clj"
      via: "vscode-imageconfigs-dir for config path"
      pattern: "util/vscode-imageconfigs-dir"
---

<objective>
Implement `aishell vscode` subcommand that opens VSCode attached to the aishell container as the `developer` user, with zero manual configuration.

Purpose: Users currently must manually configure VSCode's per-image `remoteUser` setting to avoid connecting as root. This subcommand automates that entirely.
Output: Working `aishell vscode` command that checks for `code` CLI, writes per-image config JSON, starts container if needed, and launches VSCode attached to it.
</objective>

<execution_context>
@/home/jonasrodrigues/.claude/get-shit-done/workflows/execute-plan.md
@/home/jonasrodrigues/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@vscode.md
@artifacts/investigate/20260213-1958-aishell-vscode-subcommand/REPORT.md
@src/aishell/cli.clj
@src/aishell/attach.clj
@src/aishell/run.clj
@src/aishell/docker/naming.clj
@src/aishell/util.clj
@src/aishell/state.clj
@src/aishell/docker.clj
@src/aishell/output.clj
@src/aishell/docker/run.clj
@src/aishell/docker/build.clj
</context>

<tasks>

<task type="auto">
  <name>Task 1: Add vscode-imageconfigs-dir to util.clj and create src/aishell/vscode.clj</name>
  <files>src/aishell/util.clj, src/aishell/vscode.clj</files>
  <action>
**1. Add `vscode-imageconfigs-dir` function to `src/aishell/util.clj`:**

Add a new public function that returns the platform-appropriate path to VSCode's Dev Containers imageConfigs directory. The function must handle 4 cases:

```clojure
(defn vscode-imageconfigs-dir
  "Get platform-appropriate path to VSCode Dev Containers imageConfigs directory.
   Windows: %APPDATA%/Code/User/globalStorage/ms-vscode-remote.remote-containers/imageConfigs
   macOS: ~/Library/Application Support/Code/User/globalStorage/ms-vscode-remote.remote-containers/imageConfigs
   Linux/WSL2: ~/.config/Code/User/globalStorage/ms-vscode-remote.remote-containers/imageConfigs"
  []
  (let [subpath ["Code" "User" "globalStorage" "ms-vscode-remote.remote-containers" "imageConfigs"]]
    (cond
      (fs/windows?)
      (let [appdata (or (System/getenv "APPDATA")
                        (str (fs/path (get-home) "AppData" "Roaming")))]
        (str (apply fs/path appdata subpath)))

      (= "Mac OS X" (System/getProperty "os.name"))
      (str (apply fs/path (get-home) "Library" "Application Support" subpath))

      :else ;; Linux and WSL2 (WSL2 code CLI reads from Linux paths)
      (let [config-home (or (System/getenv "XDG_CONFIG_HOME")
                            (str (fs/path (get-home) ".config")))]
        (str (apply fs/path config-home subpath))))))
```

Note: WSL2 does NOT need special detection here. When `code` is invoked from WSL2, the VSCode server reads config from the WSL2 filesystem (Linux paths), so the Linux path is correct for both native Linux and WSL2.

**2. Create `src/aishell/vscode.clj`:**

Create the new namespace with these functions:

```clojure
(ns aishell.vscode
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [cheshire.core :as json]
            [aishell.docker :as docker]
            [aishell.docker.naming :as naming]
            [aishell.docker.build :as build]
            [aishell.run :as run]
            [aishell.state :as state]
            [aishell.util :as util]
            [aishell.output :as output]))
```

**Functions to implement:**

a) `check-vscode!` - Check that `code` is on PATH via `(fs/which "code")`. If not found, call `(output/error "VSCode 'code' CLI not found on PATH.\n\nInstall VSCode and enable the 'code' command:\n  https://code.visualstudio.com/docs/setup/setup-overview\n\nOn macOS: Cmd+Shift+P > 'Shell Command: Install code command in PATH'\nOn Linux/Windows: 'code' is added to PATH during installation.")`.

b) `hex-encode` - Hex-encode a string (UTF-8 bytes to hex). Implementation: `(apply str (map #(format "%02x" (int %)) s))`. This is used to encode the container name for VSCode's remote URI scheme.

c) `ensure-imageconfig!` - Write/update VSCode per-image config JSON so `remoteUser` is `developer`.
   - Get image tag from state: `(or (:image-tag (state/read-state)) build/foundation-image-tag)`
   - Compute config filename: sanitize the image tag by replacing `:` and `/` with `_` (e.g., `aishell:foundation` becomes `aishell_foundation.json`). This filename is aishell-managed -- VSCode uses its own filename convention for configs it creates, but writing a file here makes it discoverable via "Open Container Configuration File" command.
   - Full path: `(str (fs/path (util/vscode-imageconfigs-dir) filename))`
   - Read existing file if present: `(when (fs/exists? path) (json/parse-string (slurp path) true))`
   - Merge with `{:remoteUser "developer"}`: `(merge existing {:remoteUser "developer"})`
   - Ensure parent dir exists: `(fs/create-dirs (fs/parent path))`
   - Write: `(spit path (json/generate-string merged {:pretty true}))`
   - Wrap in try/catch -- warn on failure but do NOT error (VSCode can still open, just as root): `(output/warn (str "Could not write VSCode image config: " (ex-message e) "\nVSCode may connect as root instead of developer."))`

d) `open-vscode` - Main entry point. Takes no arguments (reads project-dir from `System/getProperty "user.dir"`).

   Flow:
   1. `(check-vscode!)` -- error if no `code` CLI
   2. `(docker/check-docker!)` -- error if no Docker
   3. Read state, error if nil: `(when-not state (output/error-no-setup))`
   4. `(ensure-imageconfig!)` -- write per-image config (advisory, non-blocking)
   5. Determine container name: `(naming/container-name project-dir "vscode")`
   6. Check if running: `(naming/container-running? container-name)`
   7. If NOT running: start it via `(run/run-container nil [] {:container-name "vscode"})` -- BUT this will p/exec and replace the process on Unix. Instead, we need to start the container in the background. Use a different approach:
      - Build docker run args manually like run/run-container does, but use `docker run -d` (detached) instead of `-it` (interactive). The container needs to stay alive, so run with a long-running command.
      - **Simpler approach**: Use `docker start` if container exists but stopped, or create a new detached container. Actually the simplest correct approach:
        - Check `(naming/container-running? container-name)` -- if true, skip to step 8
        - Check `(naming/container-exists? container-name)` -- if true (stopped), run `(p/shell {:out :string :err :string} "docker" "start" container-name)` and wait for it
        - If container doesn't exist at all, we need to create one. Run a detached container with `sleep infinity` command so it stays alive:
          ```clojure
          (let [image-tag (or (:image-tag state) build/foundation-image-tag)]
            (p/shell {:out :string :err :string}
                     "docker" "run" "-d" "--name" container-name
                     image-tag "sleep" "infinity"))
          ```
          But this misses all the mounts, env vars, etc. that `run/run-container` sets up.

        **Best approach**: Build docker args using `docker-run/build-docker-args` but override to use `-d` (detached) instead of `-it`, and use `sleep infinity` as the command. This gives us all the mounts, env vars, volumes, etc.

        Implementation:
        ```clojure
        (require '[aishell.docker.run :as docker-run]
                 '[aishell.config :as config]
                 '[aishell.docker.volume :as vol])
        ```

        When container doesn't exist or is stopped-and-removed:
        ```clojure
        (let [cfg (config/load-config project-dir)
              git-id (docker-run/read-git-identity project-dir)
              ;; Resolve image tag (handles extensions)
              image-tag (or (:image-tag state) build/foundation-image-tag)
              ;; Get harness volume if available
              harness-volume-name (when (some #(get state %) [:with-claude :with-opencode :with-codex :with-gemini])
                                   (:harness-volume-name state))
              ;; Build standard docker args (this gives us all mounts, env vars, etc.)
              docker-args (docker-run/build-docker-args
                            {:project-dir project-dir
                             :image-tag image-tag
                             :config cfg
                             :state state
                             :git-identity git-id
                             :container-name container-name
                             :harness-volume-name harness-volume-name})
              ;; Replace "-it" with "-d" in the args vector for detached mode
              docker-args (mapv #(if (= % "-it") "-d" %) docker-args)]
          ;; Remove stopped container if exists
          (naming/remove-container-if-stopped! container-name)
          ;; Start detached with sleep infinity to keep alive
          (apply p/shell {:out :string :err :string} (concat docker-args ["sleep" "infinity"]))
          ;; Brief pause for container to be ready
          (Thread/sleep 1500))
        ```

   8. Build VSCode remote URI and launch:
      ```clojure
      (let [hex-name (hex-encode container-name)
            workspace-path (if (fs/windows?) "/workspace" project-dir)]
        (println (str "Opening VSCode attached to container..."))
        (p/shell {:inherit true}
                 "code" "--folder-uri"
                 (str "vscode-remote://attached-container+" hex-name workspace-path)))
      ```

      Note: `p/shell` with `{:inherit true}` is correct here -- `code` CLI returns immediately (non-blocking) after launching VSCode. Do NOT use `p/exec` since we want to print a success message after.

   9. Print success: `(println "VSCode should open shortly. If the Dev Containers extension is not installed, VSCode will prompt you.")`
  </action>
  <verify>
Run `bb -e "(require 'aishell.vscode)"` to verify the namespace compiles without errors. Run `bb -e "(require 'aishell.util) (println (aishell.util/vscode-imageconfigs-dir))"` to verify the config path function returns a valid path.
  </verify>
  <done>
`src/aishell/vscode.clj` exists with functions: `check-vscode!`, `hex-encode`, `ensure-imageconfig!`, `open-vscode`. `src/aishell/util.clj` has `vscode-imageconfigs-dir` function. Both compile without errors under babashka.
  </done>
</task>

<task type="auto">
  <name>Task 2: Wire vscode subcommand into CLI dispatch and help</name>
  <files>src/aishell/cli.clj, src/aishell/output.clj</files>
  <action>
**1. Add require to `src/aishell/cli.clj`:**

Add `[aishell.vscode :as vscode]` to the ns require vector.

**2. Add "vscode" case to the `dispatch` function's case statement:**

In the `(case (first clean-args) ...)` block, add a new case BEFORE the harness commands (claude, opencode, etc.) and AFTER "attach". Follow the `attach` pattern for help handling:

```clojure
"vscode" (let [rest-args (vec (rest clean-args))]
           (if (some #{"-h" "--help"} rest-args)
             (do
               (println (str output/BOLD "Usage:" output/NC " aishell vscode"))
               (println)
               (println "Open VSCode attached to the aishell container as the developer user.")
               (println)
               (println (str output/BOLD "Options:" output/NC))
               (println "  -h, --help    Show this help")
               (println)
               (println (str output/BOLD "What this does:" output/NC))
               (println "  1. Writes VSCode per-image config (remoteUser: developer)")
               (println "  2. Starts the container if not already running")
               (println "  3. Opens VSCode attached to the container")
               (println)
               (println (str output/BOLD "Prerequisites:" output/NC))
               (println "  - VSCode with 'code' CLI on PATH")
               (println "  - Dev Containers extension installed in VSCode")
               (println "  - aishell setup completed")
               (println)
               (println (str output/BOLD "Examples:" output/NC))
               (println (str "  " output/CYAN "aishell vscode" output/NC "    Open VSCode for current project")))
             (vscode/open-vscode)))
```

**3. Add "vscode" to help output in `print-help` function:**

Add a line for `vscode` right after the `attach` line (line ~107):
```clojure
(println (str "  " output/CYAN "vscode" output/NC "     Open VSCode attached to container"))
```

This should appear unconditionally (not behind an `installed-harnesses` check) since vscode is a core command, not a harness-specific one.

**4. Add "vscode" to known-commands in `src/aishell/output.clj`:**

Update the `known-commands` set to include `"vscode"` so the typo suggestion system can suggest it:
```clojure
(def known-commands #{"setup" "update" "check" "exec" "attach" "ps" "volumes"
                      "claude" "opencode" "codex" "gemini" "gitleaks" "vscode"})
```

**5. Add "vscode" to `known-subcommands` set in dispatch function:**

In the `dispatch` function, the `known-subcommands` set is used to decide whether to extract `--name` flag. Add `"vscode"` to this set since `vscode` doesn't use `--name`:
```clojure
known-subcommands #{"setup" "update" "check" "exec" "ps" "volumes" "attach" "vscode"}
```
  </action>
  <verify>
Run `bb -e "(require 'aishell.cli)"` to verify compilation. Run `bb -cp src -m aishell.core -- --help` (or however the project runs) to verify "vscode" appears in help output. Run `bb -cp src -m aishell.core -- vscode --help` to verify the vscode help text prints.
  </verify>
  <done>
`aishell --help` shows the `vscode` command. `aishell vscode --help` displays usage info. `aishell vscod` (typo) suggests "vscode". The vscode case in dispatch calls `vscode/open-vscode` for execution.
  </done>
</task>

<task type="checkpoint:human-verify" gate="blocking">
  <what-built>Complete `aishell vscode` subcommand: CLI dispatch, help text, per-image config writing, container lifecycle (start if needed / reuse if running), and VSCode launch via `code --folder-uri`.</what-built>
  <how-to-verify>
1. Run `aishell --help` and confirm "vscode" appears in the commands list
2. Run `aishell vscode --help` and confirm help text is displayed
3. Run `aishell vscode` from a project directory with a completed setup:
   - Confirm VSCode opens attached to the container
   - In VSCode terminal, run `whoami` -- should show `developer` (not root)
   - Confirm the workspace folder is correct (project files visible)
4. Run `aishell vscode` again while container is still running -- should open VSCode without restarting container
5. Check that the imageConfigs JSON was written to the correct platform path (look in the directory printed by `bb -e "(require 'aishell.util) (println (aishell.util/vscode-imageconfigs-dir))"`)
  </how-to-verify>
  <resume-signal>Type "approved" or describe issues</resume-signal>
</task>

</tasks>

<verification>
- `aishell --help` lists `vscode` as a command
- `aishell vscode --help` prints usage information
- `aishell vscode` with no running container starts one and opens VSCode
- `aishell vscode` with running container reuses it and opens VSCode
- VSCode connects as `developer` user (verify with `whoami` in VSCode terminal)
- imageConfigs JSON exists at platform-correct path with `{"remoteUser": "developer"}`
- `aishell vscod` (typo) suggests "vscode"
</verification>

<success_criteria>
- `aishell vscode` opens VSCode attached to the container as `developer` with zero manual config
- Container is started automatically if not running, reused if already running
- Works on the user's current platform (Linux/macOS/Windows/WSL2)
- Per-image config JSON persists across sessions
</success_criteria>

<output>
After completion, create `.planning/quick/7-implement-aishell-vscode-subcommand/7-SUMMARY.md`
</output>
