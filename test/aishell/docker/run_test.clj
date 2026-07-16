(ns aishell.docker.run-test
  (:require [clojure.test :refer [deftest is testing]]
            [babashka.fs :as fs]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [aishell.util :as util]
            [aishell.docker.naming :as naming]
            [aishell.docker.run :as run]))

(def ^:private build-mounts #'run/build-harness-config-mounts)

(defn- seed-home
  "Create a temp HOME with a .claude dir containing the given relative entries
   (dirs and files), plus a .claude.json file. Returns the home path."
  [entries]
  (let [home (str (fs/create-temp-dir))
        claude (fs/path home ".claude")]
    (fs/create-dirs claude)
    (spit (str (fs/path home ".claude.json")) "{}\n")
    (doseq [{:keys [rel dir?]} entries]
      (let [p (apply fs/path claude rel)]
        (if dir?
          (fs/create-dirs p)
          (do (fs/create-dirs (fs/parent p))
              (spit (str p) "")))))
    home))

(defn- mount-strings
  "Extract the src:dst strings from a flat [-v s -v s ...] arg vector."
  [args]
  (->> args (partition 2) (map second)))

(deftest shared-mode-byte-identical
  (let [home (seed-home [])
        tmp-state (str (fs/create-temp-dir))]
    (try
      (with-redefs [util/get-home (fn [] home)
                    util/state-dir (fn [] tmp-state)]
        (let [state {:with-claude true}]
          (testing "shared mode (default cfg) mounts host ~/.claude and ~/.claude.json wholesale"
            (let [result (vec (build-mounts state home {}))]
              (is (= ["-v" (str (fs/path home ".claude") ":" (fs/path home ".claude"))
                      "-v" (str (fs/path home ".claude.json") ":" (fs/path home ".claude.json"))]
                     result))))
          (testing "explicit shared cfg matches default"
            (is (= (vec (build-mounts state home {}))
                   (vec (build-mounts state home {:claude_isolation "shared"})))))))
      (finally
        (fs/delete-tree home)
        (fs/delete-tree tmp-state)))))

(deftest project-mode-inverts-claude-mount
  (let [home (seed-home [{:rel ["skills"] :dir? true}
                         {:rel ["projects"] :dir? true}
                         {:rel ["settings.json"] :dir? false}
                         {:rel ["history.jsonl"] :dir? false}])
        tmp-state (str (fs/create-temp-dir))]
    (try
      (with-redefs [util/get-home (fn [] home)
                    util/state-dir (fn [] tmp-state)]
        (let [state {:with-claude true}
              cfg {:claude_isolation "project"}
              result (vec (build-mounts state home cfg))
              strs (mount-strings result)
              dot-claude (naming/claude-dot-claude-dir home)
              claude (str (fs/path home ".claude"))]
          (testing "base mount maps per-project dot-claude to container ~/.claude"
            (is (some #{(str dot-claude ":" claude)} strs)))
          (testing "no wholesale host ~/.claude mount"
            (is (not (some #{(str claude ":" claude)} strs))))
          (testing "~/.claude.json mount is unchanged"
            (is (some #{(str (fs/path home ".claude.json") ":" (fs/path home ".claude.json"))} strs)))
          (testing "overlays present only for allowlist sources that exist on host"
            (is (some #{(str (fs/path claude "skills") ":" (fs/path claude "skills"))} strs))
            (is (some #{(str (fs/path claude "projects") ":" (fs/path claude "projects"))} strs))
            (is (some #{(str (fs/path claude "settings.json") ":" (fs/path claude "settings.json"))} strs))
            (is (some #{(str (fs/path claude "history.jsonl") ":" (fs/path claude "history.jsonl"))} strs))
            (testing "missing allowlist dirs are pre-created on host and mounted (bootstrapping)"
              (is (some #{(str (fs/path claude "agents") ":" (fs/path claude "agents"))} strs)))
            (testing "credentials never seeded, skipped when absent on host"
              (is (not (some #{(str (fs/path claude ".credentials.json") ":" (fs/path claude ".credentials.json"))} strs)))))
          (testing "base mount precedes its overlay children"
            (let [idx (fn [s] (.indexOf (vec strs) s))]
              (is (< (idx (str dot-claude ":" claude))
                     (idx (str (fs/path claude "skills") ":" (fs/path claude "skills")))))))))
      (finally
        (fs/delete-tree home)
        (fs/delete-tree tmp-state)))))

(deftest project-mode-mounts-user-shared-paths
  ;; A user-listed root-level script (present on host) must mount like a
  ;; built-in, so it is callable from settings hooks inside the sandbox.
  (let [home (seed-home [{:rel ["run-hook.sh"] :dir? false}
                         {:rel ["team-skills"] :dir? true}])
        tmp-state (str (fs/create-temp-dir))]
    (try
      (with-redefs [util/get-home (fn [] home)
                    util/state-dir (fn [] tmp-state)]
        (let [state {:with-claude true}
              cfg {:claude_isolation "project"
                   :claude_shared_paths ["run-hook.sh" "team-skills"]}
              claude (str (fs/path home ".claude"))
              strs (mount-strings (vec (build-mounts state home cfg)))]
          (testing "user file entry mounted as a single-file overlay"
            (is (some #{(str (fs/path claude "run-hook.sh") ":" (fs/path claude "run-hook.sh"))} strs)))
          (testing "user dir entry mounted as a dir overlay"
            (is (some #{(str (fs/path claude "team-skills") ":" (fs/path claude "team-skills"))} strs)))))
      (finally
        (fs/delete-tree home)
        (fs/delete-tree tmp-state)))))

(deftest project-mode-precreates-missing-user-shared-sources
  ;; Missing user sources are pre-created per bootstrapping conventions:
  ;; a file entry becomes a file, a dir entry becomes a directory.
  (let [home (seed-home [])
        tmp-state (str (fs/create-temp-dir))]
    (try
      (with-redefs [util/get-home (fn [] home)
                    util/state-dir (fn [] tmp-state)]
        (let [state {:with-claude true}
              cfg {:claude_isolation "project"
                   :claude_shared_paths ["hooks-dir" "startup.sh"]}
              claude (fs/path home ".claude")
              strs (mount-strings (vec (build-mounts state home cfg)))]
          (testing "missing dir entry pre-created as a directory"
            (is (fs/directory? (fs/path claude "hooks-dir"))))
          (testing "missing file entry pre-created as a file, not a directory"
            (is (fs/regular-file? (fs/path claude "startup.sh"))))
          (testing "both mounted"
            (is (some #{(str (fs/path claude "hooks-dir") ":" (fs/path claude "hooks-dir"))} strs))
            (is (some #{(str (fs/path claude "startup.sh") ":" (fs/path claude "startup.sh"))} strs)))))
      (finally
        (fs/delete-tree home)
        (fs/delete-tree tmp-state)))))

(deftest project-mode-user-entry-covered-by-builtin-warns-and-no-dupes
  (let [home (seed-home [{:rel ["skills"] :dir? true}])
        tmp-state (str (fs/create-temp-dir))]
    (try
      (with-redefs [util/get-home (fn [] home)
                    util/state-dir (fn [] tmp-state)]
        (let [state {:with-claude true}
              cfg {:claude_isolation "project"
                   :claude_shared_paths ["skills"]}
              claude (fs/path home ".claude")
              sw (java.io.StringWriter.)
              strs (binding [*err* sw]
                     (mount-strings (vec (build-mounts state home cfg))))
              skills-mount (str (fs/path claude "skills") ":" (fs/path claude "skills"))]
          (testing "warning emitted for a built-in-covered entry"
            (is (str/includes? (str sw) "already shared by default")))
          (testing "no duplicate mount for the built-in entry"
            (is (= 1 (count (filter #{skills-mount} strs)))))))
      (finally
        (fs/delete-tree home)
        (fs/delete-tree tmp-state)))))

(deftest project-mode-first-run-creates-state-and-meta
  (let [home (seed-home [{:rel ["skills"] :dir? true}])
        tmp-state (str (fs/create-temp-dir))]
    (try
      (with-redefs [util/get-home (fn [] home)
                    util/state-dir (fn [] tmp-state)]
        (let [state {:with-claude true}
              cfg {:claude_isolation "project"}]
          (build-mounts state home cfg)
          (let [dot-claude (naming/claude-dot-claude-dir home)
                meta-file (naming/claude-meta-file home)]
            (testing "dot-claude dir created"
              (is (fs/exists? dot-claude)))
            (testing "meta.edn written with canonicalized project path and created-at"
              (is (fs/exists? meta-file))
              (let [m (edn/read-string (slurp meta-file))]
                (is (= (str (fs/canonicalize home)) (:project-path m)))
                (is (string? (:created-at m)))))
            (testing "second run does not rewrite meta.edn"
              (let [before (slurp meta-file)]
                (build-mounts state home cfg)
                (is (= before (slurp meta-file))))))))
      (finally
        (fs/delete-tree home)
        (fs/delete-tree tmp-state)))))

(deftest project-mode-distinct-projects-independent-state
  (let [home (seed-home [{:rel ["skills"] :dir? true}])
        tmp-state (str (fs/create-temp-dir))
        proj-a (str (fs/create-temp-dir))
        proj-b (str (fs/create-temp-dir))]
    (try
      (with-redefs [util/get-home (fn [] home)
                    util/state-dir (fn [] tmp-state)]
        (let [state {:with-claude true}
              cfg {:claude_isolation "project"}
              base (fn [proj]
                     (str (naming/claude-dot-claude-dir proj) ":" (fs/path home ".claude")))]
          (build-mounts state proj-a cfg)
          (build-mounts state proj-b cfg)
          (testing "each project gets its own dot-claude base mount"
            (is (not= (base proj-a) (base proj-b)))
            (is (some #{(base proj-a)} (mount-strings (vec (build-mounts state proj-a cfg)))))
            (is (some #{(base proj-b)} (mount-strings (vec (build-mounts state proj-b cfg))))))))
      (finally
        (fs/delete-tree home)
        (fs/delete-tree tmp-state)
        (fs/delete-tree proj-a)
        (fs/delete-tree proj-b)))))

(def ^:private allowlist-dirs
  ["skills" "agents" "commands" "hooks" "plugins" "projects"])

(deftest project-mode-bootstraps-missing-sources
  ;; Fresh HOME: only .claude/ + .claude.json exist, no allowlist entries.
  (let [home (seed-home [])
        tmp-state (str (fs/create-temp-dir))]
    (try
      (with-redefs [util/get-home (fn [] home)
                    util/state-dir (fn [] tmp-state)]
        (let [state {:with-claude true}
              cfg {:claude_isolation "project"}
              claude (fs/path home ".claude")]
          (build-mounts state home cfg)
          (testing "missing allowlist dirs pre-created on host"
            (doseq [d allowlist-dirs]
              (is (fs/directory? (fs/path claude d)) (str d " should be a directory"))))
          (testing "settings.json seeded with an empty JSON object"
            (let [f (str (fs/path claude "settings.json"))]
              (is (fs/regular-file? f))
              (is (str/includes? (slurp f) "{}"))))
          (testing "CLAUDE.md seeded empty"
            (let [f (str (fs/path claude "CLAUDE.md"))]
              (is (fs/regular-file? f))
              (is (= "" (slurp f)))))
          (testing ".credentials.json never seeded and never manufactured as a dir"
            (is (not (fs/exists? (fs/path claude ".credentials.json")))))
          (testing "history.jsonl (:never) not seeded when absent"
            (is (not (fs/exists? (fs/path claude "history.jsonl")))))))
      (finally
        (fs/delete-tree home)
        (fs/delete-tree tmp-state)))))

(deftest project-mode-present-sources-mounted-and-preserved
  ;; Fresh HOME with every allowlist source already present.
  (let [home (seed-home [{:rel ["skills"] :dir? true}
                         {:rel ["agents"] :dir? true}
                         {:rel ["commands"] :dir? true}
                         {:rel ["hooks"] :dir? true}
                         {:rel ["plugins"] :dir? true}
                         {:rel ["projects"] :dir? true}
                         {:rel ["settings.json"] :dir? false}
                         {:rel ["CLAUDE.md"] :dir? false}
                         {:rel ["history.jsonl"] :dir? false}
                         {:rel [".credentials.json"] :dir? false}])
        tmp-state (str (fs/create-temp-dir))]
    (try
      (with-redefs [util/get-home (fn [] home)
                    util/state-dir (fn [] tmp-state)]
        (let [state {:with-claude true}
              cfg {:claude_isolation "project"}
              claude (fs/path home ".claude")]
          ;; sentinel content proves seeding does not overwrite existing files
          (spit (str (fs/path claude "settings.json")) "{\"a\":1}")
          (spit (str (fs/path claude "CLAUDE.md")) "keep me")
          (let [strs (mount-strings (vec (build-mounts state home cfg)))]
            (testing "all allowlist sources mounted"
              (doseq [rel (concat allowlist-dirs
                                  ["settings.json" "CLAUDE.md" "history.jsonl" ".credentials.json"])]
                (is (some #{(str (fs/path claude rel) ":" (fs/path claude rel))} strs)
                    (str rel " should be mounted")))))
          (testing "existing config files are not overwritten"
            (is (= "{\"a\":1}" (slurp (str (fs/path claude "settings.json")))))
            (is (= "keep me" (slurp (str (fs/path claude "CLAUDE.md"))))))))
      (finally
        (fs/delete-tree home)
        (fs/delete-tree tmp-state)))))

(deftest project-mode-no-credentials-notice
  (let [home (seed-home [])
        tmp-state (str (fs/create-temp-dir))]
    (try
      (with-redefs [util/get-home (fn [] home)
                    util/state-dir (fn [] tmp-state)]
        (let [state {:with-claude true}
              cfg {:claude_isolation "project"}
              claude (fs/path home ".claude")
              sw (java.io.StringWriter.)
              strs (binding [*err* sw]
                     (mount-strings (vec (build-mounts state home cfg))))]
          (testing "visible notice emitted on stderr"
            (is (str/includes? (str sw) "credential")))
          (testing "no credentials mount emitted when absent"
            (is (not (some #{(str (fs/path claude ".credentials.json") ":"
                                  (fs/path claude ".credentials.json"))} strs))))))
      (finally
        (fs/delete-tree home)
        (fs/delete-tree tmp-state)))))

(deftest project-mode-promotes-project-local-credentials
  (let [home (seed-home [])
        tmp-state (str (fs/create-temp-dir))]
    (try
      (with-redefs [util/get-home (fn [] home)
                    util/state-dir (fn [] tmp-state)]
        (let [state {:with-claude true}
              cfg {:claude_isolation "project"}
              dot-claude (naming/claude-dot-claude-dir home)
              proj-creds (str (fs/path dot-claude ".credentials.json"))
              host-creds (str (fs/path home ".claude" ".credentials.json"))]
          ;; project state has a login; host does not
          (fs/create-dirs dot-claude)
          (spit proj-creds "{\"token\":\"x\"}")
          (is (not (fs/exists? host-creds)))
          (let [strs (mount-strings (vec (build-mounts state home cfg)))]
            (testing "creds promoted up to host before mounting"
              (is (fs/exists? host-creds))
              (is (= "{\"token\":\"x\"}" (slurp host-creds))))
            (testing "creds mount now present"
              (is (some #{(str host-creds ":" host-creds)} strs))))
          (testing "promotion is idempotent on a second run"
            (let [strs2 (mount-strings (vec (build-mounts state home cfg)))]
              (is (= "{\"token\":\"x\"}" (slurp host-creds)))
              (is (some #{(str host-creds ":" host-creds)} strs2))))))
      (finally
        (fs/delete-tree home)
        (fs/delete-tree tmp-state)))))
