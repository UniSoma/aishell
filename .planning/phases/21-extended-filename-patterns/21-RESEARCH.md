# Phase 21: Extended Filename Patterns - Research

**Researched:** 2026-01-23
**Domain:** Extended filename-based detection for cloud credentials, package manager configs, application secrets, and database credentials
**Confidence:** HIGH

## Summary

Phase 21 extends Phase 20's filename-based detection framework with three additional categories: (1) cloud credentials (GCP application_default_credentials.json, terraform.tfstate*, kubeconfig), (2) package manager credentials (.pypirc, .netrc, .npmrc, .yarnrc.yml, .docker/config.json, .terraformrc, credentials.tfrc.json), and (3) application secrets (Rails master.key, credentials*.yml.enc, secret.*, secrets.*, vault.*, token.*, apikey.*, private.*, database.yml, .pgpass, .my.cnf).

Research confirms that these files represent significant security risks. GCP application_default_credentials.json contains long-lived service account keys that Google strongly recommends against. Terraform state files store secrets in plain text. PyPI's .pypirc and .netrc store credentials in plain text and have been repeatedly exposed on GitHub. Rails master.key decrypts all application secrets, making it critical. Docker's config.json uses only base64 encoding (not encryption). Database credential files like .pgpass and .my.cnf store connection passwords in plain text.

The implementation follows Phase 20's established pattern: glob-based detection with case-insensitive post-filtering, threshold-of-3 grouping, and excluded directory handling. The main new challenge is wildcard pattern matching (secret.*, vault.*, token.*) which requires glob patterns like "**/*secret.*" and "**/*secrets.*" to match files with those terms anywhere in the name.

**Primary recommendation:** Add new detection functions to patterns.clj following the exact structure from Phase 20. Use fs/glob with extension patterns for exact files (.pypirc, .netrc), glob with wildcards for pattern-based matching (secret.*, vault.*), and case-insensitive post-filtering for all matches. Group all findings through existing group-findings function to maintain consistent threshold-of-3 behavior.

## Standard Stack

This phase uses the existing Phase 20 infrastructure with no new dependencies.

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| babashka.fs/glob | Built-in | File pattern matching with ** recursion and wildcards | Already used in Phase 20, handles hidden files and recursive patterns |
| clojure.string | Built-in | Case-insensitive matching, path manipulation | Standard library for string operations |
| Phase 20 patterns framework | Current | detect-* functions, group-findings, in-excluded-dir? | Established pattern ready for extension |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| babashka.fs/file-name | Built-in | Extract filename from path for matching | For name-based filtering and display |
| clojure.string/ends-with? | Built-in | Extension matching for case-insensitive checks | When glob case-sensitivity is insufficient |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Glob wildcards (*.secret.*) | Regex matching with fs/match | Glob wildcards simpler for filename patterns, regex overkill |
| Multiple glob calls | Single comprehensive regex | Phase 20 established multiple glob pattern - maintain consistency |
| Content inspection | Filename-only | REQUIREMENTS.md assigns content-based to future phases |

## Architecture Patterns

### Recommended Extension Structure

```clojure
src/aishell/detection/
├── core.clj              # Extend scan-project concat with new detectors
├── formatters.clj        # Already handles summary? from Phase 20
└── patterns.clj          # ADD: 8 new detection functions
```

### Pattern 1: Exact Filename Detection (GCP, Package Managers)

**What:** Match specific filenames like application_default_credentials.json, .pypirc, .netrc, master.key.

**When to use:** For well-known configuration files with standard names.

