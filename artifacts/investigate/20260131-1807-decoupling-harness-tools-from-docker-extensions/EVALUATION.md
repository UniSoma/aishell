# Investigation Quality Evaluation

**Session:** 20260131-1807-decoupling-harness-tools-from-docker-extensions
**Evaluated:** 2026-01-31
**Topic:** Decoupling harness tool installation from heavy per-project Docker extensions
**Iteration:** 1

## Verdict: ACCEPT

High-quality investigation with excellent groundedness, comprehensive coverage, and strong cross-perspective synthesis. All four perspectives converge on a clear architectural recommendation (Hybrid Foundation + Volume-Mounted Harness) with detailed implementation guidance. Minor gaps exist in empirical validation and volume management strategy, but these are well-documented limitations rather than critical research deficiencies.

## Quality Scores

| Dimension | Score | Assessment |
|-----------|-------|------------|
| Groundedness | 1.0 | PASS |
| Coverage | 1.0 | PASS |
| Synthesis Quality | 1.0 | PASS |

**Aggregate:** 1.0/1.0

## Dimensional Analysis

### Groundedness: PASS (1.0/1.0)

**Reasoning:**

The report demonstrates exceptional source quality and citation practices. Major technical claims are consistently supported by Tier 1 (official Docker documentation, BuildKit specifications, codebase analysis) and Tier 2 (expert technical blogs, community best practices) sources.

**Examples of well-grounded claims:**

