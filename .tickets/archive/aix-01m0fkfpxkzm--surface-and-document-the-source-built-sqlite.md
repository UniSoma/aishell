---
id: aix-01m0fkfpxkzm
title: Surface and document the source-built SQLite toolchain
status: closed
type: task
priority: 2
mode: afk
created: '2026-08-20T12:49:09.555431634Z'
updated: '2026-08-20T13:16:09.506795655Z'
closed: '2026-08-20T13:16:09.506795655Z'
acceptance:
- title: aishell info --foundation names SQLite and its version
  done: true
- title: parse-sqlite-version has unit test coverage
  done: true
- title: ADR 0004 records the source-build decision and the facts that forced it
  done: true
- title: ADR 0003 records that its admission test gates admission, not upgrades
  done: true
- title: README's foundation section gives SQLite its own entry
  done: true
- title: CONTEXT.md defines Foundation image and Base image
  done: true
- title: clj-kondo lint passes
  done: true
deps:
- aix-01m0fkf9tz6n
tags:
- ready-for-agent
- sqlite
links:
- aix-01kr1qqhakrv
---

## Description

Follow-up to the SQLite source build: make the change visible to `aishell info`
users and explicable to future readers.

`aishell info --foundation` would silently lose SQLite, because
`aishell.info/parse-packages` scrapes the apt install block and the sqlite3
package is no longer in it. Re-surface it the way uv is surfaced: a
`parse-sqlite-version` fn plus a render line, reading the pinned ARG. Render as:

  SQLite 3.53.4 (sqlite3, sqldiff, sqlite3_rsync + libsqlite3/headers, built from source)

`info.clj` has no test coverage at all today. Add a test for the new parser only —
retrofitting the five existing parse-* fns is separate work.

Docs. A new ADR 0004 records the decision and the facts that forced it: prebuilt
tools need GLIBC_2.38 while bookworm has 2.36; upstream ships no prebuilt shared
library; the flag set is a Debian-parity floor because a naive build silently
loses FTS/RTREE/SESSION; the .so deliberately shadows Debian's, which leaves
apt/dpkg believing 3.40.1 is installed; the checksum pin departs from the
template's convention on purpose.

ADR 0003's admission test needs amending too. Its criterion 3 (installable via
apt) fails by construction here, but that criterion was written to gate the
ADMISSION of new tools, not to freeze already-admitted tools at Debian's version.
Record that distinction so the test is not read as forbidding this. ADR 0003 asks
to be applied to and updated by the next foundation change — this is that change.

README's foundation section moves sqlite3 out of the generic CLI-tools bullet into
its own entry naming the version, the shared library, and the extra binaries.

CONTEXT.md gains `Foundation image` and `Base image`. Both terms are load-bearing
across base.clj, build.clj and extension.clj and neither is defined. SQLite itself
gets no glossary entry — it is a tool, not domain vocabulary.

## Notes

**2026-08-20T13:16:09.401933226Z**

Done alongside the parent (aix-01m0fkf9tz6n) rather than after it, in the same commit. Splitting them would have shipped an interim state where `aishell info` silently drops SQLite from its output — the exact gap this ticket exists to close — so the surfacing landed with the change that causes it.

All seven criteria are met: parse-sqlite-version plus the render line in info.clj (worded as this ticket specifies), test/aishell/info_test.clj covering the new parser (and also pinning that a builder stage's apt block cannot be scraped as the foundation's package list), ADR 0004, the admission-test amendment in ADR 0003, the README entry, and Foundation image / Base image in CONTEXT.md. clj-kondo is clean of new warnings (14 pre-existing, unchanged) and `bb test` passes at 84 tests.

Beyond the stated scope, llm.txt:500 also listed sqlite3 in its generic tool line and was updated to match README — it is hand-maintained alongside README by repo precedent.

**2026-08-20T13:16:09.506795655Z**

Surfaced the source-built SQLite in aishell info, README, llm.txt and CONTEXT.md; recorded the decision in ADR 0004 and amended ADR 0003's admission test. Landed in the same commit as the parent build change.
