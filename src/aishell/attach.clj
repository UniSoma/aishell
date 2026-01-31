(ns aishell.attach
  "Attach to running container's tmux session.
   Provides pre-flight validations and terminal takeover via docker exec."
  (:require [babashka.process :as p]
            [clojure.string :as str]
            [aishell.docker.naming :as naming]
            [aishell.output :as output]))

(defn- validate-tty!
  "Ensure command is running in an interactive terminal.
   Exits with error if not (e.g., running in a script, pipe, or CI)."
  []
  (when-not (System/console)
    (output/error "Attach requires an interactive terminal.\nCannot attach from non-interactive contexts (scripts, pipes, CI).")))

(defn- validate-container-state!
  "Validate container exists and is running.
   Exits with error and guidance if container doesn't exist or is stopped."
  [container-name short-name]
  ;; Check container exists
  (when-not (naming/container-exists? container-name)
    (output/error (str "Container '" short-name "' not found.\n\n"
                      "Use 'aishell ps' to list containers.\n"
                      "To start: aishell " short-name " --detach")))
  ;; Check container is running
  (when-not (naming/container-running? container-name)
    (output/error (str "Container '" short-name "' is not running.\n\n"
                      "To start: aishell " short-name " --detach\n"
                      "Or use: docker start " container-name))))

(defn- validate-session-exists!
  "Validate that the requested tmux session exists in the container.
   Exits with error and lists available sessions if not found."
  [container-name session-name short-name]
  (let [{:keys [exit]} (p/shell {:out :string :err :string :continue true}
                                 "docker" "exec" container-name
                                 "tmux" "has-session" "-t" session-name)]
    (when-not (zero? exit)
      ;; Session not found - try to list available sessions
      (let [{:keys [exit out]} (p/shell {:out :string :err :string :continue true}
                                         "docker" "exec" container-name
                                         "tmux" "list-sessions")]
        (if (and (zero? exit) (not (str/blank? out)))
          ;; Sessions exist, show them
          (let [sessions (str/split-lines out)]
            (output/error (str "Session '" session-name "' not found in container '" short-name "'.\n\n"
                              "Available sessions:\n"
                              (str/join "\n" (map #(str "  " %) sessions)))))
          ;; No sessions at all
          (output/error (str "No tmux sessions found in container '" short-name "'.\n\n"
                            "The container may have been started without tmux.\n"
                            "Try restarting: docker stop " container-name " && aishell " short-name " --detach")))))))

(defn attach-to-session
  "Attach to a tmux session in a running container.

   Single-arity form uses default session 'main'.
   Two-arity form allows specifying a custom session name.

   Performs pre-flight validations:
   1. Interactive terminal check
   2. Container exists and is running
   3. Tmux session exists

   On success, uses p/exec to replace current process with docker exec,
   giving tmux full terminal control."
  ([name]
   (attach-to-session name "main"))
  ([name session]
   (let [project-dir (System/getProperty "user.dir")
         container-name (naming/container-name project-dir name)]
     ;; Run all validations
     (validate-tty!)
     (validate-container-state! container-name name)
     (validate-session-exists! container-name session name)

     ;; All checks passed - exec into tmux (replaces current process)
     (p/exec "docker" "exec" "-it" container-name
             "tmux" "attach-session" "-t" session))))
