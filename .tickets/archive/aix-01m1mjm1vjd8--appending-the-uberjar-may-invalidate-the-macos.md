---
id: aix-01m1mjm1vjd8
title: Appending the uberjar may invalidate the macOS arm64 code signature
status: closed
type: bug
priority: 1
mode: hitl
created: '2026-09-03T21:25:57.234664070Z'
updated: '2026-09-04T01:37:21.506626877Z'
closed: '2026-09-04T01:37:21.506626877Z'
parent: aix-01m1kydv7r76
acceptance:
- title: The dry-run smoke matrix result for macos-latest is recorded here
  done: true
- title: If the binary is killed at exec, the build re-signs the macOS arm64 asset and the smoke matrix passes
  done: true
---

## Description

Found during the phase-2 review of aix-01m1kydv7r76.

### The risk

The upstream babashka `macos-aarch64` binary is code-signed: its Mach-O carries an
`LC_CODE_SIGNATURE` at offset 74207360, size 579888, ending at 74787248 — exactly the file size.
Appending the uberjar puts ~300 KB of data past the signature blob.

On Apple Silicon the kernel refuses to execute a binary whose signature does not validate. Unlike
Gatekeeper quarantine this applies to every binary however it arrived, so if the appended-jar file
fails validation, `aishell-macos-aarch64` is killed at exec on every arm64 Mac — user story 2 of the
parent spec.

The ADR does not cover this: its Further Notes record a verification done on Linux, and its "no code
signing yet" bullet reasons only about Gatekeeper and SmartScreen prompts, not signature validity at
exec.

### What is not yet known

Whether trailing data past the signature superblob actually invalidates it. That is decided on real
hardware. The five-runner smoke matrix (aix-01m1kyn7nsxe) runs `--version --json` on `macos-latest`,
which is arm64, so the first dry-run dispatch answers it.

### If it does break

Cheapest first:
- Re-sign ad-hoc after appending (`codesign -f -s -`) — needs a macOS runner, which breaks the ADR's
  "one Linux runner builds all five targets" decision.
- Re-sign on Linux with `rcodesign` (the Rust apple-codesign tool), keeping the single-runner build.
- Ship the macOS arm64 asset some other way.

## Notes

**2026-09-04T01:37:12.559587857Z**

Answered on real hardware: Release workflow run 53 (https://github.com/UniSoma/aishell/actions/runs/33822513442) for tag v4.1.0 passed the smoke matrix on all five runners. The macos-latest (arm64) leg ran `aishell-macos-aarch64 --version --json`, got version 4.1.0, and the checksum matched. Trailing data past the LC_CODE_SIGNATURE superblob does not invalidate the signature at exec, so no re-signing is needed. The binary is not notarized, so a browser download still needs the quarantine flag cleared; that is aix-01m1kxchv7n0.

**2026-09-04T01:37:21.506626877Z**

The macos-latest smoke leg of release run 53 (v4.1.0) executed the appended-jar binary; the arm64 signature survives trailing data, no re-signing needed.
