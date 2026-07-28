# Document and data tooling in the foundation image

We add PDF extraction (`poppler-utils`, `poppler-data`) and a small set of
file/data plumbing utilities (`libxml2-utils`, `moreutils`, `zip`, `zstd`,
`xz-utils`) to the foundation image, and in doing so fix the state of `git`'s
dropped Recommends (`openssh-client`, `patch`). Agents repeatedly encounter PDFs
and semi-structured files across projects; these are generic capabilities, not
project-specific ones.

The more durable output of this decision is the admission test below, which
exists so the next "should X go in the foundation image?" question is answered
rather than re-derived.

## Admission test

A tool belongs in the foundation image when all of these hold:

1. **Generic agent capability, not project- or language-specific.** Anything
   tied to one stack belongs in the per-project `.aishell/Dockerfile` overlay.
2. **The per-session cost is real.** Agents *can* self-install — passwordless
   sudo is granted in the entrypoint and no network restrictions are applied —
   but the container is `--rm`, so an install costs ~20-30s of `apt-get update`
   **every session, forever**, and silently requires egress. Baking converts a
   recurring cost into a one-time one.
3. **Installable via apt.** An apt package is a one-line change:
   `aishell.info/parse-packages` scrapes the list straight out of the Dockerfile,
   so `aishell info --foundation` picks it up with no wiring. The curl/tarball
   path (`cue`, `uv`, `gosu`, `gitleaks`) costs an `ARG`, an arch `case`, a
   `parse-*` fn, `info` rendering, and version-bump maintenance forever.
4. **Marginal size under roughly 20 MB**, measured against what the image already
   pulls in — not the package's headline size.
5. **No baked language interpreter.** ADR 0002 settled this for Python; it
   generalizes.

## Considered Options

- **Placement: foundation, not overlay or ARG-gate.** The per-project
  `.aishell/Dockerfile` overlay already supports `RUN apt-get install -y
  poppler-utils`, and `gitleaks` sets the precedent for an ARG-gated opt-in. We
  chose unconditional foundation because these are the same shape as the
  `jq`/`sqlite3`/`ripgrep`/`file` tools already baked in: generic capabilities an
  agent cannot usefully install on demand. ARG-gating was rejected outright —
  a ~40 MB saving does not justify forking the image into variants that the
  content hash, cache logic, and `info` output all have to reason about.

- **Cost is smaller than it looks.** `openjdk-17-jre-headless` already pulls
  freetype, fontconfig, lcms2, libjpeg and nss, so poppler's marginal cost is
  libpoppler126, the cairo/X11 chain, libtiff, openjp2 and libpng — roughly
  14 MB rather than the ~30 MB a fresh closure would cost. Total for this change
  is ~42 MB installed, ~12 MB downloaded.

- **`poppler-data` included, deliberately.** It is a `libpoppler126` *Recommends*
  and so dropped by `--no-install-recommends`. At 13 MB it is nearly as large as
  the rest of the poppler closure, and it only matters for CJK PDFs that do not
  embed their fonts — a narrow intersection with a legible failure mode (garbled
  or empty text, not silent-wrong). We included it anyway: the alternative is
  rediscovering the gap later from mangled output and paying a full foundation
  rebuild to fix it.

- **Single apt layer, at a real cost.** `parse-packages` matches the *first*
  `apt-get install ... && rm` block only, so a second install layer would be
  silently absent from `aishell info --foundation`. Appending to the existing
  layer invalidates every layer after it — the node copy, babashka, bbin's Maven
  pre-warm, gosu, CUE, uv — making the rebuild a near-full one. We took that over
  splitting the list (a permanent drift trap, where contributors would not know
  which block to extend) and over teaching the regex about multiple blocks
  (fixing a parser to accommodate a worse Dockerfile). The constraint is now
  commented in the template so it is not silently undone.

- **Rejected, with measurements.** `pandoc` is 168 MB installed — twelve times
  this entire change — and belongs in a project overlay. `yq` in bookworm is the
  *Python* wrapper (3.1.0, needs python3 + PyYAML), which violates the
  no-interpreter rule; and babashka already bundles `clj-yaml`, so `bb` reads
  YAML today. `imagemagick`, `ffmpeg`, `ghostscript`, `libreoffice` and
  `build-essential` are heavy and domain-specific. `tesseract-ocr` (~18 MB) is
  the natural completion of poppler — `pdftoppm` rasterizes a scanned page,
  tesseract OCRs it — but its value depends on whether the PDFs encountered are
  scanned or born-digital, a fact not yet established. It is deferred rather
  than rejected.

## Consequences

- Users get one near-full foundation rebuild on next run, automatic via the
  `foundation-content` hash, consistent with every other change to the template.
- **Nothing advertises these tools inside the container.** The only in-container
  advertising mechanism — the entrypoint-generated `~/.bash_aliases` — is
  exclusively for harnesses, and no library or CLI tool has ever been surfaced to
  agents this way. Discovery is by attempt, or by a project's own `CLAUDE.md`.
  This bites `pdftotext` hardest: Claude Code's `Read` tool handles PDFs
  natively, so an agent will reach for `Read` and never think to try
  `pdftotext -layout` on the table-heavy document where it would win. If these
  tools go unused for that reason, the deliberate follow-up is a build-time tool
  manifest (e.g. `/etc/aishell/tools.md`) for projects to reference — reopening
  the "no in-container advertising" decision on purpose, and for all the
  foundation's tooling rather than just these additions.
- `openssh-client` is inert without a `mounts: ~/.ssh` opt-in, documented in
  `docs/CONFIGURATION.md` along with its key-exposure trade-off.
- The admission test above should be applied to, and updated by, the next
  foundation addition.