**Example:**
```clojure
;; Source: Phase 20 patterns.clj + REQUIREMENTS.md CLOD-02, PKGM-02, PKGM-03

(defn detect-gcp-credentials
  "Detect GCP application_default_credentials.json (high severity).
   Returns vector of findings."
  [project-dir excluded-dirs]
  (let [all-files (fs/glob project-dir "**" {:hidden true})
        filtered (remove #(in-excluded-dir? % excluded-dirs) all-files)
        ;; Case-insensitive match for exact filename
        matches (filter (fn [path]
                         (let [name-lower (str/lower-case (str (fs/file-name path)))]
                           (= name-lower "application_default_credentials.json")))
                       filtered)]
    (for [path matches]
      {:path (str path)
       :type :gcp-credentials
       :severity :high
       :reason "GCP application default credentials file"})))

(defn detect-package-manager-credentials
  "Detect .pypirc and .netrc (high severity).
   Returns vector of findings."
  [project-dir excluded-dirs]
  (let [all-files (fs/glob project-dir "**" {:hidden true})
        filtered (remove #(in-excluded-dir? % excluded-dirs) all-files)
        exact-names [".pypirc" ".netrc"]
        matches (filter (fn [path]
                         (let [name-lower (str/lower-case (str (fs/file-name path)))]
                           (some #(= name-lower %) exact-names)))
                       filtered)]
    (for [path matches]
      {:path (str path)
       :type :package-manager-creds
       :severity :high
       :reason "Package manager credentials file"})))
```

### Pattern 2: Wildcard Extension Detection (terraform.tfstate*)

**What:** Match files with patterns like terraform.tfstate, terraform.tfstate.backup using wildcard glob.

**When to use:** For files with common prefix and variable suffix.

**Example:**
```clojure
;; Source: Phase 20 key-containers pattern + REQUIREMENTS.md CLOD-03

(defn detect-terraform-state
  "Detect terraform.tfstate* files (high severity - contains plaintext secrets).
   Returns vector of findings."
  [project-dir excluded-dirs]
  (let [;; Match terraform.tfstate and terraform.tfstate.backup etc
        state-files (fs/glob project-dir "**/terraform.tfstate*" {:hidden true})
        filtered (remove #(in-excluded-dir? % excluded-dirs) state-files)]
    (for [path filtered]
      {:path (str path)
       :type :terraform-state
       :severity :high
       :reason "Terraform state file (may contain plaintext secrets)"})))
```

### Pattern 3: Wildcard Filename Patterns (secret.*, vault.*, token.*)

**What:** Match files with patterns like secret.yaml, secrets.json, vault.txt, token.env, apikey.config.

**When to use:** For application-specific secret files following naming conventions.

**Example:**
```clojure
;; Source: REQUIREMENTS.md ASEC-02 + glob wildcard patterns

(defn detect-secret-pattern-files
  "Detect secret.*, secrets.*, vault.*, token.*, apikey.*, private.* files (medium severity).
   Returns vector of findings."
  [project-dir excluded-dirs]
  (let [;; Glob patterns for each term - match files with these terms in name
        patterns ["**/secret.*" "**/secrets.*" "**/vault.*"
                  "**/token.*" "**/apikey.*" "**/private.*"]
        all-matches (mapcat (fn [pattern]
                             (fs/glob project-dir pattern {:hidden true}))
                           patterns)
        filtered (remove #(in-excluded-dir? % excluded-dirs) all-matches)]
    (for [path filtered]
      {:path (str path)
       :type :secret-pattern-file
       :severity :medium
       :reason "File with secret-like naming pattern"})))
```

### Pattern 4: Rails Encrypted Credentials Detection (master.key, credentials*.yml.enc)

**What:** Match Rails-specific secret files: master.key (exact), credentials.yml.enc, credentials.production.yml.enc (pattern).

**When to use:** For Rails application secret management files.

