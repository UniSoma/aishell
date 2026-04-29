---
description: Cut a new release with version bump, changelog, commit, and tag
allowed-tools:
  - Read
  - Edit
  - Bash
  - Glob
  - Grep
---

# Release Workflow

Cut a new semantic version release for aishell.

## Context

<version-file>
The version is defined in `src/aishell/cli.clj` on line 13:
```clojure
(def version "X.Y.Z")
```
</version-file>

<changelog>
CHANGELOG.md follows Keep a Changelog format. New entries go at the top, above the previous version.
</changelog>

<git-tags>
Tags use `vX.Y.Z` format (e.g., `v2.0.1`).
</git-tags>

## Procedure

### Step 1: Gather context

Run these commands to understand current state:

```bash
# Get current version
grep 'def version' src/aishell/cli.clj

# Get latest tag
git describe --tags --abbrev=0

# Get commits since last tag
git log --oneline $(git describe --tags --abbrev=0)..HEAD
```

### Step 2: Determine new version

Based on commits since last tag, determine version bump:
- **patch** (X.Y.Z+1): Bug fixes, minor improvements
- **minor** (X.Y+1.0): New features, non-breaking changes
- **major** (X+1.0.0): Breaking changes

Ask user to confirm version if unclear.

### Step 3: Update version

Edit `src/aishell/cli.clj` line 13 to new version.

### Step 4: Update CHANGELOG.md

Add new section at top (below header, above previous version):

```markdown
## [X.Y.Z] - YYYY-MM-DD

### Added/Changed/Fixed/Removed

- Description of change
```

Categorize commits appropriately:
- **Added**: New features
- **Changed**: Changes to existing functionality
- **Fixed**: Bug fixes
- **Removed**: Removed features

### Step 5: Commit release preparation

```bash
git add -A
git commit -m "chore: prepare vX.Y.Z release"
```

### Step 6: Create tag

```bash
git tag vX.Y.Z
```

### Step 7: Report next steps

Tell user to:
1. Push: `git push origin main --tags`
2. Trigger Release workflow on GitHub Actions

## Constraints

- Do NOT push automatically
- Do NOT trigger GitHub Actions automatically
- Ask for confirmation before committing if changelog content is unclear
