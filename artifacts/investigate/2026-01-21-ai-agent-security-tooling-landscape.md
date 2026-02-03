# Landscape Map: AI Agent Security Tooling

## Strategic Summary

The AI agent security tooling landscape in 2026 spans six major categories: sandbox/isolation, secret detection, guardrails/firewalls, observability, MCP security, and AI coding tools. **The key trend is consolidation through M&A** (Lakera→Check Point, Robust Intelligence→Cisco, Protect AI→Palo Alto), signaling enterprise demand is outpacing point-solution capabilities. **The main opportunity for harness** lies in the gap between enterprise-grade cloud platforms (E2B, Modal) and local developer tooling—there's no comprehensive "safe AI sandbox for local development" that combines container isolation with proactive secret/sensitive data detection.

## Scope

**Included:**
- Container/VM isolation platforms for AI agents
- Secret detection and credential scanning tools
- AI guardrails and firewall solutions
- LLM/agent observability and monitoring
- MCP (Model Context Protocol) security
- AI coding assistant security approaches

**Excluded:**
- General cloud security platforms (not AI-specific)
- Model training security (fine-tuning, poisoning defenses)
- AI ethics and bias detection (non-security focus)
- Pure MLOps platforms without security features

---

## Categories

### 1. Sandbox & Isolation Platforms

**Purpose:** Run AI-generated code safely in isolated environments

| Player | Type | Isolation Tech | Key Differentiator | Pricing |
|--------|------|----------------|-------------------|---------|
| **E2B** | Cloud | Firecracker microVMs | AI-first SDK, <200ms cold start | $0.05/hr, Free tier |
| **Modal** | Cloud | gVisor | GPU support (H100/A100), Python-centric | Per-second billing |
| **Daytona** | Cloud | Docker + Kata Containers | Fastest cold start (<90ms) | Usage-based |
| **CodeSandbox** | Cloud | microVMs | Web dev focus, Together AI acquisition | Credits-based |
| **Replit** | Cloud | Custom | Full IDE + Replit Agent | $20/mo Pro |
| **Northflank** | Cloud | Kubernetes | BYOC, enterprise features | Usage-based |
| **Blaxel** | Cloud | Unknown | 25ms cold start (fastest) | Unknown |

**Established:** E2B, Modal, Replit
**Emerging:** Daytona, Blaxel, Northflank
**Trend:** Firecracker microVMs becoming standard for AI workloads; GPU access is major differentiator

**Local/Self-Hosted Options:**
| Tool | Type | Notes |
|------|------|-------|
| **Anthropic sandbox-runtime** | Open Source | OS-level sandboxing (sandbox-exec, bubblewrap) |
| **gVisor** | Open Source | Google's user-space kernel |
| **Firecracker** | Open Source | AWS Lambda's isolation tech |
| **Docker + seccomp** | Open Source | Requires careful configuration |

---

### 2. Secret Detection & Credential Scanning

**Purpose:** Detect credentials, API keys, and secrets in code/repos

| Tool | Type | Coverage | Key Feature | Best For |
|------|------|----------|-------------|----------|
| **GitGuardian** | Commercial | 350+ secret types | Real-time scanning, enterprise dashboard | Enterprise-wide |
| **TruffleHog** | Open Source | 600+ detectors | Live verification, non-code scanning (S3, Docker) | Deep scanning |
| **Gitleaks** | Open Source | Regex + entropy | Fast, lightweight, CI-friendly | CI/CD integration |
| **detect-secrets** | Open Source | Plugin-based | Low false positives | Production precision |
| **git-secrets** | Open Source | AWS-focused | Pre-commit hooks | AWS projects |
| **GitHub Secret Scanning** | Built-in | 200+ partners | Native, auto-revocation | GitHub repos |

**Established:** GitGuardian (enterprise), Gitleaks/TruffleHog (open source)
**Emerging:** Infisical, Spectral (AI-enhanced)
**Trend:** Research shows using multiple tools is necessary—no single tool catches everything

**Gap for Harness:** These tools exist standalone but aren't integrated into AI sandbox startup flows

---

### 3. AI Guardrails & Firewalls

