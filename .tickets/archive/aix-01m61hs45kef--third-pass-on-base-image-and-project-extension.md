---
id: aix-01m61hs45kef
title: Third pass on Base image and Project extension wording
status: closed
type: chore
priority: 2
mode: afk
created: '2026-10-28T15:24:09.765025058Z'
updated: '2026-10-28T15:54:47.070477986Z'
closed: '2026-10-28T15:53:50.365125674Z'
tags:
- ready-for-agent
acceptance:
- title: No template var named base-dockerfile remains
  done: true
- title: CONFIGURATION.md says setup builds the Base image
  done: true
- title: No lowercase 'base image' for the Base image, 'Global Dockerfile' heading or 'Extension image' remains in docs
  done: true
- title: No project-dockerfile or get-extension-dockerfile-hash remains in src or test
  done: true
links:
- aix-01m61gkfh089
- aix-01m61kh6dddr
---

## Description

Left open by aix-01m61gkfh089 (2caad42) on 2026-10-28; the user wants small cleanups done directly.

1. `templates/base-dockerfile` (docker/templates.clj:7, cited at DEVELOPMENT.md:123) holds the Foundation image's Dockerfile, now confusable with `base/base-dockerfile-path` (~/.aishell/Dockerfile). Rename it, e.g. `foundation-dockerfile`.
2. docs/CONFIGURATION.md:1159 says the Base image is not built during `aishell setup`; `cli.clj:461` calls `ensure-base-image` in setup. Fix the claim.
3. Wording: lowercase "base image" for the Base image in ARCHITECTURE.md, CONFIGURATION.md, TROUBLESHOOTING.md; "Creating a Global Dockerfile" heading and "global customization(s)" in ARCHITECTURE.md / CONFIGURATION.md; "Extension image" at ARCHITECTURE.md:57. Update anchors that point to renamed headings.
4. Renames: `extension/project-dockerfile` to `project-extension-path`, `extension/get-extension-dockerfile-hash` to `project-extension-hash` (test/aishell/run_test.clj redefines `project-dockerfile`).

Em dashes inside user-visible output strings stay.

## Notes

**2026-10-28T15:53:50.365125674Z**

Renamed templates/base-dockerfile to foundation-dockerfile and the extension fns to project-extension-path / project-extension-hash; CONFIGURATION.md and ARCHITECTURE.md now say setup and update build the Base image; Base image, Extended image and machine-wide customization wording fixed across ARCHITECTURE, CONFIGURATION and TROUBLESHOOTING.
