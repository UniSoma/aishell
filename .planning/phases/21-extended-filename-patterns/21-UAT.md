---
status: complete
phase: 21-extended-filename-patterns
source: 21-01-SUMMARY.md, 21-02-SUMMARY.md
started: 2026-01-23T17:00:00Z
updated: 2026-01-23T17:15:00Z
---

## Current Test

[testing complete]

## Tests

### 1. GCP Application Default Credentials Detection
expected: When application_default_credentials.json exists in project, HIGH severity warning shown before container launch
result: pass

### 2. Terraform State File Detection
expected: When terraform.tfstate or terraform.tfstate.backup exists (at root OR in subdirectories), HIGH severity warning shown
result: pass

### 3. Kubeconfig Detection
expected: When kubeconfig or .kube/config exists in project, MEDIUM severity warning shown
result: pass

### 4. Package Manager Credentials Detection
expected: When .pypirc or .netrc exists in project, HIGH severity warning shown (contains auth tokens)
result: pass

### 5. Tool Configuration Detection
expected: When .npmrc, .yarnrc.yml, .docker/config.json, .terraformrc, or credentials.tfrc.json exists, MEDIUM severity warning shown
result: pass

### 6. Rails Secrets Detection
expected: When master.key or credentials*.yml.enc exists, HIGH severity warning shown (Rails secrets)
result: pass

### 7. Secret Pattern File Detection
expected: When files named secret.*, secrets.*, vault.*, token.*, apikey.*, or private.* exist, MEDIUM severity warning shown
result: pass

### 8. Database Credentials Detection
expected: When .pgpass, .my.cnf, or database.yml exists, MEDIUM severity warning shown
result: pass

## Summary

total: 8
passed: 8
issues: 0
pending: 0
skipped: 0

## Gaps

[none yet]
