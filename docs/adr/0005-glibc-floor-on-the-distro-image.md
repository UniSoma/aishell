# A glibc floor on the distro image

The distro image the foundation is built FROM carries a **minimum glibc version
as a first-class property**: `>= 2.39`, asserted at build time in the main stage.
When a tool we ship outruns that floor, we bump the distro image rather than
vendoring or source-building that tool as a one-off. Moving from
`debian:bookworm-slim` (glibc 2.36) to `debian:trixie-slim` (2.41) is the first
application of the policy.

## Context

The foundation image is mostly downloaded prebuilt binaries — `uv`, `cue`,
`gosu`, `gitleaks`, plus whatever a project's own extension pulls in. Upstreams
build those on ever-newer distributions, so their glibc requirement drifts
upward on its own schedule, not ours. bookworm's 2.36 was released in 2022 and
had already blocked two tools by the time it was replaced: SQLite's prebuilt
tools want `GLIBC_2.38`, and the case is not going to get rarer.

Each of those blocks looks small in isolation, and the per-case fix (compile it
ourselves, vendor a static build, drop the tool) is always available. The cost
is that the fixes accumulate in the template with no shared reason, and nothing
records how much headroom is left before the next one.

## Considered Options

- **A declared floor, asserted on every build (chosen).** The build reads
  `getconf GNU_LIBC_VERSION` in the final stage and fails if it is below 2.39,
  comparing with `dpkg --compare-versions` because a string compare would rank
  2.4 above 2.39. The assertion costs nothing on a normal build and turns a
  silent distro regression — a bad edit, or a distro image that moves under us —
  into a build failure with a named cause.

- **Assert the policy number, not the distro's.** trixie ships 2.41; the
  assertion says 2.39. The floor is the promise made to tools; 2.41 is headroom
  above it. Asserting 2.41 would make every future distro bump also a policy
  change, and would fail the build for a distro that still honors the promise.

- **trixie, not ubuntu:24.04.** Ubuntu 24.04 provides exactly 2.39 — it would
  satisfy the floor on the day it was adopted and have zero room above it, which
  is the situation the policy exists to avoid. Debian also keeps the foundation
  on one distro family, so package names and `apt` behavior stay predictable.
  `bookworm-backports` was never an option: it ships no newer glibc.

- **Bump the distro, don't vendor per-tool.** Vendoring is the local optimum
  every time and the global loss over a few years: it moves tools out of apt one
  by one, each with its own pin, checksum and build stage. A distro bump is one
  change that raises the floor for every tool at once, including the ones a
  project adds in its own overlay. Source builds remain legitimate where the
  reason is not glibc — see ADR 0004, which survives this change on its other
  arguments.

## Consequences

- Users get one foundation rebuild on their next `setup`/`update`, automatic via
  the `foundation-content` hash, with the usual stale warning until then.
- Package names change with the distro, and a user's `~/.aishell/Dockerfile` or
  a project extension that pins bookworm-only names breaks. `libreadline8` (now
  `libreadline8t64`, from the time_t transition) and `openjdk-17-jre-headless`
  (absent from trixie; the foundation now ships 21) are the shape that breakage
  takes. The changelog entry says so loudly; this is the price of the policy.
- The floor is a floor, not a pin. A future distro bump only has to keep the
  assertion passing; raising the floor itself is a deliberate edit to this ADR
  and to the assertion together.
- Tool pins (Babashka, CUE, uv, Gitleaks) are deliberately untouched by a distro
  bump, so a build failure after one has an unambiguous cause.
