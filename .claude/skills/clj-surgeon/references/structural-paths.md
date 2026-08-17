# Structural paths, X-ray, and transactions

For reads and edits aimed at something *nested* inside an owner. Arguments live in
`clj-surgeon :op <op> --help`; this file covers the parts that are easy to get wrong.

## Path primer

A path starts at `(form 'NAME)` or `(line N)`. Navigation skips whitespace and comments:

- `right` — next structural sibling.
- `left` — previous structural sibling.
- `up` — structural parent.
- `down` — first structural child.
- `(match :href) right` — selects the value paired with a map key.
- `span 2` — selects adjacent structural peers.
- `partition-all 2` — groups the remaining sibling run into pairs.
- `outermost` — keeps selected nodes with no selected ancestor. Use `up` before `outermost`.
- `initializer` — selects a `def` right-hand side without evaluating it.

A `case` clause, `cond` branch, map entry, or binding pair is **sibling syntax**, not a
wrapper list: target the value, and let the key or guard beside it be your selector. Use
`:up :outermost`, not `:outermost :up`, to promote nested matches to disjoint owners.

## Cross-file reads

One coherent snapshot, guarded by exact counts. Pipe the manifest in the same shell action
that runs the command:

```bash
printf '%s\n' '{:reads [{:file "src/a.clj" :forms [start stop]}
                        {:file "src/b.clj" :forms [route]}]
                :expect {:file-count 2 :form-count 3}}' |
  clj-surgeon :op :cat :spec-file - :format :semantic
```

Each file is read once and reports its snapshot hash. `:format :semantic` drops comments,
layout, **and metadata** for a compact behavior view — `(def ^:private timeout-ms 180000)`
prints as `(def timeout-ms 180000)`, so never judge visibility, type hints, or any other
`^meta` from semantic output. The default `:edn` is the exact lexical source contract — keep
it when comments, layout, metadata, or reader spelling matter.

## X-ray

Plain paths return exact source. End a literal path with `expect-count` when cardinality
matters. `analyze` receives one vector of ordinary Clojure data — always a vector, even for
zero or one match — and returns a compact `:value` plus hash evidence. It must return
concrete EDN: a lazy result refuses with `:invalid-xray-result`, so wrap `keys`, `map`,
`filter`, and friends in `vec` or use `mapv`. Write one terminating pure function over that
contract rather than a separate shape-discovery query; reach for `tree-seq` only when the
shape is genuinely unknown:

```bash
clj-surgeon :op :xray :file src/policy.clj \
  :expr "(-> (form 'audit-report) initializer (expect-count 1) (analyze (fn [[report]] (frequencies (:events report)))))"
```

The sandbox is capability-limited (no I/O, processes, interop) but not termination-proof, so
keep analysis bounded. X-ray is read-only: source and plans stay untouched.

## One guarded edit

`:expect` declares the exact before-state of a literal replacement and applies it in the same
call. Whitespace is ignored; comments, metadata, and reader syntax must match. When the
replacement is computed instead, `transform` runs pure Clojure over the selected syntax and
stores its concrete result in a plan — plan-only, since the generated after-state needs review:

```bash
clj-surgeon :op :edit :file src/policy.clj \
  :expr "(-> (form 'retry-policy) (match :delays) right (transform #(mapv (partial + 100) %)))" \
  :plan-out plan.edn

clj-surgeon :op :replace-subform! :plan plan.edn
```

A literal replacement keeps its source spelling, `#()` included; a computed one prints
canonically. A successful plan atomically replaces its `:plan-out` artifact and a refusal
leaves the old one intact, so the path needs no preflight.

## One change transaction

When files, owners, targets, replacements, and counts are all known, submit one transaction
rather than a sequence of edits:

```bash
clj-surgeon :op :change! :spec-file - :receipt-out /tmp/api-change.edn <<'EDN'
{:changes [{:id :body
            :in ["src/ui.clj"]
            :forms [shell reader]
            :find ":body"
            :do [:replace ":body.page"]
            :expect {:matches 2 :each-form 1}}]
 :expect {:changes 1 :edits 2 :files 1}}
EDN
```

Every named owner must resolve exactly once. `:each-form` and `:each-file` guard the
distribution a bare total would hide. The supported scoped operator is literal
`[:replace SOURCE]`; legacy exact `:intents` still parse, but one document holds one schema.

`:change` compiles and previews the same spec without writing. `:change!` rechecks hashes,
commits every file, verifies read-back, and publishes an inverse receipt last. Pass that
receipt's path straight to `:undo-change!`, which works while every result hash is current:

```bash
clj-surgeon :op :undo-change! :receipt /tmp/api-change.edn
```
