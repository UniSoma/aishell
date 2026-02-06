(ns aishell.docker.naming
  "Container naming and Docker state query utilities.
   Generates deterministic container names from project paths and provides
   functions to query Docker container state."
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [clojure.string :as str]
            [aishell.docker.hash :as hash]
            [aishell.output :as output]))

(defn project-hash
  "Generate an 8-character hex hash from a project directory path.
  Uses SHA-256 of the canonicalized path (symlinks resolved).

  Collision probability: 8 hex chars = 2^32 possible hashes (~4.3 billion).
  Using birthday paradox analysis, collision probability at 100 projects
  is approximately 0.0001% (1 in 1 million). At 1000 projects: ~0.01%.
  This is acceptable for a local developer tool managing dozens of projects."
  [project-dir]
  (let [canonical (str (fs/canonicalize project-dir))]
    (subs (hash/compute-hash canonical) 0 8)))

(defn validate-container-name!
  "Validate user-provided name portion against Docker naming rules.
   Must start with alphanumeric, can contain alphanumeric, underscore, period, hyphen.
   Full container name (aishell-XXXXXXXX-{name}) must not exceed 63 characters.
   Exits with error if validation fails."
  [name]
  (when (empty? name)
    (output/error "Container name cannot be empty"))
  (when-not (re-matches #"^[a-zA-Z0-9][a-zA-Z0-9_.-]*$" name)
    (output/error (str "Invalid container name: " name
                      "\nMust start with alphanumeric, can contain alphanumeric, underscore, period, hyphen")))
  ;; Full name format: "aishell-" (8 chars) + hash (8 chars) + "-" (1 char) + name
  ;; = 17 chars + name length. Max Docker name length is 63 chars.
  ;; So name portion max is 46 chars.
  (when (> (count name) 46)
    (output/error (str "Container name too long: " name
                      "\nMaximum length: 46 characters (full container name must not exceed 63)"))))

(defn container-name
  "Generate deterministic container name from project directory and name.
   Format: aishell-{8-char-hash}-{name}
   Validates name before generating."
  [project-dir name]
  (validate-container-name! name)
  (str "aishell-" (project-hash project-dir) "-" name))

(defn container-exists?
  "Check if a container exists (running or stopped)."
  [container-name]
  (try
    (let [{:keys [exit]} (p/shell {:out :string :err :string :continue true}
                                   "docker" "inspect" container-name)]
      (zero? exit))
    (catch Exception _ false)))

(defn container-running?
  "Check if a container is currently running."
  [container-name]
  (try
    (let [{:keys [exit out]} (p/shell {:out :string :err :string :continue true}
                                       "docker" "ps"
                                       "--filter" (str "name=^" container-name "$")
                                       "--format" "{{.Names}}")]
      (and (zero? exit)
           (= (str/trim out) container-name)))
    (catch Exception _ false)))

(defn remove-container-if-stopped!
  "Remove container if it exists and is stopped.
   Returns:
   - :running if container is running
   - :removed if container was stopped and is now removed
   - :not-found if container doesn't exist"
  [container-name]
  (cond
    (container-running? container-name)
    :running

    (container-exists? container-name)
    (do
      (p/shell {:out :string :err :string :continue true}
               "docker" "rm" "-f" container-name)
      :removed)

    :else
    :not-found))

(defn ensure-name-available!
  "Ensure container name is available for use.
   If a container with this name is running, exit with error showing attach options.
   If a stopped container exists, remove it and proceed.
   If no container exists, proceed silently."
  [container-name harness-name]
  (case (remove-container-if-stopped! container-name)
    :running
    (output/error (str "Container '" container-name "' is already running.\n"
                      "To attach: aishell attach " harness-name "\n"
                      "To force stop: docker stop " container-name))

    :removed
    (println (str "Removed stopped container: " container-name))

    :not-found
    nil))

(defn list-project-containers
  "List all containers (running or stopped) for a project directory.
   Returns vector of maps with :name, :status, :created keys.
   Returns empty vector if no containers found or on error."
  [project-dir]
  (try
    (let [hash (project-hash project-dir)
          filter-pattern (str "name=^aishell-" hash "-")
          {:keys [exit out]} (p/shell {:out :string :err :string :continue true}
                                       "docker" "ps" "-a"
                                       "--filter" filter-pattern
                                       "--format" "{{.Names}}\t{{.Status}}\t{{.CreatedAt}}")]
      (if (and (zero? exit) (not (str/blank? out)))
        (mapv (fn [line]
                (let [[name status created] (str/split line #"\t")]
                  {:name name
                   :status status
                   :created created}))
              (str/split-lines out))
        []))
    (catch Exception _ [])))
