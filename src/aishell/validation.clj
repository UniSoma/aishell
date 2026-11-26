(ns aishell.validation
  "Security validation for Sandbox config: refuses docker_args and mounts
   entries that weaken the Sandbox's isolation, invalid claude_shared_paths
   and project dirs at or above home, and warns on dangerous mount paths."
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [aishell.output :as output]
            [aishell.util :as util]))

(def refused-docker-flags
  "docker run flags a docker_args entry may not carry, written with `=` or
   as two tokens: each grants privilege, changes capabilities or security
   options, joins a host namespace or another network or runtime, or exposes
   a device. Hardcoded and additive (ADR 0006): config cannot relax them."
  #{"--privileged" "--cap-add" "--cap-drop" "--security-opt" "--network" "--net"
    "--runtime" "--pid" "--userns" "--ipc" "--uts" "--cgroupns" "--device"
    "--device-cgroup-rule" "--volumes-from"})

(def ^:private boolean-docker-flags
  "Refused flags that take no value, so the next token is not theirs."
  #{"--privileged"})

(defn- docker-sock? [s]
  (str/includes? s "docker.sock"))

(defn- refused-docker-arg-entries
  "The docker_args entries a Sandbox may not start with, in order. A flag
   written as two tokens is reported with its value."
  [tokens]
  (loop [[t & more] (map str tokens)
         found []]
    (if (nil? t)
      found
      (let [flag (first (str/split t #"=" 2))]
        (cond
          (and (contains? refused-docker-flags flag) (= flag t)
               (not (contains? boolean-docker-flags flag))
               (seq more) (not (str/starts-with? (first more) "-")))
          (recur (rest more) (conj found (str t " " (first more))))

          (or (contains? refused-docker-flags flag) (docker-sock? t))
          (recur more (conj found t))

          :else
          (recur more found))))))

(defn refused-entries
  "Every docker_args entry and mount host source that weakens the Sandbox's
   isolation, in order, as {:key \"docker_args\"|\"mounts\" :entry s}. docker-args are
   the tokens after variable expansion, as docker run receives them;
   mount-sources the host side of each mounts entry, expanded. A mount source
   is refused when it names docker.sock."
  [{:keys [docker-args mount-sources]}]
  (into (mapv (fn [e] {:key "docker_args" :entry e})
              (refused-docker-arg-entries docker-args))
        (comp (map str)
              (filter docker-sock?)
              (map (fn [s] {:key "mounts" :entry s})))
        mount-sources))

(defn check-refused-entries!
  "Refuse to start a Sandbox with refused entries (see refused-entries): exits
   1 through output/error naming every entry. With unsafe?, the
   --unsafe-docker-args override, each entry is let through and named in a
   warning instead. --unsafe does not apply."
  [entries unsafe?]
  (when (seq entries)
    (if unsafe?
      (doseq [{:keys [key entry]} entries]
        (output/warn (str "--unsafe-docker-args let through " key " entry: " entry)))
      (output/error
       (str "Refusing to start: these entries weaken the Sandbox's isolation:\n"
            (str/join (map (fn [{:keys [key entry]}] (str "  - " key ": " entry "\n")) entries))
            "Remove them, or put --unsafe-docker-args before the command to let them through.\n"
            "--unsafe does not override this.")))))

(def dangerous-mount-paths
  "Potentially dangerous mount paths that expose sensitive host data.
   These are advisory warnings - users may have legitimate reasons."
  [{:pattern #"^/$"
    :message "/: Root filesystem mount gives full host access"}
   {:pattern #"/etc/passwd"
    :message "/etc/passwd: System user database"}
   {:pattern #"/etc/shadow"
    :message "/etc/shadow: System password hashes"}
   {:pattern #"\.aws/credentials|\.aws$"
    :message "~/.aws: AWS credentials exposure"}
   {:pattern #"\.azure"
    :message "~/.azure: Azure credentials exposure"}
   {:pattern #"\.gnupg"
    :message "~/.gnupg: GPG private keys exposure"}
   {:pattern #"\.kube"
    :message "~/.kube: Kubernetes credentials exposure"}
   {:pattern #"\.password-store"
    :message "~/.password-store: Password manager data"}
   {:pattern #"\.ssh/id_"
    :message "~/.ssh private keys: SSH key exposure"}])

(def claude-machine-state-paths
  "Top-level ~/.claude entries that hold Claude machine state: PID/socket
   runtime data (daemon registry/lock, background jobs, tasks, session locks,
   session env, shell snapshots, file history, the internal store db).
   Sharing any of these across sandboxes corrupts the supervisor, so a
   user claude_shared_paths entry colliding with one is hard-rejected.
   Patterns match a path relative to ~/.claude (forward-slash separated)."
  [{:pattern #"(?i)^daemon(\.lock)?(/|$)"
    :message "daemon lock/registry (per-container supervisor state)"}
   {:pattern #"(?i)^roster(\.json)?(/|$)"
    :message "daemon roster (per-container supervisor state)"}
   {:pattern #"(?i)^jobs(/|$)"
    :message "background jobs registry (per-container runtime state)"}
   {:pattern #"(?i)^tasks(/|$)"
    :message "tasks registry (per-container runtime state)"}
   {:pattern #"(?i)^sessions(/|$)"
    :message "session locks (per-container runtime state)"}
   {:pattern #"(?i)^session-env(/|$)"
    :message "session environment (per-container runtime state)"}
   {:pattern #"(?i)^shell-snapshots(/|$)"
    :message "shell snapshots (per-container runtime state)"}
   {:pattern #"(?i)^file-history(/|$)"
    :message "file history (per-container runtime state)"}
   {:pattern #"(?i)^__store\.db(/|$)"
    :message "internal store db (per-container runtime state)"}])

(defn- claude-shared-path-machine-state
  "Return the blocklist message when a ~/.claude-relative path (forward-slash
   separated) collides with a Claude machine-state path, else nil."
  [rel-path]
  (some (fn [{:keys [pattern message]}]
          (when (re-find pattern rel-path) message))
        claude-machine-state-paths))

(defn- claude-shared-path-absolute?
  "True when the entry is an absolute path (unix root, home, or Windows drive)
   rather than a path relative to ~/.claude."
  [entry]
  (boolean (re-find #"^(/|~|[A-Za-z]:)" entry)))

(defn- claude-shared-path-escapes?
  "True when a forward-slash-separated relative path escapes above ~/.claude via
   '..' segments. Resolves segments against a virtual root without touching the
   filesystem, so it is platform-independent."
  [rel-path]
  (loop [comps (str/split rel-path #"/")
         depth 0]
    (if (empty? comps)
      false
      (let [c (first comps)]
        (cond
          (or (str/blank? c) (= "." c)) (recur (rest comps) depth)
          (= ".." c) (if (zero? depth) true (recur (rest comps) (dec depth)))
          :else (recur (rest comps) (inc depth)))))))

(defn check-claude-shared-paths
  "Hard-reject unsafe claude_shared_paths entries. Unlike the
   warn-only mount checkers in this namespace, a violation calls output/error
   (which exits 1) so an unsafe share never reaches Docker. Rejects, naming the
   offending entry:
     - absolute paths (must be relative to ~/.claude),
     - entries escaping ~/.claude via '..',
     - entries colliding with a Claude machine-state path.
   No-ops for a nil/empty list and for safe entries."
  [paths]
  (doseq [entry paths
          :when (string? entry)
          :let [rel-path (str/replace entry "\\" "/")]]
    (cond
      (claude-shared-path-absolute? entry)
      (output/error (str "Invalid claude_shared_paths entry '" entry
                         "': must be a path relative to ~/.claude, not absolute."))

      (claude-shared-path-escapes? rel-path)
      (output/error (str "Invalid claude_shared_paths entry '" entry
                         "': path escapes ~/.claude via '..'."))

      :else
      (when-let [msg (claude-shared-path-machine-state rel-path)]
        (output/error (str "Refusing claude_shared_paths entry '" entry
                           "': collides with Claude machine state — " msg
                           ". Machine state must not be shared across sandboxes."))))))

(defn check-dangerous-mounts
  "Check mount paths for dangerous patterns.
   Accepts a seq of mount strings (source or source:dest format).
   Returns seq of warning messages, or nil if none found."
  [mounts]
  (when (seq mounts)
    (seq
     (for [mount mounts
           :let [mount-str (str mount)
                  ;; Extract source path (before : if present)
                 source (first (str/split mount-str #":" 2))]
           {:keys [pattern message]} dangerous-mount-paths
           :when (re-find pattern source)]
       message))))

(defn warn-dangerous-mounts
  "Warn about dangerous mount paths if any found.
   Advisory only - does not block execution."
  [mounts]
  (when-let [warnings (check-dangerous-mounts mounts)]
    (println)  ; Blank line before warning block
    (output/warn "Security notice: Potentially dangerous mount paths detected")
    (doseq [msg warnings]
      (binding [*out* *err*]
        (println (str "  - " msg))))
    (binding [*out* *err*]
      (println)
      (println "These mounts may expose sensitive host data. Use only if necessary.")
      (println))))

(defn- canonical-path
  "Absolute, normalized form of `path` with symlinks resolved when the
   path exists. A missing path still normalizes, so a dangling HOME cannot
   turn the guard off."
  [path]
  (let [p (fs/absolutize path)]
    (str (if (fs/exists? p)
           (fs/canonicalize p)
           (fs/normalize p)))))

(defn home-dir-conflict
  "Why `project-dir` may not be a sandbox root given `home`, or nil when it
   may. The root must not equal or contain the user's home: sandboxing it
   would expose ~/.ssh, credentials, and every other project to the
   harness, and the container start mutates files under the mounted home.
   Both paths are canonicalized first, so symlinks and trailing slashes
   do not change the answer."
  [project-dir home]
  (let [project (canonical-path project-dir)
        home (canonical-path home)
        root? (= project (str (fs/path project "/")))
        ancestor? (or root?
                      (str/starts-with? home (str project java.io.File/separator)))]
    (cond
      (= project home)
      (str project " is your home directory")

      ancestor?
      (str project " contains your home directory (" home ")"))))

(defn check-project-dir!
  "Refuse to launch a sandbox rooted at `project-dir` when it is, or
   contains, the user's home directory. Exits 1 through output/error;
   --unsafe does not apply, and the message says so. Call it before any
   work that walks the project tree."
  [project-dir]
  (when-let [reason (home-dir-conflict project-dir (util/get-home))]
    (output/error
     (str "Refusing to sandbox: " reason ".\n"
          "A sandbox rooted there would expose your SSH keys, credentials, and\n"
          "every project to the harness, and starting it writes into your home.\n"
          "--unsafe does not override this. cd into a project directory and retry."))))
