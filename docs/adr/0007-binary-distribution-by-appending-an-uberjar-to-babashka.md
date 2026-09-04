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
- **Release assets are versionless, platform-suffixed archives**
  (`aishell-linux-amd64.tar.gz`, `aishell-windows-amd64.zip`, ...), each
  holding one file named `aishell` or `aishell.exe`, plus one `SHA256SUMS`
  over the archives. Versionless so `releases/latest/download/<name>` keeps
  working. See the amendment below for why archived rather than raw.
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
  as an open ticket. **(Amended: on Apple Silicon the signature is
  an execution requirement, not only a Gatekeeper prompt. See the second
  amendment below.)**

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

- Each binary is about 90 MB instead of 300 KB, and 20 to 30 MB once
  archived. `upgrade` shows download progress when attached to a terminal
  and prints the size otherwise.
- The binary runs the appended jar's main class, so it is not a general `bb`.
  The orchestration-library question (how user scripts require aishell
  namespaces) can no longer assume a host babashka, but it can consider the
  embedded runtime evaluating user scripts through an aishell subcommand.
- `bb uberjar` follows `bb.edn`'s `:paths`, which include `test/`; the build
  passes an explicit `src` classpath.

## Amendment: `find-aishell-path` still strips a trailing `.bat`

The decision above says the function stops stripping `.bat`. It does not, on
purpose. Before migration, `fs/which "aishell"` on Windows resolves the
`aishell.bat` shim, which is the only part of a v4.0.0 install that sits on
PATH. Stripping the suffix gives the script beside it, and that script is what
the plan reads the leading bytes of to tell a script install from a binary one.
The ADR's point still holds. The plan derives the destination from the
install *directory*, never from the resolved file name, so
`aishell.exe` is written to the right place whichever of the two `fs/which`
returns.

## Amendment: appending to the macOS arm64 binary lands past its code signature

"No code signing yet" reads as a question about Gatekeeper prompts. On Apple
Silicon it is a harder constraint: arm64 macOS refuses to execute a Mach-O
whose signature does not verify, and upstream's `macos-aarch64` babashka is
signed. Appending the jar writes bytes past the `LC_CODE_SIGNATURE` load
command, which can invalidate that signature, in which case
`aishell-macos-aarch64` is killed on start rather than shown a prompt a user
can dismiss. Upstream's binary carries an `LC_CODE_SIGNATURE` that ends
exactly at the file's end, and the jar adds about 300 KB after it. The other
four targets do not carry this constraint. Whether trailing data past
the signature superblob actually invalidates it is decided on real hardware
and is open, tracked in aix-01m1mjm1vjd8; the smoke matrix's `macos-latest`
leg is arm64, so the first dry run answers it. If it does break, re-signing
ad hoc with `codesign -f -s -` costs a macOS runner and with it the
single-runner build decision above, while `rcodesign` re-signs on Linux and
keeps it.

## Amendment: the assets are archives, not raw binaries

The decision above chose raw binaries so that install and upgrade would be a
single fetch with no unpack step. Measured on the 4.1.0 build, the native
image compresses to about a third of its size (72 MB to 26 MB for
`linux-amd64` with gzip), while the appended jar is already deflated and
contributes nothing either way. Three times the download for one saved
`tar` invocation was the wrong trade, so the assets are `tar.gz` for Linux
and macOS and `zip` for Windows, each holding one file under its bare name.

What it costs: `install.sh` and `aishell upgrade` on Linux and macOS depend
on the system `tar`, which every distribution and macOS ships;
`install.bat` depends on the `tar` Windows has carried since 10 1803, the
same release that brought `curl`; `install.ps1` uses `Expand-Archive`, and
`aishell upgrade` on Windows reads the zip in-process through `babashka.fs`,
so the CLI itself needs no external tool there. `SHA256SUMS` hashes the
archives, so verification still happens before anything is written or
unpacked, and the staged-then-rename install is unchanged: the archive is
unpacked beside the download and the binary inside is what moves onto
PATH. The 4.1.0 legacy trio is unaffected; a v4.0.0 `upgrade` never sees
the archives.