**Example:**
```clojure
;; Source: REQUIREMENTS.md ASEC-01 + Rails credential system research

(defn detect-rails-secrets
  "Detect Rails master.key and credentials*.yml.enc (high severity).
   Returns vector of findings."
  [project-dir excluded-dirs]
  (let [all-files (fs/glob project-dir "**" {:hidden true})
        filtered (remove #(in-excluded-dir? % excluded-dirs) all-files)
        ;; Match master.key exactly (case-insensitive)
        master-keys (filter (fn [path]
                             (= (str/lower-case (str (fs/file-name path))) "master.key"))
                           filtered)
        ;; Match credentials*.yml.enc pattern
        creds-enc (fs/glob project-dir "**/credentials*.yml.enc" {:hidden true})
        creds-filtered (remove #(in-excluded-dir? % excluded-dirs) creds-enc)]
    (concat
      (for [path master-keys]
        {:path (str path)
         :type :rails-secret
         :severity :high
         :reason "Rails master key (decrypts all application secrets)"})
      (for [path creds-filtered]
        {:path (str path)
         :type :rails-secret
         :severity :high
         :reason "Rails encrypted credentials file"}))))
```

### Pattern 5: Kubeconfig Path Detection

**What:** Match kubeconfig files by exact name or .kube/config path pattern.

**When to use:** For Kubernetes cluster credential files.

**Example:**
```clojure
;; Source: REQUIREMENTS.md CLOD-04 + Kubernetes security research

(defn detect-kubeconfig
  "Detect kubeconfig or .kube/config patterns (medium severity).
   Returns vector of findings."
  [project-dir excluded-dirs]
  (let [all-files (fs/glob project-dir "**" {:hidden true})
        filtered (remove #(in-excluded-dir? % excluded-dirs) all-files)
        ;; Match "kubeconfig" filename (case-insensitive)
        kubeconfig-files (filter (fn [path]
                                  (= (str/lower-case (str (fs/file-name path))) "kubeconfig"))
                                filtered)
        ;; Match .kube/config path pattern
        kube-configs (filter (fn [path]
                              (str/includes? (str/lower-case (str path)) ".kube/config"))
                            filtered)]
    (for [path (distinct (concat kubeconfig-files kube-configs))]
      {:path (str path)
       :type :kubeconfig
       :severity :medium
       :reason "Kubernetes cluster configuration file"})))
```

### Pattern 6: Database Credential Files

**What:** Match database-specific credential files: .pgpass, .my.cnf, database.yml.

**When to use:** For PostgreSQL, MySQL, and application database configuration files.

**Example:**
```clojure
;; Source: REQUIREMENTS.md ASEC-03 + database credential research

(defn detect-database-credentials
  "Detect .pgpass, .my.cnf, database.yml (medium severity).
   Returns vector of findings."
  [project-dir excluded-dirs]
  (let [all-files (fs/glob project-dir "**" {:hidden true})
        filtered (remove #(in-excluded-dir? % excluded-dirs) all-files)
        exact-names [".pgpass" ".my.cnf" "database.yml"]
        matches (filter (fn [path]
                         (let [name-lower (str/lower-case (str (fs/file-name path)))]
                           (some #(= name-lower %) exact-names)))
                       filtered)]
    (for [path matches]
      {:path (str path)
       :type :database-credentials
       :severity :medium
       :reason "Database credentials file"})))
```

### Pattern 7: Tool-Specific Config Files

**What:** Match .npmrc, .yarnrc.yml, .docker/config.json, .terraformrc, credentials.tfrc.json.

**When to use:** For package manager and infrastructure tool configuration files.

**Example:**
```clojure
;; Source: REQUIREMENTS.md PKGM-04 + Docker/npm security research

(defn detect-tool-configs
  "Detect .npmrc, .yarnrc.yml, .docker/config.json, .terraformrc, credentials.tfrc.json (medium severity).
   Returns vector of findings."
  [project-dir excluded-dirs]
  (let [all-files (fs/glob project-dir "**" {:hidden true})
        filtered (remove #(in-excluded-dir? % excluded-dirs) all-files)
        exact-names [".npmrc" ".yarnrc.yml" ".terraformrc" "credentials.tfrc.json"]
        ;; Exact name matches
        exact-matches (filter (fn [path]
                               (let [name-lower (str/lower-case (str (fs/file-name path)))]
                                 (some #(= name-lower %) exact-names)))
                             filtered)
        ;; .docker/config.json path pattern
        docker-configs (filter (fn [path]
                                (and (str/includes? (str/lower-case (str path)) ".docker/")
                                     (= (str/lower-case (str (fs/file-name path))) "config.json")))
                              filtered)]
    (for [path (distinct (concat exact-matches docker-configs))]
      {:path (str path)
       :type :tool-config
       :severity :medium
       :reason "Tool configuration file (may contain credentials)"})))
```

