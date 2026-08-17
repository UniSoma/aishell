(ns aishell.attach.parse
  "Pure argv parser for `aishell attach`. Splits CLI args around `--` into
   a container name and an optional command argv.")

(defn parse-attach-args
  "Parse the args that follow `aishell attach`.

   Returns either {:name <string|nil> :command-argv <vector|nil>} on
   success, or {:error <string>} on a parse failure. No I/O, no printing.

   The parse is purely syntactic: a missing name is not an error here, it
   yields a nil name for the caller to resolve against the running
   containers. Only malformed argv — a trailing bare `--`, or two names
   before `--` — is rejected."
  [args]
  (let [args (vec args)
        sep-idx (.indexOf args "--")]
    (cond
      (neg? sep-idx)
      {:name (first args) :command-argv nil}

      (= (inc sep-idx) (count args))
      {:error "attach: '--' given but no command followed"}

      (> sep-idx 1)
      {:error "attach: only one container name allowed before '--'"}

      :else
      {:name (first (subvec args 0 sep-idx))
       :command-argv (vec (subvec args (inc sep-idx)))})))
