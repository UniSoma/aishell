(ns aishell.run-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [aishell.output :as output]
            [aishell.run :as run]))

(def ^:private report-defaults #'run/report-harness-defaults!)

(defn- captured-err
  [f]
  (let [err (java.io.StringWriter.)]
    (binding [*err* err]
      (f))
    (str err)))

(deftest harness-args-for-a-launchable-harness-are-announced-verbosely
  (testing "defaults that reach the argv are a verbose note, not a warning"
    (let [out (captured-err
               #(binding [output/*verbose* true]
                  (report-defaults "claude" ["--model" "opus"])))]
      (is (str/includes? out "Applying claude defaults: --model opus"))
      (is (not (str/includes? out "Warning")))))
  (testing "without --verbose nothing is printed"
    (is (= "" (captured-err #(report-defaults "claude" ["--model" "opus"])))))
  (testing "no defaults means no output at all"
    (is (= "" (captured-err
               #(binding [output/*verbose* true]
                  (report-defaults "claude" [])))))))

(deftest harness-args-for-a-non-launchable-harness-warn
  (testing "gitleaks cannot receive configured defaults, so they warn"
    (let [out (captured-err #(report-defaults "gitleaks" ["--redact"]))]
      (is (str/includes? out "Warning"))
      (is (str/includes? out "harness_args"))
      (is (str/includes? out "gitleaks"))
      (is (str/includes? out "--redact"))))
  (testing "the warning is not suppressed by non-verbose mode"
    (let [quiet (captured-err #(report-defaults "gitleaks" ["--redact"]))
          verbose (captured-err #(binding [output/*verbose* true]
                                   (report-defaults "gitleaks" ["--redact"])))]
      (is (= quiet verbose))
      (is (seq quiet))))
  (testing "gitleaks with no configured defaults stays silent"
    (is (= "" (captured-err #(report-defaults "gitleaks" []))))))

(deftest shell-mode-defaults-keep-their-verbose-note
  (testing "a non-harness command has no descriptor and is not warned about"
    (let [out (captured-err
               #(binding [output/*verbose* true]
                  (report-defaults "shell" ["-x"])))]
      (is (str/includes? out "Applying shell defaults: -x"))
      (is (not (str/includes? out "Warning"))))))