### Integration Pattern: Extend scan-project

**What:** Add all new detectors to scan-project's concat chain.

**When to use:** This is the final step after implementing all detection functions.

**Example:**
```clojure
;; Source: Phase 20 core.clj scan-project pattern

(defn scan-project
  "Scan project directory for sensitive files.
   Returns vector of findings: [{:path :type :severity :reason}]"
  [project-dir]
  (let [all-findings (concat
                       ;; Phase 20 detectors
                       (patterns/detect-env-files project-dir excluded-dirs)
                       (patterns/detect-ssh-keys project-dir excluded-dirs)
                       (patterns/detect-key-containers project-dir excluded-dirs)
                       (patterns/detect-pem-key-files project-dir excluded-dirs)
                       ;; Phase 21 detectors - NEW
                       (patterns/detect-gcp-credentials project-dir excluded-dirs)
                       (patterns/detect-terraform-state project-dir excluded-dirs)
                       (patterns/detect-kubeconfig project-dir excluded-dirs)
                       (patterns/detect-package-manager-credentials project-dir excluded-dirs)
                       (patterns/detect-rails-secrets project-dir excluded-dirs)
                       (patterns/detect-secret-pattern-files project-dir excluded-dirs)
                       (patterns/detect-database-credentials project-dir excluded-dirs)
                       (patterns/detect-tool-configs project-dir excluded-dirs))]
    (patterns/group-findings all-findings)))
```

### Anti-Patterns to Avoid

- **Over-specific wildcard patterns:** Pattern "**/mysecret.txt" catches one team's convention, but "**/secret.*" catches ecosystem-wide convention
- **Missing case-insensitive handling:** terraform.tfstate ≠ Terraform.tfstate on Linux but same file conceptually
- **Forgetting hidden file support:** .docker/config.json requires {:hidden true} to detect
- **Not filtering excluded directories:** Detecting node_modules/.cache/credentials.json creates false positives
- **Creating separate :type for every pattern:** Rails master.key and credentials.yml.enc both use :rails-secret for coherent grouping

## Don't Hand-Roll

Problems with existing solutions:

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Wildcard pattern matching | Custom filename regex parser | fs/glob with wildcard patterns (**/secret.*) | Glob handles ** recursion and * wildcards natively |
| Path normalization | Custom path comparison logic | str/lower-case + str/includes? | Simple, reliable, already used in Phase 20 |
| Duplicate detection across patterns | Complex deduplication logic | Use distinct on final results | Multiple patterns may match same file (e.g., .docker/secret.json) |
| Finding grouping | Custom aggregation | Existing group-findings from Phase 20 | Already implements threshold-of-3 with sample paths |

**Key insight:** fs/glob supports wildcards in patterns (terraform.tfstate*, secret.*), eliminating need for custom matching logic. The ** recursive pattern + single * wildcard covers all Phase 21 requirements.

## Common Pitfalls

### Pitfall 1: Wildcard Pattern Misunderstanding

**What goes wrong:** Pattern "*secret*" matches entire path (/foo/secret/bar.txt) instead of just filename (secret.txt).
**Why it happens:** Glob wildcards match path segments, not just filenames.
**How to avoid:** Use patterns like "**/secret.*" or "**/secrets.*" to match files with terms in basename.
**Warning signs:** Detection matches directories or catches unintended paths.