1. **COPY --link behavior**: "COPY --link flag (Dockerfile 1.4+, BuildKit 0.10+) creates truly decoupled layers that bypass cache invalidation" — directly cited from [BuildKit Dockerfile Syntax Specification](https://github.com/moby/buildkit/blob/dockerfile/1.4.3/frontend/dockerfile/docs/syntax.md) and [Docker Dockerfile Reference](https://docs.docker.com/reference/dockerfile/#copy---link).

2. **npm global package structure**: "Global npm packages install to {prefix}/lib/node_modules (binaries in {prefix}/bin)" — cited from official [npm Folders Documentation](https://docs.npmjs.com/cli/v10/configuring-npm/folders/).

3. **Docker volume auto-population**: "When a named volume is mounted to a directory containing files in the image, Docker copies the image directory's content into the volume on first run" — cited from official [Docker Volumes Documentation](https://docs.docker.com/engine/storage/volumes/).

4. **Cache invalidation logic**: "Extension rebuild logic (extension.clj:91-95) uses base-image-id-label to detect when extensions need rebuilding" — directly references specific codebase lines from `/home/jonasrodrigues/projects/harness/src/aishell/docker/extension.clj`.

**Confidence language appropriateness:**

- High-confidence claims (Tier 1 sources): "According to the official documentation...", "BuildKit's specification states..."
- Community patterns (Tier 2): "Widely recommended approach...", "Common practice is..."
- Estimates: "Estimated 2-3 days", "1-3 seconds startup overhead" — appropriately hedged with context
- Gaps acknowledged: "Not empirically tested against debian:bookworm-slim", "Performance benchmarks are estimated rather than measured"

**Issues:**

None identified. The investigation maintains appropriate confidence levels throughout, with 95%+ of major claims properly cited.

---

### Coverage: PASS (1.0/1.0)

**Coverage Map:**

Expected coverage areas derived from core question: "Which approach best balances query performance [Docker layer mechanics], scalability [volume/layer management], cost [rebuild time/storage], and developer experience [migration path]?"

- **Docker layer mechanics and BuildKit features**: Addressed (Docker Layer Mechanics perspective, 23 key findings)
- **Volume-based injection patterns and runtime overhead**: Addressed (Volume-Based Injection perspective, 6 patterns analyzed)
- **Architectural models for layer inversion**: Addressed (Layer Inversion Architecture perspective, 3-tier model detailed)
- **Current codebase constraints and migration complexity**: Addressed (Current Implementation perspective, 8 files analyzed)
- **Performance implications (build time, storage, runtime)**: Addressed (all perspectives contribute benchmarks and analysis)
- **Migration path and backward compatibility**: Addressed (Layer Inversion and Current Implementation provide detailed migration strategies)
- **Cross-perspective synthesis and recommendation**: Addressed (REPORT.md provides clear architectural choice with rationale)

**Reasoning:**

The investigation achieved comprehensive coverage of all expected technical dimensions. Each perspective was scoped to a distinct angle (layer mechanics, volume patterns, architectural models, codebase constraints), creating minimal overlap while ensuring completeness. The four perspectives collectively address:

1. **Technical feasibility** (Docker Layer Mechanics confirms COPY --link, multi-stage builds, cache mounts)
2. **Runtime injection mechanics** (Volume-Based Injection validates npm path handling, native binary dependencies, entrypoint patterns)
3. **Architectural options** (Layer Inversion Architecture evaluates 3-tier model with ARG-based FROM, state management refactoring)
4. **Implementation reality** (Current Implementation analyzes existing code patterns, estimates LOC changes, identifies codebase coupling)

**Gaps acknowledged:**

The report explicitly documents limitations in the "Gaps & Limitations" section:
- npm global package permissions with volume mounting (needs validation with aishell's gosu-based entrypoint)
- Volume versioning and cleanup strategy (no automated Docker tooling)
- BuildKit cache mount persistence across CI environments (known GitHub issue moby/buildkit#1673)
- Entrypoint script PATH manipulation performance (estimated 1-3s, not benchmarked for aishell specifically)
- Native binary shared library compatibility matrix (theoretically sound, not empirically tested for gitleaks/babashka/opencode)

These gaps are inherent to the research scope (architecture design phase, not implementation validation) and are well-documented for future work.

---

### Synthesis Quality: PASS (1.0/1.0)

**Reasoning:**

The REPORT.md demonstrates strong cross-perspective integration with thematic organization, tension identification, and multi-perspective recommendations.

**Thematic organization (not per-perspective summaries):**

The report is organized by architectural concerns rather than perspective order:
- "Foundation Layer Architecture" synthesizes insights from Docker Layer Mechanics (multi-stage builds), Layer Inversion (3-tier model), and Current Implementation (foundation-base split complexity)
- "Harness Tool Delivery Mechanisms" compares three patterns (layer inversion, volume mounting, hybrid) using evidence from all four perspectives
- "Cache Invalidation and State Management" integrates Docker Layer Mechanics (BuildKit dependency graph), Layer Inversion (state schema), and Current Implementation (base-image-id-label coupling)

**Tension identification and resolution:**

The report explicitly documents four tensions with context and resolution paths:

1. **Layer composition complexity vs. runtime flexibility**: COPY --link enables remote rebase (Docker Layer Mechanics) but breaks aishell's build-then-run model (Layer Inversion). Resolution: Use volumes for local development, reserve image rebase for future CI scenarios.

2. **Static vs. dynamic binary linking**: Volume-Based Injection recommends static linking for cross-distribution compatibility, but Current Implementation notes aishell uses debian:bookworm-slim consistently. Resolution: Use static binaries for external tools (gitleaks), dynamic linking for built-from-source tools.

3. **Semantic clarity vs. backward compatibility**: Layer Inversion proposes renaming aishell:base to aishell:foundation, Current Implementation flags this breaks user Dockerfiles. Resolution: Introduce aishell:foundation as primary tag, alias aishell:base during 6-12 month deprecation period.

4. **Build-time vs. runtime tool availability**: Layer Inversion identifies edge case where user extensions might reference harness binaries during build (RUN claude --version). Resolution: Scan user Dockerfiles for harness binary references, provide clear error message with mitigation guidance.

**Multi-perspective recommendations:**

All six recommendations draw evidence from multiple perspectives:

1. "Implement Hybrid Foundation + Volume-Mounted Harness" cites Layer Inversion (3-tier model), Volume-Based Injection (volume mounting patterns), Current Implementation (4-5 files, ~350 lines complexity estimate), Docker Layer Mechanics (COPY --link as future optimization)

2. "Use static linking for externally sourced native binaries" cites Volume-Based Injection (shared library challenges), Docker Layer Mechanics (cross-distribution concerns), Current Implementation (gitleaks/babashka installation paths)

3. "Implement automated volume cleanup" addresses gap identified by Volume-Based Injection (volume versioning), provides implementation path using Docker volume labels

4. "Add extension Dockerfile validation for harness binary references" addresses edge case from Layer Inversion, provides implementation guidance from Current Implementation (extension.clj parsing)

5. "Measure and document actual performance characteristics" acknowledges estimates across all perspectives (foundation build time, harness volume build time, container startup overhead)

6. "Reserve COPY --link for future multi-user scenarios" synthesizes Docker Layer Mechanics (image rebase capability) with Current Implementation (local development workflow), documents pattern for future reference

**Strengths:**

- Clear executive summary with main finding ("Hybrid Foundation + Volume-Mounted Harness") and key insight (volume-based injection is architecturally consistent with existing run.clj patterns)
- Detailed analysis sections with inline citations showing which perspectives support each claim
- Explicit trade-off discussions with context about when each concern applies
- Actionable recommendations with phased implementation (Phase 1-3) and concrete file/line estimates

**Issues:**

None identified. The synthesis demonstrates strong integration across all four perspectives.

## Recommendations

**Priority improvements:**

1. **Empirical validation phase**: Before finalizing the Hybrid approach, implement a prototype of the foundation image split and harness volume mounting in a test branch. Measure actual build times (foundation, harness volume, extension) and container startup overhead with aishell's specific entrypoint script (gosu-based user creation + tmux initialization). This addresses the gap between estimated benchmarks and real-world performance.

2. **Volume management tooling**: Implement the recommended `aishell doctor prune-volumes` command as part of the initial harness volume rollout (not deferred). Volume cleanup should be a first-class feature, not an afterthought, to prevent disk space issues as users experiment with different harness combinations.

**Strengths to maintain:**

- Exceptional source quality with consistent Tier 1-2 citations for technical claims
- Clear documentation of gaps and limitations (builds trust, enables informed decision-making)
- Multi-perspective convergence on a single architectural recommendation (avoids analysis paralysis)
- Detailed implementation guidance with specific file changes and LOC estimates (accelerates execution)
- Explicit tension documentation with resolution paths (prevents future architectural drift)

## Evaluation Metadata

- **Dimensions evaluated:** Groundedness, Coverage, Synthesis Quality
- **Scoring method:** Ternary (PASS=1.0 / PARTIAL=0.5 / FAIL=0.0)
- **Reasoning approach:** Chain-of-thought with specific claim-source examples
- **Perspectives evaluated:** 4 available of 4 total (100% completion)
- **Source distribution:** Tier 1 (18 sources), Tier 2 (15 sources), Tier 3 (11 sources) — appropriate mix with strong authoritative grounding
