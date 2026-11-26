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
            [aishell.harness :as harness]
            [aishell.output :as output]
            [aishell.validation :as validation]
            [aishell.attach.resolve :as attach-resolve]
            [aishell.docker.bootstrap :as bootstrap]
            [clojure.string :as str]))

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

(defn plan-target
  "Decide which Sandbox `aishell vscode` opens, from the project's container
   listing. A `name` must be running: {:join name}. With no name, the only
   running Sandbox is joined, none running means {:start true} for the
   `vscode` Sandbox, and several running is an {:error} that lists them."
  [name containers]
  (let [running (filter #(bootstrap/running? (:status %)) containers)
        running-names (set (map #(naming/extract-short-name (:name %)) running))]
    (cond
      name (if (contains? running-names name)
             {:join name}
             {:error (str "Container '" name "' is not running.\n\n"
                          "Use 'aishell ps' to list containers.")})
      (empty? running) {:start true}
      :else (let [{picked :name error :error} (attach-resolve/resolve-attach-target containers "vscode")]
              (if error {:error error} {:join picked})))))

(defn- start-container!
  "Start the `vscode` Sandbox in detached mode if not already running.
   Returns the container name."
  [state project-dir image-tag unsafe-docker-args]
  (let [container-name (naming/container-name project-dir "vscode")]
    (when-not (naming/container-running? container-name)
      (let [cfg (config/load-config project-dir)
            git-id (docker-run/read-git-identity project-dir)
            harness-volume-name (when (harness/volume-harnesses-enabled? state)
                                 (or (:harness-volume-name state)
                                     (vol/volume-name (vol/compute-harness-hash state))))
            docker-args (docker-run/build-docker-args
                          {:project-dir project-dir
                           :image-tag image-tag
                           :config cfg
                           :state state
                           :git-identity git-id
                           :container-name container-name
                           :harness-volume-name harness-volume-name
                           :unsafe-docker-args unsafe-docker-args})
            docker-args (mapv #(if (= % "-it") "-d" %) docker-args)]
        (vol/ensure-vscode-server-volume! state image-tag)
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

(defn- open-own-sandbox!
  "Start the `vscode` Sandbox and open VSCode in it. Without `detach?`, block
   until the window closes, then stop the Sandbox."
  [state project-dir detach? extra-args unsafe-docker-args]
  (let [image-tag (run/resolve-image-tag project-dir false)
        _ (build/warn-if-foundation-out-of-date! image-tag)
        _ (ensure-imageconfig! image-tag)
        container-name (start-container! state project-dir image-tag unsafe-docker-args)]
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
        (cleanup! false)))))

(defn- join-sandbox!
  "Open VSCode in a running Sandbox that `aishell vscode` did not start, and
   return. The window is an Attached session: the Sandbox's Owning session
   decides when it stops, never this command."
  [project-dir short-name extra-args]
  (let [container-name (naming/container-name project-dir short-name)]
    (when-let [image (naming/container-image container-name)]
      (ensure-imageconfig! image))
    (launch-vscode! container-name project-dir false extra-args)
    (println (str "VSCode should open shortly in '" short-name "'. "
                  "Closing it leaves the Sandbox running."))))

(defn parse-args
  "Split the arguments after `aishell vscode`. A first argument that is not a
   flag names the Sandbox. Before `--`, aishell keeps its own flags and the
   rest go to code; everything after `--` goes to code."
  [args]
  (let [[before-separator after-separator] (split-with #(not= "--" %) args)
        sandbox-name (let [a (first before-separator)]
                       (when (and a (not (str/starts-with? a "-"))) a))
        before-separator (cond-> before-separator sandbox-name rest)
        own-flags #{"--detach" "--stop" "-h" "--help"}]
    (cond-> {:help? (boolean (some #{"-h" "--help"} before-separator))
             :stop? (boolean (some #{"--stop"} before-separator))
             :detach? (boolean (some #{"--detach"} before-separator))
             :code-args (into (vec (remove own-flags before-separator))
                              (rest after-separator))}
      sandbox-name (assoc :name sandbox-name))))

(defn print-help
  "Print help for the vscode command."
  []
  (println (str output/BOLD "Usage:" output/NC " aishell vscode [NAME] [OPTIONS] [-- CODE_ARGS...]"))
  (println)
  (println "Open VSCode as the developer user in a Sandbox.")
  (println "NAME picks a running Sandbox. Without it, the only running Sandbox is used;")
  (println "with none running, a vscode Sandbox starts.")
  (println (str output/YELLOW "(Experimental — API subject to change)" output/NC))
  (println)
  (println (str output/BOLD "Options:" output/NC))
  (println "  -h, --help      Show this help")
  (println "      --detach    Leave a vscode Sandbox this command starts running")
  (println "      --stop      Stop the vscode Sandbox")
  (println)
  (println (str output/BOLD "Extra arguments:" output/NC))
  (println "  Any arguments not listed above are passed through to the 'code' CLI.")
  (println (str "  Default args can be set via harness_args.vscode in "
  (util/resolve-project-config-dir (System/getProperty "user.dir"))
  "/config.yaml."))
  (println)
  (println (str output/BOLD "What this does:" output/NC))
  (println "  A running Sandbox: opens VSCode in it and returns. Closing the window")
  (println "  leaves the Sandbox running.")
  (println "  No Sandbox running: starts a vscode Sandbox, opens VSCode, waits for the")
  (println "  window to close, then stops it (unless --detach).")
  (println)
  (println (str output/BOLD "Note:" output/NC))
  (println "  Every Sandbox mounts the aishell-vscode-server volume while VSCode is")
  (println "  enabled, so extensions installed in VSCode persist across Sandboxes.")
  (println)
  (println (str output/BOLD "Prerequisites:" output/NC))
  (println "  - VSCode with 'code' CLI on PATH")
  (println "  - Dev Containers extension installed in VSCode")
  (println "  - aishell setup --with-vscode completed")
  (println)
  (println (str output/BOLD "Examples:" output/NC))
  (println (str "  " output/CYAN "aishell vscode" output/NC "                         Open VSCode in the running Sandbox, or a new one"))
  (println (str "  " output/CYAN "aishell vscode shell" output/NC "                   Open VSCode in the running 'shell' Sandbox"))
  (println (str "  " output/CYAN "aishell vscode --detach" output/NC "                Leave a new vscode Sandbox running"))
  (println (str "  " output/CYAN "aishell vscode --stop" output/NC "                  Stop the vscode Sandbox"))
  (println (str "  " output/CYAN "aishell vscode --disable-gpu" output/NC "           Pass --disable-gpu to code"))
  (println (str "  " output/CYAN "aishell vscode --detach --profile Work" output/NC " Detach with a code profile")))

(defn open-vscode
  "Main entry point for 'aishell vscode' command.
   Opens VSCode as the developer user in the Sandbox `plan-target` picks:
   a running one is joined and left running; otherwise the `vscode` Sandbox
   starts, and by default stops when the window closes unless detach? is set.
   Extra code-args are passed through to the 'code' CLI, merged after
   harness_args.vscode defaults from config. unsafe-docker-args lets refused
   docker_args and mounts entries through when the `vscode` Sandbox starts;
   joining a running Sandbox starts nothing, so it has no effect there."
  [& [{:keys [name detach? code-args unsafe-docker-args]}]]
  ;; Refuse a home-containing root before anything walks the tree
  (validation/check-project-dir! (System/getProperty "user.dir"))

  (let [state (state/read-state)
        project-dir (System/getProperty "user.dir")]
    (when-not state
      (output/error-no-setup))
    ;; The setup hint comes before the code and Docker checks: it is the fix
    ;; a user with VSCode disabled needs first
    (when-not (:with-vscode state)
      (output/error (str "aishell vscode needs VSCode enabled at setup.\n\n"
                         "Run: aishell setup --reuse-config --with-vscode")))
    (check-vscode!)
    (docker/check-docker!)

    (let [cfg (config/load-config project-dir)
          defaults (vec (or (get-in cfg [:harness_args :vscode]) []))
          extra-args (vec (concat defaults (or code-args [])))
          _ (when (seq defaults)
              (output/verbose (str "harness_args.vscode defaults: " (pr-str defaults))))
          _ (when (seq extra-args)
              (output/verbose (str "Passing to code CLI: " (pr-str extra-args))))
          plan (plan-target name (naming/list-project-containers project-dir))]
      (cond
        (:error plan) (output/error (:error plan))
        (:join plan) (join-sandbox! project-dir (:join plan) extra-args)
        :else (open-own-sandbox! state project-dir detach? extra-args unsafe-docker-args)))))
