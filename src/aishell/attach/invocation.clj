(ns aishell.attach.invocation
  "Pure builder for the `docker exec` argv used by `aishell attach`.")

(defn build-docker-exec-argv
  "Build the full argv vector to pass to docker exec.

   Inputs:
     :container     full container name (string)
     :term          TERM value to forward (string)
     :colorterm     COLORTERM value to forward (string)
     :command-argv  optional vector of strings to run before the post-shell

   When :command-argv is nil or empty, the trailing form is the same plain
   `bash --login` invocation used today. Otherwise, bash is launched as a
   wrapper that runs the command (argv intact via \"$@\"), then execs a
   fresh login shell."
  [{:keys [container term colorterm command-argv]}]
  (let [prefix ["docker" "exec" "-it" "-u" "developer"
                "-e" (str "TERM=" term)
                "-e" (str "COLORTERM=" colorterm)
                "-e" "LANG=C.UTF-8"
                "-e" "LC_ALL=C.UTF-8"
                container
                "/bin/bash" "--login"]]
    (if (seq command-argv)
      (into prefix
            (concat ["-c" "\"$@\"; exec /bin/bash --login" "_"]
                    command-argv))
      prefix)))
