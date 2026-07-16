# uv (Python toolchain) in the foundation image

We add `uv` (and `uvx`) as a single pinned static binary to the foundation image, so Python tooling is available in every sandbox. `uv` is treated as universal tooling like `node`/`cue`, not as a language runtime.

## Considered Options

- **Placement.** Baked unconditionally into foundation (chosen) vs. ARG-gated like `gitleaks` vs. added in the per-project overlay like Clojure/JDK25. Foundation deliberately excludes heavy language runtimes, but `uv` is a ~35MB static binary with no runtime dependencies and pulls in no interpreter unless asked — closer to `node` than to the JVM stack. Unconditional wins; gating or overlaying adds config surface for marginal savings.

- **Scope: binary only, no Python.** We ship `uv`/`uvx` and no interpreter. `uv` fetches the interpreter a project pins (`pyproject.toml` / `.python-version`) on demand. This keeps foundation version-agnostic — mirroring how the Clojure layer ships tooling (`clojure`, `bbin`) but bakes no application classpath. Baking one Python would force a single version on every project, which we rejected.

- **Cache location: uv's HOME defaults, not `/usr/local/share`.** The Clojure caches (`m2`, `gitlibs`) are redirected to shared `/usr/local/share/...` paths so a build-time pre-warm survives `--rm`. We deliberately do *not* do this for `uv`. Containers are `--rm` with an ephemeral HOME, so the interpreter re-downloads **once per session** (then is cached for that session's lifetime) — a small, one-time cost. Redirecting uv's dirs without a build-time pre-warm would persist nothing under `--rm`; it is speculative machinery. Cross-session cache persistence is left to projects as a `mounts:` opt-in (e.g. mounting `~/.cache/uv`), documented rather than built in.

## Consequences

- The first `uv run` / `uv sync` in each new sandbox session incurs a one-time interpreter download.
- If cross-session Python caching later proves a common pain, the deliberate follow-up is to redirect uv's dirs to a shared `/usr/local/share/uv` path *and* pre-warm an interpreter at build time — reopening the "binary only" decision on purpose.
- `uv` is pinned via `ARG UV_VERSION`; bumping flows through the `foundation-content` hash and auto-triggers a rebuild, consistent with every other tool in the template.