**Correct patterns:**
```clojure
;; WRONG: Matches /secret/unrelated.txt
(fs/glob project-dir "*secret*")

;; RIGHT: Matches /foo/secret.yaml, /bar/secrets.json
(fs/glob project-dir "**/secret.*")
(fs/glob project-dir "**/secrets.*")
```

### Pitfall 2: .docker/config.json Path Matching

**What goes wrong:** Glob pattern "**/.docker/config.json" may not catch all .docker paths.
**Why it happens:** .docker is a hidden directory, config.json is not hidden - pattern complexity.
**How to avoid:** Use {:hidden true} and filter results with path pattern matching (str/includes? ".docker/").
**Warning signs:** Known .docker/config.json files not detected.

**Reliable approach:**
```clojure
;; Match any config.json, then filter for .docker/ in path
(let [all-files (fs/glob project-dir "**" {:hidden true})
      docker-configs (filter (fn [path]
                              (and (str/includes? (str path) ".docker/")
                                   (= (fs/file-name path) "config.json")))
                            all-files)]
  ...)
```

### Pitfall 3: Credentials*.yml.enc Wildcard Positioning

**What goes wrong:** Pattern "**/credentials.*.yml.enc" misses credentials.production.yml.enc.
**Why it happens:** Glob * doesn't match empty string in middle of pattern.
**How to avoid:** Use pattern "**/credentials*.yml.enc" (no dot between credentials and *).
**Warning signs:** credentials.production.yml.enc detected but credentials.yml.enc missed.

**Correct pattern:**
```clojure
;; WRONG: Requires at least one char between credentials and .yml.enc
(fs/glob project-dir "**/credentials.*.yml.enc")

;; RIGHT: Matches credentials.yml.enc, credentials.production.yml.enc, etc.
(fs/glob project-dir "**/credentials*.yml.enc")
```

### Pitfall 4: Duplicate Findings from Overlapping Patterns

**What goes wrong:** File named secret.vault.txt matched by both secret.* and vault.* patterns, shows twice.
**Why it happens:** Multiple detection functions can match same file.
**How to avoid:** Use distinct on paths before creating findings, or accept duplicates (group-findings handles it).
**Warning signs:** Same file path appears multiple times in output.

**Solution options:**
```clojure
;; Option 1: Distinct before mapping (preferred)
(for [path (distinct all-matches)]
  {:path (str path) ...})

;; Option 2: Accept duplicates, group-findings deduplicates by type+path
;; (Current Phase 20 approach - simpler, works with grouping)
```

### Pitfall 5: False Positives from Common Filenames

**What goes wrong:** Node.js private.js (module name) detected as sensitive, but it's not a secret.
**Why it happens:** private.* pattern is broad.
**How to avoid:** Accept some false positives - REQUIREMENTS.md specifies medium severity (advisory), not blocking.
**Warning signs:** High volume of complaints about false positives.

**Mitigation:**
- Medium severity for broad patterns (secret.*, private.*)
- High severity only for specific patterns (master.key, .pypirc)
- Phase 23 will add configuration to suppress specific files
- Current approach: Better safe than sorry with advisory warnings

## Code Examples

### Complete Detection Function Template

All Phase 21 detectors follow this structure:

```clojure
;; Source: Phase 20 patterns.clj established pattern

(defn detect-<category>
  "Detect <file types> (<severity> severity).
   Returns vector of findings."
  [project-dir excluded-dirs]
  (let [;; Step 1: Glob for pattern(s)
        matches (fs/glob project-dir "<pattern>" {:hidden true})
        ;; OR: Multiple patterns
        ;; matches (mapcat #(fs/glob project-dir % {:hidden true})
        ;;                 ["pattern1" "pattern2"])

        ;; Step 2: Filter excluded directories
        filtered (remove #(in-excluded-dir? % excluded-dirs) matches)

        ;; Step 3: Additional filtering if needed (case-insensitive, path checks)
        ;; final-matches (filter (fn [path] ...) filtered)
        ]

    ;; Step 4: Map to findings
    (for [path filtered]
      {:path (str path)
       :type :<category-keyword>
       :severity :<high|medium|low>
       :reason "<Human-readable description>"})))
```

