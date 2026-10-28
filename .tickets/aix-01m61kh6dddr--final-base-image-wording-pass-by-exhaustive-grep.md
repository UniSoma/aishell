---
id: aix-01m61kh6dddr
title: Final Base image wording pass by exhaustive grep
status: open
type: chore
priority: 2
mode: afk
created: '2026-10-28T15:54:47.070477986Z'
updated: '2026-10-28T16:01:58.605675906Z'
tags:
- ready-for-agent
acceptance:
- title: The grep patterns are recorded in a ticket note
  done: false
- title: Those patterns find no remaining hit for the Base image, Foundation image, Project extension or Extended image in src, test, docs or README.md, other than ones the note lists as legitimate
  done: false
- title: resolve-image-tag has no _base-tag parameter
  done: false
links:
- aix-01m61hs45kef
- aix-01m61f50zynt
---

## Description

Three wording passes on 2026-10-28 (aix-01m61g5a0b1a, aix-01m61gkfh089, aix-01m61hs45kef) each worked from a list and each found more. This pass works from patterns instead: grep src, test, docs and README.md case-insensitively for the Base image and Project extension _Avoid_ terms in CONTEXT.md and their variants ("base image", "global Dockerfile", "global packages", "global customization", "extension image", "project image", "project Dockerfile", "custom base image"), fix every hit that means the Base image, Foundation image, Project extension or Extended image, and record the exact patterns in a ticket note so the held glossary test aix-01m61f50zynt can reuse them.

Known hits left by aix-01m61hs45kef (b480fca): DEVELOPMENT.md:219 "Lazy build pattern" heading (stale: setup/update build the Base image); CONFIGURATION.md:1182, :1201 "global Dockerfile"; ARCHITECTURE.md diagram "+ global packages"; README.md:11 "fixed base image" (means the Foundation image); user-visible strings "Set up base image" (cli.clj:369), "Orphaned custom base image found" (cli.clj:673), "base and extension images" (docker/build.clj:45), with a CHANGELOG line for the changed output.

Also: rename locals that hold the Project extension's path (`dockerfile` in run/resolve-image-tag, `_dockerfile` in check/check-extension, `dockerfile-path` in extension/project-extension-hash and build-extended-image) to `extension-path`; drop the unused `_base-tag` parameter of run/resolve-image-tag and update its callers and tests.

CHANGELOG.md and .planning/ are historical and stay as they are.

## Notes

**2026-10-28T16:01:58.605675906Z**

Also (small, from aix-01m61jhk8bz9 at 8888d25): add ["a" "x" "--" "cmd" "--json"] to flags-after-attach-separator-belong-to-the-command in cli_test.clj so the attach alias is covered.
