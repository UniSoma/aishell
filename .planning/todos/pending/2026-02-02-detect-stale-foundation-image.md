# Detect Stale Foundation Image

**Source:** Phase 41 UAT — user had to manually rebuild with `--force` to pick up entrypoint changes

## Problem

When the foundation image (aishell:foundation) is outdated, `aishell build` does not warn the user. This caused Phase 41 entrypoint changes (plugin bridging, config injection, conditional startup) to silently not take effect until the user manually ran `aishell build --force`.

## Proposal

`aishell build` should detect when the foundation image predates the current tool volume or code changes and either:
1. Warn the user that the foundation image may be stale
2. Auto-rebuild the foundation image when source changes are detected

## Priority

Minor — workaround exists (`--force` flag). But it causes confusing debugging sessions when new features don't appear.
