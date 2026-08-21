(ns aishell.output-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.set :as set]
            [aishell.output :as output]))

(deftest error-json-payload-shape
  (testing "returns nested {:error {:message :code}} map"
    (is (= {:error {:message "boom" :code "internal_error"}}
           (output/error-json-payload "boom" "internal_error")))))

(deftest error-json-payload-preserves-message
  (testing "message is passed through verbatim, including special chars"
    (is (= {:error {:message "no setup found: run 'aishell setup'" :code "no_setup"}}
           (output/error-json-payload "no setup found: run 'aishell setup'" "no_setup")))))

(deftest emit-json-compact-with-trailing-newline-empty-array
  (testing "empty vector emits []\\n on *out*"
    (is (= "[]\n" (with-out-str (output/emit-json []))))))

(deftest emit-json-compact-with-trailing-newline-map
  (testing "map emits compact JSON (no whitespace) with trailing newline"
    (is (= "{\"name\":\"aishell\",\"version\":\"3.17.0\"}\n"
           (with-out-str (output/emit-json {:name "aishell" :version "3.17.0"}))))))

(deftest emit-json-array-of-maps
  (testing "vector of maps emits compact JSON array with no whitespace"
    (is (= "[{\"name\":\"claude\",\"status\":\"Up\"}]\n"
           (with-out-str (output/emit-json [{:name "claude" :status "Up"}]))))))

(defn- capture-emit-error-json [msg code]
  (let [exit-calls (atom [])
        err (java.io.StringWriter.)]
    (with-redefs [output/exit! (fn [c] (swap! exit-calls conj c))]
      (binding [*err* err]
        (output/emit-error-json msg code)))
    {:stderr (str err) :exit-calls @exit-calls}))

(deftest emit-error-json-writes-to-stderr-and-exits-1
  (testing "compact error envelope on *err*, then exit 1"
    (let [{:keys [stderr exit-calls]}
          (capture-emit-error-json "boom" "internal_error")]
      (is (= "{\"error\":{\"message\":\"boom\",\"code\":\"internal_error\"}}\n" stderr))
      (is (= [1] exit-calls)))))

(deftest emit-error-json-unsupported-json-code
  (testing "unsupported_json code is preserved in the envelope"
    (let [{:keys [stderr]}
          (capture-emit-error-json "--json is not supported for this command"
                                   "unsupported_json")]
      (is (= (str "{\"error\":"
                  "{\"message\":\"--json is not supported for this command\","
                  "\"code\":\"unsupported_json\"}}\n")
             stderr)))))

;; --- typo suggestions -------------------------------------------------------

(deftest suggestion-vocabulary-covers-every-subcommand
  (testing "every harness subcommand, including pi, can be suggested"
    (is (= #{"claude" "opencode" "codex" "gemini" "pi" "gitleaks"}
           (set/intersection output/known-commands
                             #{"claude" "opencode" "codex" "gemini" "pi" "gitleaks"}))))
  (testing "the attach alias 'a' is part of the vocabulary"
    (is (contains? output/known-commands "a")))
  (testing "the vocabulary is exactly aishell's command surface"
    (is (= #{"setup" "update" "check" "exec" "ps" "volumes" "attach" "a"
             "vscode" "upgrade" "info"
             "claude" "opencode" "codex" "gemini" "pi" "gitleaks"}
           output/known-commands))))

(deftest suggests-the-nearest-command
  (testing "a typo near a harness suggests that harness"
    (is (= "pi" (output/suggest-command "p1")))
    (is (= "claude" (output/suggest-command "clade")))
    (is (= "gitleaks" (output/suggest-command "gitleak"))))
  (testing "a typo near a plain subcommand suggests it"
    (is (= "attach" (output/suggest-command "attch")))
    (is (= "volumes" (output/suggest-command "volume")))
    (is (= "setup" (output/suggest-command "setpu")))))

(deftest suggestions-are-case-insensitive
  (testing "input is lower-cased before matching"
    (is (= "codex" (output/suggest-command "CODEX")))))

(deftest ties-break-alphabetically
  (testing "'p1' is one edit from both pi and ps; the alphabetically first wins"
    (is (= "pi" (output/suggest-command "p1"))))
  (testing "the winner does not depend on set iteration order"
    (is (= (output/suggest-command "p1") (output/suggest-command "p1")))))

(deftest short-commands-only-match-close-input
  (testing "the one-letter attach alias is suggested only for near-identical input"
    (is (= "a" (output/suggest-command "a")))
    (is (not= "a" (output/suggest-command "xyz"))))
  (testing "distant garbage gets no suggestion"
    (is (nil? (output/suggest-command "completelyunrelated")))))
