(ns aishell.info-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [aishell.docker.templates :as templates]
            [aishell.info :as info]))

(def ^:private parse-packages #'info/parse-packages)
(def ^:private parse-sqlite-version #'info/parse-sqlite-version)

(deftest package-list-comes-from-the-main-stage-apt-block
  (let [packages (parse-packages templates/base-dockerfile)]
    (testing "the main-stage packages are found"
      (is (some #{"git"} packages))
      (is (some #{"ripgrep"} packages)))
    (testing "builder-stage build deps do not leak into the list"
      ;; Every builder stage must keep its apt-get install on a single
      ;; line: parse-packages anchors on a trailing backslash, so a
      ;; continued install block earlier in the file would win.
      (is (not (some #{"gcc" "make" "libc6-dev" "libreadline-dev"} packages))))
    (testing "sqlite3 is built from source, not installed from apt"
      (is (not (some #{"sqlite3"} packages))))
    (testing "libreadline8 is present for the source-built sqlite3 shell"
      (is (some #{"libreadline8"} packages)))
    (testing "libsqlite3-0 is never removed — the shadow relies on ldconfig order"
      (is (not (str/includes? templates/base-dockerfile "apt-get remove"))))))

(deftest sqlite-version-is-parsed-from-the-pinned-arg
  (is (re-matches #"\d+\.\d+\.\d+"
                  (parse-sqlite-version templates/base-dockerfile))))
