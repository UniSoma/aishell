# Landing a ticket

What the `land` and `refine` skills read from this project. Empty slots (door-sweep project slots, class repeats, post-commit checks) are deliberately unfilled.

## Gate

`bb pre-commit` (bb.edn): clj-kondo over `src` and `test`, then every test. It reads the working tree, so run it on the staged tree right before `git commit`, and again after any edit, ticket files included.

## Focused tests

One namespace:

    bb -e "(require 'clojure.test '<ns>) (let [{:keys [fail error]} (clojure.test/run-tests '<ns>)] (System/exit (+ fail error)))"

`<ns>` follows the file path: `test/aishell/docker/run_test.clj` is `aishell.docker.run-test`.

## Ticket-only commit

A commit holding only ticket files skips the gate. Stage each ticket's files by id, which also stages a move into `.tickets/archive/`, then commit:

    git add -A -- ':(glob).tickets/**/<id>--*'
    git commit -m "<message>"

Name each ticket's paths and stage nothing else: other sessions share the tree and its index.

## Kept work

Drafts and set-aside patches go in `wip/land/` (git-ignored), named as the land skill names them. Nothing durable lives only there.

## Host-only checks

The sandbox has no Docker (see AGENTS.md). A step or criterion that runs a container or builds an image needs the host: refine records `host_needed_to_land: yes` and the ticket carries `host-needed`.
