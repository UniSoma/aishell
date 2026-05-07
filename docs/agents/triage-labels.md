# Triage Labels

The engineering skills speak in terms of five canonical triage roles. This file maps those roles to the actual tag strings used on knot tickets in this repo.

| Role in skills    | knot tag          | Meaning                                  |
| ----------------- | ----------------- | ---------------------------------------- |
| `needs-triage`    | `needs-triage`    | Maintainer needs to evaluate this ticket |
| `needs-info`      | `needs-info`      | Waiting on reporter for more information |
| `ready-for-agent` | `ready-for-agent` | Fully specified, ready for an AFK agent  |
| `ready-for-human` | `ready-for-human` | Requires human implementation            |
| `wontfix`         | `wontfix`         | Will not be actioned                     |

When a skill mentions a role (e.g. "apply the AFK-ready triage label"), apply the corresponding tag with:

    knot update <id> --add-tag <tag>

Use `--add-tag` / `--remove-tag` rather than `--tags` so the rest of the tag list is preserved. See [issue-tracking.md](issue-tracking.md) for full knot CLI usage.
