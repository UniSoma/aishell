---
phase: quick-001
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - src/aishell/validation.clj
  - src/aishell/docker/run.clj
autonomous: true

must_haves:
  truths:
    - "User sees warning when mounting root filesystem (/)"
    - "User sees warning when mounting credential files (~/.aws, ~/.gnupg, ~/.kube)"
    - "User sees warning when mounting docker socket via mounts config"
    - "Warnings are advisory only - execution continues"
  artifacts:
    - path: "src/aishell/validation.clj"
      provides: "Dangerous mount path detection"
      contains: "dangerous-mount-paths"
    - path: "src/aishell/docker/run.clj"
      provides: "Mount warning integration"
      contains: "warn-dangerous-mounts"
  key_links:
    - from: "src/aishell/docker/run.clj"
      to: "src/aishell/validation.clj"
      via: "require and function call"
      pattern: "validation/warn-dangerous-mounts"
---

<objective>
Add advisory warnings for potentially dangerous mount paths in aishell config.

Purpose: Alert users when they configure mounts that could expose sensitive host data to containers, matching the advisory pattern already used for dangerous docker_args.

Output: Users see security warnings when mounting paths like /, ~/.aws, ~/.gnupg, docker.sock via the mounts config option.
</objective>

<context>
@src/aishell/validation.clj - Existing dangerous docker_args pattern detection
@src/aishell/docker/run.clj - build-mount-args function that processes mounts
@src/aishell/run.clj - Where validation/warn-dangerous-args is called (line 102)
</context>

<tasks>

<task type="auto">
  <name>Task 1: Add dangerous mount path detection to validation.clj</name>
  <files>src/aishell/validation.clj</files>
  <action>
Add dangerous mount path patterns and warning function to validation.clj, following the exact pattern of dangerous-patterns and warn-dangerous-args:

1. Add `dangerous-mount-paths` def after `dangerous-patterns` (around line 17):
```clojure
(def dangerous-mount-paths
  "Potentially dangerous mount paths that expose sensitive host data.
   These are advisory warnings - users may have legitimate reasons."
  [{:pattern #"^/$"
    :message "/: Root filesystem mount gives full host access"}
   {:pattern #"/etc/passwd"
    :message "/etc/passwd: System user database"}
   {:pattern #"/etc/shadow"
    :message "/etc/shadow: System password hashes"}
   {:pattern #"docker\.sock"
    :message "docker.sock: Container can control Docker daemon"}
   {:pattern #"\.aws/credentials|\.aws$"
    :message "~/.aws: AWS credentials exposure"}
   {:pattern #"\.azure"
    :message "~/.azure: Azure credentials exposure"}
   {:pattern #"\.gnupg"
    :message "~/.gnupg: GPG private keys exposure"}
   {:pattern #"\.kube"
    :message "~/.kube: Kubernetes credentials exposure"}
   {:pattern #"\.password-store"
    :message "~/.password-store: Password manager data"}
   {:pattern #"\.ssh/id_"
    :message "~/.ssh private keys: SSH key exposure"}])
```

2. Add `check-dangerous-mounts` function after `check-dangerous-args`:
```clojure
(defn check-dangerous-mounts
  "Check mount paths for dangerous patterns.
   Accepts a seq of mount strings (source or source:dest format).
   Returns seq of warning messages, or nil if none found."
  [mounts]
  (when (seq mounts)
    (seq
      (for [mount mounts
            :let [mount-str (str mount)
                  ;; Extract source path (before : if present)
                  source (first (str/split mount-str #":" 2))]
            {:keys [pattern message]} dangerous-mount-paths
            :when (re-find pattern source)]
        message))))
```

3. Add `warn-dangerous-mounts` function after `warn-dangerous-args`:
```clojure
(defn warn-dangerous-mounts
  "Warn about dangerous mount paths if any found.
   Advisory only - does not block execution."
  [mounts]
  (when-let [warnings (check-dangerous-mounts mounts)]
    (println)  ; Blank line before warning block
    (output/warn "Security notice: Potentially dangerous mount paths detected")
    (doseq [msg warnings]
      (binding [*out* *err*]
        (println (str "  - " msg))))
    (binding [*out* *err*]
      (println)
      (println "These mounts may expose sensitive host data. Use only if necessary.")
      (println))))
```
  </action>
  <verify>bb -e "(require '[aishell.validation :as v]) (println (v/check-dangerous-mounts [\"~/.aws\" \"/etc/passwd\" \"~/projects:/work\"]))"</verify>
  <done>check-dangerous-mounts returns warning messages for ~/.aws and /etc/passwd, nil for safe ~/projects path</done>
</task>

<task type="auto">
  <name>Task 2: Integrate mount warnings into run.clj</name>
  <files>src/aishell/docker/run.clj</files>
  <action>
Add call to validation/warn-dangerous-mounts in run.clj, right after the existing warn-dangerous-args call (around line 102):

1. Add validation require to the ns declaration (already there via run.clj, but run.clj imports from aishell.run - the actual integration point)

Actually, looking at the code flow:
- run.clj (aishell.run) is where validation/warn-dangerous-args is called at line 101-102
- docker/run.clj (aishell.docker.run) builds the mount args

The warning should go in aishell.run where config is available, NOT in docker/run.clj.

Edit src/aishell/run.clj to add mount path warning right after the docker_args warning (around line 102):

```clojure
;; Warn about dangerous docker_args (advisory warning)
_ (when-let [docker-args (:docker_args cfg)]
    (validation/warn-dangerous-args docker-args))

;; Warn about dangerous mount paths (advisory warning)
_ (when-let [mounts (:mounts cfg)]
    (validation/warn-dangerous-mounts mounts))
```

Note: This is in src/aishell/run.clj, NOT src/aishell/docker/run.clj. The validation namespace is already required.
  </action>
  <verify>Create test config with dangerous mount, run aishell and verify warning appears:
echo "mounts: ['~/.aws']" > /tmp/test-config.yaml && bb -e "(require '[aishell.validation :as v]) (v/warn-dangerous-mounts [\"~/.aws\"])"</verify>
  <done>Running aishell with mounts: ['~/.aws'] in config shows "Security notice: Potentially dangerous mount paths detected" warning</done>
</task>

</tasks>

<verification>
1. Load validation namespace and test pattern detection:
   ```
   bb -e "(require '[aishell.validation :as v]) (v/check-dangerous-mounts [\"/\" \"~/.gnupg\" \"~/safe/path\"])"
   ```
   Should return warnings for / and ~/.gnupg, not for ~/safe/path

2. Test warning output format matches docker_args warnings:
   ```
   bb -e "(require '[aishell.validation :as v]) (v/warn-dangerous-mounts [\"/var/run/docker.sock:/var/run/docker.sock\"])"
   ```
   Should print formatted warning to stderr
</verification>

<success_criteria>
- Dangerous mount patterns defined covering: /, /etc/passwd, /etc/shadow, docker.sock, ~/.aws, ~/.azure, ~/.gnupg, ~/.kube, ~/.password-store, ~/.ssh private keys
- check-dangerous-mounts function correctly identifies dangerous source paths
- warn-dangerous-mounts prints advisory warning matching docker_args warning format
- Integration in run.clj warns when config contains dangerous mounts
- Safe mount paths produce no warnings
- Warnings are advisory only - do not block execution
</success_criteria>

<output>
After completion, create `.planning/quick/001-add-dangerous-mount-path-warnings-to-clo/001-SUMMARY.md`
</output>
