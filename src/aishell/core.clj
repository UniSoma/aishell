(ns aishell.core
  (:require [aishell.cli :as cli]
            [aishell.output :as output]))

(defn -main [& args]
  (try
    (cli/dispatch args)
    (catch clojure.lang.ExceptionInfo e
      (output/error (ex-message e)))
    (catch Exception e
      (output/error (str "Unexpected error: " (.getMessage e))))))
