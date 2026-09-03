(ns aishell.docker.templates-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [aishell.docker.templates :as templates]))

;; WORKAROUND(bbin-gensym-tmp): drop this whole test once upstream bbin no
;; longer writes the shim's temp deps.edn to (gensym "bbin"). See
;; aix-01m1ms2ngp23.
(deftest bbin-shim-temp-file-is-per-user
  (testing "the bbin install layer rewrites the gensym form to a per-user name"
    ;; The form sits inside a Clojure string literal in bbin, so its quotes
    ;; are backslash-escaped there and the sed has to match them escaped.
    (is (str/includes? templates/base-dockerfile
                       "sed -i 's|(gensym \\\\\"bbin\\\\\")|(gensym (str \\\\\"bbin-\\\\\" (System/getProperty \\\\\"user.name\\\\\") \\\\\"-\\\\\"))|g' /usr/local/bin/bbin")))
  (testing "the same layer fails the build unless exactly five forms were patched"
    (is (str/includes? templates/base-dockerfile
                       "test \"$(grep -o 'gensym (str \\\\\"bbin-\\\\\"' /usr/local/bin/bbin | wc -l)\" -eq 5"))
    (is (str/includes? templates/base-dockerfile "WORKAROUND(bbin-gensym-tmp)")))
  (testing "the entrypoint clears leftover shim temp files beside the sentinels"
    (is (str/includes? templates/entrypoint-script "rm -f /tmp/bbin*"))
    (is (str/includes? templates/entrypoint-script "WORKAROUND(bbin-gensym-tmp)"))))
