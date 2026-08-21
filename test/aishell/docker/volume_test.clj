(ns aishell.docker.volume-test
  (:require [clojure.test :refer [deftest is testing]]
            [aishell.docker.hash :as hash]
            [aishell.docker.volume :as vol]))

(def ^:private claude-only
  {:with-claude true :claude-version "2.0.22"})

(deftest harness-hash-ignores-removed-openspec
  (testing "a stale OpenSpec flag no longer contributes to the hash"
    (is (= (vol/compute-harness-hash claude-only)
           (vol/compute-harness-hash (assoc claude-only
                                            :with-openspec true
                                            :openspec-version "1.2.3"))))))

(deftest harness-hash-is-stable-for-unaffected-users
  (testing "hashes for OpenSpec-free configs are unchanged by the removal"
    (is (= "65b8e9d41105" (vol/compute-harness-hash claude-only)))))

(deftest install-commands-omit-removed-openspec
  (testing "a stale OpenSpec flag installs no npm package"
    (is (not (re-find #"openspec"
                      (vol/build-install-commands
                       (assoc claude-only :with-openspec true)))))))

;; ---------------------------------------------------------------------------
;; Hash equivalence
;;
;; The functions below are a frozen copy of the pre-registry hash derivation.
;; They are the independent oracle: every user's harness volume is keyed by
;; this value, so the registry-derived hash must reproduce it byte for byte.
;; Do not "simplify" them to call the production code.
;; ---------------------------------------------------------------------------

(def ^:private legacy-harness-keys
  [:claude :codex :gemini :opencode :pi])

(defn- legacy-normalize
  [state]
  (->> legacy-harness-keys
       (filter #(get state (keyword (str "with-" (name %)))))
       (map (fn [harness-kw]
              (let [version-key (keyword (str (name harness-kw) "-version"))
                    version (get state version-key)]
                [harness-kw (or version "latest")])))
       (sort-by first)
       vec))

(defn- legacy-hash
  [state]
  (hash/compute-hash (pr-str (legacy-normalize state))))

(def ^:private version-shapes
  {:unpinned {}
   :all-pinned {:claude-version "2.0.22"
                :opencode-version "0.4.5"
                :codex-version "0.89.0"
                :gemini-version "1.2.3"
                :pi-version "9.9.9"}
   :mixed {:claude-version "2.0.22"
           :gemini-version "1.2.3"}})

(def ^:private enabled-combinations
  "All 2^5 enabled/disabled combinations of the five volume-participating
   harnesses."
  (let [harnesses [:claude :opencode :codex :gemini :pi]]
    (for [bits (range 32)]
      (into {}
            (map-indexed (fn [i h]
                           [(keyword (str "with-" (name h))) (bit-test bits i)]))
            harnesses))))

(deftest harness-hash-matches-the-pre-registry-derivation
  (testing "every enabled combination and version shape hashes identically"
    (doseq [flags enabled-combinations
            [shape-name versions] version-shapes
            :let [state (merge flags versions)]]
      (is (= (legacy-hash state) (vol/compute-harness-hash state))
          (str "hash drift for " shape-name " " (pr-str flags)))))
  (testing "the canonical hash input itself is unchanged"
    (doseq [flags enabled-combinations
            [_ versions] version-shapes
            :let [state (merge flags versions)]]
      (is (= (legacy-normalize state) (vol/normalize-harness-config state))))))

(deftest harness-hash-input-shape-is-pinned
  (testing "pairs are vectors of [keyword version-string], sorted by keyword"
    (is (= [[:claude "2.0.22"] [:codex "latest"]]
           (vol/normalize-harness-config {:with-codex true
                                          :with-claude true
                                          :claude-version "2.0.22"})))
    (is (= "[[:claude \"2.0.22\"] [:codex \"latest\"]]"
           (pr-str (vol/normalize-harness-config {:with-codex true
                                                  :with-claude true
                                                  :claude-version "2.0.22"})))))
  (testing "no enabled harness serializes as an empty vector"
    (is (= "[]" (pr-str (vol/normalize-harness-config {}))))))

(deftest harness-hash-ignores-non-participants
  (testing "gitleaks, unisoma and stale keys never enter the hash"
    (doseq [flags enabled-combinations]
      (is (= (vol/compute-harness-hash flags)
             (vol/compute-harness-hash (assoc flags
                                              :with-gitleaks true
                                              :unisoma true
                                              :with-openspec true
                                              :openspec-version "1.2.3")))))))

;; ---------------------------------------------------------------------------
;; Install commands
;; ---------------------------------------------------------------------------

(def ^:private opencode-latest-url
  "https://github.com/anomalyco/opencode/releases/latest/download/opencode-linux-x64.tar.gz")

(deftest install-commands-for-npm-harnesses
  (testing "a single pinned npm harness"
    (is (= (str "export NPM_CONFIG_PREFIX=/tools/npm"
                " && npm install -g @anthropic-ai/claude-code@2.0.22"
                " && chmod -R a+rwX /tools")
           (vol/build-install-commands claude-only))))
  (testing "an unpinned npm harness installs @latest"
    (is (= (str "export NPM_CONFIG_PREFIX=/tools/npm"
                " && npm install -g @google/gemini-cli@latest"
                " && chmod -R a+rwX /tools")
           (vol/build-install-commands {:with-gemini true})))))

(deftest install-commands-for-the-opencode-tarball
  (testing "unpinned OpenCode downloads the latest release asset"
    (is (= (str "export NPM_CONFIG_PREFIX=/tools/npm"
                " && mkdir -p /tools/bin && curl -fsSL " opencode-latest-url
                " | tar -xz -C /tools/bin"
                " && chmod -R a+rwX /tools")
           (vol/build-install-commands {:with-opencode true}))))
  (testing "a pinned OpenCode version uses the versioned release URL"
    (is (= (str "export NPM_CONFIG_PREFIX=/tools/npm"
                " && mkdir -p /tools/bin && curl -fsSL "
                "https://github.com/anomalyco/opencode/releases/download/v0.4.5/opencode-linux-x64.tar.gz"
                " | tar -xz -C /tools/bin"
                " && chmod -R a+rwX /tools")
           (vol/build-install-commands {:with-opencode true :opencode-version "0.4.5"})))))

(deftest install-commands-cover-every-volume-participant
  (testing "npm packages keep registry order, the tarball follows them"
    (is (= (str "export NPM_CONFIG_PREFIX=/tools/npm"
                " && npm install -g @anthropic-ai/claude-code@latest"
                " @openai/codex@latest @google/gemini-cli@latest"
                " @earendil-works/pi-coding-agent@latest"
                " && mkdir -p /tools/bin && curl -fsSL " opencode-latest-url
                " | tar -xz -C /tools/bin"
                " && chmod -R a+rwX /tools")
           (vol/build-install-commands {:with-claude true :with-opencode true
                                        :with-codex true :with-gemini true
                                        :with-pi true}))))
  (testing "gitleaks is baked into the image, never installed into the volume"
    (is (= "export NPM_CONFIG_PREFIX=/tools/npm && chmod -R a+rwX /tools"
           (vol/build-install-commands {:with-gitleaks true}))))
  (testing "no harness enabled still yields a well-formed command"
    (is (= "export NPM_CONFIG_PREFIX=/tools/npm && chmod -R a+rwX /tools"
           (vol/build-install-commands {})))))
