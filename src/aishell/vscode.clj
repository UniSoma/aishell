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
  "Ensure VSCode per-image config exists with a remoteUser default.
   If the config already has a remoteUser, it is left unchanged.
   Advisory only - warns on failure but does not error."
  [image-tag]
  (try
    (let [filename (str (.toLowerCase (java.net.URLEncoder/encode image-tag "UTF-8")) ".json")
          config-dir (util/vscode-imageconfigs-dir)
          config-path (str (fs/path config-dir filename))
          existing (when (fs/exists? config-path)
                     (json/parse-string (slurp config-path) true))]
      (when-not (:remoteUser existing)
        (fs/create-dirs config-dir)
        (spit config-path (json/generate-string (assoc (or existing {}) :remoteUser "developer")
                                                {:pretty true}))))
    (catch Exception e
      (output/warn (str "Could not write VSCode image config: " (ex-message e)
                       "\nVSCode may connect as root instead of developer.")))))

(defn- start-container!
  "Start the vscode container in detached mode if not already running.
   Returns the container name."
  [state project-dir image-tag]
  (let [container-name (naming/container-name project-dir "vscode")]
    (when-not (naming/container-running? container-name)
      (let [cfg (config/load-config project-dir)
            git-id (docker-run/read-git-identity project-dir)
            harness-volume-name (when (some #(get state %) [:with-claude :with-opencode :with-codex :with-gemini :with-pi :with-openspec])
                                 (or (:harness-volume-name state)
                                     (vol/volume-name (vol/compute-harness-hash state))))
            docker-args (docker-run/build-docker-args
                          {:project-dir project-dir
                           :image-tag image-tag
                           :config cfg
                           :state state
                           :git-identity git-id
                           :container-name container-name
                           :harness-volume-name harness-volume-name})
            docker-args (mapv #(if (= % "-it") "-d" %) docker-args)
            ;; Mount ~/.vscode-server for server persistence across restarts
            home (util/get-home)
            vs-src (str (fs/path home ".vscode-server"))
            vs-dst (if (fs/windows?)
                     "/home/developer/.vscode-server"
                     vs-src)
            _ (fs/create-dirs vs-src)
            docker-args (let [img (peek docker-args)]
                          (-> (pop docker-args)
                              (into ["-v" (str vs-src ":" vs-dst)])
                              (conj img)))]
        (naming/remove-container-if-stopped! container-name)
        (apply p/shell {:out :string :err :string} (concat docker-args ["sleep" "infinity"]))
        (Thread/sleep 1500)))
    container-name))

(defn- stop-container!
  "Stop and remove the vscode container."
  [container-name]
  (when (naming/container-running? container-name)
    (p/shell {:out :string :err :string} "docker" "stop" container-name)))

(defn stop-vscode
  "Stop the vscode container for the current project."
  []
  (docker/check-docker!)
  (let [state (state/read-state)
        project-dir (System/getProperty "user.dir")]
    (when-not state
      (output/error-no-setup))
    (let [container-name (naming/container-name project-dir "vscode")]
      (if (naming/container-running? container-name)
        (do
          (stop-container! container-name)
          (println (str "Stopped " container-name)))
        (println "No running vscode container found.")))))

(defn- launch-vscode!
  "Launch VSCode attached to the container. When wait? is true, opens a new
   window and blocks until it is closed. Extra args are passed through to code."
  [container-name project-dir wait? extra-args]
  (let [hex-name (hex-encode container-name)
        workspace-path (if (fs/windows?) "/workspace" project-dir)
        uri (str "vscode-remote://attached-container+" hex-name workspace-path)
        base-args (if wait?
                    ["code" "--new-window" "--folder-uri" uri "--wait"]
                    ["code" "--folder-uri" uri])
        all-args (into base-args extra-args)]
    (println "Opening VSCode attached to container...")
    (apply p/shell {:inherit true} all-args)))

(defn open-vscode
  "Main entry point for 'aishell vscode' command.
   Opens VSCode attached to the aishell container as developer user.
   By default blocks until VSCode window closes, then stops the container.
   With detach?=true, returns immediately and leaves container running.
   Extra code-args are passed through to the 'code' CLI, merged after
   harness_args.vscode defaults from config."
  [& [{:keys [detach? code-args]}]]
  ;; 1. Check prerequisites
  (check-vscode!)
  (docker/check-docker!)

  ;; 2. Read state
  (let [state (state/read-state)
        project-dir (System/getProperty "user.dir")]
    (when-not state
      (output/error-no-setup))

    ;; 3. Resolve image tag (handles extensions like aishell claude does)
    (let [base-tag (or (:image-tag state) build/foundation-image-tag)
          image-tag (run/resolve-image-tag base-tag project-dir false)
          cfg (config/load-config project-dir)

          ;; 4. Merge harness_args.vscode defaults with CLI args
          defaults (vec (or (get-in cfg [:harness_args :vscode]) []))
          extra-args (vec (concat defaults (or code-args [])))
          _ (when (seq defaults)
              (output/verbose (str "harness_args.vscode defaults: " (pr-str defaults))))
          _ (when (seq extra-args)
              (output/verbose (str "Passing to code CLI: " (pr-str extra-args))))]

      ;; 5. Write per-image config (advisory, non-blocking)
      (ensure-imageconfig! image-tag)

      ;; 6. Start container and launch VSCode
      (let [container-name (start-container! state project-dir image-tag)]
        (if detach?
          (do
            (launch-vscode! container-name project-dir false extra-args)
            (println "VSCode should open shortly. Container will keep running in the background.\nStop it with: aishell vscode --stop"))
          ;; Wait mode: --new-window ensures each instance gets its own window,
          ;; so multiple `aishell vscode` processes don't interfere via --wait.
          (let [stopped? (atom false)
                cleanup! (fn [from-hook?]
                           (when (compare-and-set! stopped? false true)
                             (when from-hook? (println))
                             (println "VSCode closed. Stopping container...")
                             (stop-container! container-name)
                             (println (str "Stopped " container-name))))]
            (.addShutdownHook (Runtime/getRuntime)
              (Thread. (fn [] (cleanup! true))))
            (try
              (launch-vscode! container-name project-dir true extra-args)
              (catch Exception _))
            (cleanup! false)))))))
