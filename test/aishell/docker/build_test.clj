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
