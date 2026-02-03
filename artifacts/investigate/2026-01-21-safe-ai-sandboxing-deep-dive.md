# Deep Dive: Safe AI Sandboxing - Feature Opportunities for Harness

## Strategic Summary

Safe AI sandboxing requires defense-in-depth across container isolation, sensitive data detection, and runtime monitoring. Harness already implements strong container isolation and warns about dangerous mounts/docker args, but significant opportunities exist to proactively detect secrets, PII, and sensitive files before they enter the AI context. The key insight is that **prevention at the context boundary** (before data reaches the AI) is more effective than trying to filter outputs.

## Key Questions This Research Answers

- What sensitive data detection capabilities are missing from harness?
- What threat models apply to AI sandboxing that harness doesn't currently address?
- What patterns and tools exist for secret/credential detection?
- What feature opportunities would provide the most security value?

---

## Overview

AI agents like Claude Code present unique security challenges beyond traditional sandboxing. While container isolation prevents escape attacks, the primary risks in 2025-2026 involve **data flowing into the AI context** — credentials, PII, and sensitive files that could be inadvertently processed, learned, or exfiltrated.

Harness currently implements:
- Container isolation with ephemeral containers
- Dangerous mount path warnings (AWS creds, SSH keys, GPG, Kubernetes)
- Dangerous docker arg warnings (--privileged, docker.sock, CAP_SYS_ADMIN)
- Input validation for version strings and port mappings

However, it lacks:
- **Proactive secret scanning** of the project directory before AI access
- **Runtime file access monitoring** for sensitive patterns
- **PII detection** in code and configuration files
- **Secret detection in git history** (committed credentials)
- **Network egress monitoring** for potential data exfiltration
- **.env file presence warnings** and best practice enforcement

---

## How It Works: Threat Model for AI Sandboxing

### OWASP Agentic AI Top 10 (2025/2026)

