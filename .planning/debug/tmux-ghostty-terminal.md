---
status: diagnosed
trigger: "tmux inside Docker containers fails with 'missing or unsuitable terminal: xterm-ghostty'"
created: 2026-01-31T00:00:00Z
updated: 2026-01-31T00:06:30Z
symptoms_prefilled: true
goal: find_root_cause_only
---

## Current Focus

hypothesis: CONFIRMED - Container inherits $TERM from host (xterm-ghostty) but doesn't have terminfo entry
test: Verified docker run args and entrypoint template
expecting: Entrypoint doesn't sanitize TERM before running tmux
next_action: Return diagnosis to caller

## Symptoms

expected: tmux should start successfully inside Docker containers
actual: tmux fails with "missing or unsuitable terminal: xterm-ghostty"
errors: "missing or unsuitable terminal: xterm-ghostty"
reproduction: Run container from Ghostty terminal with tmux auto-start enabled
started: After Phase 32 added tmux auto-start for all container modes

## Eliminated

## Evidence

- timestamp: 2026-01-31T00:05:00Z
  checked: src/aishell/docker/templates.clj lines 229-234
  found: Entrypoint executes `tmux new-session -A -s main -c "$PWD" "$@"` without any TERM sanitization
  implication: tmux tries to use whatever TERM value was passed through from host

- timestamp: 2026-01-31T00:05:30Z
  checked: src/aishell/docker/run.clj lines 201-203
  found: Docker run args explicitly pass through TERM: `-e" (str "TERM=" (or (System/getenv "TERM") "xterm-256color"))`
  implication: Host TERM value (xterm-ghostty) is passed directly to container; fallback only if unset

- timestamp: 2026-01-31T00:06:00Z
  checked: Debian base image terminfo database
  found: Standard Debian bookworm-slim doesn't include xterm-ghostty terminfo entry (custom terminal)
  implication: When tmux starts, it can't find xterm-ghostty in terminfo database and fails

## Resolution

root_cause: Container inherits $TERM=xterm-ghostty from host (line 202 of src/aishell/docker/run.clj) but the Debian container doesn't have the xterm-ghostty terminfo entry. When entrypoint.sh executes tmux (line 234 of src/aishell/docker/templates.clj), tmux validates the TERM value against /usr/share/terminfo and fails with "missing or unsuitable terminal: xterm-ghostty".

fix: The entrypoint.sh needs to check if the current $TERM terminfo entry exists before running tmux. If not found, fallback to xterm-256color (which is universally available). Add check before line 234: `command -v infocmp > /dev/null && ! infocmp "$TERM" >/dev/null 2>&1 && export TERM=xterm-256color`

verification: Run container from Ghostty terminal, tmux should start successfully with fallback TERM
files_changed: [src/aishell/docker/templates.clj]
