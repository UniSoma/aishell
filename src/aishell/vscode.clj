(ns aishell.vscode
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [cheshire.core :as json]
            [aishell.docker :as docker]
            [aishell.docker.naming :as naming]
            [aishell.docker.build :as build]
            [aishell.docker.run :as docker-run]
            [aishell.run :as run]
            [aishell.state :as state]
            [aishell.util :as util]
            [aishell.config :as config]
            [aishell.docker.volume :as vol]
            [aishell.output :as output]))

(defn check-vscode!
  "Check that 'code' CLI is available on PATH. Exit with error if not found."
  []
  (when-not (fs/which "code")
    (output/error "VSCode 'code' CLI not found on PATH.

Install VSCode and enable the 'code' command:
  https://code.visualstudio.com/docs/setup/setup-overview

On macOS: Cmd+Shift+P > 'Shell Command: Install code command in PATH'
On Linux/Windows: 'code' is added to PATH during installation.")))

(defn hex-encode
  "Hex-encode a string (UTF-8 bytes to hex).
   Used to encode container name for VSCode's remote URI scheme."
  [s]
  (apply str (map #(format "%02x" (int %)) s)))

(defn ensure-imageconfig!
  "Write/update VSCode per-image config JSON so remoteUser is developer.
   Advisory only - warns on failure but does not error (VSCode can still open as root)."
  []
  (try
    (let [state (state/read-state)
          image-tag (or (:image-tag state) build/foundation-image-tag)
          ;; Sanitize image tag for filename (replace : and / with _)
          filename (str (clojure.string/replace image-tag #"[:/]" "_") ".json")
          config-dir (util/vscode-imageconfigs-dir)
          config-path (str (fs/path config-dir filename))
          ;; Read existing config if present
          existing (when (fs/exists? config-path)
                     (json/parse-string (slurp config-path) true))
          ;; Merge with remoteUser: developer
          merged (merge existing {:remoteUser "developer"})]
      ;; Ensure parent directory exists
      (fs/create-dirs config-dir)
      ;; Write config
      (spit config-path (json/generate-string merged {:pretty true})))
    (catch Exception e
      (output/warn (str "Could not write VSCode image config: " (ex-message e)
                       "\nVSCode may connect as root instead of developer.")))))

(defn open-vscode
  "Main entry point for 'aishell vscode' command.
   Opens VSCode attached to the aishell container as developer user.
   Starts container if not running, reuses if already running."
  []
  ;; 1. Check prerequisites
  (check-vscode!)
  (docker/check-docker!)

  ;; 2. Read state
  (let [state (state/read-state)
        project-dir (System/getProperty "user.dir")]
    (when-not state
      (output/error-no-setup))

    ;; 3. Write per-image config (advisory, non-blocking)
    (ensure-imageconfig!)

    ;; 4. Determine container name
    (let [container-name (naming/container-name project-dir "vscode")]

      ;; 5. Check if container is running
      (when-not (naming/container-running? container-name)
        ;; Container not running - start it in detached mode
        (let [cfg (config/load-config project-dir)
              git-id (docker-run/read-git-identity project-dir)
              image-tag (or (:image-tag state) build/foundation-image-tag)
              ;; Get harness volume if available
              harness-volume-name (when (some #(get state %) [:with-claude :with-opencode :with-codex :with-gemini])
                                   (:harness-volume-name state))
              ;; Build standard docker args
              docker-args (docker-run/build-docker-args
                            {:project-dir project-dir
                             :image-tag image-tag
                             :config cfg
                             :state state
                             :git-identity git-id
                             :container-name container-name
                             :harness-volume-name harness-volume-name})
              ;; Replace "-it" with "-d" for detached mode
              docker-args (mapv #(if (= % "-it") "-d" %) docker-args)]
          ;; Remove stopped container if exists
          (naming/remove-container-if-stopped! container-name)
          ;; Start detached with sleep infinity to keep alive
          (apply p/shell {:out :string :err :string} (concat docker-args ["sleep" "infinity"]))
          ;; Brief pause for container to be ready
          (Thread/sleep 1500)))

      ;; 6. Build VSCode remote URI and launch
      (let [hex-name (hex-encode container-name)
            workspace-path (if (fs/windows?) "/workspace" project-dir)]
        (println "Opening VSCode attached to container...")
        (p/shell {:inherit true}
                 "code" "--folder-uri"
                 (str "vscode-remote://attached-container+" hex-name workspace-path)))

      ;; 7. Print success message
      (println "VSCode should open shortly. If the Dev Containers extension is not installed, VSCode will prompt you."))))
