---
status: resolved
trigger: "Declared tmux plugins (e.g., tmux-plugins/tmux-sensible) are NOT being installed during `aishell build --with-tmux` or `aishell update`. Only the TPM directory (/tools/tmux/plugins/tpm) exists after build — the actual declared plugins are missing."
created: 2026-02-02T00:00:00Z
updated: 2026-02-02T00:02:00Z
resolved: 2026-02-02
goal: find_root_cause_only
---

## Current Focus

hypothesis: CONFIRMED - install_plugins expects plugin declarations in ~/.tmux.conf, but code writes them to /tmp/plugins.conf which install_plugins never reads
test: Confirmed via TPM documentation research
expecting: install_plugins script uses AWK to parse ~/.tmux.conf for "set -g @plugin" lines
next_action: Return root cause diagnosis

## Symptoms

expected: Both TPM and declared plugins (e.g., tmux-plugins/tmux-sensible) should be installed in /tools/tmux/plugins/
actual: Only TPM directory exists (/tools/tmux/plugins/tpm), declared plugins are missing
errors: None reported (silent failure)
reproduction: Run `aishell build --with-tmux` with plugins declared in .aishell/config.yaml
started: Current issue - TPM clones successfully but plugins don't install

## Eliminated

## Evidence

- timestamp: 2026-02-02T00:00:00Z
  checked: .aishell/config.yaml lines 173-175
  found: Plugins are declared correctly: tmux-plugins/tmux-sensible
  implication: Configuration is present and properly formatted

- timestamp: 2026-02-02T00:01:00Z
  checked: src/aishell/docker/volume.clj lines 196-211 (build-tpm-install-command)
  found: Function generates shell command that writes plugins to /tmp/plugins.conf (line 209) and then calls install_plugins with TMUX_PLUGIN_MANAGER_PATH=/tools/tmux/plugins (line 210)
  implication: The generated command IS calling install_plugins, but the plugin configuration location is wrong

- timestamp: 2026-02-02T00:02:00Z
  checked: TPM documentation and source code behavior
  found: TPM's install_plugins script reads plugin declarations by parsing ~/.tmux.conf (or ~/.config/tmux/tmux.conf) for lines matching "set -g @plugin 'plugin-name'". It uses AWK to extract these: `/^[ \t]*set(-option)? +-g +@plugin/`. The script does NOT read from arbitrary config files.
  implication: Writing plugin declarations to /tmp/plugins.conf is ineffective - install_plugins never reads that file

## Resolution

root_cause: build-tpm-install-command (line 209 of src/aishell/docker/volume.clj) writes plugin declarations to /tmp/plugins.conf, but TPM's install_plugins script only reads from ~/.tmux.conf (or ~/.config/tmux/tmux.conf). The install_plugins script uses AWK to parse the tmux configuration file for "set -g @plugin" declarations, so writing to /tmp/plugins.conf has no effect - the plugins are never discovered or installed.

fix: Change line 209 to write plugin declarations to ~/.tmux.conf instead of /tmp/plugins.conf. The correct path should be either /root/.tmux.conf or /root/.config/tmux/tmux.conf (depending on container's tmux version and XDG config preferences).

verification: Run `aishell build --with-tmux` or `aishell update`, then inspect volume to verify plugins exist in /tools/tmux/plugins/ (e.g., /tools/tmux/plugins/tmux-sensible should exist).

files_changed:
  - src/aishell/docker/volume.clj: Write plugin declarations to ~/.tmux.conf (fixed in 95c53ac)
  - src/aishell/docker/run.clj: Mount host tmux config at /tmp/host-tmux.conf staging path instead of original path; added :with-tmux to ensure-harness-volume guard
  - src/aishell/docker/templates.clj: Entrypoint reads from staging path, writes runtime config to XDG path so TPM discovers plugins

additional_root_cause: >
  Even after the initial fix (writing to ~/.tmux.conf), plugins still didn't load at runtime.
  TPM's _get_user_tmux_conf() prefers the XDG path (~/.config/tmux/tmux.conf) over ~/.tmux.conf.
  The host config was mounted read-only at its original XDG path, so TPM always read the
  original config without plugin declarations. Fix: stage the host config at /tmp/host-tmux.conf
  and write the runtime config (with injected plugin declarations) to the XDG path.
