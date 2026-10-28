---
id: aix-01m61edx76kq
title: Rename get-foundation-image-id to image-id
status: open
type: chore
priority: 2
mode: afk
created: '2026-10-28T14:25:36.480510306Z'
updated: '2026-10-28T14:25:36.480510306Z'
tags:
- ready-for-agent
acceptance:
- title: No get-foundation-image-id remains in src or test; callers use the new name
  done: false
deps:
- aix-01m61edx003f
---

## Description

`ext/get-foundation-image-id` (extension.clj) reads the image id of whatever tag it is given; its callers in extension.clj and docker/base.clj pass both the Base image and the Foundation image tags. The name says Foundation only. Rename it to `image-id` (or move it to where both callers can share it) and update every call site. Raised by the context review of aix-01m61c17fyb8 on 2026-10-28. Waits on the tidy-up ticket because both edit extension.clj.
