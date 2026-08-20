---
id: aix-01m0fkfpxkzm
title: Surface and document the source-built SQLite toolchain
status: open
type: task
priority: 2
mode: afk
created: '2026-08-20T12:49:09.555431634Z'
updated: '2026-08-20T12:49:21.557186617Z'
acceptance:
- title: aishell info --foundation names SQLite and its version
  done: false
- title: parse-sqlite-version has unit test coverage
  done: false
- title: ADR 0004 records the source-build decision and the facts that forced it
  done: false
- title: ADR 0003 records that its admission test gates admission, not upgrades
  done: false
- title: README's foundation section gives SQLite its own entry
  done: false
- title: CONTEXT.md defines Foundation image and Base image
  done: false
- title: clj-kondo lint passes
  done: false
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
