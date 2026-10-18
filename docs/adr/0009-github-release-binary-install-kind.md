# GitHub-release binary install kind for harnesses without an npm-runnable package

A Harness descriptor may declare `:install {:kind :github-release ...}`: the
volume install downloads a prebuilt binary from the Harness's GitHub releases
into `/tools/bin`, checks it against the release's own checksum file, and
fails the volume build on a mismatch. omp (oh-my-pi) is the first Harness to
use it.

## Context

Every volume-installed Harness before omp was an npm package installed with
`npm install -g` into `/tools/npm`. omp's npm package runs on Bun only, and
the Foundation image carries no Bun. omp also publishes standalone binaries
for linux x64 and arm64 (glibc and musl), plus a `SHA256SUMS.txt`, on every
GitHub release.

## Decision

- **Download the release binary into the Harness volume.** omp stays a volume
  participant, so pinning or refreshing its version rebuilds a volume, not an
  image. The descriptor holds the repo, the asset name template (`{arch}`), the
  binary name, the tag prefix and the checksum file name; nothing in the
  install code is specific to omp.
- **Pick the asset from `uname -m` inside the volume container.** `x86_64`
  maps to `x64` and `aarch64` to `arm64`, using the glibc build because the
  Distro image is Debian. Any other architecture fails the build with a clear
  error.
- **"latest" follows GitHub's `releases/latest/download/` redirect.**
  `aishell update` refreshes it, matching npm "latest".
- **Checksums come from the same release (same-origin).** This catches a
  corrupt or truncated download, not a tampered release.

## Considered options

- **Add Bun to the Foundation image and `bun install -g` into the volume.**
  Rejected: it puts a second JavaScript runtime in every user's image for one
  Harness, and the Foundation image is identical for everyone.
- **Bake the binary into the image, as with gitleaks.** Rejected: every omp
  version change would rebuild the image. omp releases about daily.
- **Pin per-version hashes inside aishell.** Rejected: it would take an aishell
  release for every omp release, and an unpinned "latest" could not be
  verified at all.
