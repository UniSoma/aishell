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


(deftest expand-env-refs-substitutes-braced-references
  (testing "a set variable takes its value"
    (is (= {:value "127.0.0.1:9999:7888"}
           (util/expand-env-refs "127.0.0.1:${P}:7888" {"P" "9999"}))))
  (testing "an unset variable with a default takes the default"
    (is (= {:value "127.0.0.1:7888:7888"}
           (util/expand-env-refs "127.0.0.1:${P:-7888}:7888" {}))))
  (testing "an empty variable with a default takes the default"
    (is (= {:value "7888:7888"}
           (util/expand-env-refs "${P:-7888}:7888" {"P" ""}))))
  (testing "a set variable wins over its default"
    (is (= {:value "9999:7888"}
           (util/expand-env-refs "${P:-7888}:7888" {"P" "9999"}))))
  (testing "several references in one string all expand"
    (is (= {:value "10.0.0.1:9999:7888"}
           (util/expand-env-refs "${IP}:${P}:7888" {"IP" "10.0.0.1" "P" "9999"})))))

(deftest expand-env-refs-never-substitutes-an-empty-string
  (testing "an unset variable with no default is an error naming it"
    (is (= {:error {:var "P" :reason :unset}}
           (util/expand-env-refs "${P}:7888" {}))))
  (testing "a set but empty variable with no default is an error"
    (is (= {:error {:var "P" :reason :empty}}
           (util/expand-env-refs "${P}:7888" {"P" ""}))))
  (testing "an empty default used for an unset variable is an error"
    (is (= {:error {:var "P" :reason :empty-default}}
           (util/expand-env-refs "${P:-}:7888" {})))))

(deftest expand-env-refs-leaves-other-forms-as-written
  (doseq [s ["8080:80" "$P:7888" "${}:7888" "${P:7888" "${P-1}:7888"
             "${P:?x}:7888" "${1P}:7888"]]
    (testing s
      (is (= {:value s} (util/expand-env-refs s {"P" "9999" "1P" "9999"}))))))