**Purpose:** Real-time protection against prompt injection, data leakage, toxic output

| Player | Type | Key Features | Status |
|--------|------|--------------|--------|
| **Lakera Guard** | Commercial | 98%+ detection, <50ms latency | Acquired by Check Point |
| **Robust Intelligence** | Commercial | AI Firewall, automated red teaming | Acquired by Cisco ($400M) |
| **Protect AI** | Commercial | Model scanning, ML supply chain security | Reported acquisition by Palo Alto ($700M) |
| **Akamai Firewall for AI** | Commercial | Edge-based, prompt injection detection | Generally available |
| **AWS Bedrock Guardrails** | Cloud | Integrated with Bedrock models | AWS-native |
| **Straiker** | Startup | AI-native, Ascend AI/Defend AI | $21M funding (2025) |
| **CalypsoAI** | Commercial | Inference red-teaming, security leaderboards | Enterprise focus |
| **NVIDIA NeMo Guardrails** | Open Source | Programmable guardrails | Developer-focused |

**Established:** Lakera, Robust Intelligence (now via acquirers)
**Emerging:** Straiker, CalypsoAI, Invariant Labs
**Trend:** Major consolidation—Check Point, Cisco, and Palo Alto acquiring AI security startups

---

### 4. LLM/Agent Observability & Monitoring

**Purpose:** Trace, debug, and monitor AI agent behavior

| Platform | Type | Framework Focus | Key Features |
|----------|------|-----------------|--------------|
| **Langfuse** | Open Source | Framework-agnostic | 19K GitHub stars, self-host option, free tier |
| **LangSmith** | Commercial | LangChain-native | Best debugging for LangChain, zero setup |
| **Arize Phoenix** | Open Source | Framework-agnostic | OTEL-based, evaluation focus, self-host |
| **Helicone** | Commercial | Framework-agnostic | Caching, rate limiting, cost optimization |
| **Datadog LLM** | Commercial | Framework-agnostic | Integrates with existing Datadog |
| **Weights & Biases** | Commercial | ML-focused | Experiment tracking, production monitoring |

**Established:** LangSmith, Arize Phoenix, Langfuse
**Emerging:** Helicone, Maxim AI
**Trend:** Open source (Langfuse, Phoenix) gaining on commercial options; self-hosting for data control

---

### 5. MCP (Model Context Protocol) Security

**Purpose:** Secure the protocol connecting AI models to external tools

**Threat Landscape:**
- Tool Poisoning Attacks (TPA) via malicious server metadata
- Cross-tool contamination between MCP servers
- Confused deputy attacks through proxy servers
- Real breaches: GitHub MCP exfiltration, Asana cross-tenant access

| Security Approach | Implementation |
|-------------------|----------------|
| **Server allowlisting** | Agentforce mandatory allowlist |
| **Zero-trust for tools** | Treat all external MCP resources as untrusted |
| **Sandboxing MCP servers** | OWASP recommendation |
| **SCA/SAST for MCP components** | Scan dependencies for vulnerabilities |

**Established Players:** Anthropic (protocol author), Salesforce Agentforce (enterprise governance)
**Security Research:** Invariant Labs, Palo Alto Unit42
**Trend:** MCP is a new attack surface; security practices still maturing

---

### 6. AI Coding Assistant Security

**Purpose:** Security approaches for developer-facing AI tools

| Tool | Sandbox Approach | Security Model |
|------|------------------|----------------|
| **Claude Code** | Local terminal, optional sandbox-exec | Developer-in-the-loop, local control |
| **OpenAI Codex** | Cloud sandbox (isolated), network-off default | Async cloud execution, approval modes |
| **Cursor** | IDE-based | Visual approval flow |
| **Windsurf** | AI-first editor | Cascade agent with context tracking |
| **Aider** | Local CLI | Git-aware, confirmations |
| **Continue.dev** | Open source, local | Privacy-focused, no cloud |

**Trend:** Split between cloud-first (Codex) and local-first (Claude Code) approaches; privacy becoming differentiator

---

