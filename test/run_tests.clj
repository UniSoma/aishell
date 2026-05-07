(ns run-tests
  "Discover *_test.clj namespaces under test/ and run them.

   Returns the clojure.test summary map. The bb task (see bb.edn) is
   responsible for translating that into a process exit code."
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [clojure.test :as t]))

(defn- file->ns [path]
  (-> path
      (str/replace #"\.clj$" "")
      (str/replace #"^test/" "")
      (str/replace "/" ".")
      (str/replace "_" "-")
      symbol))

(defn run-all []
  (let [files (->> (fs/glob "test" "**/*_test.clj")
                   (map str)
                   sort)
        nses (mapv file->ns files)]
    (if (empty? nses)
      (do (println "run-tests: no *_test.clj files found under test/")
          {:test 0 :pass 0 :fail 0 :error 0})
      (do (doseq [n nses] (require n))
          (apply t/run-tests nses)))))