The [OWASP Top 10 for Agentic Applications](https://genai.owasp.org/resource/owasp-top-10-for-agentic-applications-for-2026/) identifies key risks:

| Risk | Description | Harness Coverage |
|------|-------------|------------------|
| ASI01 - Agent Goal Hijacking | Manipulating agent objectives through injected instructions | Not applicable (agent-side) |
| ASI02 - Tool Misuse | Agents misuse legitimate tools for data exfiltration | Partial (mount restrictions) |
| ASI04 - Supply Chain | Poisoned MCP servers, malicious packages | Not covered |
| ASI05 - Code Execution | Generated code performs unauthorized actions | Container isolation helps |
| ASI06 - Memory Poisoning | Persistent malicious instructions in context | Not applicable |

### Data Leakage Vectors

1. **Direct Context Inclusion**: Sensitive files mounted and read by AI
2. **Git History**: Credentials committed historically, accessible via `git log`
3. **Environment Variables**: Secrets passed through but logged/echoed
4. **Configuration Files**: `.env`, `config.yaml` with plaintext credentials
5. **AI-Initiated Exfiltration**: Prompt injection causing data to be sent externally

### Current Gap Analysis

```
┌─────────────────────────────────────────────────────────────┐
│                    HARNESS SECURITY LAYERS                   │
├─────────────────────────────────────────────────────────────┤
│ ✅ Container Isolation (ephemeral, UID/GID matching)        │
│ ✅ Dangerous Mount Warnings (AWS, SSH, GPG, Kube)           │
│ ✅ Docker Arg Warnings (privileged, docker.sock)            │
│ ✅ Input Validation (version strings, ports)                │
├─────────────────────────────────────────────────────────────┤
│ ❌ Project Directory Secret Scanning                        │
│ ❌ Git History Credential Detection                         │
│ ❌ .env File Presence/Content Warnings                      │
│ ❌ PII Detection in Code/Configs                            │
│ ❌ Sensitive File Pattern Detection                         │
│ ❌ Network Egress Monitoring                                │
│ ❌ Runtime File Access Logging                              │
└─────────────────────────────────────────────────────────────┘
```

---

## History & Context

### Evolution of AI Sandboxing

- **2023-2024**: Initial AI coding assistants operated with full system access
- **2024-2025**: Container-based isolation became standard (Docker, Firecracker)
- **2025**: OWASP released Agentic AI Top 10, recognizing unique AI threats
- **2025-2026**: Focus shifted to **context boundary security** — preventing sensitive data from entering AI context

### Why This Matters Now

According to [research by Palo Alto's Unit42](https://unit42.paloaltonetworks.com/agentic-ai-threats/), the most common attacker objective in Q4 2025 was system prompt extraction, but data exfiltration via tool misuse is rapidly increasing. [Smart Labs AI research](https://www.ikangai.com/the-complete-guide-to-sandboxing-autonomous-agents-tools-frameworks-and-safety-essentials/) demonstrated that agents can trivially read internal files, encode contents (Base64), and leak them via HTTP requests.

---

## Patterns & Best Practices

### 1. Secret Detection Tools

| Tool | Strengths | Detection Method |
|------|-----------|------------------|
| [Gitleaks](https://github.com/gitleaks/gitleaks) | Fast, lightweight, CI-friendly | 800+ regex patterns, entropy |
| [TruffleHog](https://github.com/trufflesecurity/trufflehog) | Credential verification, 800+ types | Detectors + live verification |
| [git-secrets](https://github.com/awslabs/git-secrets) | Pre-commit hooks, AWS-focused | Regex patterns |
| [detect-secrets](https://github.com/Yelp/detect-secrets) | Baseline support, plugin system | Multiple detectors |

**Gitleaks Pattern Example**:
```toml
[[rules]]
id = "aws-access-key"
description = "AWS Access Key ID"
regex = '''(A3T[A-Z0-9]|AKIA|AGPA|AIDA|AROA|AIPA|ANPA|ANVA|ASIA)[A-Z0-9]{16}'''
```

### 2. Sensitive File Patterns

From [ban-sensitive-files](https://github.com/bahmutov/ban-sensitive-files) and security best practices:

```
# High-Risk Files
*.pem
*.key
*.p12
*.pfx
id_rsa
id_dsa
id_ed25519
*.ppk

# Credential Files
.env
.env.*
.npmrc
.pypirc
.netrc
.htpasswd
credentials
credentials.json
config.json (often contains secrets)
secrets.yaml
secrets.json

# Cloud Provider Credentials
.aws/credentials
.azure/
.gcloud/
kubeconfig
```

### 3. PII Detection Patterns

From [PII Crawler](https://www.piicrawler.com/blog/regular-expressions-used-in-pii-scanning/) and security research:

```python
PII_PATTERNS = {
    "ssn": r"\b(?!000|666|9\d{2})([0-8]\d{2}|7([0-6]\d|7[012]))([-]?)\d{2}\3\d{4}\b",
    "credit_card": r"\b(?:4[0-9]{12}(?:[0-9]{3})?|5[1-5][0-9]{14}|3[47][0-9]{13})\b",
    "email": r"\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Z|a-z]{2,}\b",
    "phone_us": r"\b(?:\+1|1)?[-.\s]?(?:\d{3})[-.\s]?\d{3}[-.\s]?\d{4}\b",
}
```

### 4. Context Boundary Security

From [Anthropic's sandboxing approach](https://www.anthropic.com/engineering/claude-code-sandboxing):

- **Deny sensitive paths by default**: `~/.ssh`, `~/.aws`, `~/.gnupg`
- **Allow-list for writes**: Only project directory and `/tmp`
- **Network allow-list**: Only necessary domains (package registries, APIs)

---

## Limitations & Edge Cases

### Secret Detection Limitations

- **High entropy detection** produces false positives (git SHAs look like AWS keys)
- **Custom secret formats** may not match predefined patterns
- **Base64/encoded secrets** can evade pattern matching
- **Context-dependent secrets** (e.g., "password" field in YAML) need schema awareness

### PII Detection Limitations

- **Structured vs. unstructured**: Regex works for structured (SSN, CC), fails for names
- **International formats**: Phone numbers, IDs vary by country
- **False positives**: Test data, documentation examples trigger alerts

### Mitigation Strategies

- Use **baseline files** to ignore known false positives
- Combine **regex + entropy + ML** for higher accuracy
- Allow **inline ignores** (e.g., `# gitleaks:allow`)
- Provide **clear remediation guidance** with warnings

---

## Current State & Trends

### Industry Direction (2025-2026)

1. **Layered defense**: No single control is sufficient; combine isolation + detection + monitoring
2. **Least agency principle**: Grant minimum autonomy needed for tasks
3. **Context boundary hardening**: Focus on what enters AI context, not just outputs
4. **Credential verification**: Tools like TruffleHog verify if secrets are still active
5. **Pre-commit as standard**: Secret scanning before code leaves developer machine

### Emerging Tools

- [Anthropic sandbox-runtime](https://github.com/anthropic-experimental/sandbox-runtime): OS-level sandboxing without containers
- [GitGuardian](https://www.gitguardian.com/): Enterprise secret detection with real-time scanning
- [Spectral](https://spectralops.io/): AI-enhanced detection with lower false positives

---

## Key Takeaways

1. **Prevention > Detection**: Scanning the project directory BEFORE AI access is more effective than trying to filter outputs after processing.

2. **Multi-layered approach needed**: Container isolation (which harness has) + secret scanning + sensitive file detection + PII awareness creates defense-in-depth.

3. **Baseline support is essential**: Any secret scanning feature must support baselines/ignores to reduce false positive fatigue, otherwise users will disable it.

4. **Advisory vs. blocking**: Continue harness's pattern of advisory warnings that educate rather than block — power users have legitimate needs for risky configurations.

5. **Git history is a risk**: Credentials committed years ago remain accessible; scanning current files isn't sufficient.

---

## Remaining Unknowns

- [ ] What's the performance impact of running gitleaks on large monorepos at container start?
- [ ] Should network egress monitoring be in-scope for harness, or left to the container runtime?
- [ ] How to handle encrypted secrets (e.g., SOPS, age) that appear as "secrets" but are safe?
- [ ] What's the right UX for presenting multiple warnings without overwhelming users?

---

## Feature Opportunities for Harness

### Tier 1: High Impact, Low Complexity

#### 1. `.env` File Presence Warning
```clojure
;; Check for .env files in project directory
(def env-file-patterns
  [#"^\.env$"
   #"^\.env\..*"
   #"\.env\.local$"])

;; Warning message
"Found .env file(s) in project directory. These may contain secrets
that will be accessible to the AI agent. Consider:
- Using a secrets manager instead
- Adding to .gitignore if not already ignored
- Reviewing contents before proceeding"
```

#### 2. Sensitive File Pattern Detection
```clojure
(def sensitive-file-patterns
  [{:pattern #"id_rsa$|id_dsa$|id_ed25519$"
    :message "SSH private keys detected in project"}
   {:pattern #"\.pem$|\.key$|\.p12$|\.pfx$"
    :message "Certificate/key files detected"}
   {:pattern #"\.npmrc$|\.pypirc$"
    :message "Package manager credentials file detected"}
   {:pattern #"credentials\.json$|secrets\.ya?ml$"
    :message "Credentials file detected"}])
```

#### 3. Git-Ignored Secrets Warning
Check if sensitive files exist but are NOT in .gitignore:
```clojure
;; If .env exists but .env is not in .gitignore, extra warning
"WARNING: .env file exists but is not in .gitignore.
This secret file may be committed to version control."
```

### Tier 2: Medium Impact, Medium Complexity

#### 4. Lightweight Secret Scanning (Built-in)
Implement basic secret pattern detection without external dependencies:

```clojure
(def secret-patterns
  [{:id "aws-access-key"
    :pattern #"(A3T[A-Z0-9]|AKIA|AGPA|AIDA|AROA|AIPA|ANPA|ANVA|ASIA)[A-Z0-9]{16}"
    :message "AWS Access Key ID detected"}
   {:id "aws-secret-key"
    :pattern #"(?i)aws(.{0,20})?['\"][0-9a-zA-Z/+]{40}['\"]"
    :message "AWS Secret Access Key detected"}
   {:id "github-token"
    :pattern #"ghp_[a-zA-Z0-9]{36}|github_pat_[a-zA-Z0-9]{22}_[a-zA-Z0-9]{59}"
    :message "GitHub token detected"}
   {:id "generic-api-key"
    :pattern #"(?i)(api[_-]?key|apikey|api_secret)['\"]?\s*[:=]\s*['\"][a-zA-Z0-9]{20,}['\"]"
    :message "Potential API key detected"}])
```

#### 5. Gitleaks Integration (Optional)
```clojure
;; If gitleaks is available, run deep scan
(defn run-gitleaks-scan [project-dir]
  (when (gitleaks-available?)
    (let [result (shell "gitleaks" "detect" "--source" project-dir
                        "--no-git" "--report-format" "json")]
      (when (seq (:findings result))
        (warn-secrets-found (:findings result))))))
```

#### 6. Pre-start Security Audit Command
New CLI command to audit project before running:
```bash
aishell audit /path/to/project
# Output:
# Security Audit Results:
# ✓ No dangerous mount paths configured
# ⚠ Found 2 .env files (review recommended)
# ⚠ AWS credentials pattern detected in config/aws.json
# ✓ No SSH keys in project directory
# ✓ .gitignore includes .env
```

### Tier 3: High Impact, High Complexity

#### 7. Git History Secret Scanning
```clojure
;; Scan git history for committed secrets
(defn scan-git-history [project-dir]
  (when (git-repo? project-dir)
    (let [result (shell "gitleaks" "detect" "--source" project-dir
                        "--report-format" "json")]
      (when (seq (:findings result))
        (warn-historical-secrets (:findings result))))))
```

#### 8. PII Detection (Optional/Configurable)
```clojure
(def pii-patterns
  [{:id "ssn"
    :pattern #"\b\d{3}-\d{2}-\d{4}\b"
    :message "Potential SSN detected"}
   {:id "credit-card"
    :pattern #"\b(?:4[0-9]{12}(?:[0-9]{3})?|5[1-5][0-9]{14})\b"
    :message "Potential credit card number detected"}])

;; Opt-in via config
;; pii_scan: true
```

#### 9. Network Egress Warnings
Detect when project contains code that might exfiltrate data:
```clojure
;; Check for base64 + HTTP patterns (potential exfiltration)
(def exfil-patterns
  [{:pattern #"base64.*fetch|fetch.*base64|btoa.*http"
    :message "Potential data encoding + HTTP pattern detected"}])
```

#### 10. Security Profile Presets
```yaml
# .aishell.yaml
security_profile: strict  # or: standard, permissive

# strict profile enables:
# - All file pattern warnings
# - Secret scanning
# - PII detection
# - Block non-.gitignored secrets
```

---

## Implementation Context

<claude_context>
<application>
- when_to_use: When running AI agents on codebases that may contain secrets, credentials, PII, or sensitive configuration
- when_not_to_use: Trusted internal codebases with no external code, environments where secrets are managed externally
- prerequisites: Docker installed, project directory accessible
</application>
<technical>
- libraries: gitleaks (optional external), native regex for built-in detection
- patterns: Advisory warnings (non-blocking), baseline support for ignoring false positives, configurable strictness levels
- gotchas: High entropy detection has false positives, scanning large repos can be slow, encrypted secrets appear as secrets
</technical>
<integration>
- works_with: git hooks, CI/CD secret scanning, HashiCorp Vault, AWS Secrets Manager
- conflicts_with: None — complements existing tools
- alternatives: Run gitleaks/trufflehog separately before aishell, use Claude Code's built-in sandbox
</integration>
</claude_context>

**Next Action:** Prioritize features (recommend starting with Tier 1), create implementation plan, or research specific aspect deeper

---

## Sources

- [OWASP Top 10 for Agentic Applications (2026)](https://genai.owasp.org/resource/owasp-top-10-for-agentic-applications-for-2026/) - accessed 2026-01-21
- [OWASP GenAI Security Project](https://genai.owasp.org/2025/12/09/owasp-genai-security-project-releases-top-10-risks-and-mitigations-for-agentic-ai-security/) - accessed 2026-01-21
- [Gitleaks GitHub Repository](https://github.com/gitleaks/gitleaks) - accessed 2026-01-21
- [TruffleHog GitHub Repository](https://github.com/trufflesecurity/trufflehog) - accessed 2026-01-21
- [ban-sensitive-files npm package](https://github.com/bahmutov/ban-sensitive-files) - accessed 2026-01-21
- [git-secrets AWS Labs](https://github.com/awslabs/git-secrets) - accessed 2026-01-21
- [Anthropic Claude Code Sandboxing](https://www.anthropic.com/engineering/claude-code-sandboxing) - accessed 2026-01-21
- [Anthropic sandbox-runtime](https://github.com/anthropic-experimental/sandbox-runtime) - accessed 2026-01-21
- [Unit42 Agentic AI Threats](https://unit42.paloaltonetworks.com/agentic-ai-threats/) - accessed 2026-01-21
- [MAESTRO Framework - CSA](https://cloudsecurityalliance.org/blog/2025/02/06/agentic-ai-threat-modeling-framework-maestro) - accessed 2026-01-21
- [GitGuardian Best Practices for .env](https://blog.gitguardian.com/secure-your-secrets-with-env/) - accessed 2026-01-21
- [Jit - TruffleHog vs Gitleaks Comparison](https://www.jit.io/resources/appsec-tools/trufflehog-vs-gitleaks-a-detailed-comparison-of-secret-scanning-tools) - accessed 2026-01-21
- [Top 8 Git Secrets Scanners 2026 - Jit](https://www.jit.io/resources/appsec-tools/git-secrets-scanners-key-features-and-top-tools-) - accessed 2026-01-21
- [PII Crawler - Regex Patterns](https://www.piicrawler.com/blog/regular-expressions-used-in-pii-scanning/) - accessed 2026-01-21
- [OWASP LLM Prompt Injection](https://genai.owasp.org/llmrisk/llm01-prompt-injection/) - accessed 2026-01-21
- [NVIDIA AI Red Team Practical Security Advice](https://developer.nvidia.com/blog/practical-llm-security-advice-from-the-nvidia-ai-red-team/) - accessed 2026-01-21
- [Sandboxing Guide - CodeAnt AI](https://www.codeant.ai/blogs/agentic-rag-shell-sandboxing) - accessed 2026-01-21
- [Docker Runtime Security for AI Agents](https://www.docker.com/blog/secure-ai-agents-runtime-security/) - accessed 2026-01-21
