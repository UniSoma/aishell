---
status: complete
phase: 20-filename-detection
source: [20-01-SUMMARY.md, 20-02-SUMMARY.md]
started: 2026-01-23T15:30:00Z
updated: 2026-01-23T15:45:00Z
---

## Current Test

[testing complete]

## Tests

### 1. Detect .env file with medium severity
expected: Running aishell in a project with .env file shows MEDIUM severity warning mentioning the file
result: pass

### 2. Detect .env.local and .env.production with medium severity
expected: Running aishell in a project with .env.local or .env.production shows MEDIUM severity warning
result: pass

### 3. Detect .envrc with medium severity
expected: Running aishell in a project with .envrc shows MEDIUM severity warning
result: pass

### 4. Detect .env.example with low severity
expected: Running aishell in a project with .env.example shows LOW severity info (template file, not secret)
result: pass

### 5. Threshold grouping - 3 or fewer files shown individually
expected: When 3 or fewer env files exist, each file is listed individually in the warning output
result: pass

### 6. Threshold grouping - more than 3 files summarized
expected: When more than 3 env files exist, output shows count with 2 sample filenames (e.g., "6 files detected (e.g., .env, .env.local)")
result: pass

### 7. Detect SSH private key (id_rsa) with high severity
expected: Running aishell in a project with id_rsa file shows HIGH severity warning requiring confirmation
result: pass

### 8. Detect SSH key variants (id_ed25519, id_ecdsa, id_dsa)
expected: Any of id_ed25519, id_ecdsa, id_dsa files trigger HIGH severity warning
result: pass

### 9. Detect PuTTY key (.ppk) with high severity
expected: Running aishell in a project with .ppk file shows HIGH severity warning
result: pass

### 10. Detect key containers (.p12, .pfx, .jks, .keystore)
expected: Any of .p12, .pfx, .jks, .keystore files trigger HIGH severity warning
result: pass

### 11. Detect PEM/key files with medium severity
expected: Running aishell in a project with .pem or .key files shows MEDIUM severity warning
result: pass

### 12. High severity requires confirmation in interactive mode
expected: When HIGH severity findings exist, aishell prompts for y/n confirmation before proceeding
result: pass

## Summary

total: 12
passed: 12
issues: 0
pending: 0
skipped: 0

## Gaps

[none yet]
