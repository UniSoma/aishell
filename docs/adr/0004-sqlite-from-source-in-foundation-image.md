# SQLite compiled from source in the foundation image

We build SQLite 3.53.4 from upstream's `sqlite-src` tarball in a throwaway
builder stage and install it under `/usr/local`, replacing Debian's `sqlite3`
package. The shared library is `ldconfig`'d ahead of Debian's `libsqlite3-0`, so
every dynamically-linked consumer in the container gets the new version, not
just the CLI.

## Against the admission test

ADR 0003 asks that its admission test be applied to, and updated by, the next
foundation change. This is not an admission — SQLite was admitted long ago and is
only being sourced differently — but criterion 3 ("Installable via apt") fails
here by construction, so it is worth being explicit rather than quiet. It fails
because apt cannot deliver the capability at all: bookworm's 3.40.1 is the gap,
so "install it from apt" and "have current SQLite" are mutually exclusive. ADR
0003 is amended to record that its test gates admission rather than upgrades. The
arch `case` that criterion 3 warns about is also absent here — compiling is the
one path that does not need one.

## Considered Options

- **Source build, not upstream's prebuilt tools.** Every binary in
  `sqlite-tools-linux-x64-*.zip` requires `GLIBC_2.38` and bookworm provides
  2.36, so the prebuilt tools do not run here at all. Upstream ships no prebuilt
  shared library, and no prebuilt tools for arm64. Compiling is the only option
  that works, and it settles the architecture question at the same time.

- **Shadowing `libsqlite3-0`, not removing it.** `libsqlite3-0` stays installed
  — other packages link it and apt would remove them along with it. Installing
  our library as `/usr/local/lib/libsqlite3.so.0` with the same soname means the
  loader, which searches `/usr/local/lib` first, hands every consumer the newer
  library. SQLite's ABI is backward compatible under `libsqlite3.so.0`, so the
  risk is low, and this is the only arrangement that puts *every* consumer on
  the current version rather than just whoever types `sqlite3`. The apt
  `sqlite3` package is dropped so `/usr/bin/sqlite3` and `/usr/local/bin/sqlite3`
  cannot coexist with `PATH` order deciding the winner.

- **Debian-parity compile flags, not upstream defaults.** Upstream's `configure`
  defaults most features OFF and `--all` covers only fts4, fts5, rtree, geopoly,
  session, dbpage, dbstat and carray. A naive build would ship a *newer* SQLite
  with *fewer* features than the 3.40.1 it replaces — FTS3, column metadata,
  `UPDATE`/`DELETE LIMIT`, soundex, secure delete and a 250000 variable limit
  are all things Debian enables and consumers may already rely on. Every one of
  them is passed explicitly. `EXPLAIN_COMMENTS` and `BYTECODE_VTAB` are added on
  top, for query-plan readability.

- **No TCL, therefore no `sqlite3_analyzer`.** The tree bundles jimsh for its own
  build scripts and every deliverable we want builds without TCL.
  `sqlite3_analyzer` is the one tool that needs it, and pulling `tcl-dev` into
  the builder for a single database-size reporting tool is not worth it. It is
  deferred, not rejected.

- **SHA-256 pinned, unlike every other download in the template.** The rest of
  the template curls and trusts TLS. SQLite is the one tool we compile and then
  load into every process in the container through a shadowing `.so`, so a
  swapped tarball is worst here. sqlite.org publishes SHA3-256; we pin the
  SHA-256 instead so the builder needs nothing beyond coreutils, with the
  cross-check procedure recorded next to the pin.

- **The shell stays statically linked; a probe proves the shadow.** Upstream
  deliberately embeds a private copy of `sqlite3.c` in the `sqlite3` CLI so the
  shell can enable flags with runtime costs the library should not pay, and
  `--dynlink-tools` does not change that. The CLI therefore cannot report on the
  installed library, and `pragma compile_options` in the shell describes the
  shell. The build instead compiles a small dynamically-linked probe that prints
  `sqlite3_libversion()` and `sqlite3_compileoption_get()`; the main stage runs
  it once, asserts the version and every expected compile option, and deletes
  it. `sqldiff` is built with `--dynlink-tools` so `ldd` on it shows which
  `libsqlite3.so.0` the loader actually picks.

- **`libreadline-dev` in the builder, `libreadline8` in the image.** Debian's
  `sqlite3` links readline. Without it upstream's shell silently falls back to
  plain `fgets` — no history, no arrow keys — which is exactly the kind of
  regression this change exists to avoid. `--enable-readline` is passed
  explicitly so a missing library fails the build rather than degrading it.

## Consequences

- Users get one near-full foundation rebuild on the next run, automatic via the
  `foundation-content` hash. This change edits the apt block, so a tail-only
  rebuild was never available; the `COPY --from` is still placed late so that
  *future* version bumps invalidate only the tail.
- Foundation builds now compile C, which costs a few minutes on a cold build
  where every other tool is a download. This is the first such case in the
  template.
- `SQLITE_VERSION`, `SQLITE_YEAR` and `SQLITE_SHA256` are bumped by hand like
  every other pinned tool. Automated drift detection across all pinned tools is
  out of scope here and belongs in its own change.
- A project that needs `sqlite3_analyzer`, ICU collation, or a different feature
  set can still install or build its own in the per-project overlay.
