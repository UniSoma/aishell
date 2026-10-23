(ns aishell.docker.build-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [aishell.docker :as docker]
            [aishell.docker.build :as build])
  (:import [java.time Instant]))

(def ^:private now (Instant/parse "2026-10-22T12:00:00Z"))

(defn- days-before [n]
  (str (.minusSeconds now (* n 24 60 60))))

(deftest build-args-carry-the-build-date
  (let [args (#'build/build-docker-args {} "abc123" now)]
    (is (some #{(str build/build-date-label "=2026-10-22T12:00:00Z")} args))))

(deftest foundation-out-of-date-warning-by-build-date
  (testing "a foundation built today gets no warning"
    (is (nil? (#'build/foundation-out-of-date-warning (str now) now))))
  (testing "a foundation built exactly 30 days ago gets no warning"
    (is (nil? (#'build/foundation-out-of-date-warning (days-before 30) now))))
  (testing "a foundation older than 30 days gets a warning naming the fix"
    (let [msg (#'build/foundation-out-of-date-warning (days-before 31) now)]
      (is (str/includes? msg "31 days"))
      (is (str/includes? msg "aishell update --force"))))
  (testing "a foundation without the label counts as out of date"
    (let [msg (#'build/foundation-out-of-date-warning nil now)]
      (is (str/includes? msg "aishell update --force"))))
  (testing "an unparseable label counts as out of date"
    (is (str/includes? (#'build/foundation-out-of-date-warning "yesterday" now)
                       "aishell update --force"))))

(defn- warning-for-label [label]
  (with-redefs [docker/get-image-label
                (fn [tag key]
                  (is (= "aishell:base" tag))
                  (is (= build/build-date-label key))
                  label)]
    (let [err (java.io.StringWriter.)]
      (binding [*err* err]
        (build/warn-if-foundation-out-of-date! "aishell:base"))
      (str err))))

(deftest warn-if-foundation-out-of-date!-reads-the-image-label
  (testing "a foundation built just now prints nothing"
    (is (= "" (warning-for-label (str (Instant/now))))))
  (testing "a foundation built in 2020 prints the warning"
    (is (str/includes? (warning-for-label "2020-01-01T00:00:00Z")
                       "aishell update --force")))
  (testing "an image without the label prints the warning"
    (is (str/includes? (warning-for-label nil) "no build date"))))

(deftest build-date-by-build-kind
  (testing "a forced build bypasses the layer cache, so it is stamped now"
    (is (= (str now) (build/foundation-build-date {:force? true :image-exists? true
                                                   :previous "2026-01-01T00:00:00Z"
                                                   :now now}))))
  (testing "a cached rebuild keeps the previous stamp, since its apt layer may be reused"
    (is (= "2026-01-01T00:00:00Z"
           (build/foundation-build-date {:force? false :image-exists? true
                                         :previous "2026-01-01T00:00:00Z"
                                         :now now}))))
  (testing "a cached rebuild of an image built before the label stays unstamped"
    (is (nil? (build/foundation-build-date {:force? false :image-exists? true
                                            :previous nil :now now}))))
  (testing "a first build is stamped now"
    (is (= (str now) (build/foundation-build-date {:force? false :image-exists? false
                                                   :previous nil :now now})))))

(deftest build-args-without-a-build-date-omit-the-label
  (let [args (#'build/build-docker-args {} "abc123" nil)]
    (is (not-any? #(str/starts-with? % build/build-date-label) args))))

(deftest a-cached-foundation-build-reports-a-content-match
  (with-redefs [docker/check-docker! (fn [])
                build/needs-rebuild? (fn [_ _] false)]
    (let [out (with-out-str (build/build-foundation-image {}))]
      (is (str/includes? out "Image aishell:foundation matches its embedded Dockerfile (use --force to rebuild)"))
      (is (not (str/includes? out "up to date"))))))
