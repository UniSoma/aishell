---
id: aix-01m1kyn7s2kj
title: install.sh installs the platform binary, no babashka
status: open
type: task
priority: 1
mode: afk
created: '2026-09-03T15:37:04.546003196Z'
updated: '2026-09-03T15:37:04.546003196Z'
parent: aix-01m1kydv7r76
tags:
- ready-for-agent
---

## Description

Parent spec: aix-01m1kydv7r76 (ADR 0007).

### What to build

`install.sh` installs the platform binary and nothing else. It maps `uname -s`/`uname -m` to an asset name, treating `arm64` and `aarch64` as one and checking `sysctl -n hw.optional.arm64` on Darwin so a Rosetta shell still receives the arm64 binary. It downloads `SHA256SUMS`, extracts the line for its asset, verifies, installs to the existing install directory as `aishell` with `chmod +x`, and keeps `VERSION` and `INSTALL_DIR` overrides. The babashka install block is gone. Unsupported OS/arch pairs exit with a message naming the supported targets.

### Acceptance criteria

- [ ] No reference to babashka remains in the script
- [ ] Asset selection covers linux/macos × amd64/aarch64, including the Rosetta case
- [ ] Checksum mismatch removes the download and exits non-zero
- [ ] Unsupported platform exits with the supported list
- [ ] Verified against a locally served `SHA256SUMS` and binary, or against the 4.1.0 release once published

### Blocked by

None (can start immediately).