## Landscape Map

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     AI AGENT SECURITY TOOLING LANDSCAPE                      │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  CLOUD SANDBOXES          SECRET DETECTION         AI GUARDRAILS            │
│  ───────────────          ────────────────         ────────────             │
│  E2B ★                    GitGuardian ★            Lakera→CheckPoint        │
│  Modal ★                  TruffleHog               Robust Int→Cisco         │
│  Daytona                  Gitleaks ★               Protect AI→PaloAlto      │
│  Replit                   detect-secrets           AWS Bedrock Guardrails   │
│  CodeSandbox→Together     git-secrets              Straiker ★               │
│  Blaxel                   GitHub Native            CalypsoAI                │
│  Northflank               Spectral                 NeMo Guardrails          │
│                                                                             │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  OBSERVABILITY            MCP SECURITY             CODING ASSISTANTS        │
│  ─────────────            ────────────             ─────────────────        │
│  Langfuse ★               Invariant Labs           Claude Code ★            │
│  LangSmith                Agentforce               OpenAI Codex ★           │
│  Arize Phoenix ★          (Emerging area)          Cursor                   │
│  Helicone                                          Windsurf                 │
│  Datadog LLM                                       Aider                    │
│  W&B                                               Continue.dev             │
│                                                                             │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  LOCAL/SELF-HOSTED ISOLATION                                                │
│  ───────────────────────────                                                │
│  Anthropic sandbox-runtime    gVisor    Firecracker    Docker+seccomp       │
│                                                                             │
│  ★ = Market leader or highly recommended                                    │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘

                              ┌─────────────────┐
                              │   GAP: LOCAL    │
                              │  DEVELOPER AI   │
                              │  SANDBOX WITH   │
                              │ INTEGRATED DATA │
                              │   PROTECTION    │
                              └─────────────────┘
                                     ↑
                           Harness opportunity
