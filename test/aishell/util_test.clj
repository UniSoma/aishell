(ns aishell.util-test
  (:require [clojure.test :refer [deftest is testing]]
            [babashka.fs :as fs]
            [aishell.util :as util]))

(defn- with-temp-dir [f]
  (let [dir (str (fs/create-temp-dir {:prefix "aishell-util-test"}))]
    (try
      (f dir)
      (finally
        (fs/delete-tree dir)))))

(deftest resolve-project-config-dir-defaults-to-aishell
  (testing "neither dir present → .aishell"
    (with-temp-dir
      (fn [dir]
        (is (= ".aishell" (util/resolve-project-config-dir dir)))))))

(deftest resolve-project-config-dir-honors-aishell
  (testing "only .aishell present → .aishell"
    (with-temp-dir
      (fn [dir]
        (fs/create-dirs (fs/path dir ".aishell"))
        (is (= ".aishell" (util/resolve-project-config-dir dir)))))))

(deftest resolve-project-config-dir-honors-sandbox
  (testing "only .sandbox present → .sandbox"
    (with-temp-dir
      (fn [dir]
        (fs/create-dirs (fs/path dir ".sandbox"))
        (is (= ".sandbox" (util/resolve-project-config-dir dir)))))))

(deftest resolve-project-config-dir-rejects-both
  (testing "both present → ex-info with use-only-one message"
    (with-temp-dir
      (fn [dir]
        (fs/create-dirs (fs/path dir ".aishell"))
        (fs/create-dirs (fs/path dir ".sandbox"))
        (is (thrown-with-msg? clojure.lang.ExceptionInfo
                              #"both .aishell/ and .sandbox/"
                              (util/resolve-project-config-dir dir)))))))

(deftest project-config-path-uses-active-dir
  (testing "config path points at the active dir"
    (with-temp-dir
      (fn [dir]
        (fs/create-dirs (fs/path dir ".sandbox"))
        (is (= (str (fs/path dir ".sandbox" "config.yaml"))
               (util/project-config-path dir)))))))
