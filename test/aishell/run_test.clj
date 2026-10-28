(ns aishell.run-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [aishell.docker.base :as base]
            [aishell.docker.extension :as ext]
            [aishell.docker.hash :as hash]
            [aishell.docker.templates :as templates]
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

(deftest base-image-skips-the-project-extension
  (let [built (atom false)]
    (with-redefs [base/ensure-base-image (fn [& _])
                  ext/project-extension-path (fn [_] "/p/.aishell/Dockerfile")
                  ext/needs-extended-rebuild? (fn [& _] true)
                  ext/build-extended-image (fn [& _] (reset! built true))]
      (testing "with :base-image the Base image is used and nothing is built"
        (let [out (captured-err
                   #(is (= "aishell:base"
                           (run/resolve-image-tag "/p" false {:base-image true}))))]
          (is (false? @built))
          (is (str/includes? out ".aishell/Dockerfile skipped"))))
      (testing "without it the Extended image is still built"
        (run/resolve-image-tag "/p" false)
        (is (true? @built)))))
  (testing "with :base-image and no Project extension it reports none to skip"
    (with-redefs [base/ensure-base-image (fn [& _])
                  ext/project-extension-path (fn [_] nil)]
      (let [out (captured-err
                 #(is (= "aishell:base"
                         (run/resolve-image-tag "/p" false {:base-image true}))))]
        (is (str/includes? out "no Project extension"))))))

(deftest base-image-launch-skips-pre-start
  (is (true? (run/skip-pre-start? {:base-image true})))
  (is (true? (run/skip-pre-start? {:skip-pre-start true})))
  (is (not (run/skip-pre-start? {}))))

(deftest warn-if-foundation-stale-compares-the-stored-hash
  (let [warn-stale #'run/warn-if-foundation-stale
        current (hash/compute-hash templates/foundation-content)]
    (testing "a stored hash that differs from the embedded content warns"
      (is (str/includes? (captured-err #(warn-stale {:dockerfile-hash "old"}))
                         "Image may be stale")))
    (testing "a matching hash prints nothing"
      (is (= "" (captured-err #(warn-stale {:dockerfile-hash current})))))
    (testing "state without a stored hash prints nothing"
      (is (= "" (captured-err #(warn-stale {})))))))
