---
id: aix-01m1kydv7r76
title: 'Binary distribution: babashka with the uberjar appended, one executable per platform'
status: open
type: feature
priority: 1
mode: afk
created: '2026-09-03T15:33:02.328332646Z'
updated: '2026-09-03T22:11:04.632312951Z'
tags:
- ready-for-agent
acceptance:
- title: Build script produces five platform binaries plus SHA256SUMS, and still the legacy aishell/aishell.bat/aishell.sha256 for the bridging release
  done: true
- title: Release workflow smoke-tests every binary with --version --json on a matching runner before creating the release
  done: false
- title: install.sh, install.ps1 and install.bat download the platform binary, verify it against SHA256SUMS, and no longer install babashka
  done: false
- title: aishell upgrade fetches the platform binary, migrates a script install in place, and replaces a running aishell.exe via rename-to-.old
  done: false
- title: Upgrade plan is a pure function with unit tests covering every platform and both install shapes
  done: true
- title: README, CHANGELOG and ADR 0007 describe the new install shape, the bridging release and the manual-download quarantine workaround
  done: true
- title: clj-kondo clean
  done: true
links:
- aix-01m1kxchv7n0
- aix-01kxmdezv7jp
---

## Description

Design settled in a grilling session and recorded in `docs/adr/0007-binary-distribution-by-appending-an-uberjar-to-babashka.md`. Read the ADR first; this spec is its implementation.

## Problem Statement

Installing aishell today means installing babashka first. Each of the three installers carries its own "download bb if missing" block, the interpreter version is whatever `bb` the user has, and a user who just wants a sandbox for their harness has to understand why a Clojure runtime showed up on their machine. Windows users additionally get a `.bat` shim whose only job is to call `bb -f`.

## Solution

aishell ships as one standalone executable per platform: the upstream babashka binary with aishell's uberjar appended. Users need Docker and nothing else. The installers download the binary for the current platform, verify it against a `SHA256SUMS` file, and put it on PATH. `aishell upgrade` does the same and converts an existing script install in place. One bridging release (4.1.0) keeps publishing the old uberscript assets so every installed v4.0.0 can upgrade itself; 4.2.0 stops.

## User Stories

