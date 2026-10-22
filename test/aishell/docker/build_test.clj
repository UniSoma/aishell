(ns aishell.docker.build-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [aishell.docker.build :as build])
  (:import [java.time Instant]))

(def ^:private now (Instant/parse "2026-10-22T12:00:00Z"))

(defn- days-before [n]
  (str (.minusSeconds now (* n 24 60 60))))

(deftest build-args-carry-the-build-timestamp
  (let [args (#'build/build-docker-args {} "abc123" now)]
    (is (some #{(str build/build-timestamp-label "=2026-10-22T12:00:00Z")} args))))

(deftest foundation-age-warning
  (testing "a foundation built today gets no warning"
    (is (nil? (build/foundation-stale-warning (str now) now))))
  (testing "a foundation built exactly 30 days ago gets no warning"
    (is (nil? (build/foundation-stale-warning (days-before 30) now))))
  (testing "a foundation older than 30 days gets a warning naming the fix"
    (let [msg (build/foundation-stale-warning (days-before 31) now)]
      (is (str/includes? msg "31 days"))
      (is (str/includes? msg "aishell update --force"))))
  (testing "a foundation without the label counts as stale"
    (let [msg (build/foundation-stale-warning nil now)]
      (is (str/includes? msg "aishell update --force"))))
  (testing "an unparseable label counts as stale"
    (is (str/includes? (build/foundation-stale-warning "yesterday" now)
                       "aishell update --force"))))

(deftest build-timestamp-by-build-kind
  (testing "a forced build bypasses the layer cache, so it is stamped now"
    (is (= (str now) (build/build-timestamp {:force? true :image-exists? true
                                             :previous "2026-01-01T00:00:00Z"
                                             :now now}))))
  (testing "a cached rebuild keeps the previous stamp, since its apt layer may be reused"
    (is (= "2026-01-01T00:00:00Z"
           (build/build-timestamp {:force? false :image-exists? true
                                   :previous "2026-01-01T00:00:00Z"
                                   :now now}))))
  (testing "a cached rebuild of an image built before the label stays unstamped"
    (is (nil? (build/build-timestamp {:force? false :image-exists? true
                                      :previous nil :now now}))))
  (testing "a first build is stamped now"
    (is (= (str now) (build/build-timestamp {:force? false :image-exists? false
                                             :previous nil :now now})))))

(deftest build-args-without-a-timestamp-omit-the-label
  (let [args (#'build/build-docker-args {} "abc123" nil)]
    (is (not-any? #(str/starts-with? % build/build-timestamp-label) args))))
