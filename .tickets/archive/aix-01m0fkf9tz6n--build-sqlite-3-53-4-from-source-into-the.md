---
id: aix-01m0fkf9tz6n
title: Build SQLite 3.53.4 from source into the foundation image
status: closed
type: feature
priority: 1
mode: afk
created: '2026-08-20T12:48:56.159784248Z'
updated: '2026-08-20T17:01:48.605902228Z'
closed: '2026-08-20T17:01:48.605902228Z'
acceptance:
- title: sqlite3 --version reports 3.53.4 in a fresh sandbox
  done: true
- title: sqldiff and sqlite3_rsync are on PATH and run
  done: true
- title: libsqlite3.so.0, sqlite3.h and sqlite3.pc are installed under /usr/local and ldconfig'd
  done: true
- title: A dynamically-linked consumer reports sqlite_version() 3.53.4, proving the shadow took
  done: true
- title: The apt sqlite3 package is gone from the install block; libsqlite3-0 remains
  done: true
- title: Build fails if any expected pragma compile_options flag is missing
  done: true
- title: Build fails if the downloaded tarball does not match the pinned SHA-256
  done: true
- title: clj-kondo lint passes
  done: true
- title: Builder stage pulls no tcl-dev (gcc, make, libc6-dev, libreadline-dev, curl, ca-certificates, unzip only)
  done: true
- title: ldd $(which sqldiff) resolves libsqlite3.so.0 to /usr/local/lib, not /usr/lib (the sqlite3 CLI statically embeds sqlite3.c by upstream design)
  done: true
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

## Notes

**2026-08-20T13:13:36.198809051Z**

Implemented in templates.clj: a `sqlite-source` builder stage plus a late `COPY --from` and verification block in the main stage. Also: CHANGELOG, README and llm.txt updated, ADR 0004 records the decisions, `aishell info` reports the SQLite version, and a new test/aishell/info_test.clj pins that a builder stage's apt block cannot be scraped as the foundation's package list (a real hazard, given how parse-packages anchors its regex).

THREE DEVIATIONS FROM THE SPEC, each forced by upstream's build:

1. AC 4 ("ldd $(which sqlite3) resolves libsqlite3.so.0 to /usr/local/lib") is not satisfiable as written. main.mk deliberately embeds a private copy of sqlite3.c in the sqlite3 CLI ("the shell needs to be able to enable arbitrary library features, some of which have significant performance impacts"), and --dynlink-tools covers only sqldiff and sqlite3_analyzer, not the shell. Satisfying it literally would mean hand-rolling the shell's link line or running a second autoconf-tarball build. Instead the build asserts that `ldd $(which sqldiff)` resolves to /usr/local/lib/libsqlite3.so.0 — sqldiff links -lsqlite3 with --disable-rpath, so the loader resolves it through the ldconfig cache. That tests the shadow just as directly; the CLI reporting 3.53.4 is asserted separately.

2. AC 5 needs a purpose-built consumer. `pragma compile_options` in the shell describes the shell's embedded copy, not the installed library, so it would pass on EXPLAIN_COMMENTS even if the library lacked it. The builder compiles a small dynamically-linked probe that prints sqlite3_libversion() and every sqlite3_compileoption_get(); the main stage runs it once, asserts the version and all 23 expected options (token names derived from tool/mkctimec.tcl, not guessed), then deletes it. This covers AC 5 and AC 7 against the library itself.

3. AC 9 ("builder needs only gcc and make") is looser in practice. The builder also needs ca-certificates, curl and unzip (upstream ships the full source as a .zip, not a tarball), libc6-dev (gcc only Recommends it, and --no-install-recommends is in force), and libreadline-dev. The last is a judgement call: Debian's sqlite3 links readline, and without it upstream's shell silently drops to plain fgets — no history, no line editing — exactly the kind of regression this ticket exists to prevent. No tcl-dev, per the spec's actual intent.

The pinned SHA-256 (d18fa15aec74d8c17e1463f861095adc01b5ad190256acb4f91d22f0368d232b) was derived locally after confirming the download's SHA3-256 against the PRODUCT line on sqlite.org/download.html; sqlite.org publishes only SHA3.

