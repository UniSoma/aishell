---
id: aix-01kr1d764vty
title: Decide validation.clj patterns — data-driven or hardcoded
status: open
type: chore
priority: 2
mode: hitl
created: '2026-05-07T14:24:42.139395775Z'
updated: '2026-08-27T16:17:27.349549261Z'
tags:
- needs-triage
- codebase-design
- security
links:
- aix-01m1203dkknf
---

## Description

## What to build

`src/aishell/validation.clj` (~105 lines) hard-codes two pattern lists (`dangerous-patterns`, `dangerous-mount-paths`) and exposes 6 public functions for 2 concerns. There is no extension hook; users cannot add or relax checks via config.

**Decision needed (real fork):**
- **Leave hardcoded** — security checks that users can disable via config defeat their purpose. If this is the intent, record an ADR so future architecture passes do not re-suggest opening it up.
- **Open up** — move to a small data-driven structure with the current patterns as defaults, allowing additive (never relaxing) custom rules from config.

**Why this matters:**
- This is the kind of decision that should be recorded once and not re-litigated. An ADR is probably the right output regardless of which way it lands.

**Files involved:** `src/aishell/validation.clj`, `docs/CONFIGURATION.md` (if opened up).

## Blocked by

None.

## Notes

**2026-08-27T16:15:59.528421591Z**

Decided in ADR 0006 (docs/adr/0006-harden-on-docker-with-an-egress-proxy.md): patterns stay hardcoded and additive; config can add checks, never relax them; the only override is an explicit CLI flag. Implementation lands with the security-profiles ticket aix-01m1203dkknf, which closes this one.