### Real-World GCP Credentials Detection

```clojure
;; Source: Phase 20 detect-ssh-keys pattern + REQUIREMENTS.md CLOD-02

(defn detect-gcp-credentials
  "Detect GCP application_default_credentials.json (high severity).
   Returns vector of findings."
  [project-dir excluded-dirs]
  (let [all-files (fs/glob project-dir "**" {:hidden true})
        filtered (remove #(in-excluded-dir? % excluded-dirs) all-files)
        ;; Case-insensitive exact match
        matches (filter (fn [path]
                         (= (str/lower-case (str (fs/file-name path)))
                            "application_default_credentials.json"))
                       filtered)]
    (for [path matches]
      {:path (str path)
       :type :gcp-credentials
       :severity :high
       :reason "GCP application default credentials file"})))
```

### Real-World Secret Pattern Detection

```clojure
;; Source: REQUIREMENTS.md ASEC-02 + wildcard pattern research

(defn detect-secret-pattern-files
  "Detect secret.*, secrets.*, vault.*, token.*, apikey.*, private.* (medium severity).
   Returns vector of findings."
  [project-dir excluded-dirs]
  (let [;; Multiple glob patterns for different terms
        patterns ["**/secret.*" "**/secrets.*" "**/vault.*"
                  "**/token.*" "**/apikey.*" "**/private.*"]
        ;; Collect all matches
        all-matches (mapcat (fn [pattern]
                             (fs/glob project-dir pattern {:hidden true}))
                           patterns)
        ;; Filter excluded directories
        filtered (remove #(in-excluded-dir? % excluded-dirs) all-matches)
        ;; Remove duplicates (file might match multiple patterns)
        unique-matches (distinct filtered)]
    (for [path unique-matches]
      {:path (str path)
       :type :secret-pattern-file
       :severity :medium
       :reason "File with secret-like naming pattern"})))
```

### Integration Test Script

