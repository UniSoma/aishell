(ns aishell.docker.base-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [babashka.process :as p]
            [aishell.docker.base :as base]
            [aishell.docker.build :as build]))

(deftest tag-foundation-as-base-tags-the-foundation-image
  (let [calls (atom [])]
    (with-redefs [p/shell (fn [_opts & args] (swap! calls conj args) {:exit 0})]
      (base/tag-foundation-as-base))
    (is (= [["docker" "tag" build/foundation-image-tag base/base-image-tag]] @calls))))

(deftest build-base-image-names-the-base-image
  (testing "a verbose build names the Base image by its glossary term, not \"global\""
    (with-redefs [p/shell (fn [& _] {:exit 0 :out "sha256:abc"})]
      (let [out (with-out-str (base/build-base-image {:verbose true}))]
        (is (str/includes? out "Base image built"))
        (is (not (str/includes? (str/lower-case out) "global")))))))
