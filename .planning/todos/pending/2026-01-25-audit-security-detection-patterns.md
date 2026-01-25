---
created: 2026-01-25T15:18
title: Audit security detection patterns
area: security
files:
  - src/aishell/validation.clj:19-41
  - src/aishell/detection/core.clj
  - src/aishell/detection/patterns.clj
---

## Problem

The current dangerous mount patterns in `validation.clj` may be incomplete. During documentation review, we identified that `~/.ssh` should trigger warnings but the current pattern only matches `~/.ssh/id_*` (private key files), not the entire directory.

More broadly, we need a systematic audit of all security detection patterns to ensure we're not missing common security failure vectors when users mount sensitive paths or expose credentials to AI harnesses.

**Current patterns in validation.clj (lines 22-41):**
- `^/etc$` - System config
- `^/var/run/docker.sock$` - Docker socket
- `\.docker/config\.json$` - Docker credentials
- `\.kube/config$` - Kubernetes config
- `\.aws/credentials$` - AWS credentials
- `\.ssh/id_` - SSH private keys (but not ~/.ssh directory itself)

**Potentially missing patterns:**
- `~/.ssh` (entire directory, not just id_* files)
- `~/.gnupg` - GPG keys
- `~/.password-store` - pass password manager
- `~/.netrc` - plaintext credentials
- `~/.npmrc` with auth tokens
- `~/.pypirc` - PyPI credentials
- Cloud provider credential files beyond AWS
- Database credential files

## Solution

1. Review OWASP guidelines for credential exposure
2. Research common credential file locations across ecosystems (npm, pip, gem, cargo, etc.)
3. Update `dangerous-mount-paths` in validation.clj with comprehensive patterns
4. Add `~/.ssh` as a directory-level warning (not just id_* files)
5. Document the security model in docs/CONFIGURATION.md or a dedicated security doc
6. Consider adding a `--security-audit` command that lists what's being exposed
