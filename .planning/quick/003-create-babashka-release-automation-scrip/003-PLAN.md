---
task: 003
type: quick
description: Create Babashka release automation script
files_modified:
  - scripts/create-release.clj
autonomous: true
---

<objective>
Create a Babashka script to automate GitHub release creation.

Purpose: Streamline the release process by automating `gh release create` with idempotency
Output: scripts/create-release.clj - executable script that creates GitHub releases safely
</objective>

<context>
@scripts/build-release.clj (pattern reference for Babashka scripts)
</context>

<tasks>

<task type="auto">
  <name>Task 1: Create idempotent GitHub release script</name>
  <files>scripts/create-release.clj</files>
  <action>
Create `scripts/create-release.clj` following the build-release.clj pattern:

**Script structure:**
```clojure
#!/usr/bin/env bb

(ns create-release
  (:require [babashka.process :as p]
            [cheshire.core :as json]))
```

**Version extraction:**
- Run `./dist/aishell --version --json` via babashka.process
- Parse JSON output: `{"name":"aishell","version":"2.0.0"}`
- Extract version string (e.g., "2.0.0")
- Construct tag as `v{version}` (e.g., "v2.0.0")

**Idempotency check:**
- Run `gh release view v{version}` via p/shell with :continue true
- Check exit code: 0 = exists, non-zero = doesn't exist
- If exists: print "Release v{version} already exists" and exit 0 (success, not error)
- If not exists: proceed to create

**Release creation:**
- Run `gh release create v{version} ./dist/aishell ./dist/aishell.sha256`
- Title: "v{version}" (use --title flag)
- Generate notes: use --generate-notes flag
- Assets: dist/aishell and dist/aishell.sha256

**Output:**
- Print clear status messages at each step
- Print release URL on success
- Exit 0 on success (including "already exists")
- Exit 1 on actual errors (missing files, gh failure, etc.)

**Pre-flight checks:**
- Verify dist/aishell exists before attempting anything
- Verify dist/aishell.sha256 exists
- If missing: print clear error and exit 1
  </action>
  <verify>
```bash
# Script exists and is executable
ls -la scripts/create-release.clj

# Syntax check
bb --parse scripts/create-release.clj

# Dry run - check it detects missing dist/ gracefully
rm -rf dist 2>/dev/null; ./scripts/create-release.clj 2>&1 | grep -i "not found\|missing\|error"
```
  </verify>
  <done>
- scripts/create-release.clj exists with +x permission
- Script parses without error
- Script handles missing dist/ files gracefully with clear error
- Script is idempotent (safe to run multiple times)
  </done>
</task>

</tasks>

<verification>
```bash
# Full verification (requires dist/ to exist):
./scripts/build-release.clj
./scripts/create-release.clj
# Should either create release or report "already exists"
```
</verification>

<success_criteria>
- Script extracts version from `./dist/aishell --version --json`
- Script checks for existing release before creating
- Script creates release with both assets attached
- Script is idempotent (exits 0 if release exists)
- Script follows project conventions (Babashka, same style as build-release.clj)
</success_criteria>

<output>
After completion, update `.planning/STATE.md` quick tasks table.
</output>
