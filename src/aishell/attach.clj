(ns aishell.attach
  "Attach to running container's tmux session.
   Provides pre-flight validations and terminal takeover via docker exec."
  (:require [babashka.process :as p]
            [clojure.string :as str]
            [aishell.docker.naming :as naming]
            [aishell.output :as output]))

(defn- resolve-term
  "Resolve a TERM value valid inside the container.
   Checks if the host TERM has a terminfo entry in the container via infocmp.
   Falls back to xterm-256color if unsupported (e.g., xterm-ghostty)."
  [container-name]
  (let [host-term (or (System/getenv "TERM") "xterm-256color")
        {:keys [exit]} (p/shell {:out :string :err :string :continue true}
                                "docker" "exec" "-u" "developer" container-name
                                "infocmp" host-term)]
    (if (zero? exit)
      host-term
      "xterm-256color")))

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
                                 "docker" "exec" "-u" "developer" container-name
                                 "tmux" "has-session" "-t" session-name)]
    (when-not (zero? exit)
      ;; Session not found - try to list available sessions
      (let [{:keys [exit out]} (p/shell {:out :string :err :string :continue true}
                                         "docker" "exec" "-u" "developer" container-name
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

     ;; Resolve TERM valid inside the container (host TERM may lack terminfo)
     (let [term (resolve-term container-name)
           colorterm (or (System/getenv "COLORTERM") "truecolor")]
       ;; All checks passed - exec into tmux (replaces current process)
       (p/exec "docker" "exec" "-it" "-u" "developer"
               "-e" (str "TERM=" term)
               "-e" (str "COLORTERM=" colorterm)
               "-e" "LANG=C.UTF-8"
               "-e" "LC_ALL=C.UTF-8"
               container-name
               "tmux" "attach-session" "-t" session)))))

(defn- ensure-bashrc!
  "Ensure /etc/bash.aishell is sourced from ~/.bashrc inside the container.
   The entrypoint only injects this when the container starts with /bin/bash,
   so containers started with a harness command (claude, opencode, etc.) won't
   have it. This mirrors the entrypoint's bashrc injection logic."
  [container-name]
  (p/shell {:out :string :err :string :continue true}
           "docker" "exec" "-u" "developer" container-name
           "bash" "-c"
           "grep -q 'source /etc/bash.aishell' \"$HOME/.bashrc\" 2>/dev/null || echo 'source /etc/bash.aishell' >> \"$HOME/.bashrc\""))

(defn attach-shell
  "Attach to or create a bash shell session in a running container.

   Creates a tmux session named 'shell' running /bin/bash if it doesn't exist,
   or attaches to it if it does (using tmux new-session -A).

   Performs pre-flight validations:
   1. Interactive terminal check
   2. Container exists and is running

   On success, uses p/exec to replace current process with docker exec,
   giving tmux full terminal control."
  [name]
  (let [project-dir (System/getProperty "user.dir")
        container-name (naming/container-name project-dir name)]
    ;; Run validations (no session check - tmux new-session -A handles both create and attach)
    (validate-tty!)
    (validate-container-state! container-name name)

    ;; Ensure bashrc sources /etc/bash.aishell (entrypoint skips this for non-bash commands)
    (ensure-bashrc! container-name)

    ;; Resolve TERM valid inside the container (host TERM may lack terminfo)
    (let [term (resolve-term container-name)
          colorterm (or (System/getenv "COLORTERM") "truecolor")]
      ;; All checks passed - exec into tmux with new-session -A (creates or attaches)
      (p/exec "docker" "exec" "-it" "-u" "developer"
              "-e" (str "TERM=" term)
              "-e" (str "COLORTERM=" colorterm)
              "-e" "LANG=C.UTF-8"
              "-e" "LC_ALL=C.UTF-8"
              container-name
              "tmux" "new-session" "-A" "-s" "shell" "/bin/bash"))))
