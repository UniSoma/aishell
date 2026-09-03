# Distribute aishell as a standalone binary: babashka with the uberjar appended

aishell ships as one native executable per platform, made by appending the
`bb uberjar` of `aishell.core` to the upstream babashka binary for that
platform. Users need Docker and nothing else; the installers and `aishell
upgrade` no longer install or look for `bb`.

## Context

Until v4.0.0 the release was a single platform-neutral uberscript with a
`#!/usr/bin/env bb` shebang plus a `.bat` wrapper running `bb -f`. That kept
the artifact at 300 KB but made babashka a runtime prerequisite: every
installer carried its own "download bb if missing" block (bash, PowerShell,
CMD), the runtime version was whatever `bb` the user happened to have, and CI
built with `bb: latest`, so no two installs were guaranteed to run the same
interpreter. Nothing in aishell shells out to a host `bb`; the dependency
existed only to start the program.

## Decision

- **Five targets, one per babashka upstream build**: linux-amd64,
  linux-aarch64, macos-amd64, macos-aarch64, windows-amd64. The rule is "what
  babashka publishes, we publish". Linux takes the static builds; the dynamic
  amd64 build exists only for `babashka.ffi`, which the host CLI does not use,
  and static runs on any glibc or musl distro.
- **The babashka version is pinned in the build script**, bumped on purpose
  and noted in the changelog. It is independent of the foundation image's
  `BABASHKA_VERSION`: one is the host CLI's runtime, the other a tool offered
  inside the sandbox.
- **Release assets are versionless, platform-suffixed raw binaries**
  (`aishell-linux-amd64`, `aishell-windows-amd64.exe`, ...) plus one
  `SHA256SUMS`. Raw rather than archived so that install and upgrade stay a
  single fetch with no unpack step; versionless so
  `releases/latest/download/<name>` keeps working.
- **One bridging release** (4.1.0) still publishes the old `aishell` and
  `aishell.bat` assets, because every installed v4.0.0 has an `upgrade`
  command that fetches those names and would otherwise drop a Linux ELF on a
  Windows machine. 4.2.0 stops publishing them; from then on a v4.0.0 install
  must re-run the installer. `bbin install` from git remains the route for
  people who want to run aishell on their own babashka.
- **`aishell upgrade` migrates a script install in place**: replace the script
  with the binary, and on Windows write `aishell.exe` and delete the old
  `aishell` and `aishell.bat`. On Windows a running executable is locked, so
  upgrade renames it to `.old` first and the next start deletes the leftover.
- **CI builds every target on one Linux runner** (download the platform's
  bb, append the jar) and smoke-tests each binary with `--version --json` on
  a matching runner before the release is created.
- **No code signing yet.** curl and PowerShell downloads carry no quarantine
  flag, so the supported paths do not hit Gatekeeper or SmartScreen. Manual
  browser downloads do; the workaround is documented and signing is tracked
  as an open ticket.

## Considered options

- **Keep the uberscript and auto-install babashka.** Status quo. Rejected:
  three installers to keep correct, an unpinned runtime, and a prerequisite
  that exists only to boot the program.
- **GraalVM native-image of aishell itself.** Smaller binary and faster
  start, but a real native build per platform (matrix runners, reflection
  config, long compile) for a CLI whose hot path is `docker run`. The
  appended-jar route gets the same "no dependencies" property for the cost of
  a concatenation, and can be swapped for native-image later without
  changing asset names or installers.
- **Archives per platform, like upstream babashka.** Rejected for the extra
  unpack step in three installers and in `upgrade`; nothing else would ship
  in the archive.

## Consequences

- Each binary is about 90 MB instead of 300 KB. `upgrade` shows download
  progress when attached to a terminal and prints the size otherwise.
- The binary runs the appended jar's main class, so it is not a general `bb`.
  The orchestration-library question (how user scripts require aishell
  namespaces) can no longer assume a host babashka, but it can consider the
  embedded runtime evaluating user scripts through an aishell subcommand.
- `bb uberjar` follows `bb.edn`'s `:paths`, which include `test/`; the build
  passes an explicit `src` classpath.
