---
id: aix-01m1kyn7s2kj
title: install.sh installs the platform binary, no babashka
status: closed
type: task
priority: 1
mode: afk
created: '2026-09-03T15:37:04.546003196Z'
updated: '2026-09-04T00:33:07.431214772Z'
closed: '2026-09-03T21:26:18.034637143Z'
parent: aix-01m1kydv7r76
tags:
- ready-for-agent
acceptance:
- title: No reference to babashka remains in the script
  done: true
- title: Asset selection covers linux/macos × amd64/aarch64, including the Rosetta case
  done: true
- title: Checksum mismatch removes the download and exits non-zero
  done: true
- title: Unsupported platform exits with the supported list
  done: true
- title: Verified against a locally served `SHA256SUMS` and binary, or against the 4.1.0 release once published
  done: true
---

## Description

Parent spec: aix-01m1kydv7r76 (ADR 0007).

### What to build

`install.sh` installs the platform binary and nothing else. It maps `uname -s`/`uname -m` to an asset name, treating `arm64` and `aarch64` as one and checking `sysctl -n hw.optional.arm64` on Darwin so a Rosetta shell still receives the arm64 binary. It downloads `SHA256SUMS`, extracts the line for its asset, verifies, installs to the existing install directory as `aishell` with `chmod +x`, and keeps `VERSION` and `INSTALL_DIR` overrides. The babashka install block is gone. Unsupported OS/arch pairs exit with a message naming the supported targets.

### Blocked by

None (can start immediately).


## Notes

**2026-09-03T21:26:18.034637143Z**

install.sh downloads the platform binary, verifies it against SHA256SUMS in a temp dir before touching the install path, and installs it. No babashka. Rosetta detection via sysctl; unsupported pairs exit with the supported list. Verified end-to-end against a locally served dist/, including the mismatch path leaving nothing behind. Adds AISHELL_RELEASE_URL so the install can be pointed at a local release tree.
