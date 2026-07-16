(ns aishell.check-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [babashka.fs :as fs]
            [aishell.util :as util]
            [aishell.docker.naming :as naming]
            [aishell.check :as check]))

(def ^:private check-isolation #'check/check-claude-isolation)

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
