# Support XDG tmux config path (~/.config/tmux/tmux.conf)

**Source:** UAT Phase 39, Test 6 user feedback
**Priority:** Low
**Phase:** Future (could fit in Phase 41 or 43)

## Context

Starting with tmux 3.1, `~/.config/tmux/tmux.conf` works as an alternative to `~/.tmux.conf`. The current implementation only checks `~/.tmux.conf`.

## Proposal

Check `~/.config/tmux/tmux.conf` first (XDG convention), fall back to `~/.tmux.conf`. This matches tmux's own resolution order in 3.1+.

## Affected files

- `src/aishell/docker/run.clj` — `build-tmux-config-mount` function
