(ns aishell.info-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [aishell.docker.templates :as templates]
            [aishell.info :as info]))

(def ^:private parse-packages #'info/parse-packages)
(def ^:private format-harnesses #'info/format-harnesses)
(def ^:private harness-labels @#'info/harness-labels)
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

(deftest harness-lines-use-canonical-labels
  (testing "each enabled harness prints its canonical label and version"
    (is (= ["  Claude Code: 2.0.22" "  Pi coding agent: latest"]
           (format-harnesses {:with-claude true :claude-version "2.0.22"
                              :with-pi true}))))
  (testing "no harnesses reads as None"
    (is (= ["  None"] (format-harnesses {})))))

(deftest config-path-labels-are-canonical
  (testing "the state keys that carry host config map to canonical labels"
    (is (= "Pi coding agent" (get harness-labels :with-pi)))
    (is (= "Codex CLI" (get harness-labels :with-codex)))
    (is (= "Gemini CLI" (get harness-labels :with-gemini)))))
