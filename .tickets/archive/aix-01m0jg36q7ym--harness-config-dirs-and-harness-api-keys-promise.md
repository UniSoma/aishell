---
id: aix-01m0jg36q7ym
title: Harness config mounts and API-key passthrough follow registry order, not map size
status: closed
type: task
priority: 3
mode: afk
created: '2026-08-21T15:47:37.319402952Z'
updated: '2026-11-26T18:04:30.381898876Z'
closed: '2026-11-26T14:47:03.720193425Z'
tags:
- registry
- settled
acceptance:
- title: With nine or more enabled harnesses in the registry, the harness config -v mount args come out in registry order (test rebinds harness/registry)
  done: true
- title: With nine or more enabled harnesses in the registry, the harness API-key -e args come out in registry order (test rebinds harness/registry and run/host-env)
  done: true
- title: The docstrings of harness-config-dirs and harness-api-keys no longer promise display order
  done: true
- title: bb pre-commit passes
  done: true
---

## Description

The harness config mounts (`-v`) and the harness API-key passthrough (`-e`) that `docker run` gets come out in harness registry order, however many harnesses the registry holds. Today both follow the iteration order of `harness-config-dirs` and `harness-api-keys` in `aishell.docker.run`, which are built with `(into {} ...)` and promise display order in their docstrings. That holds only while each map stays an array-map: a ninth entry turns it into a hash-map and the argv reorders without warning.

Not a live bug: the registry holds seven harnesses today (omp joined after this ticket was filed), so two more flip it.

Found by the verification agent during the harness registry migration (aix-01m0dp97mdhs).

### Decisions

- `build-harness-config-mounts` and `build-api-env-args` walk `harness/registry` at call time, filtering on each descriptor's `:state-key` in the setup state and taking `:config-paths` (their `:path`) or `:env-passthrough`, then `distinct`, the way `build-harness-runtime-env-args` already does. Their order then comes from the registry vector, not from any map's size. The Claude project-isolation removal of `[".claude"]` and the `host-env` and `fs/exists?` filters stay as they are.
- `harness-config-dirs` and `harness-api-keys` stay maps, for lookup only: `info.clj` filters `harness-config-dirs`, sorts it by state key and calls `some` on it, none of which depends on map order, and the run tests use `get` and compare whole tables. Their docstrings drop the display-order promise.
- Rejected: turning the two vars into vectors of pairs (every `val` caller and the `get` lookups would change) and sorting explicitly (the registry vector already is the order; a sort key would restate it).
- Tests rebind `harness/registry` with `with-redefs` to nine or more synthetic descriptors, all enabled, and check the argv order. Because the builders read the registry at call time, the rebinding reaches them. The config-mount test makes each synthetic directory under a temp home bound through `util/get-home`, as the existing run tests do; the env test binds `run/host-env`.
- The "iteration order is preserved for mount and info output" block of `harness-config-dirs-table` goes: the argv-order tests replace it. The table equality checks stay.
- No glossary or ADR change: CONTEXT.md already calls the Harness registry ordered.

## Notes

**2026-11-26T14:45:31.121784035Z**

Proposed amendment (not applied, Decisions left as written): the second Decisions bullet's rationale is slightly off. info.clj filters harness-config-dirs, sorts it by state key and calls some on it; after this landing the run tests only use get and compare whole tables (they no longer call keys). The decision itself is unchanged.

**2026-11-26T14:47:03.720193425Z**

build-harness-config-mounts and build-api-env-args now walk harness/registry at call time (through a shared enabled-harnesses filter), so -v and -e args follow registry order whatever the map size; the two lookup maps' docstrings drop the display-order promise; argv-order tests over ten synthetic harnesses replace the keys-order check.
