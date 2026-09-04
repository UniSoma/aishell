---
id: aix-01m1kyn7nsxe
title: 'Release workflow: build, five-runner smoke matrix, then release'
status: closed
type: task
priority: 1
mode: afk
created: '2026-09-03T15:37:04.436449350Z'
updated: '2026-09-04T00:33:06.996234253Z'
closed: '2026-09-04T00:28:43.751731858Z'
parent: aix-01m1kydv7r76
deps:
- aix-01m1kyn7j5th
acceptance:
- title: A dry-run dispatch builds, smoke-tests on all five runners and creates no release
  done: true
- title: The tagged path publishes only after the matrix is green
  done: true
- title: bb version in the workflow matches the build script pin
  done: true
---

## Description

Parent spec: aix-01m1kydv7r76 (ADR 0007).

### What to build

The release workflow becomes three stages. A build job on ubuntu runs the build script and uploads `dist/` as a workflow artifact. A smoke matrix job downloads it on `ubuntu-latest`, `ubuntu-24.04-arm`, `macos-13`, `macos-latest` and `windows-latest`, runs the matching binary with `--version --json`, and asserts the version equals the tag. The release job depends on the matrix and creates the GitHub release with every asset. The existing `dry_run` input skips only the release job, so a workflow_dispatch dry run proves the whole path. The `setup-clojure` step pins the same babashka version the build script pins.

### Blocked by

The build script ticket.


## Notes

**2026-09-04T00:03:44.192039930Z**

Agent session 2026-09-04: the workflow is complete and the only open criterion is the dry-run dispatch, which needs GitHub access this environment lacks (no `gh`, no token, no SSH key, and `origin/main` is still at v4.0.0 with seven local commits unpushed). To finish: push `main`, run `gh workflow run release.yml -f dry_run=true`, confirm build plus all five smoke legs pass and no release appears, then `knot update aix-01m1kyn7nsxe --ac "A dry-run dispatch builds, smoke-tests on all five runners and creates no release" --done --status closed`.

**2026-09-04T00:28:43.751731858Z**

Dry-run dispatch on 2026-09-04 was green: build, all five smoke legs (including macos-aarch64), release job skipped, no release created.
