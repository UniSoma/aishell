(ns aishell.docker.bootstrap
  "Pre-start bootstrap readiness probe for project containers.

   Surfaces the outcome of the `pre_start` command (run detached by the
   entrypoint) as a per-row `:bootstrap` field on `aishell ps` output.

   The `:none` value is intentionally overloaded: it covers both
   \"pre_start was never configured for this container\" and
   \"container is not running, so the sentinels can't be probed.\" Both
   collapse to the same value because the user-facing answer is the
   same — there is no readiness signal to report."
  (:require [babashka.process :as p]
            [clojure.string :as str]
            [aishell.output :as output]))

(defn running?
  "Docker `--format {{.Status}}` returns strings like `Up 3 minutes`,
   `Exited (0) 2 hours ago`, `Created`, `Restarting (1) …`. Only the
   `Up …` form is a running container; everything else is a state for
   which `docker exec` would either fail or be meaningless."
  [status]
  (boolean (and status (re-find #"^Up" status))))

(defn merge-bootstrap
  "Pure: attach `:bootstrap` to each container.
   Running rows take their bootstrap from the state map; non-running
   rows short-circuit to `:none` regardless of the map."
  [containers name->state]
  (mapv (fn [c]
          (assoc c :bootstrap
                 (if (running? (:status c))
                   (get name->state (:name c) :none)
                   :none)))
        containers))

(defn derive-state
  "Pure: map the entrypoint's sentinel state to a bootstrap keyword.

   `:failed` wins over `:ready` when both sentinels are present. The
   entrypoint cleans both at start, so the only path to a both-present
   state is a brief write-ordering race inside the pre_start subshell;
   surfacing failure is the safer resolution."
  [{:keys [pre-start-configured? done? failed?]}]
  (cond
    (not pre-start-configured?) :none
    failed?                     :failed
    done?                       :ready
    :else                       :pending))

(defn- inspect-env
  "Read container env vars via `docker inspect`. Returns map name->value."
  [container-name]
  (let [{:keys [exit out]}
        (p/shell {:out :string :err :string :continue true}
                 "docker" "inspect"
                 "--format" "{{range .Config.Env}}{{println .}}{{end}}"
                 container-name)]
    (when (zero? exit)
      (into {}
            (keep (fn [line]
                    (let [[k v] (str/split line #"=" 2)]
                      (when (seq k) [k (or v "")]))))
            (str/split-lines out)))))

(defn- read-sentinels
  "Probe sentinels in container via `docker exec`. Returns
   {:done? bool :failed? bool}; nil on failure."
  [container-name]
  (let [script "if [ -f /tmp/pre-start.done ]; then echo done; fi\nif [ -f /tmp/pre-start.failed ]; then echo failed; fi\ntrue"
        {:keys [exit out]}
        (p/shell {:out :string :err :string :continue true}
                 "docker" "exec" container-name "sh" "-c" script)]
    (when (zero? exit)
      (let [tokens (set (map str/trim (str/split-lines out)))]
        {:done?   (contains? tokens "done")
         :failed? (contains? tokens "failed")}))))

(defn- probe-one
  "Probe bootstrap state for one running container.

   Inspect env first to short-circuit when PRE_START is unset (skip the
   exec). Otherwise read the sentinels and feed `derive-state`. Any
   inspect/exec error degrades to `:pending` with a warning (silenced in
   JSON mode); the next `aishell ps` self-corrects."
  [container-name]
  (try
    (let [env (inspect-env container-name)
          pre-start (some-> env (get "PRE_START") not-empty)]
      (if-not pre-start
        :none
        (if-let [sentinels (read-sentinels container-name)]
          (derive-state (assoc sentinels :pre-start-configured? true))
          (do (output/warn (str "bootstrap probe failed for " container-name))
              :pending))))
    (catch Exception e
      (output/warn (str "bootstrap probe error for " container-name
                        ": " (.getMessage e)))
      :pending)))

(defn probe-running!
  "Impure: probe bootstrap state for every running container in parallel.
   Returns a name->state-keyword map; non-running containers are skipped
   (`merge-bootstrap` handles them)."
  [containers]
  (let [running (filter #(running? (:status %)) containers)]
    (into {} (pmap (fn [c] [(:name c) (probe-one (:name c))]) running))))

(defn attach-bootstrap!
  "Impure composition: probe running containers and attach `:bootstrap`."
  [containers]
  (merge-bootstrap containers (probe-running! containers)))