1. As a Linux user, I want to install aishell with one curl command and no babashka, so that the only prerequisite is Docker.
2. As a macOS user on Apple Silicon, I want a native arm64 binary, so that aishell starts without Rosetta.
3. As a macOS user on an Intel machine, I want an amd64 binary, so that the install script works on older hardware.
4. As a Linux user on an arm64 machine, I want an aarch64 binary, so that aishell works on ARM servers and laptops.
5. As a Linux user on a musl or old-glibc distro, I want the Linux binaries statically linked, so that they run without a matching libc.
6. As a Windows PowerShell user, I want `aishell.exe` installed directly, so that there is no `.bat` shim and no `bb` on PATH.
7. As a Windows CMD user, I want `install.bat` to fetch the same `aishell.exe`, so that CMD and PowerShell installs are identical.
8. As any user, I want the installer to verify the download against a published checksum, so that a corrupted or tampered download is refused.
9. As any user, I want the installer to pick the right binary from my OS and CPU architecture automatically, so that I never choose from a list.
10. As a macOS user running an Intel shell under Rosetta, I want the installer to detect the real hardware, so that I get the arm64 binary.
11. As a user on an unsupported platform, I want the installer to say so plainly and name the supported ones, so that I do not get a binary that will not run.
12. As a user who installed v4.0.0, I want `aishell upgrade` to keep working, so that I reach the binary release without re-running the installer.
13. As a user upgrading from a script install, I want `aishell upgrade` to replace the script with the binary in place, so that my PATH entry stays the same.
14. As a Windows user upgrading from a script install, I want the old `aishell` and `aishell.bat` removed and `aishell.exe` put in their place, with a one-line note saying so, so that no stale shim lingers next to the exe.
15. As a Windows user upgrading a binary install, I want the upgrade to succeed even though `aishell.exe` is running, so that self-update does not fail with a file lock error.
16. As a Windows user, I want the leftover `aishell.exe.old` from the previous upgrade cleaned up on the next start, so that the install directory stays tidy.
17. As any user, I want `aishell upgrade` to verify the new binary against `SHA256SUMS` before replacing anything, so that a bad download never replaces a working install.
18. As any user on a slow link, I want a progress bar while the ~90 MB binary downloads when I am at a terminal, so that the command does not look hung.
19. As a user running upgrade from a script or CI, I want a one-line size notice instead of a progress bar, so that logs stay readable.
20. As any user, I want `aishell upgrade <VERSION>` to still install a specific version, including a downgrade with a warning, so that I can pin or roll back.
21. As any user, I want `aishell --version --json` from the binary to report the same shape as before, so that scripts and CI reading it keep working.
22. As a user who prefers running aishell on my own babashka, I want `bbin install` from the git repo documented as the route, so that dropping the uberscript does not strand me.
23. As a maintainer, I want the babashka version pinned in one place in the build script, so that every release runs a known interpreter and bumps are deliberate.
24. As a maintainer, I want the build to run on a single Linux runner for all five targets, so that the release job stays simple and fast.
25. As a maintainer, I want every produced binary smoke-tested on a matching runner before the release is created, so that a corrupt concatenation never ships.
26. As a maintainer, I want the bridging release to also publish the legacy `aishell`, `aishell.bat` and `aishell.sha256` assets, so that v4.0.0 installs can upgrade.
27. As a maintainer, I want the build script to be able to build only the current platform locally, so that I can test a binary without downloading five babashka archives.
28. As a maintainer, I want a dry-run of the release workflow to build and smoke-test without publishing, so that workflow changes are verifiable.
29. As a maintainer, I want the changelog for the bridging release to say that 4.2.0 drops the legacy assets, so that the migration window is on record.
30. As a user who downloaded a binary from the release page in a browser, I want the README to explain the Gatekeeper and SmartScreen prompts and their workaround, so that I am not blocked by an unsigned binary.
31. As a contributor, I want `bb -m aishell.core` and `bb test` to keep working unchanged, so that development does not require building a binary.
32. As a contributor, I want the upgrade logic that decides asset names, destinations and cleanup to be unit-testable without network or filesystem, so that every platform path is covered by tests.

## Implementation Decisions

**Artifacts.** Five release assets named `aishell-linux-amd64`, `aishell-linux-aarch64`, `aishell-macos-amd64`, `aishell-macos-aarch64`, `aishell-windows-amd64.exe`, plus one `SHA256SUMS` in the `hash  filename` format already parsed by the code. Linux takes babashka's `-static` archives, aarch64 having no other choice. Names carry no version, so `releases/latest/download/<name>` works.

**Bridging release.** The build additionally produces the legacy trio (`aishell` uberscript with shebang, `aishell.bat`, `aishell.sha256`) for 4.1.0 only. Implement it as a flag or a version-gated branch in the build script that 4.2.0 removes together with the legacy assets. The release-creation script's file checks cover both sets in 4.1.0.

**Build script.** Stays a babashka script. It pins a single babashka version constant, currently 1.13.220. For each target it downloads the upstream archive for that pin, extracts the `bb` (or `bb.exe`) executable, and writes `bb-bytes ++ jar-bytes` to the asset name; the uberjar is built once with `bb uberjar --main aishell.core` on an explicit `src` classpath so `test/` is not bundled. Windows binaries need no special handling beyond the `.exe` name; concatenation is byte-level and works on Linux. A target selector lets a maintainer build only the host platform locally. `SHA256SUMS` lists every asset produced.

**Release workflow.** One `build` job on ubuntu builds everything and uploads `dist/` as a workflow artifact. A `smoke` matrix job downloads it and runs `--version --json` for its target on `ubuntu-latest`, `ubuntu-24.04-arm`, `macos-13`, `macos-latest`, `windows-latest`, asserting the version matches the tag. The `release` job depends on `smoke` and creates the GitHub release with all assets. The existing `dry_run` input skips only the release job. The `setup-clojure` step pins the same babashka version the build script pins, so the uberjar is built by the interpreter it will run on.