```

---

## Trends

### 1. **M&A Consolidation in AI Security**
Check Point acquired Lakera, Cisco acquired Robust Intelligence ($400M), Palo Alto reportedly pursuing Protect AI ($700M). Enterprise security vendors are buying AI security startups to build "AI Transformation Security" stacks. Implication: Point solutions may struggle against integrated offerings.

### 2. **Firecracker MicroVMs Becoming Standard**
E2B, AWS Lambda, and others use Firecracker for AI workloads. No documented VM escapes as of late 2025. gVisor offers a middle ground. Standard Docker containers are increasingly seen as insufficient for truly untrusted AI code.

### 3. **MCP as New Attack Surface**
Tool Poisoning Attacks and prompt injection through MCP sampling are documented threats with real breaches. Expect MCP security to become its own category within 12-18 months.

### 4. **Shift from Output Filtering to Context Boundary Security**
Research consistently shows that preventing sensitive data from entering AI context is more effective than filtering outputs. Secret scanning before AI interaction > DLP on outputs.

### 5. **Open Source Gaining in Observability**
Langfuse (19K stars) and Arize Phoenix are competing effectively with commercial options. Self-hosting for data control is major driver.

### 6. **Local-First AI Development Rising**
Privacy concerns driving interest in local tools (Claude Code local mode, Continue.dev, Aider). Enterprises want AI without sending code to cloud.

---

## Gaps & White Space

### **Gap 1: Integrated Local AI Sandbox with Data Protection**
**What's missing:** No tool combines local container isolation with proactive secret/PII detection before AI access.
**Why underserved:** Cloud sandboxes (E2B, Modal) handle isolation but not pre-scan. Secret scanners (Gitleaks) exist standalone but aren't integrated into AI workflows.
**Opportunity:** Harness is positioned here—add secret/sensitive file detection to existing container isolation.

### **Gap 2: MCP Security Tooling**
**What's missing:** No comprehensive MCP security platform (beyond Agentforce's enterprise offering).
**Why underserved:** MCP is new (2024); security practices still forming.
**Opportunity:** Medium-term—wait for standards to stabilize, but watch this space.

### **Gap 3: AI Agent Supply Chain Security**
**What's missing:** Beyond Protect AI's ModelScan, limited tooling for validating AI agent dependencies (MCP servers, plugins, tool chains).
**Why underserved:** Fragmented ecosystem, no standard package format.
**Opportunity:** Long-term—could be significant as AI agents become more composable.

### **Gap 4: Developer-Friendly Guardrails**
**What's missing:** Guardrails solutions are enterprise-focused (Lakera, Straiker). Nothing lightweight for individual developers or small teams.
**Why underserved:** Enterprise deals drive revenue; OSS alternatives (NeMo) are complex.
**Opportunity:** Harness could add basic guardrail patterns without full-blown enterprise platform.

---

## Key Insights

1. **Defense-in-depth is non-negotiable:** Container isolation alone isn't enough. The 2026 consensus is: sandbox + secret scanning + runtime monitoring + context boundary protection.

2. **The "context boundary" is the new perimeter:** What enters the AI's context matters more than what leaves. Preventing secrets from being read is more effective than filtering outputs.

3. **Local development tools lag cloud security:** E2B and Modal are well-secured for cloud, but local AI development (Claude Code, Codex CLI, Aider) relies on minimal OS-level controls. Harness fills this gap.

4. **MCP is a ticking time bomb:** Real breaches have occurred. Any tool that connects to MCP servers needs security considerations—this is an emerging concern.

5. **Acquisitions signal enterprise validation:** $1B+ in AI security M&A in 2025-2026 shows enterprises are taking AI agent security seriously. The market is real.

---

## Implications for Harness

### Positioning Opportunity
Harness occupies a unique niche: **local AI sandbox for developers**. The competitive landscape shows:
- Cloud sandboxes (E2B, Modal) = enterprise cloud workloads
- Enterprise guardrails (Lakera, Straiker) = large companies with security teams
- Secret scanners (Gitleaks, TruffleHog) = standalone CI/CD tools
- **Missing: Integrated local sandbox + data protection for developers**

### Differentiation Strategy
1. **Not competing with cloud platforms:** Different use case (local dev vs. cloud execution)
2. **Not competing with enterprise guardrails:** Simpler, advisory-based approach
3. **Integrating secret detection:** Bring Gitleaks-style patterns into container startup
4. **Developer-first UX:** Warnings, not blocks; education, not friction

### Feature Priorities (Based on Landscape Analysis)
| Priority | Feature | Landscape Rationale |
|----------|---------|---------------------|
| **High** | .env file detection | No cloud sandbox does this; addresses context boundary |
| **High** | Sensitive file patterns | Complements existing mount warnings |
| **High** | Built-in secret patterns | Lightweight alternative to running Gitleaks separately |
| **Medium** | Gitleaks integration | Optional deep scan for users who want it |
| **Medium** | Pre-run audit command | Developer experience differentiator |
| **Low** | PII detection | Enterprise feature, lower priority for dev tool |
| **Watch** | MCP server validation | Emerging concern, wait for standards |

---

## Implementation Context

<claude_context>
<positioning>
- opportunities: Local AI sandbox with integrated data protection—unique in market
- crowded: Cloud sandbox platforms (E2B, Modal dominant), enterprise guardrails (consolidating)
- emerging: MCP security, AI agent supply chain
</positioning>
<technical>
- standard_stack: Firecracker/gVisor for isolation, regex patterns for secret detection, TOML/YAML for config
- integrations: Gitleaks (optional), Docker, podman, git hooks
- tools_to_evaluate: Gitleaks patterns, TruffleHog detectors, ban-sensitive-files patterns
</technical>
<trends>
- adopt: Context boundary security (scan before AI access), advisory warnings pattern
- watch: MCP security standards, AI agent supply chain tooling
- avoid: Blocking-first approaches (causes user friction), enterprise-only features
</trends>
</claude_context>

**Next Action:** Prioritize Tier 1 features from deep dive (.env warnings, sensitive file patterns, gitignore checks), or run /plan to define implementation approach

---

## Sources

### Sandbox & Isolation
- [E2B - Enterprise AI Agent Cloud](https://e2b.dev/) - accessed 2026-01-21
- [Modal - Top Code Agent Sandbox Products](https://modal.com/blog/top-code-agent-sandbox-products) - accessed 2026-01-21
- [Northflank - Best Code Execution Sandbox 2026](https://northflank.com/blog/best-code-execution-sandbox-for-ai-agents) - accessed 2026-01-21
- [Better Stack - 10 Best Sandbox Runners 2026](https://betterstack.com/community/comparisons/best-sandbox-runners/) - accessed 2026-01-21
- [CodeAnt AI - LLM Shell Sandboxing Guide](https://www.codeant.ai/blogs/agentic-rag-shell-sandboxing) - accessed 2026-01-21
- [Awesome Sandbox GitHub](https://github.com/restyler/awesome-sandbox) - accessed 2026-01-21

### Secret Detection
- [Jit - Top 8 Git Secrets Scanners 2026](https://www.jit.io/resources/appsec-tools/git-secrets-scanners-key-features-and-top-tools-) - accessed 2026-01-21
- [Jit - TruffleHog vs Gitleaks Comparison](https://www.jit.io/resources/appsec-tools/trufflehog-vs-gitleaks-a-detailed-comparison-of-secret-scanning-tools) - accessed 2026-01-21
- [SentinelOne - Best Secret Scanning Tools 2026](https://www.sentinelone.com/cybersecurity-101/cloud-security/secret-scanning-tools/) - accessed 2026-01-21
- [GitGuardian - TruffleHog Comparison](https://www.gitguardian.com/comparisons/trufflehog-v3) - accessed 2026-01-21

### AI Guardrails & Security Platforms
- [Lakera](https://www.lakera.ai/) - accessed 2026-01-21
- [Check Point - Lakera Acquisition](https://www.checkpoint.com/press-releases/check-point-acquires-lakera-to-deliver-end-to-end-ai-security-for-enterprises/) - accessed 2026-01-21
- [Reco - Top 10 AI Security Tools 2026](https://www.reco.ai/compare/ai-security-tools-for-enterprises) - accessed 2026-01-21
- [Straiker Launch Announcement](https://www.straiker.ai/blog/straiker-launches-with-21-million-to-safeguard-ai) - accessed 2026-01-21
- [Protect AI - ModelScan](https://github.com/protectai/modelscan) - accessed 2026-01-21
- [BankInfoSecurity - Palo Alto/Protect AI](https://www.bankinfosecurity.com/blogs/palo-alto-networks-eyeing-700m-buy-protect-ai-p-3852) - accessed 2026-01-21

### Observability
- [Langfuse vs Arize Phoenix](https://langfuse.com/faq/all/best-phoenix-arize-alternatives) - accessed 2026-01-21
- [O-mega - Top 5 AI Agent Observability Platforms 2026](https://o-mega.ai/articles/top-5-ai-agent-observability-platforms-the-ultimate-2026-guide) - accessed 2026-01-21
- [LakersFS - LLM Observability Tools 2026](https://lakefs.io/blog/llm-observability-tools/) - accessed 2026-01-21
- [Arize Phoenix GitHub](https://github.com/Arize-ai/phoenix) - accessed 2026-01-21

### MCP Security
- [Practical DevSecOps - MCP Security Vulnerabilities](https://www.practical-devsecops.com/mcp-security-vulnerabilities/) - accessed 2026-01-21
- [Unit42 - MCP Attack Vectors](https://unit42.paloaltonetworks.com/model-context-protocol-attack-vectors/) - accessed 2026-01-21
- [Red Hat - MCP Security Risks](https://www.redhat.com/en/blog/model-context-protocol-mcp-understanding-security-risks-and-controls) - accessed 2026-01-21
- [AuthZed - Timeline of MCP Breaches](https://authzed.com/blog/timeline-mcp-breaches) - accessed 2026-01-21
- [Jit - Hidden Dangers of MCP](https://www.jit.io/resources/app-security/the-hidden-dangers-of-mcp-emerging-threats-for-the-novel-protocol) - accessed 2026-01-21

### AI Coding Tools
- [Northflank - Claude Code vs Codex](https://northflank.com/blog/claude-code-vs-openai-codex) - accessed 2026-01-21
- [AIMultiple - Agentic CLI Tools](https://research.aimultiple.com/agentic-cli/) - accessed 2026-01-21
- [DevCompare - AI Coding Tools Comparison](https://www.devcompare.io/) - accessed 2026-01-21
