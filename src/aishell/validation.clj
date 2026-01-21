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

(def dangerous-mount-paths
  "Potentially dangerous mount paths that expose sensitive host data.
   These are advisory warnings - users may have legitimate reasons."
  [{:pattern #"^/$"
    :message "/: Root filesystem mount gives full host access"}
   {:pattern #"/etc/passwd"
    :message "/etc/passwd: System user database"}
   {:pattern #"/etc/shadow"
    :message "/etc/shadow: System password hashes"}
   {:pattern #"docker\.sock"
    :message "docker.sock: Container can control Docker daemon"}
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

(defn check-dangerous-args
  "Check docker_args for dangerous patterns.
   Accepts string or vector of args.
   Returns seq of warning messages, or nil if none found."
  [docker-args]
  (let [args-str (if (sequential? docker-args)
                   (str/join " " docker-args)
                   docker-args)]
    (when (and args-str (not (str/blank? args-str)))
      (seq
        (keep
          (fn [{:keys [pattern message]}]
            (when (if (string? pattern)
                    (str/includes? args-str pattern)
                    (re-find pattern args-str))
              message))
          dangerous-patterns)))))

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
