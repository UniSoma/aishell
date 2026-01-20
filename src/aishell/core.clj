(ns aishell.core
  (:require [aishell.output :as output]))

(def version "2.0.0")

(defn print-version []
  (println (str "aishell " version)))

(defn print-version-json []
  (println (str "{\"name\":\"aishell\",\"version\":\"" version "\"}")))

(defn -main [& args]
  (try
    ;; Require cli at runtime to avoid circular dependency
    (require '[aishell.cli :as cli])
    ((resolve 'aishell.cli/dispatch) args)
    (catch clojure.lang.ExceptionInfo e
      (output/error (ex-message e)))
    (catch Exception e
      (output/error (str "Unexpected error: " (.getMessage e))))))

;; Entry point for script execution
(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
