(ns aishell.attach
  "Attach to running container via docker exec bash.
   Provides pre-flight validations and terminal takeover via docker exec."
  (:require [babashka.process :as p]
            [babashka.fs :as fs]
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
                      "To start: aishell " short-name)))
  ;; Check container is running
  (when-not (naming/container-running? container-name)
    (output/error (str "Container '" short-name "' is not running.\n\n"
                      "To start: aishell " short-name "\n"
                      "Or use: docker start " container-name))))

(defn attach-to-container
  "Attach to a running container by opening a bash shell.

   Performs pre-flight validations:
   1. Interactive terminal check
   2. Container exists and is running

   On success, transfers terminal control to container bash:
   - Unix: uses p/exec (replaces process, cleaner process tree)
   - Windows: uses p/process :inherit (child process with I/O inheritance)"
  [name]
  (let [project-dir (System/getProperty "user.dir")
        container-name (naming/container-name project-dir name)]
    ;; Run all validations
    (validate-tty!)
    (validate-container-state! container-name name)

    ;; Resolve TERM valid inside the container (host TERM may lack terminfo)
    (let [term (resolve-term container-name)
          colorterm (or (System/getenv "COLORTERM") "truecolor")]
      ;; All checks passed - exec into bash (transfer terminal control)
      ;; Use --login so /etc/profile sources /etc/profile.d/aishell.sh
      ;; which sets PATH (tools), prompt, aliases — matching normal startup
      (if (fs/windows?)
        ;; Windows: spawn child process with inherited I/O, wait, propagate exit
        (let [result @(p/process {:inherit true}
                                 "docker" "exec" "-it" "-u" "developer"
                                 "-e" (str "TERM=" term)
                                 "-e" (str "COLORTERM=" colorterm)
                                 "-e" "LANG=C.UTF-8"
                                 "-e" "LC_ALL=C.UTF-8"
                                 container-name
                                 "/bin/bash" "--login")]
          (System/exit (:exit result)))
        ;; Unix: replace process (cleaner process tree)
        (p/exec "docker" "exec" "-it" "-u" "developer"
                "-e" (str "TERM=" term)
                "-e" (str "COLORTERM=" colorterm)
                "-e" "LANG=C.UTF-8"
                "-e" "LC_ALL=C.UTF-8"
                container-name
                "/bin/bash" "--login")))))
