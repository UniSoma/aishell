---
id: aix-01m1203dqawd
title: Network modes with an egress proxy sidecar
status: open
type: feature
priority: 1
mode: afk
created: '2026-08-27T16:15:58.186299017Z'
updated: '2026-08-27T16:17:26.782182777Z'
acceptance:
- title: network accepts open|restricted|allowlist|none from config and CLI
  done: false
- title: Under restricted, curl to the Docker gateway, 127.0.0.1 via a rebinding name, and 169.254.169.254 all fail; curl to a public host succeeds
  done: false
- title: Under allowlist, only descriptor hosts + registries + project additions succeed; the harness completes a real prompt
  done: false
- title: Proxy sidecar is created once per host, reused, and recreated when its config hash changes
  done: false
- title: Works on Docker Desktop (verified on macOS or WSL2) without host firewall rules
  done: false
- title: Per-harness proxy-variable support verified and documented; harness descriptors carry :allowed-hosts
  done: false
- title: strict profile selects allowlist unless the project sets network explicitly
  done: false
deps:
- aix-01m1203dkknf
tags:
- security
---

## Description

Add a per-project `network` key (`open` | `restricted` | `allowlist` | `none`) and CLI flag.

- `open`: today's behavior (default bridge). Remains the interactive default for now.
- `none`: `--network none`.
- `restricted` / `allowlist`: create (once per host) a user-defined bridge with `--internal` and an aishell-managed squid sidecar (`ubuntu/squid` or pinned equivalent) attached to both the internal bridge and the default one. Sandboxes on these modes join only the internal bridge and get `HTTP_PROXY`/`HTTPS_PROXY`/`http_proxy`/`https_proxy`/`NO_PROXY` pointing at the sidecar. Anything that ignores the proxy has no route out.
- `restricted` squid config: allow any destination except resolved addresses in 127.0.0.0/8, 10/8, 172.16/12, 192.168/16, 169.254/16, ::1, fc00::/7, fe80::/10, and the Docker gateway. Squid must resolve names itself (`dns_nameservers`), so a rebinding name still hits the `dst` ACL.
- `allowlist`: deny by default; allowed domains = union of the harness descriptor's new `:allowed-hosts` (e.g. Claude Code: `api.anthropic.com`, `statsig.anthropic.com`, `sentry.io`; verify each harness's real set from its source/docs) + a shared package-registry set (`registry.npmjs.org`, `pypi.org`, `files.pythonhosted.org`, `github.com`, `objects.githubusercontent.com`, `deb.debian.org`, …) + project `network_allow:` additions. Config can add hosts, never remove the harness's own.

Sidecar lifecycle: started lazily by the first sandbox that needs it, named with a fixed host-wide name, reused across projects, survives sandbox exit, restarted if its image or generated config hash changes. Must work on Docker Desktop (macOS/Windows) — no host networking, no iptables.

Verification task inside this ticket: confirm each harness (Claude Code, Codex, OpenCode, Gemini, pi) and each tool in the foundation image that fetches (npm, pip/uv, git, apt, curl, gh) honors the proxy variables; document the ones that do not in `docs/CONFIGURATION.md`.

Ships after `aix-security-profiles` so `strict` can select `allowlist`. Decision record: ADR 0006.
