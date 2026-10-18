(ns aishell.docker.volume-test
  (:require [clojure.test :refer [deftest is testing]]
            [babashka.fs :as fs]
            [babashka.process :as p]
            [clojure.string :as str]
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

(deftest copilot-hash-is-opt-in
  (testing "disabled Copilot does not alter an existing volume hash"
    (is (= (vol/compute-harness-hash claude-only)
           (vol/compute-harness-hash (assoc claude-only
                                            :with-copilot false
                                            :copilot-version "0.0.339")))))
  (testing "enabled Copilot participates with its normalized version"
    (is (= [[:copilot "0.0.339"]]
           (vol/normalize-harness-config {:with-copilot true
                                          :copilot-version "0.0.339"})))))

(deftest omp-hash-is-opt-in
  (testing "disabled omp does not alter an existing volume hash"
    (is (= (vol/compute-harness-hash claude-only)
           (vol/compute-harness-hash (assoc claude-only
                                            :with-omp false
                                            :omp-version "18.12.1")))))
  (testing "enabled omp participates with its normalized version"
    (is (= [[:omp "latest"]]
           (vol/normalize-harness-config {:with-omp true})))))

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
           (vol/build-install-commands {:with-gemini true}))))
  (testing "Copilot accepts latest and exact versions"
    (doseq [[version suffix] [[nil "latest"]
                              ["1.2.3" "1.2.3"]]]
      (is (= (str "export NPM_CONFIG_PREFIX=/tools/npm"
                  " && npm install -g @github/copilot@" suffix
                  " && chmod -R a+rwX /tools")
             (vol/build-install-commands (cond-> {:with-copilot true}
                                           version (assoc :copilot-version version))))))))

(deftest install-commands-for-opencode
  (testing "unpinned OpenCode installs the latest @opencode/cli"
    (is (= (str "export NPM_CONFIG_PREFIX=/tools/npm"
                " && npm install -g @opencode/cli@latest"
                " && chmod -R a+rwX /tools")
           (vol/build-install-commands {:with-opencode true}))))
  (testing "a pinned OpenCode version pins the npm package"
    (is (= (str "export NPM_CONFIG_PREFIX=/tools/npm"
                " && npm install -g @opencode/cli@2.0.16"
                " && chmod -R a+rwX /tools")
           (vol/build-install-commands {:with-opencode true :opencode-version "2.0.16"})))))

