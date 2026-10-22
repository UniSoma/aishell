---
id: aix-01m5jqhzgdft
title: Refresh Debian packages on plain aishell update
status: open
type: feature
priority: 2
mode: afk
created: '2026-10-22T21:16:30.605662282Z'
updated: '2026-10-22T21:16:56.313262120Z'
deps:
- aix-01m5jqhzdcd6
acceptance:
- title: Plain aishell update re-runs the apt layer when the ISO week changed since the last foundation build, and reuses cache otherwise
  done: false
- title: The staleness warning suggests plain aishell update
  done: false
- title: Rebuild time of plain update vs update --force is measured and noted on the ticket
  done: false
- title: clj-kondo --lint src test is clean
  done: false
---

## Description

Follow-up to aix-01m5jqhzdcd6. Add a build arg keyed to the ISO week (e.g. 2026-W43) to the foundation's single apt-get install RUN, so plain `aishell update` rebuilds the apt layer at most once a week and picks up Debian security updates without --force. Then change the staleness warning from aix-01m5jqhzdcd6 to suggest plain `aishell update`.

Keep the ONE apt install block (aishell.info/parse-packages scrapes only the first). Layers after apt also rebuild; measure the cost and record it.
