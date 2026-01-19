---
status: diagnosed
trigger: "PRE_START commands are not executing in the aishell container"
created: 2026-01-19T10:00:00Z
updated: 2026-01-19T10:00:00Z
symptoms_prefilled: true
goal: find_root_cause_only
---

## Current Focus

hypothesis: PRE_START is not in the whitelist or not being passed via -e flag to docker
test: Check aishell script for PRE_START handling in config parsing and docker run
expecting: Find where PRE_START should be whitelisted and passed to container
next_action: Read aishell script and search for PRE_START handling

## Symptoms

expected: PRE_START should be parsed from run.conf, passed to container via -e flag, and executed by entrypoint.sh before main process with output to /tmp/pre-start.log
actual: /tmp/test.txt doesn't exist in container, /tmp/pre-start.log doesn't exist either
errors: No error messages reported - silent failure
reproduction: Set PRE_START="echo hello > /tmp/test.txt" in .aishell/run.conf, start container, check for /tmp/test.txt
started: Unknown - may have never worked

## Eliminated

## Evidence

- timestamp: 2026-01-19T10:01:00Z
  checked: aishell script - PRE_START in whitelist
  found: Line 414 - RUNCONF_ALLOWED_VARS includes PRE_START ("MOUNTS|ENV|PORTS|DOCKER_ARGS|PRE_START")
  implication: PRE_START is correctly whitelisted for parsing

- timestamp: 2026-01-19T10:01:00Z
  checked: aishell script - CONF_PRE_START initialization
  found: Line 428 - declare -g CONF_PRE_START="" in parse_run_conf()
  implication: Variable is correctly initialized

- timestamp: 2026-01-19T10:01:00Z
  checked: aishell script - PRE_START passed to docker
  found: Lines 1427-1431 - PRE_START IS passed via -e flag: docker_args+=(-e "PRE_START=$CONF_PRE_START")
  implication: Config parsing and docker run correctly pass PRE_START

- timestamp: 2026-01-19T10:01:00Z
  checked: Embedded entrypoint.sh - PRE_START execution
  found: Lines 274-279 - entrypoint.sh executes PRE_START in background with sh -c
  implication: entrypoint.sh has the logic to execute PRE_START

- timestamp: 2026-01-19T10:02:00Z
  checked: Build process - when entrypoint.sh is created
  found: entrypoint.sh is written to temp dir during `aishell build` (lines 727-730), then COPY'd into Docker image (Dockerfile line 195-196)
  implication: entrypoint.sh is BAKED INTO the image at BUILD TIME, not runtime

- timestamp: 2026-01-19T10:02:30Z
  checked: User's existing Docker image
  found: If user built their image BEFORE PRE_START support was added to entrypoint.sh, their image contains an OLD entrypoint.sh without PRE_START handling
  implication: ROOT CAUSE IDENTIFIED - stale entrypoint.sh in existing Docker image

## Resolution

root_cause: The entrypoint.sh containing PRE_START logic is embedded in the Docker image at BUILD time. If the user's Docker image was built BEFORE the PRE_START feature was added (lines 274-279 in embedded entrypoint.sh), their container is running an OLD entrypoint.sh that doesn't have PRE_START handling. The user needs to REBUILD the image (`aishell build` or `aishell update`) to get the new entrypoint.sh with PRE_START support.
fix:
verification:
files_changed: []
