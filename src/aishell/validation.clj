(ns aishell.validation
  "Security validation for Docker configurations.
   Checks for dangerous patterns and provides advisory warnings."
  (:require [clojure.string :as str]
            [aishell.output :as output]))

;; Dangerous docker_args patterns from v1.2 bash implementation
;; These reduce container isolation and should be used with caution
(def dangerous-patterns
  [{:pattern "--privileged"
    :message "--privileged: Container has full host access"}
   {:pattern "docker.sock"
    :message "docker.sock mount: Container can control Docker daemon"}
   {:pattern #"--cap-add=(SYS_ADMIN|ALL)"
    :message "Elevated capabilities: Increases container escape risk"}
   {:pattern #"(apparmor|seccomp)=unconfined"
    :message "Disabled security profiles: Reduces container isolation"}])

(defn check-dangerous-args
  "Check docker_args string for dangerous patterns.
   Returns seq of warning messages, or nil if none found."
  [docker-args]
  (when (and docker-args (not (str/blank? docker-args)))
    (seq
      (keep
        (fn [{:keys [pattern message]}]
          (when (if (string? pattern)
                  (str/includes? docker-args pattern)
                  (re-find pattern docker-args))
            message))
        dangerous-patterns))))

(defn warn-dangerous-args
  "Warn about dangerous docker_args if any found.
   Advisory only - does not block execution."
  [docker-args]
  (when-let [warnings (check-dangerous-args docker-args)]
    (println)  ; Blank line before warning block
    (output/warn "Security notice: Potentially dangerous Docker options detected")
    (doseq [msg warnings]
      (binding [*out* *err*]
        (println (str "  - " msg))))
    (binding [*out* *err*]
      (println)
      (println "These options reduce container isolation. Use only if necessary.")
      (println))))
