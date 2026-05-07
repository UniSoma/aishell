(ns aishell.attach.parse
  "Pure argv parser for `aishell attach`. Splits CLI args around `--` into
   a container name and an optional command argv.")

(defn parse-attach-args
  "Parse the args that follow `aishell attach`.

   Returns either {:name <string> :command-argv <vector|nil>} on success,
   or {:error <string>} on a parse failure. No I/O, no printing."
  [args]
  (let [args (vec args)
        sep-idx (.indexOf args "--")
        no-name-error {:error (str "Container name required.\n\n"
                                   "Usage: aishell attach <name>\n\n"
                                   "Use 'aishell ps' to list running containers.")}]
    (cond
      (zero? (count args))
      no-name-error

      (zero? sep-idx)
      no-name-error

      (neg? sep-idx)
      {:name (first args) :command-argv nil}

      (= (inc sep-idx) (count args))
      {:error "attach: '--' given but no command followed"}

      (> sep-idx 1)
      {:error "attach: only one container name allowed before '--'"}

      :else
      {:name (first args)
       :command-argv (vec (subvec args (inc sep-idx)))})))
