(ns aishell.output-test
  (:require [clojure.test :refer [deftest is testing]]
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