(deftest install-commands-cover-every-volume-participant
  (testing "npm packages keep registry order"
    (is (= (str "export NPM_CONFIG_PREFIX=/tools/npm"
                " && npm install -g @anthropic-ai/claude-code@latest"
                " @opencode/cli@latest @openai/codex@latest"
                " @google/gemini-cli@latest @earendil-works/pi-coding-agent@latest"
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

;; ---------------------------------------------------------------------------
;; GitHub-release installs (ADR 0009)
;; ---------------------------------------------------------------------------

(def ^:private omp-release
  "https://github.com/can1357/oh-my-pi/releases")

(deftest github-release-install-for-a-pinned-version
  (let [cmd (vol/build-install-commands {:with-omp true :omp-version "18.12.1"})]
    (testing "the asset and its checksum file come from the pinned tag"
      (is (str/includes? cmd (str omp-release "/download/v18.12.1/omp-linux-$arch")))
      (is (str/includes? cmd (str omp-release "/download/v18.12.1/SHA256SUMS.txt"))))
    (testing "a pinned version never follows the latest redirect"
      (is (not (str/includes? cmd "/latest/"))))))

(deftest github-release-install-for-latest
  (let [cmd (vol/build-install-commands {:with-omp true})]
    (testing "an unpinned harness follows GitHub's latest-release redirect"
      (is (str/includes? cmd (str omp-release "/latest/download/omp-linux-$arch")))
      (is (str/includes? cmd (str omp-release "/latest/download/SHA256SUMS.txt"))))))

(deftest github-release-install-maps-the-architecture
  (let [cmd (vol/build-install-commands {:with-omp true})]
    (testing "uname -m picks the glibc asset: x86_64 is x64, aarch64 is arm64"
      (is (str/includes? cmd "$(uname -m)"))
      (is (str/includes? cmd "x86_64) arch=x64"))
      (is (str/includes? cmd "aarch64) arch=arm64")))
    (testing "any other architecture fails the build"
      (is (re-find #"\*\) echo \"[^\"]*unsupported architecture[^\"]*\" >&2; exit 1" cmd)))))

(deftest github-release-install-verifies-before-installing
  (let [cmd (vol/build-install-commands {:with-omp true})
        check (str/index-of cmd "sha256sum -c")
        install (str/index-of cmd "/tools/bin/omp")]
    (testing "the asset is checked against the release's checksum file"
      (is (some? check)))
    (testing "a mismatch stops the build with its own message"
      (is (str/includes? cmd "checksum verification failed"))
      (is (str/includes? cmd "exit 1")))
    (testing "the binary lands in /tools/bin, executable, only after the check"
      (is (str/includes? cmd "install -m 0755"))
      (is (< check install)))))

(deftest github-release-installs-follow-npm
  (testing "npm installs first, the release binary next, permissions last"
    (let [cmd (vol/build-install-commands {:with-claude true :with-omp true})
          npm (str/index-of cmd "npm install -g")
          omp (str/index-of cmd "/tools/bin/omp")
          chmod (str/index-of cmd "chmod -R a+rwX /tools")]
      (is (< npm omp chmod))))
  (testing "omp alone installs no npm package"
    (is (not (str/includes? (vol/build-install-commands {:with-omp true})
                            "npm install")))))

(defn- run-omp-install
  "Run the omp install snippet under sh against local fixtures: `curl` copies
   the file named by the URL's last segment out of `assets`, `uname -m`
   answers `machine`, and /tools is a temp dir. Returns the process result
   plus the temp :root."
  [assets machine]
  (let [root (str (fs/create-temp-dir))
        stubs (fs/create-dirs (fs/path root "stubs"))
        fixtures (fs/create-dirs (fs/path root "fixtures"))]
    (doseq [[file content] assets]
      (spit (str (fs/path fixtures file)) content))
    (spit (str (fs/path stubs "curl"))
          (str "#!/bin/sh\n"
               "while [ $# -gt 0 ]; do case \"$1\" in"
               " -o) out=\"$2\"; shift 2 ;; -*) shift ;; *) url=\"$1\"; shift ;; esac; done\n"
               "cp \"" fixtures "/${url##*/}\" \"$out\"\n"))
    (spit (str (fs/path stubs "uname")) (str "#!/bin/sh\necho " machine "\n"))
    (doseq [stub ["curl" "uname"]]
      (fs/set-posix-file-permissions (fs/path stubs stub) "rwxr-xr-x"))
    (let [cmd (str/replace (vol/build-install-commands {:with-omp true})
                           "/tools" (str (fs/path root "tools")))]
      (assoc (p/shell {:out :string :err :string :continue true
                       :extra-env {"PATH" (str stubs ":" (System/getenv "PATH"))}}
                      "sh" "-c" cmd)
             :root root))))

(defn- with-omp-install
  "Call `f` with the result of `run-omp-install`, then delete its temp root."
  [assets machine f]
  (let [{:keys [root] :as result} (run-omp-install assets machine)]
    (try
      (f result)
      (finally (fs/delete-tree root)))))

(defn- sha256-hex [^String s]
  (let [digest (.digest (java.security.MessageDigest/getInstance "SHA-256")
                        (.getBytes s "UTF-8"))]
    (apply str (map #(format "%02x" %) digest))))

(deftest github-release-install-runs-against-a-release
  (let [binary "#!/bin/sh\necho omp\n"
        sums (str (sha256-hex binary) "  omp-linux-x64\n"
                  (sha256-hex "musl") "  omp-linux-musl-x64\n")]
    (testing "a matching download is installed as an executable /tools/bin/omp"
      (with-omp-install {"omp-linux-x64" binary "SHA256SUMS.txt" sums} "x86_64"
        (fn [{:keys [exit root]}]
          (let [installed (fs/path root "tools" "bin" "omp")]
            (is (zero? exit))
            (is (= binary (slurp (str installed))))
            (is (fs/executable? installed))))))
    (testing "a truncated download fails with a checksum error and installs nothing"
      (with-omp-install {"omp-linux-x64" "#!/bin/sh\n" "SHA256SUMS.txt" sums} "x86_64"
        (fn [{:keys [exit err root]}]
          (is (not (zero? exit)))
          (is (str/includes? err "omp: checksum verification failed for omp-linux-x64"))
          (is (not (fs/exists? (fs/path root "tools" "bin" "omp")))))))
    (testing "an asset missing from the checksum file fails the same way"
      (with-omp-install {"omp-linux-arm64" binary "SHA256SUMS.txt" sums} "aarch64"
        (fn [{:keys [exit err]}]
          (is (not (zero? exit)))
          (is (str/includes? err "omp: checksum verification failed for omp-linux-arm64")))))
    (testing "an unsupported architecture fails before downloading"
      (with-omp-install {} "riscv64"
        (fn [{:keys [exit err]}]
          (is (not (zero? exit)))
          (is (str/includes? err "omp: unsupported architecture riscv64")))))))
