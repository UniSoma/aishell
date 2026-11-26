(ns aishell.vscode-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [aishell.vscode :as vscode]
            [aishell.docker :as docker]
            [aishell.docker.naming :as naming]
            [aishell.output :as output]
            [aishell.state :as state]
            [aishell.validation :as validation]))

(defn- container
  [short-name status]
  {:name (str "aishell-a1b2c3d4-" short-name)
   :status status
   :created "2026-08-17 10:00:00 +0000 UTC"})

(deftest bare-vscode-joins-the-only-running-sandbox
  (is (= {:join "shell"}
         (vscode/plan-target nil [(container "shell" "Up 3 minutes")
                                  (container "old" "Exited (0) 1 hour ago")]))))

(deftest bare-vscode-starts-its-own-sandbox-when-none-runs
  (is (= {:start true}
         (vscode/plan-target nil [(container "old" "Exited (0) 1 hour ago")])))
  (is (= {:start true} (vscode/plan-target nil []))))

(deftest bare-vscode-with-several-running-asks-for-a-name
  (let [{:keys [error]} (vscode/plan-target nil [(container "claude" "Up 3 minutes")
                                                 (container "shell" "Up 20 minutes")])]
    (is (str/includes? error "Multiple running containers"))
    (is (str/includes? error "aishell vscode claude"))))

(deftest named-vscode-joins-that-sandbox
  (is (= {:join "claude"}
         (vscode/plan-target "claude" [(container "claude" "Up 3 minutes")
                                       (container "shell" "Up 20 minutes")]))))

(deftest named-vscode-refuses-a-sandbox-that-is-not-running
  (testing "stopped"
    (is (str/includes? (:error (vscode/plan-target "old" [(container "old" "Exited (0) 1 hour ago")]))
                       "'old' is not running")))
  (testing "absent"
    (is (str/includes? (:error (vscode/plan-target "nope" [(container "shell" "Up 3 minutes")]))
                       "'nope' is not running"))))

(defn- open-with
  "Run open-vscode against `setup-state` and a project whose containers are
   `containers`, with Docker and the code CLI stubbed. Returns the calls it
   made, or [:error message] when it exits."
  [setup-state containers opts]
  (let [calls (atom [])
        log! (fn [& xs] (swap! calls conj (vec xs)))]
    (with-redefs [validation/check-project-dir! (fn [_])
                  vscode/check-vscode! (fn [])
                  docker/check-docker! (fn [])
                  state/read-state (constantly setup-state)
                  naming/list-project-containers (constantly containers)
                  naming/container-image (fn [c] (str "image-of-" c))
                  vscode/ensure-imageconfig! (fn [img] (log! :imageconfig img))
                  vscode/launch-vscode! (fn [c _ wait? args] (log! :launch c wait? args))
                  vscode/stop-container! (fn [c] (log! :stop c))
                  output/error (fn [msg] (throw (ex-info "exit" {:msg msg})))]
      (try
        (with-out-str (vscode/open-vscode opts))
        @calls
        (catch clojure.lang.ExceptionInfo e
          [:error (:msg (ex-data e))])))))

(deftest vscode-refuses-without-the-setup-option
  (testing "the setup hint comes before the code CLI check"
    (let [[kind msg] (with-redefs [vscode/check-vscode! #(throw (ex-info "exit" {:msg "code not found"}))]
                       (open-with {:with-claude true} [(container "shell" "Up 1 minute")] {}))]
      (is (= :error kind))
      (is (str/includes? msg "aishell setup --reuse-config --with-vscode")))))

(deftest parse-args-reads-name-flags-and-code-args
  (is (= {:help? false :stop? false :detach? false :name "shell" :code-args ["--disable-gpu" "--json"]}
         (vscode/parse-args ["shell" "--disable-gpu" "--" "--json"])))
  (is (= {:help? false :stop? false :detach? true :code-args ["--profile" "Work"]}
         (vscode/parse-args ["--detach" "--profile" "Work"]))))

(deftest joining-opens-the-window-and-never-stops-the-sandbox
  (let [calls (open-with {:with-vscode true} [(container "shell" "Up 1 minute")]
                         {:code-args ["--disable-gpu"]})
        sandbox (naming/container-name (System/getProperty "user.dir") "shell")]
    (is (= [[:imageconfig (str "image-of-" sandbox)]
            [:launch sandbox false ["--disable-gpu"]]]
           calls))))
