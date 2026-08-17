# Advanced operations

Read only the section the task needs. Every write here closes with the loop in `SKILL.md`.

## Map a repo, or a tree of repos

```bash
clj-surgeon :op :ls-tree :dir .                              # this project's API surface
clj-surgeon :op :ls-tree :dir ~/projects :grep "postgres|jdbc"
clj-surgeon :op :ls-tree :dir . :format :edn
```

Discovers projects via `deps.edn` / `project.clj` / `bb.edn`, reads their `:paths`, and
outlines every `.clj/.cljs/.cljc` file: ns names, requires, form signatures. `:grep` (a
regex, via ripgrep) picks candidate files before parsing, which is what makes a cross-repo
sweep cheap. It answers "which repo does X?" in one command — the job you would otherwise
give an Explore agent over several directories.

## Dependencies and extraction

Inspect before extracting:

```bash
clj-surgeon :op :deps        :file src/state.clj :form sync-draft!   # intra-ns call graph
clj-surgeon :op :ls-deps     :file src/state.clj :form transition!   # transitive tree
clj-surgeon :op :ls-extract  :file src/state.clj :form rebuild!      # form + exclusive helpers
clj-surgeon :op :declares    :file src/state.clj
clj-surgeon :op :topo        :file src/state.clj                     # optimal form ordering
```

`:ls-extract` names the minimal extractable unit; the architecture call on top of it is
yours. Preview the extraction, then execute it:

```bash
clj-surgeon :op :extract  :file src/state.clj :forms '[distill refine helper]' :to src/state/distillery.clj
clj-surgeon :op :extract! :file src/state.clj :forms '[distill refine helper]' :to src/state/distillery.clj
```

`:extract!` creates the new namespace with forms in topological order, copies the source
`(ns …)` as a template (over-including requires, which is safe), removes the forms from the
source, and reports callers that may need updating.

**Its `{:action :add-require …}` log entry is not trustworthy in 0.1.0 — fix the source `ns`
by hand after every `:extract!`.** Two observed failures: with an existing `(:require …)` the
alias is spliced in as a sibling of that list rather than inside it, producing an ns form
Clojure rejects (`clj-kondo`: *Unknown ns option*); with no `(:require …)` at all the entry
is logged and nothing is written. Every extracted call site is also left unqualified. So:
read the source `ns` form, place the require correctly yourself, qualify the bare references
the compiler finds (or pass them as parameters), and keep the dependency acyclic. The
`clj-kondo` and reload steps in `SKILL.md` are what catch this — do not skip them here.

## Declares

When a namespace holds or gains a `(declare …)`, inspect, then apply:

```bash
clj-surgeon :op :fix-declares  :file src/state.clj
clj-surgeon :op :fix-declares! :file src/state.clj
```

It moves defns above their callers, pulling leaf deps along, deletes the stale declares, and
skips unsafe moves with a warning rather than guessing.

## Move a form

`:mv` writes unless `:dry-run true`, so begin with the preview:

```bash
clj-surgeon :op :mv :file src/my/ns.clj :form foo :before bar :dry-run true
```

- On `:ok true`, review `:plan`/`:diff`, then rerun the same command without `:dry-run`.
- On `:would-strand-dependencies`, run the returned `:recommended-command` — it previews
  `:mv-with-deps` (exactly `:mv :with-deps true`). Review `:requested-forms`, `:added-forms`,
  `:move-order`, and `:diff`, and consent to every added form before applying. The alias
  moves the dependency closure only; callers and declarations stay where they are.
- Any other refusal — `:would-strand-users`, a cycle, ambiguity — is the answer: pick a
  different move rather than a workaround.

A preview is not a hash-bound plan, so preview again after any source change. After writing,
rerun `:ls` and `:declares` to confirm the new order.

## Namespace rename

```bash
clj-surgeon :op :rename-ns  :from old.prefix :to new.prefix :root .
clj-surgeon :op :rename-ns! :from old.prefix :to new.prefix :root .
```

**This rewrites `ns` forms and requires only; it does not move files.** In 0.1.0 the plan
reports `:file-moves []` even when the prefix change implies a new directory, so the sources
stay on the old path and nothing loads. Move them yourself as the second half of the rename:

```bash
git mv src/old/prefix src/new/prefix     # mirror for test/ and any other :paths root
```

Then reload and run the tests — a missed path shows up as a file-not-found on require.

## CLJC

These preserve reader conditionals; hand-splicing them does not:

```bash
clj-surgeon :op :cljc-analyze     :clj src/foo.clj :cljs src/foo.cljs
clj-surgeon :op :cljc-merge       :clj src/foo.clj :cljs src/foo.cljs :out src/foo.cljc
clj-surgeon :op :cljc-split       :file src/foo.cljc :clj-out src/foo.clj :cljs-out src/foo.cljs
clj-surgeon :op :cljc-add-require :file src/foo.cljc :platform :cljs :ns goog.string :as gstr :out src/foo.cljc
```

`:cljc-analyze` returns `{:requires {:shared … :clj-only … :cljs-only … :divergent …}
:forms-clj […] :forms-cljs […]}` — plan a merge or a surgical edit from that instead of
reading both files.

`:cljc-merge` collapses one alias bound to different namespaces (`dom` → `fulcro.dom-server`
in CLJ, `fulcro.dom` in CLJS) into a single `#?@(:clj […] :cljs […])` splice, routes npm
string requires to `:cljs`, emits identical bodies once, and wraps differing bodies in
`#?(:clj … :cljs …)`. A throw on an ns docstring, attr-map, `:import`, ns-name mismatch, or
body-count mismatch means the source holds something it declines to rewrite silently: fix
that by hand and retry.

Round-trip a merge through `:cljc-split` before deleting the originals.
`:cljc-add-require` refuses an alias collision; npm requires take a string, `:ns "react"`.
