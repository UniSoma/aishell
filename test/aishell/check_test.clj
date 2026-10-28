(ns aishell.check-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [babashka.fs :as fs]
            [aishell.util :as util]
            [aishell.docker.naming :as naming]
            [aishell.check :as check]
            [aishell.cli :as cli]
            [aishell.config :as config]
            [aishell.state :as state]
            [aishell.docker.build :as build]
            [aishell.docker.extension :as ext]
            [aishell.docker :as docker]
            [aishell.docker.base :as base]
            [aishell.docker.hash :as hash]
            [aishell.docker.templates :as templates]))

(def ^:private check-isolation #'check/check-claude-isolation)
(def ^:private check-harnesses #'check/check-harnesses)

(defn- with-temp-env
  "Run f with util/get-home and util/state-dir redefined to fresh temp dirs.
   Calls f with a map of {:home :state :project-dir}, returning the captured
   stdout string."
  [f]
  (let [home (str (fs/create-temp-dir))
        state (str (fs/create-temp-dir))
        project-dir (str (fs/create-temp-dir))]
    (with-redefs [util/get-home (fn [] home)
                  util/state-dir (fn [] state)]
      (with-out-str
        (f {:home home :state state :project-dir project-dir})))))

(deftest reports-shared-mode
  (let [out (with-temp-env
              (fn [{:keys [project-dir]}]
                (check-isolation project-dir {} {:with-claude true})))]
    (testing "shared is the default and is reported"
      (is (str/includes? out "Claude isolation: shared"))
      (is (not (str/includes? out "Claude isolation: project"))))))

(deftest reports-project-mode
  (let [out (with-temp-env
              (fn [{:keys [project-dir]}]
                (check-isolation project-dir
                                 {:claude_isolation "project"}
                                 {:with-claude true})))]
    (testing "project mode reported with state dir and no-creds branch"
      (is (str/includes? out "Claude isolation: project"))
      (is (str/includes? out "Claude project state dir"))
      (is (str/includes? out "Claude credentials: none yet")))))

(deftest project-mode-reports-existing-state-dir
  (let [path (atom nil)
        out (with-temp-env
              (fn [{:keys [project-dir]}]
                (let [dot-claude (naming/claude-dot-claude-dir project-dir)]
                  (reset! path dot-claude)
                  (fs/create-dirs dot-claude))
                (check-isolation project-dir
                                 {:claude_isolation "project"}
                                 {:with-claude true})))]
    (testing "existing state dir reported by path, as present"
      (is (str/includes? out @path))
      (is (str/includes? out "Claude project state dir:"))
      (is (not (str/includes? out "created on first start"))))))

(deftest project-mode-host-mounted-credentials
  (let [out (with-temp-env
              (fn [{:keys [home project-dir]}]
                (let [host-claude (fs/path home ".claude")]
                  (fs/create-dirs host-claude)
                  (spit (str (fs/path host-claude ".credentials.json")) "{}"))
                (check-isolation project-dir
                                 {:claude_isolation "project"}
                                 {:with-claude true})))]
    (testing "host credentials reported as host-mounted"
      (is (str/includes? out "Claude credentials: host-mounted")))))

(deftest project-mode-project-local-credentials
  (let [out (with-temp-env
              (fn [{:keys [project-dir]}]
                (let [dot-claude (naming/claude-dot-claude-dir project-dir)]
                  (fs/create-dirs dot-claude)
                  (spit (str (fs/path dot-claude ".credentials.json")) "{}"))
                (check-isolation project-dir
                                 {:claude_isolation "project"}
                                 {:with-claude true})))]
    (testing "project-local credentials reported and promotion noted"
      (is (str/includes? out "Claude credentials: project-local"))
      (is (str/includes? out "promoted on next start")))))

(deftest no-output-when-claude-not-installed
  (let [out (with-temp-env
              (fn [{:keys [project-dir]}]
                (check-isolation project-dir
                                 {:claude_isolation "project"}
                                 {:with-claude false})))]
    (testing "gated on :with-claude — nothing printed when Claude absent"
      (is (str/blank? out)))))

(deftest harness-report-uses-canonical-labels
  (let [out (with-out-str (check-harnesses {:with-claude true
                                            :with-codex true :codex-version "1.2.3"
                                            :with-copilot true :copilot-version "0.0.339"
                                            :with-omp true :omp-version "18.12.1"
                                            :with-gitleaks true}))]
    (testing "every harness is reported, by its canonical label"
      (is (str/includes? out "Claude Code installed"))
      (is (str/includes? out "Codex CLI installed (1.2.3)"))
      (is (str/includes? out "GitHub Copilot CLI installed (0.0.339)"))
      (is (str/includes? out "Pi coding agent not installed"))
      (is (str/includes? out "oh-my-pi (omp) installed (18.12.1)"))
      (is (str/includes? out "Gemini CLI not installed"))
      (is (str/includes? out "OpenCode not installed"))
      (is (str/includes? out "Gitleaks installed")))
    (testing "the version-less harness reports no version"
      (is (not (str/includes? out "Gitleaks installed ("))))))

(deftest staleness-check-passes-a-foundation-built-from-current-content
  (let [out (with-out-str
              (#'check/check-foundation-staleness
               {:dockerfile-hash (hash/compute-hash templates/foundation-content)}))]
    (is (str/includes? out "Foundation image matches its embedded Dockerfile"))))

(deftest staleness-check-prefers-foundation-hash-over-dockerfile-hash
  (let [out (with-out-str
              (#'check/check-foundation-staleness
               {:foundation-hash (hash/compute-hash templates/foundation-content)
                :dockerfile-hash "old"}))]
    (is (str/includes? out "Foundation image matches its embedded Dockerfile"))))

(deftest base-image-check-looks-up-the-base-image-tag
  (let [checked (atom nil)]
    (with-redefs [base/base-image-tag "aishell:base-under-test"
                  docker/image-exists? (fn [tag] (reset! checked tag) true)]
      (with-out-str (#'check/check-base-image)))
    (is (= "aishell:base-under-test" @checked))))

(deftest base-dockerfile-check-says-the-base-image-is-built-from-it
  (with-redefs [base/base-dockerfile-exists? (constantly true)
                base/needs-base-rebuild? (constantly false)]
    (let [out (with-out-str (#'check/check-base-dockerfile))]
      (is (not (str/includes? out "custom")))
      (is (str/includes? out "Base image: built from ~/.aishell/Dockerfile")))))

(defn- state-as-setup-writes-it
  "Run cli/handle-setup with Docker stubbed out and return the state map it
   writes."
  []
  (let [written (atom nil)]
    (with-redefs [state/warn-removed-harnesses! (fn [])
                  state/read-state (constantly nil)
                  state/write-state (fn [s] (reset! written s))
                  docker/image-exists? (constantly true)
                  build/build-foundation-image (constantly {:success true :image build/foundation-image-tag})
                  base/ensure-base-image (constantly {:success true :image base/base-image-tag})
                  config/load-config (constantly {})]
      (with-out-str (cli/handle-setup {:opts {}})))
    @written))

(deftest image-checks-on-setup-state-check-the-base-image
  (let [setup-state (state-as-setup-writes-it)
        rebuild-base-tag (atom nil)
        out (with-redefs [docker/image-exists? (constantly true)
                          base/base-dockerfile-exists? (constantly true)
                          base/needs-base-rebuild? (constantly false)
                          ext/project-extension-path (constantly "/project/.aishell/Dockerfile")
                          ext/compute-extended-tag (constantly "aishell:ext-under-test")
                          ext/needs-extended-rebuild?
                          (fn [_extended-tag base-tag _project-dir]
                            (reset! rebuild-base-tag base-tag)
                            (not= base-tag base/base-image-tag))]
              (with-out-str (#'check/check-images setup-state "/project")))]
    (testing "the Base image is reported by the Base image tag"
      (is (str/includes? out (str "Base image exists: " base/base-image-tag)))
      (is (not (str/includes? out build/foundation-image-tag))))
    (testing "the Extended image is compared against the Base image"
      (is (= base/base-image-tag @rebuild-base-tag))
      (is (str/includes? out "Project extension is up to date: aishell:ext-under-test")))))

(deftest image-checks-ignore-the-image-tag-older-setups-wrote
  (let [checked (atom [])]
    (with-redefs [docker/image-exists? (fn [tag] (swap! checked conj tag) true)
                  base/base-dockerfile-exists? (constantly false)
                  ext/project-extension-path (constantly nil)]
      (with-out-str (#'check/check-images {:image-tag build/foundation-image-tag}
                                          "/project")))
    (is (= [base/base-image-tag] @checked))))
