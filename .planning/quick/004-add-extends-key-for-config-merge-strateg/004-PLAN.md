---
phase: quick
plan: 004
type: execute
wave: 1
depends_on: []
files_modified:
  - src/aishell/config.clj
  - .aishell/config.yaml
autonomous: true

must_haves:
  truths:
    - "Project config with extends: global merges with global config"
    - "Project config with extends: none replaces global config entirely"
    - "Missing extends key defaults to global merge behavior"
    - "Lists concatenate (mounts, ports, docker_args)"
    - "Maps shallow merge (env)"
    - "Scalars from project replace global (pre_start)"
  artifacts:
    - path: "src/aishell/config.clj"
      provides: "Config loading with merge strategy"
      contains: "merge-configs"
    - path: ".aishell/config.yaml"
      provides: "Template with extends key documentation"
      contains: "extends:"
  key_links:
    - from: "load-config"
      to: "merge-configs"
      via: "merge when extends != none"
      pattern: "merge-configs"
---

<objective>
Add `extends` key to config.yaml handling for flexible config inheritance.

Purpose: Allow users to choose whether project config merges with or replaces global config, enabling better defaults (global) with project-specific overrides.

Output: Updated config.clj with merge logic and documented config.yaml template.
</objective>

<execution_context>
@~/.claude/get-shit-done/workflows/execute-plan.md
@~/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@src/aishell/config.clj
@.aishell/config.yaml
</context>

<tasks>

<task type="auto">
  <name>Task 1: Implement config merge strategy in config.clj</name>
  <files>src/aishell/config.clj</files>
  <action>
Update src/aishell/config.clj to support the `extends` key:

1. Add `:extends` to `known-keys` set

2. Create `merge-configs` function that:
   - Takes global-config and project-config as arguments
   - Concatenates list keys: :mounts, :ports, :docker_args (global first, project appends)
   - Shallow merges map keys: :env (project values override global)
   - Replaces scalar keys: :pre_start (project value wins if present)
   - Removes :extends key from final result (internal-only key)

3. Create helper `load-yaml-config` function to DRY up the yaml loading:
   - Takes path, returns parsed config or nil on error
   - Handles fs/exists? check, slurp, yaml/parse-string, validate-config
   - Catches Exception and calls output/error

4. Update `load-config` function:
   - Load both project-config and global-config using helper
   - Check project-config's :extends value (default "global" if missing)
   - If extends is "none": return project-config as-is (current behavior)
   - If extends is "global" (or missing): call merge-configs with global first, project second
   - If no project config exists: return global-config as-is
   - If neither exists: return nil

5. Update `config-source` to return :merged when extends=global and both configs exist
  </action>
  <verify>
Test manually with bb:
```bash
cd /home/jonasrodrigues/projects/harness
# Create test global config
mkdir -p ~/.aishell
echo -e "mounts:\n  - ~/.gitconfig\nenv:\n  GLOBAL_VAR: hello" > ~/.aishell/config.yaml

# Test extends: global (default)
echo -e "mounts:\n  - ~/data\nenv:\n  PROJECT_VAR: world" > /tmp/test-config.yaml
bb -e '(require (quote [aishell.config :as c])) (prn (c/load-config "/tmp"))'

# Test extends: none
echo -e "extends: none\nmounts:\n  - ~/data" > /tmp/test-config.yaml
bb -e '(require (quote [aishell.config :as c])) (prn (c/load-config "/tmp"))'
```
  </verify>
  <done>
- extends: global merges both configs (lists concat, maps merge, scalars replace)
- extends: none returns only project config
- Missing extends key defaults to global merge behavior
- config-source returns :merged when both configs used
  </done>
</task>

<task type="auto">
  <name>Task 2: Document extends key in config.yaml template</name>
  <files>.aishell/config.yaml</files>
  <action>
Update .aishell/config.yaml to add documentation for the extends key at the TOP of the file (before mounts):

```yaml
# =============================================================================
# EXTENDS - Config inheritance strategy
# =============================================================================
# Controls how this project config relates to global (~/.aishell/config.yaml):
#   - "global" (default): Merge with global config
#       - Lists (mounts, ports, docker_args): concatenate (global + project)
#       - Maps (env): shallow merge (project overrides global)
#       - Scalars (pre_start): project replaces global
#   - "none": This config fully replaces global (no merging)

extends: global

# =============================================================================
# MOUNTS - Additional directories...
```

Keep the rest of the file unchanged.
  </action>
  <verify>
```bash
head -20 /home/jonasrodrigues/projects/harness/.aishell/config.yaml | grep -A5 "EXTENDS"
```
  </verify>
  <done>
- extends key documented at top of config.yaml template
- Merge rules clearly explained in comments
- Default value shown as "global"
  </done>
</task>

</tasks>

<verification>
1. Load config with extends: global (or missing) - should merge both configs
2. Load config with extends: none - should return only project config
3. List values should concatenate (global mounts + project mounts)
4. Map values should merge (global env + project env, project wins on conflicts)
5. Scalar values should replace (project pre_start wins)
6. config-source returns :merged when appropriate
</verification>

<success_criteria>
- `extends: global` merges project config with global config using defined rules
- `extends: none` makes project config fully replace global (backward compatible)
- Missing `extends` key defaults to "global" (merge behavior)
- Config template documents the new key and merge rules
- No breaking changes to existing behavior when extends key is absent
</success_criteria>

<output>
After completion, create `.planning/quick/004-add-extends-key-for-config-merge-strateg/004-SUMMARY.md`
</output>
