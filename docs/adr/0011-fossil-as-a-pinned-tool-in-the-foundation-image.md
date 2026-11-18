# Fossil as a Pinned tool in the Foundation image

We add Fossil 2.28 to the Foundation image as a Pinned tool: upstream's
prebuilt binary from fossil-scm.org, pinned by `ARG FOSSIL_VERSION` and a
SHA-256 per architecture, not Debian's `fossil` package. Agents across
projects need to clone and read Fossil-hosted sources (SQLite, Tcl, Fossil
itself), so it passes ADR 0003's first criterion as a generic capability.

## Context

Debian trixie ships Fossil 2.26 and will stay on it for the life of the
release; upstream is at 2.28. The Foundation image already holds two kinds of
tool. For some (SQLite, CUE, uv, Babashka, gosu) we want to choose the exact
version and move it deliberately. For the rest, the apt list, whatever the
Distro image ships is good enough. ADR 0003's criterion 3 ("Installable via
apt") did not name that split, so this change amends it: criterion 3 now
chooses the class. Fossil is in the first class.

## Decision

- **Prebuilt, not compiled.** `fossil-linux-x64-<version>.tar.gz` on amd64
  and `fossil-pi-<version>.tar.gz` on arm64; the "pi" build is aarch64.
  Both are single binaries that need only glibc (2.25 and 2.35 at most) and,
  on arm64, `libz.so.1` from `zlib1g`, which the Distro image already has.
  Both are under the 2.39 floor of ADR 0005.
- **SHA-256 pinned per architecture.** The tarballs come from Fossil's own
  server, not GitHub releases. Upstream publishes SHA3-256 in `/juvlist`; a
  bump confirms that and then pins `sha256sum`, as for SQLite.
- **Listed under "Runtimes" in `aishell info`,** next to the other Pinned
  tools. "System Packages" stays the scraped apt list.

## Considered options

- **Debian's `fossil` package.** A one-line change, rejected: it fixes the
  version at 2.26 for the release's life, and Fossil is a tool we want to
  choose the version of.
- **Compiling from source, as for SQLite.** Would link Fossil against the
  shadowed libsqlite3. Rejected: it adds a builder stage and build time for a
  CLI that bundles its own SQLite, and upstream already ships both
  architectures we build.
- **Trusting TLS without a checksum, as for uv and CUE.** Rejected: the hashes
  are cheap to pin and the server is not a release host we already rely on.

## Consequences

- One near-full Foundation rebuild, automatic via the foundation-content hash.
  The binary is about 10 MB, under ADR 0003's size criterion.
- Each version bump costs a SHA3 check and two SHA-256 pins.
- Fossil's bundled SQLite (3.52 in 2.28) is not the image's SQLite;
  `fossil sql` reports Fossil's own.