**Installers.** All three drop the babashka block. `install.sh` maps `uname -s`/`uname -m` to an asset name, treating `arm64` and `aarch64` as one and checking `sysctl -n hw.optional.arm64` on Darwin so a Rosetta shell still gets the arm64 binary. It downloads `SHA256SUMS`, extracts the line for its asset, verifies, and installs to `~/.local/bin/aishell` with `chmod +x`. `install.ps1` and `install.bat` install `aishell.exe` into the existing install directory, verify against `SHA256SUMS`, and do not write a `.bat`. Unsupported OS/arch pairs exit with a message listing the supported targets. Version pinning via `VERSION` env var is preserved.

**Upgrade plan as a pure seam.** The upgrade module gains a pure function that takes a description of the environment (OS, architecture, installed path, whether the installed file is a script, whether a `.bat` sits beside it, current version, target version) and returns a plan: the asset name, the download URLs for binary and `SHA256SUMS`, the destination path (`aishell` on Unix, `aishell.exe` on Windows), the files to delete after a successful install (the old script and `.bat` on a Windows migration; the old script on Unix is simply overwritten), whether the rename-to-`.old` step applies, and the human-readable notes to print. The effectful `do-upgrade` becomes a thin executor of that plan: detect environment, compute plan, download with progress or size notice, verify, apply. Script-vs-binary detection reads the installed file's first bytes for `#!`.

**Windows locked-exe replacement.** When the destination is the running executable, rename it to `aishell.exe.old` before moving the new file in. On every start, if an `aishell.exe.old` sits beside the running executable, delete it quietly, ignoring failure.

**Finding the install path.** The existing `find-aishell-path` stops stripping `.bat`; on Windows `fs/which` resolves `aishell.exe`, and a script install is found via the `aishell.bat` shim's directory during migration.

**Progress output.** Download with `curl -#` / `wget --show-progress` when stdout is a terminal (use the existing terminal detection the output module has), otherwise print one line naming the asset and its size from the `Content-Length` header or a fixed "about 90 MB" when unavailable. PowerShell's native progress applies in `install.ps1`.

**Version bumps.** Bridging release is 4.1.0. The changelog entry states that 4.2.0 stops publishing `aishell`, `aishell.bat` and `aishell.sha256`, and that a v4.0.0 install must either pass through 4.1.0 or re-run the installer.

**Docs.** README quick-starts drop every "Babashka is installed automatically" note, list Docker as the only requirement, add the `bbin install` route, and document `xattr -d com.apple.quarantine` and Windows "Unblock" for browser downloads. `aishell info`/`check` are unchanged; they report sandbox tooling, not the host runtime.

## Testing Decisions

A good test exercises external behaviour through the pure upgrade-plan seam and never inspects how the download or move is done. Tests live beside the existing ones under `test/aishell/` in the `clojure.test` style used by `cli_test.clj` and `harness_test.clj`, and run through `bb test`.

Cover, for every OS/arch pair and both install shapes: the asset name chosen, the two URLs for latest and pinned versions, the destination path, the cleanup list, the `.old` rename flag, and that unsupported pairs produce an explicit error value rather than a plan. Cover `SHA256SUMS` line lookup: correct asset picked from a multi-line file, missing asset is an error, hash compared case-insensitively. Cover script detection from leading bytes. Keep `parse-semver`/`version-compare` behaviour with a small regression test since the executor still relies on them.

The build script, installers and workflow are not unit-tested; the CI smoke matrix on five runners is their test, and the `dry_run` workflow input is how a change to them is verified before tagging.

## Out of Scope

- Code signing and notarization (tracked separately).
- Exposing the embedded runtime to user scripts (the orchestration-library ticket weighs it).
- Removing the legacy assets: that is the 4.2.0 follow-up, one line in the build script and the release-creation checks plus a changelog note.
- Any change to the foundation image's own babashka pin or install.
- GraalVM native-image; asset names and installers are chosen so it could replace the appended-jar build later without user-visible change.

## Further Notes

Verified locally with bb 1.13.220: `bb uberjar` of `aishell.core` concatenated onto the bb binary yields a 90 MB executable that passes arguments through and answers `--version --json` correctly. The jar's manifest main class is what the appended-jar loader runs, so the uberjar must be built with `--main aishell.core`.

Hard rules from AGENTS.md apply: lint with clj-kondo before commit, no AI attribution in commits.