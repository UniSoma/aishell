# Domain Docs

How the engineering skills should consume this repo's domain documentation when exploring the codebase.

## Before exploring, read these

- **`CONTEXT.md`** at the repo root — project glossary and domain language
- **`docs/adr/`** — architectural decision records that touch the area you're about to work in

If either of these files doesn't exist yet, **proceed silently**. Don't flag their absence; don't suggest creating them upfront. The producer skill (`/grill-with-docs`) creates them lazily when terms or decisions actually get resolved.

## File structure (single-context)

```
/
├── CONTEXT.md
├── docs/adr/
│   ├── 0001-some-decision.md
│   └── 0002-another-decision.md
└── src/
```

## Use the glossary's vocabulary

When your output names a domain concept (in a ticket title, a refactor proposal, a hypothesis, a test name), use the term as defined in `CONTEXT.md`. Don't drift to synonyms the glossary explicitly avoids.

If the concept you need isn't in the glossary yet, that's a signal — either you're inventing language the project doesn't use (reconsider) or there's a real gap (note it for `/grill-with-docs`).

## Flag ADR conflicts

If your output contradicts an existing ADR, surface it explicitly rather than silently overriding:

> _Contradicts ADR-0007 (some-decision) — but worth reopening because…_
