(ns aishell.pi
  "Pi packages auto-installation.
   Installs Pi plugins in a short-lived container before entering the main container.
   Uses hash-based skip to avoid redundant installs."
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [clojure.string :as str]
            [aishell.docker.build :as build]
            [aishell.docker.hash :as hash]
            [aishell.docker.spinner :as spinner]
            [aishell.output :as output]
            [aishell.util :as util]))

(defn- pi-packages-hash-path
  "Path to stored Pi packages hash: ~/.aishell/.pi-packages-hash"
  []
  (str (fs/path (util/config-dir) ".pi-packages-hash")))

(defn- read-stored-hash
  "Read stored Pi packages hash. Returns string or nil."
  []
  (let [path (pi-packages-hash-path)]
    (when (fs/exists? path)
      (str/trim (slurp path)))))

(defn- write-hash!
  "Write Pi packages hash to file."
  [hash-str]
  (let [path (pi-packages-hash-path)]
    (util/ensure-dir (str (fs/parent path)))
    (spit path hash-str)))

(defn- compute-packages-hash
  "Compute hash of Pi packages list (sorted, deterministic)."
  [packages]
  (hash/compute-hash (pr-str (sort packages))))

(defn- get-uid []
  (if (fs/windows?)
    "1000"
    (-> (p/shell {:out :string} "id" "-u") :out str/trim)))

(defn- get-gid []
  (if (fs/windows?)
    "1000"
    (-> (p/shell {:out :string} "id" "-g") :out str/trim)))

(defn- run-pi-install!
  "Run Pi install in a short-lived Docker container.
   Bypasses entrypoint (like populate-volume) to run as root — needed because
   the harness volume is root-owned and npm install requires write access.
   Sets PATH/HOME/NPM_CONFIG_PREFIX explicitly. Chowns ~/.pi after install
   so the real container user can read/write Pi settings at runtime.
   Returns true on success, false on failure."
  [packages harness-volume-name]
  (let [uid (get-uid)
        gid (get-gid)
        home (util/get-home)
        pi-dir (str (fs/path home ".pi"))
        ;; Ensure ~/.pi exists on host
        _ (util/ensure-dir pi-dir)
        ;; Packages are passed as positional args to avoid shell interpolation.
        install-script (str "set -e"
                            " && export PATH=/tools/npm/bin:/tools/bin:$PATH"
                            " && for pkg in \"$@\"; do pi install \"$pkg\"; done"
                            " && chmod -R a+rwX /tools"
                            " && chown -R " uid ":" gid " /root/.pi")
        cmd (into ["docker" "run" "--rm"
                   "--entrypoint" ""
                   "-v" (str harness-volume-name ":/tools")
                   "-v" (str pi-dir ":/root/.pi")
                   "-e" "HOME=/root"
                   "-e" "NPM_CONFIG_PREFIX=/tools/npm"
                   "-e" "NODE_PATH=/tools/npm/lib/node_modules"
                   build/foundation-image-tag
                   "sh" "-c" install-script "sh"]
                  packages)]
    (try
      (let [{:keys [exit err]} (apply p/shell {:out :string
                                               :err :string
                                               :continue true}
                                      cmd)]
        (when-not (zero? exit)
          (output/warn (str "Pi package installation failed:\n" err)))
        (zero? exit))
      (catch Exception e
        (output/warn (str "Pi package installation error: " (.getMessage e)))
        false))))

(defn ensure-pi-packages!
  "Install Pi packages if configured and needed.
   Compares config hash with stored hash to skip redundant installs.
   Warns on failure but does not block container entry."
  [cfg state harness-volume-name]
  (let [packages (when (sequential? (:pi_packages cfg))
                   (->> (:pi_packages cfg)
                        (filter #(and (string? %) (not (str/blank? %))))
                        vec))]
    (when (and (seq packages)
               (:with-pi state)
               harness-volume-name)
      (let [current-hash (compute-packages-hash packages)
            stored-hash (read-stored-hash)]
        (when (not= current-hash stored-hash)
          (let [success? (spinner/with-spinner
                           "Installing Pi packages"
                           #(run-pi-install! packages harness-volume-name))]
            (if success?
              (write-hash! current-hash)
              (output/warn "Pi packages may not be available. Installation will retry next run."))))))))
