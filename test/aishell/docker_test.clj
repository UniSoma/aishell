(ns aishell.docker-test
  (:require [clojure.test :refer [deftest is testing]]
            [babashka.process :as p]
            [aishell.docker :as docker]))

(deftest image-id-reads-the-docker-image-id
  (testing "a present image returns its trimmed ID"
    (with-redefs [p/shell (fn [_opts & args]
                            (is (= ["docker" "inspect" "--format={{.Id}}" "aishell:base"] args))
                            {:exit 0 :out "sha256:abc\n"})]
      (is (= "sha256:abc" (docker/image-id "aishell:base")))))
  (testing "a missing image returns nil"
    (with-redefs [p/shell (fn [& _] {:exit 1 :out ""})]
      (is (nil? (docker/image-id "aishell:base")))))
  (testing "a failing docker call returns nil"
    (with-redefs [p/shell (fn [& _] (throw (ex-info "no docker" {})))]
      (is (nil? (docker/image-id "aishell:base"))))))
