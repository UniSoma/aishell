(ns aishell.docker.extension-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [aishell.docker.extension :as ext]))

(deftest build-failure-points-to-base-image
  (testing "the error names the flag and the Dockerfile to fix"
    (let [msg (ext/build-failure-message ".aishell" "step 3 failed")]
      (is (str/includes? msg "Extension build failed"))
      (is (str/includes? msg "step 3 failed"))
      (is (str/includes? msg "aishell --base-image"))
      (is (str/includes? msg ".aishell/Dockerfile"))))
  (testing "verbose builds have no captured output to repeat"
    (is (not (str/includes? (ext/build-failure-message ".sandbox" nil) "nil")))))
