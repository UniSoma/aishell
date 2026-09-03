---
id: aix-01m1kxchv7n0
title: Code-sign and notarize the release binaries
status: open
type: task
priority: 3
mode: hitl
created: '2026-09-03T15:14:51.367735609Z'
updated: '2026-09-03T15:33:02.581894340Z'
links:
- aix-01kxmdezv7jp
- aix-01m1kydv7r76
---

## Description

ADR 0007 ships unsigned binaries. The supported paths (install scripts, `aishell upgrade`) fetch with curl or PowerShell, which set no quarantine flag, so Gatekeeper and SmartScreen stay quiet. A manual browser download of `aishell-macos-*` or `aishell-windows-amd64.exe` hits the "cannot be opened" / SmartScreen dialog; the README documents `xattr -d com.apple.quarantine` and Unblock as workarounds.

Closing this gap means an Apple Developer account plus notarization in the release workflow, and a Windows code-signing certificate. Decide whether the cost is worth it once there is a signal that manual downloads are common.
