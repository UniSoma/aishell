---
phase: quick
plan: 002
type: execute
wave: 1
depends_on: []
files_modified:
  - scripts/build-release.clj
autonomous: true

must_haves:
  truths:
    - "Running ./scripts/build-release.clj produces dist/aishell executable"
    - "Running ./scripts/build-release.clj produces dist/aishell.sha256 checksum"
    - "dist/aishell is executable and has shebang"
    - "Checksum format matches standard: {hash}  {filename}"
  artifacts:
    - path: "scripts/build-release.clj"
      provides: "Babashka build script"
      min_lines: 40
  key_links:
    - from: "scripts/build-release.clj"
      to: "bb uberscript"
      via: "babashka.process/shell"
      pattern: "p/shell.*uberscript"
---

<objective>
Rewrite the bash build-release.sh script in Babashka (Clojure).

Purpose: Consistent tooling - all scripts in the same language as the application.
Output: scripts/build-release.clj that produces identical artifacts to the bash version.
</objective>

<execution_context>
@~/.claude/get-shit-done/workflows/execute-plan.md
@~/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/STATE.md
@scripts/build-release.sh
@src/aishell/docker/hash.clj
</context>

<tasks>

<task type="auto">
  <name>Task 1: Create Babashka build-release script</name>
  <files>scripts/build-release.clj</files>
  <action>
Create a Babashka script that replicates build-release.sh functionality:

1. Script header:
   - Shebang: #!/usr/bin/env bb
   - Require: babashka.fs, babashka.process
   - No external dependencies

2. Define constants:
   - output-dir: "dist"
   - output-file: "dist/aishell"

3. Build logic (main function):
   - Print "Building aishell uberscript..."
   - Create output directory using fs/create-dirs
   - Delete existing file using fs/delete-if-exists (bb uberscript refuses to overwrite)
   - Run bb uberscript using p/shell: `bb uberscript {output-file} -m aishell.core`

4. Add shebang (native Clojure, no sed):
   - Read file content with slurp
   - Prepend "#!/usr/bin/env bb\n" to content
   - Write back with spit

5. Make executable:
   - Use fs/set-posix-file-permissions or p/shell "chmod +x"

6. Generate checksum using Java MessageDigest (same pattern as hash.clj):
   - Read file bytes
   - Compute SHA-256 using java.security.MessageDigest
   - Format as hex string (full 64 chars, not truncated)
   - Write to {output-file}.sha256 in format: "{hash}  aishell\n" (two spaces, relative filename)

7. Print completion message:
   - Empty line
   - "Build complete!"
   - "  Binary:   {output-file}"
   - "  Checksum: {output-file}.sha256"
   - Empty line
   - Print checksum file contents

Call (main) at script end for direct execution.
  </action>
  <verify>
```bash
# Run script
./scripts/build-release.clj

# Verify outputs exist
ls -la dist/aishell dist/aishell.sha256

# Verify executable
file dist/aishell | grep -q "executable"

# Verify shebang
head -1 dist/aishell | grep -q "#!/usr/bin/env bb"

# Verify checksum format (hash + two spaces + filename)
cat dist/aishell.sha256 | grep -E "^[a-f0-9]{64}  aishell$"

# Cross-verify checksum is correct
cd dist && sha256sum -c aishell.sha256 && cd ..
```
  </verify>
  <done>Script produces working dist/aishell executable with valid checksum file</done>
</task>

<task type="auto">
  <name>Task 2: Remove legacy bash script</name>
  <files>scripts/build-release.sh</files>
  <action>
Delete the bash script now that Babashka replacement is working.

Run: rm scripts/build-release.sh
  </action>
  <verify>ls scripts/build-release.sh 2>&1 | grep -q "No such file"</verify>
  <done>Only build-release.clj exists in scripts directory</done>
</task>

</tasks>

<verification>
```bash
# Full integration test
./scripts/build-release.clj
test -x dist/aishell
head -1 dist/aishell | grep -q "#!/usr/bin/env bb"
cd dist && sha256sum -c aishell.sha256 && cd ..
echo "All checks passed"
```
</verification>

<success_criteria>
- scripts/build-release.clj exists and is executable
- scripts/build-release.sh is removed
- Running build-release.clj produces dist/aishell with shebang
- Running build-release.clj produces dist/aishell.sha256 with valid checksum
- Checksum file format: "{64-char-hash}  aishell" (two spaces, relative filename)
</success_criteria>

<output>
After completion, create `.planning/quick/002-rewrite-release-build-script-in-babashka/002-SUMMARY.md`
</output>
