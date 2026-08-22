(ns aishell.info-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [aishell.docker.templates :as templates]
            [aishell.info :as info]))

(def ^:private parse-packages #'info/parse-packages)
(def ^:private format-harnesses #'info/format-harnesses)
(def ^:private harness-labels @#'info/harness-labels)
(def ^:private parse-sqlite-version #'info/parse-sqlite-version)
(def ^:private parse-distro #'info/parse-distro)
(def ^:private parse-node-tag #'info/parse-node-tag)

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
    (testing "libreadline8t64 is present for the source-built sqlite3 shell"
      (is (some #{"libreadline8t64"} packages)))
    (testing "the JRE bbin needs is the one trixie ships"
      (is (some #{"openjdk-21-jre-headless"} packages)))
    (testing "libsqlite3-0 is never removed — the shadow relies on ldconfig order"
      (is (not (str/includes? templates/base-dockerfile "apt-get remove"))))))

(deftest sqlite-version-is-parsed-from-the-pinned-arg
  (is (re-matches #"\d+\.\d+\.\d+"
                  (parse-sqlite-version templates/base-dockerfile))))

(deftest distro-comes-from-the-final-from-line
  (testing "the reported distro is the main stage's, not a builder stage's"
    (is (= "debian:trixie-slim" (parse-distro templates/base-dockerfile))))
  (testing "a builder stage never wins, even when it comes first"
    (is (= "debian:other-slim"
           (parse-distro (str "FROM debian:trixie-slim AS sqlite-source\n"
                              "FROM debian:other-slim\n")))))
  (testing "the last unaliased FROM wins, not the first"
    (is (= "debian:final-slim"
           (parse-distro (str "FROM debian:early-slim\n"
                              "FROM debian:final-slim\n"))))))

(deftest node-tag-is-parsed-without-anchoring-on-a-suite
  (let [[tag major] (parse-node-tag templates/base-dockerfile)]
    (is (= "24" major))
    (is (= "24-trixie-slim" tag)))
  (testing "a different suite still parses"
    (is (= ["26-forky-slim" "26"]
           (parse-node-tag "FROM node:26-forky-slim AS node-source\n")))))

(deftest the-distro-image-is-trixie-everywhere
  (testing "no bookworm remains in the template"
    (is (not (str/includes? templates/base-dockerfile "bookworm"))))
  (testing "the build asserts the glibc floor the policy promises"
    (is (str/includes? templates/base-dockerfile "GNU_LIBC_VERSION"))
    ;; The floor number is asserted inside the comparison, not just mentioned
    ;; in the comment above it: the two drifting apart is the failure this
    ;; guards.
    (is (str/includes? templates/base-dockerfile
                       "dpkg --compare-versions \"${glibcVersion}\" ge 2.39"))))

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
