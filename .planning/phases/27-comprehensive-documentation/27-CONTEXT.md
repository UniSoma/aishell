# Phase 27: Comprehensive Documentation - Context

**Gathered:** 2026-01-25
**Status:** Ready for planning

<domain>
## Phase Boundary

Create in-depth documentation covering architecture, configuration, all harnesses, troubleshooting, and development guide. This phase focuses on the transition from "internal/MVP" docs to a "production-ready" documentation suite in a dedicated `/docs` folder.

</domain>

<decisions>
## Implementation Decisions

### Documentation Structure
- **Location:** All new documentation lives in a dedicated `/docs/` folder in the project root.
- **Modularity:** Separate files for each major topic:
    - `docs/ARCHITECTURE.md`
    - `docs/CONFIGURATION.md`
    - `docs/HARNESSES.md`
    - `docs/TROUBLESHOOTING.md`
    - `docs/DEVELOPMENT.md`
- **Navigation:** Minimalist cross-linking (relying on GitHub UI/file tree), but with a clear table of contents/navigation section added to the main `README.md`.

### Target Audience & Tone
- **Primary Audience:** End-users running `aishell` for daily agentic tasks.
- **Knowledge Level:** Intermediate (Guided). Assume they know basic CLI concepts but provide context/links for prerequisites like Docker and API keys.
- **Tone:** Technical but approachable (Claude's discretion). Clear, direct instructions with helpful context for complex steps.
- **Troubleshooting Focus:** Usage-centric. Prioritize common CLI errors, credential mounting issues, and sandbox behavior.

### Content Depth & Style
- **Architecture:** High-level schematic focus. Use **Mermaid.js** diagrams to illustrate the Host -> Sandbox -> Harness data flow.
- **Configuration:** Provide a single, full annotated `config.yaml` example showing every available option in context.
- **Harness Guides:** Structured **Per-Harness** (Claude, OpenCode, Codex, Gemini).
    - **Setup Heavy:** Detailed instructions for API keys, OAuth, and GCP credentials (Vertex AI).
    - **Nuance-focused:** Explicitly document behavioral differences between harnesses (e.g., how they handle file paths or interactive prompts).
    - **Advanced Examples:** Include prompt examples and advanced integration scenarios like mounting local volumes or custom tools.

### Claude's Discretion
- **Writing Tone:** The specific balance of "concise vs educational" is left to the builder.
- **Opinionated vs Reference:** The documentation should lean towards "Opinionated" (best-practice paths) while still serving as a comprehensive reference for all flags/options.
- **Visuals:** Exact layout of Mermaid diagrams and formatting choices for tables/code blocks.

</decisions>

<specifics>
## Specific Ideas

- Ensure `HARNESSES.md` has a clear comparison table if helpful for choosing which harness to use for a specific task.
- `CONFIGURATION.md` should clearly explain the "merge strategy" for nested keys if applicable.
- `DEVELOPMENT.md` should include a "How to add a new harness" checklist.

</specifics>

<deferred>
## Deferred Ideas

- None — discussion stayed within phase scope.

</deferred>

---

*Phase: 27-comprehensive-documentation*
*Context gathered: 2026-01-25*
