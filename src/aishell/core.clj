(ns aishell.core
  (:require [aishell.cli :as cli]
            [aishell.output :as output]
            [aishell.upgrade :as upgrade]
            [aishell.gitleaks.scan-state]
            [aishell.gitleaks.warnings]))

(defn -main [& args]
  ;; A Windows upgrade cannot delete the executable it is running from, so it
  ;; renames it; the next start is the first moment the leftover can go.
  (try
    (upgrade/cleanup-stale-old!)
    (catch Throwable _ nil))
  (try
    (cli/dispatch args)
    (catch clojure.lang.ExceptionInfo e
      (output/error (ex-message e)))
    (catch Exception e
      (output/error (str "Unexpected error: " (.getMessage e))))))