NOT VERIFIED: THE IMAGE WAS NEVER BUILT. No docker, podman, nerdctl or buildah on this machine, and no C compiler, so ACs 1-5 and 7 are unproven and left unticked. What was verified: every RUN block passes `bash -n`; the download-id derivation and the compile-option check loop were executed against simulated input and fail correctly when an option is missing; `sha256sum -c` was run against the real 14.5 MB download, passing on the pinned hash and failing on a wrong one; the configure flags, install targets (install/install-pc/install-diff/install-rsync) and --soname=legacy behaviour were read out of the 3.53.4 tree; `bb test` and clj-kondo pass.

**2026-08-20T17:01:42.378280358Z**

VERIFIED IN THE REBUILT SANDBOX. The foundation image was rebuilt and this check
ran from inside the resulting container, closing the "NOT VERIFIED: THE IMAGE WAS
NEVER BUILT" gap in the previous note. Every AC now has runtime evidence.

- sqlite3 resolves to /usr/local/bin/sqlite3 (only entry on PATH) and reports
  3.53.4 2026-07-24 19:02:57. No apt sqlite3 package installed; libsqlite3-0
  3.40.1-2+deb12u2 still present, as designed.
- sqldiff produced a correct `UPDATE t SET x=3 WHERE rowid=2;` against two
  divergent databases; sqlite3_rsync replicated a WAL database and the copy read
  back intact.
- /usr/local/lib holds libsqlite3.so.3.53.4 with .so and .so.0 symlinks plus the
  static archive; /usr/local/include has sqlite3.h and sqlite3ext.h;
  /usr/local/lib/pkgconfig/sqlite3.pc is in place. `ldconfig -p` lists
  /usr/local/lib/libsqlite3.so.0 ahead of /lib/x86_64-linux-gnu's.
- The shadow took, proven by a consumer we did not build: Debian's own
  python3.11 `_sqlite3` extension links /usr/local/lib/libsqlite3.so.0 and
  `sqlite3.sqlite_version` reports 3.53.4. `ldd $(which sqldiff)` likewise
  resolves to /usr/local/lib. The sqlite3 CLI shows no libsqlite3 in ldd, exactly
  as deviation 1 predicted, and does link libreadline.so.8.
- `pragma compile_options` read through that python consumer — i.e. off the
  installed .so, not the shell's embedded copy — returns 60 options with every
  expected flag present: FTS3/FTS3_PARENTHESIS/FTS4/FTS5, RTREE, GEOPOLY,
  SESSION, PREUPDATE_HOOK, COLUMN_METADATA, DBSTAT_VTAB, DBPAGE_VTAB, STMTVTAB,
  CARRAY, UNLOCK_NOTIFY, UPDATE_DELETE_LIMIT, SOUNDEX, SECURE_DELETE,
  LOAD_EXTENSION, MATH_FUNCTIONS, EXPLAIN_COMMENTS, BYTECODE_VTAB and
  MAX_VARIABLE_NUMBER=250000. Compiler reported as gcc-12.2.0.
- Feature smoke test through the shell: fts5 match, rtree insert/select, ln/exp,
  json_extract all work.
- The build-time gate is proven on its positive path: the image exists, so the
  verification RUN block (version grep, ldd assertion, probe run, 23-option
  loop, sha256sum -c) executed and passed. The negative path — build fails when
  an option is absent or the hash is wrong — remains covered by the earlier
  simulation, not by this rebuild.
- On the host, `aishell info` lists
  "SQLite 3.53.4 (sqlite3, sqldiff, sqlite3_rsync + libsqlite3/headers, built
  from source)", confirming the info surface.

**2026-08-20T17:01:48.605902228Z**

SQLite 3.53.4 is built from source into the foundation image and verified inside the rebuilt sandbox: the CLI, sqldiff and sqlite3_rsync all run at 3.53.4, the shared library, header and pkg-config file are installed under /usr/local and ldconfig'd ahead of Debian, and the shadow is proven by Debian's own python3 _sqlite3 extension resolving to /usr/local/lib and reporting 3.53.4 with every expected compile option present.
