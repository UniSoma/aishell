---
name: clj-surgeon
description: Structural reads and edits on existing Clojure/ClojureScript/CLJC source via the `clj-surgeon` CLI — reach for it instead of Read, Edit, grep, sed, or cat on a `.clj`/`.cljs`/`.cljc` file, and instead of an Explore agent over Clojure code. Use when locating or reading a form and its callers, changing a nested value across forms or files, splitting a file or extracting forms into a new namespace, eliminating a declare, reordering defns, renaming a namespace prefix, merging or splitting CLJC, or mapping the API surface of a repo or a tree of repos.
---

# clj-surgeon

The CLI is on `PATH` as `clj-surgeon`; every op is `clj-surgeon :op <op> [named args]`.
`clj-surgeon :op <op> --help` is the authoritative contract — arguments, safety workflow,
worked examples. Read it before guessing an argument name.

Two words carry this tool. An **owner** is the top-level form containing your target; ops
select by owner, and reads that name one resolve fastest. A **refusal** is a guard rejecting
a whole request — ambiguous selector, count mismatch, stale hash, overlap, parse error.
Refusals are the safety model: nothing partial is ever read or written. Treat one as
information about your selector, and narrow it.

Upstream docs and `--help` route through an MCP entrance (`inspect_clojure`,
`apply_clojure_changes`). Use it when this session lists those tools; otherwise only the CLI
is installed and every instruction below is the CLI route.

## Route a read

| You know | Use |
|---|---|
| An owner name — one, or several in a file | `:cat :form NAME` / `:cat :forms '[a b]'` |
| Owner names across several files | `printf '%s\n' 'MANIFEST' \| clj-surgeon :op :cat :spec-file -` |
| A line number, or distinctive literal text | `:cat :line N` / `:cat :contains "text"` |
| A nested shape, but no owner | `:match-form :match '(post! "/api/items" _)'` |
| A question about nested data, not its text | `:xray :expr "…(analyze f)"` |
| Nothing about the file | `:ls :file F` — outline, then `:cat` the owners it names |
| Nothing about the repo | `:ls-tree :dir . :grep "pattern"` |

```bash
clj-surgeon :op :cat :file src/my/ns.clj :form transition!
clj-surgeon :op :cat :file src/my/ns.clj :contains :finish   # returns the enclosing owner
clj-surgeon :op :ls-tree :dir ~/projects :grep "postgres|jdbc"
```

`:cat` is the first source read whenever an owner name or distinctive text is known — it
replaces a reconstructed `sed` range, and it never dumps a whole file. Save `:ls` for a file
you know nothing about. `:match` takes a Clojure form pattern, not a regex: `_` matches
exactly one subtree, and arity is exact, so a two-argument loop is `(loop _ _)`.

## Route a write

| Situation | Use |
|---|---|
| One nested edit, exact before-state known | `:edit … :expect '<before>'` — verifies and applies in one call |
| One nested edit, replacement computed from the source | `:edit … :plan-out plan.edn`, review the diff, then `:replace-subform! :plan plan.edn` |
| Several exact changes across owners or files | one `:change! :spec-file -` transaction |
| A whole top-level form | native Edit |
| A new file | native Write |
| Non-Clojure, prose, comments, top-level insertion | native Write/Edit |

```bash
clj-surgeon :op :edit :file src/state.clj \
  :expr "(-> (form 'transition) (match :finish) right (replace '(assoc state :status :complete)))" \
  :expect '(assoc state :status :done)'
```

- Plan and apply are separate shell actions: generate the plan, read the returned diff, then
  apply it with `:replace-subform!`. To change a plan, generate a new one.
- The returned diff and hashes are the review evidence — act on them directly. A verified
  write is settled; the next command moves on to formatting.
- `:expect` means two different things. On `:edit` it is the literal before-state form above.
  In a `:change`/`:change!` spec it is a count map — declare the counts the task already
  fixes (`:expect {:matches 2 :each-form 1}`), and a wrong count buys you a refusal instead
  of a silent half-edit.
- `:mv` writes unless `:dry-run true` — preview first.

Structural path DSL, `:xray`, cross-file manifests, transaction specs:
[structural paths](references/structural-paths.md). Extraction, declares, moves, renames,
CLJC, cross-repo mapping: [advanced operations](references/advanced-operations.md).

## Close every write

clj-surgeon writes behind Bash, so the `Write|Edit` formatting hook never sees them. A
structural write is done when all four hold:

1. `clj-paren-repair <files>` — formats via cljfmt.
2. `clj-kondo --lint <files>` — clean.
3. `clj-nrepl-eval -p 7888 "(require 'my.ns :reload)"` — reloads.
4. The project's tests pass.

`:extract!`, `:fix-declares!`, `:mv`, and `:rename-ns!` move code across owners, files, and
namespaces, where the compiler is what catches a stranded reference. Reload and test those
every time. `:extract!` and `:rename-ns!` each leave known manual work behind in 0.1.0 — see
[advanced operations](references/advanced-operations.md) before running either.
