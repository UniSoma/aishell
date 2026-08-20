---
id: aix-01m0fkf9tz6n
title: Build SQLite 3.53.4 from source into the foundation image
status: open
type: feature
priority: 1
mode: afk
created: '2026-08-20T12:48:56.159784248Z'
updated: '2026-08-20T12:49:21.652866615Z'
acceptance:
- title: sqlite3 --version reports 3.53.4 in a fresh sandbox
  done: false
- title: sqldiff and sqlite3_rsync are on PATH and run
  done: false
- title: libsqlite3.so.0, sqlite3.h and sqlite3.pc are installed under /usr/local and ldconfig'd
  done: false
- title: ldd $(which sqlite3) resolves libsqlite3.so.0 to /usr/local/lib, not /usr/lib
  done: false
- title: A dynamically-linked consumer reports sqlite_version() 3.53.4, proving the shadow took
  done: false
- title: The apt sqlite3 package is gone from the install block; libsqlite3-0 remains
  done: false
- title: Build fails if any expected pragma compile_options flag is missing
  done: false
- title: Build fails if the downloaded tarball does not match the pinned SHA-256
  done: false
- title: Builder stage needs only gcc and make; no tcl-dev
  done: false
- title: clj-kondo lint passes
  done: false
tags:
- ready-for-agent
- sqlite
links:
- aix-01kyngxr78e1
---

## Description

Debian bookworm ships sqlite3 3.40.1 (2022). Projects using this sandbox depend on
cutting-edge SQLite, so the foundation image must track upstream rather than Debian.

Upstream prebuilt binaries are unusable here: every binary in
sqlite-tools-linux-x64-*.zip requires GLIBC_2.38 and bookworm provides 2.36
(verified against the 3.53.4 download). Upstream ships no prebuilt shared library
at all, and no prebuilt tools for arm64. A source build is the only path, and it
removes the arch question entirely.

Build in a throwaway `gcc make` builder stage (mirroring the existing node-source
stage) from the full `sqlite-src` tarball, which is needed for the `sqldiff` and
`sqlite3_rsync` targets. TCL is NOT required: upstream's README states core
deliverables build without it and the tree bundles jimsh for its build scripts.
`sqlite3_analyzer` is deliberately excluded — it is the only TCL-dependent tool
and would drag tcl-dev into the builder.

The shared library installs to /usr/local/lib and is ldconfig'd, deliberately
shadowing Debian's libsqlite3-0 for every dynamically-linked consumer in the
container. libsqlite3-0 itself cannot be removed (git depends on it). SQLite's ABI
is backward-compatible under libsqlite3.so.0, so this is low risk, and it is the
only arrangement that actually puts every consumer on the frontier.

The apt `sqlite3` package is removed from the install block so /usr/bin/sqlite3
and /usr/local/bin/sqlite3 cannot coexist with PATH order deciding the winner.

Compile flags are a Debian-parity floor plus extras, because upstream's configure
defaults most features OFF and a naive build would REGRESS against the 3.40.1 we
ship today. Debian's build enables FTS3/FTS3_PARENTHESIS/FTS4/FTS5, RTREE,
SESSION, PREUPDATE_HOOK, COLUMN_METADATA, DBSTAT_VTAB, STMTVTAB, UNLOCK_NOTIFY,
UPDATE_DELETE_LIMIT, SOUNDEX, SECURE_DELETE, LOAD_EXTENSION, MATH_FUNCTIONS and
MAX_VARIABLE_NUMBER=250000. Upstream's `--all` covers only
fts4/fts5/rtree/geopoly/session/dbpage/dbstat/carray; the rest must be passed
explicitly. Add EXPLAIN_COMMENTS and BYTECODE_VTAB on top for query-plan
readability.

Version is a pinned ARG (SQLITE_VERSION plus SQLITE_YEAR, with the zero-padded
download id derived in-shell — sqlite.org URLs need all three forms), bumped by
hand like every other tool in the template, flowing through the foundation-content
hash. Automated drift detection across all pinned tools is deliberately out of
scope and belongs in its own ticket.

The download is SHA-256 verified against a pinned ARG. This departs from the
template's existing curl-and-trust-TLS convention on purpose: this is the only
tool we compile and then load into every process in the container via a shadowing
.so, so a swapped tarball is worst here.

Place the COPY --from late (after uv, before the gitleaks block) so future version
bumps invalidate only the tail. This change eats a near-full rebuild regardless,
because it edits the apt block.