```bash
#!/bin/bash
# Source: Phase 20 verification pattern

# Create test directory with all Phase 21 file types
mkdir -p /tmp/aishell-test-21

# Cloud credentials (high)
touch /tmp/aishell-test-21/application_default_credentials.json
touch /tmp/aishell-test-21/terraform.tfstate
touch /tmp/aishell-test-21/terraform.tfstate.backup
touch /tmp/aishell-test-21/kubeconfig
mkdir -p /tmp/aishell-test-21/.kube
touch /tmp/aishell-test-21/.kube/config

# Package manager credentials (high)
touch /tmp/aishell-test-21/.pypirc
touch /tmp/aishell-test-21/.netrc

# Tool configs (medium)
touch /tmp/aishell-test-21/.npmrc
touch /tmp/aishell-test-21/.yarnrc.yml
mkdir -p /tmp/aishell-test-21/.docker
touch /tmp/aishell-test-21/.docker/config.json
touch /tmp/aishell-test-21/.terraformrc
touch /tmp/aishell-test-21/credentials.tfrc.json

# Rails secrets (high)
touch /tmp/aishell-test-21/master.key
touch /tmp/aishell-test-21/credentials.yml.enc
touch /tmp/aishell-test-21/credentials.production.yml.enc

# Secret pattern files (medium)
touch /tmp/aishell-test-21/secret.yaml
touch /tmp/aishell-test-21/secrets.json
touch /tmp/aishell-test-21/vault.txt
touch /tmp/aishell-test-21/token.env
touch /tmp/aishell-test-21/apikey.config
touch /tmp/aishell-test-21/private.pem

# Database credentials (medium)
touch /tmp/aishell-test-21/.pgpass
touch /tmp/aishell-test-21/.my.cnf
touch /tmp/aishell-test-21/database.yml

# Run detection
cd /home/jonasrodrigues/projects/harness
bb -e "
(require '[aishell.detection.core :as core])
(let [findings (core/scan-project \"/tmp/aishell-test-21\")]
  (println \"Total findings:\" (count findings))
  (core/display-warnings findings))
"

# Clean up
rm -rf /tmp/aishell-test-21
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Store secrets in config files | Use secret management tools (Vault, AWS Secrets Manager) | 2020s+ | Phase 21 detects legacy patterns still present |
| Long-lived service account keys | Short-lived tokens (OIDC, workload identity) | 2024-2026 | GCP now discourages application_default_credentials.json |
| Plain text .netrc/.pypirc | Keyring integration, environment variables | 2023-2025 | Python packaging ecosystem moving away from plain text |
| Base64-encoded Docker credentials | OS keychain integration (docker-credential-helpers) | 2023-2026 | Modern Docker uses native keychains by default |
| Terraform state in Git | Remote backends with encryption | 2020s+ | .tfstate in repos is anti-pattern but still common |

**Deprecated/outdated:**
- Storing GCP service account JSON files in projects: Google recommends workload identity federation
- Committing Rails master.key: Should be environment variable or secret management
- Plain text database passwords in .pgpass/.my.cnf: Modern apps use connection string from vault
- Docker config.json with base64 auth: Use credential helpers on macOS/Linux/Windows

**Current best practices (2026):**
- External secret management (Vault, AWS Secrets Manager, GCP Secret Manager)
- Workload identity federation (OIDC) for cloud credentials
- Environment variables for secrets, not files
- Short-lived tokens over long-lived credentials
- OS keychain integration for developer tools

## Open Questions

1. **Overlap Between secret.* and private.* Patterns**
   - What we know: A file named "secret.private.json" matches both patterns
   - What's unclear: Should we deduplicate or create two findings?
   - Recommendation: Use distinct on paths before mapping to findings, or accept that group-findings deduplicates by type (both would be :secret-pattern-file anyway)

2. **Case Sensitivity for terraform.tfstate***
   - What we know: Terraform officially uses lowercase .tfstate
   - What's unclear: Should we catch Terraform.tfstate or TERRAFORM.TFSTATE?
   - Recommendation: Yes - use case-insensitive post-filtering for consistency with Phase 20 decisions

3. **.npmrc With vs Without Auth Tokens**
   - What we know: Not all .npmrc files contain secrets (some just set registry URL)
   - What's unclear: Should filename detection warn about all .npmrc (Phase 21) or only those with authToken (content-based, Phase 22)?
   - Recommendation: Phase 21 warns about all .npmrc (medium severity, advisory). PKGM-01 in Phase 22 adds content check for authToken (high severity)

4. **Database.yml in Rails Projects**
   - What we know: Rails database.yml often has <%= ENV['DB_PASSWORD'] %>, not literal passwords
   - What's unclear: Is warning about database.yml too noisy?
   - Recommendation: Medium severity (advisory) is appropriate. Phase 23 configuration can suppress if user verifies no secrets present

5. **Threshold-of-3 Grouping for Mixed Severities**
   - What we know: Rails master.key (high) and credentials.production.yml.enc (high) both have :type :rails-secret
   - What's unclear: If 4+ Rails secret files, does summary use high severity?
   - Recommendation: Yes - group-findings uses severity from first item in group, all Rails secrets are high severity

## Sources

### Primary (HIGH confidence)

- [Google Cloud: How Application Default Credentials works](https://docs.cloud.google.com/docs/authentication/application-default-credentials) - Official GCP ADC documentation
- [Google Cloud: Service account credentials](https://docs.cloud.google.com/iam/docs/service-account-creds) - Security risks of service account keys
- [AWS Prescriptive Guidance: Protecting sensitive data in Terraform state](https://docs.aws.amazon.com/prescriptive-guidance/latest/secure-sensitive-data-secrets-manager-terraform/terraform-state-file.html) - Official AWS guidance on .tfstate security
- [HashiCorp Developer: Protect sensitive input variables](https://developer.hashicorp.com/terraform/tutorials/configuration-language/sensitive-variables) - Terraform secret management
- [Python Security: PyPI credential exposure on GitHub](https://python-security.readthedocs.io/pypi-vuln/index-2017-11-08-pypirc_exposure_on_github.html) - .pypirc security vulnerability documentation
- [PostgreSQL Documentation: The Password File](https://www.postgresql.org/docs/current/libpq-pgpass.html) - Official .pgpass documentation
- [Docker Documentation: docker login](https://docs.docker.com/reference/cli/docker/login/) - Docker credential storage
- [GitGuardian: Rails Master Key](https://docs.gitguardian.com/secrets-detection/secrets-detection-engine/detectors/specifics/rails_master_key) - Rails master.key security risks
- Phase 20 RESEARCH.md - Established patterns, glob usage, threshold-of-3
- Phase 20 patterns.clj implementation - Working code examples
- REQUIREMENTS.md - CLOD-02/03/04, PKGM-02/03/04, ASEC-01/02/03 specifications

### Secondary (MEDIUM confidence)

- [Medium: Understanding Rails Credentials, Master Keys, Secret Keys & Encryption](https://medium.com/@kmvel95/understanding-rails-credentials-master-keys-secret-keys-encryption-a-complete-guide-283a395c6638) - Rails credential system (Dec 2025)
- [Kubernetes Security News 2026](https://tasrieit.com/blog/2026-01-15-kubernetes-security-news-2026-latest-threats-updates-and-best-practices) - Kubeconfig security evolution (Jan 2026)
- [SentinelOne: Kubernetes Security Risks](https://www.sentinelone.com/cybersecurity-101/cloud-security/kubernetes-security-risks/) - Kubeconfig exposure scenarios
- [Spacelift: Terraform Secrets Management](https://spacelift.io/blog/terraform-secrets) - .tfstate security best practices
- [Cycode: Managing secrets in Terraform](https://cycode.com/blog/secrets-in-terraform/) - State file security
- [DevOps Roles: Docker Registry Credentials](https://www.devopsroles.com/how-to-store-your-docker-registry-credentials/) - config.json security (2026)
- [Neon: PostgreSQL Password File .pgpass](https://neon.com/postgresql/postgresql-administration/postgresql-password-file-pgpass) - .pgpass usage and security
- [Baeldung: Glob Patterns Guide](https://www.devzery.com/post/your-comprehensive-guide-to-glob-patterns) - Glob wildcard matching patterns
- [VS Code: Glob Patterns Reference](https://code.visualstudio.com/docs/editor/glob-patterns) - Glob pattern syntax

### Tertiary (LOW confidence)

- GitHub issue discussions about kubeconfig security - Community awareness
- HashiCorp Vault policy wildcards - Conceptual understanding of glob/wildcard differences
- WebSearch results on glob pattern best practices - General guidance, not tool-specific

## Metadata

**Confidence breakdown:**
- Standard stack (babashka.fs): HIGH - Already proven in Phase 20, no new dependencies
- Architecture (extend patterns.clj): HIGH - Exact same pattern as Phase 20, just more detection functions
- Wildcard glob patterns (secret.*, vault.*): HIGH - Verified glob supports wildcards, tested pattern syntax
- File security risks: HIGH - Official documentation from Google, AWS, PostgreSQL, Docker, Rails
- Case-insensitive approach: HIGH - Phase 20 established pattern, maintain consistency
- Exact filenames (.pypirc, master.key): HIGH - Well-documented standard filenames
- Pattern matching details: MEDIUM - Some edge cases (duplicate detection) require validation during implementation
- False positive rate: LOW - Unknown until testing, but REQUIREMENTS.md specifies medium severity (advisory) for broad patterns

**Research date:** 2026-01-23
**Valid until:** 90 days (stable technology, established patterns, unlikely to change)
