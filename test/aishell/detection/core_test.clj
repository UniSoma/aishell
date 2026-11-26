(ns aishell.detection.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [babashka.fs :as fs]
            [aishell.detection.core :as detection]))

(def ^:private env-names [".env" ".env.local" ".env.dev" ".env.prod" ".env.test"])

(defn- project-with
  "Create a temp project dir holding an empty file for each name."
  [names]
  (let [dir (str (fs/create-temp-dir))]
    (doseq [n names]
      (spit (str (fs/path dir n)) ""))
    dir))

(defn- allowlist [names]
  (mapv (fn [n] {:path n :reason "test"}) names))

(deftest allowlist-applies-before-grouping
  (testing "a fully allowlisted type leaves no summary finding"
    (let [names (take 4 env-names)
          dir (project-with names)]
      (is (empty? (detection/scan-project dir {:allowlist (allowlist names)})))))

  (testing "the summary counts only files that are not allowlisted"
    (let [dir (project-with env-names)
          [finding & more] (detection/scan-project dir {:allowlist (allowlist [".env"])})]
      (is (nil? more))
      (is (:summary? finding))
      (is (= "4 files detected" (:reason finding)))
      (is (not-any? #(= ".env" (str (fs/file-name %))) (:sample-paths finding)))))

  (testing "allowlisting below the threshold yields individual findings"
    (let [dir (project-with env-names)
          findings (detection/scan-project dir {:allowlist (allowlist [".env" ".env.local"])})]
      (is (= 3 (count findings)))
      (is (every? :path findings))
      (is (not-any? :summary? findings)))))
