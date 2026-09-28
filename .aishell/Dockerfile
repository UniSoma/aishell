FROM aishell:base

SHELL ["/bin/bash", "-eo", "pipefail", "-c"]

# >>> clojure (managed) >>>
# JDK 25 from Eclipse Temurin (foundation only ships JRE 17 headless).
COPY --from=eclipse-temurin:25-jdk-jammy /opt/java/openjdk /opt/java/openjdk
ENV JAVA_HOME=/opt/java/openjdk \
    PATH="/opt/java/openjdk/bin:${PATH}"

ARG CLOJURE_VERSION=1.12.6.1673
RUN curl -fsSL "https://github.com/clojure/brew-install/releases/download/${CLOJURE_VERSION}/linux-install.sh" -o /tmp/linux-install.sh; \
    bash /tmp/linux-install.sh; \
    rm /tmp/linux-install.sh; \
    clojure --version

ARG CLOJURE_LSP_VERSION=2026.07.06-14.34.19
RUN curl -fsSL "https://github.com/clojure-lsp/clojure-lsp/releases/download/${CLOJURE_LSP_VERSION}/clojure-lsp-native-static-linux-amd64.zip" -o /tmp/clojure-lsp.zip; \
    unzip /tmp/clojure-lsp.zip -d /usr/local/bin; \
    rm /tmp/clojure-lsp.zip; \
    clojure-lsp --version

ARG CLJ_KONDO_VERSION=2026.08.04
RUN curl -fsSL "https://github.com/clj-kondo/clj-kondo/releases/download/v${CLJ_KONDO_VERSION}/clj-kondo-${CLJ_KONDO_VERSION}-linux-static-amd64.zip" -o /tmp/clj-kondo.zip; \
    unzip /tmp/clj-kondo.zip -d /usr/local/bin; \
    rm /tmp/clj-kondo.zip; \
    clj-kondo --version

# Clojure MCP
ARG CLOJURE_MCP_VERSION=v0.5.1
ARG CLOJURE_MCP_SHA=c9cdd6bf61b2265954f0445f8bea389c50b09bcb
RUN clojure -Ttools install io.github.bhauman/clojure-mcp "{:git/tag \"${CLOJURE_MCP_VERSION}\" :git/sha \"${CLOJURE_MCP_SHA}\"}" :as mcp

ARG CLOJURE_MCP_LIGHT_VERSION=0.2.2
RUN bbin install https://github.com/bhauman/clojure-mcp-light.git --tag "v${CLOJURE_MCP_LIGHT_VERSION}"; \
    bbin install https://github.com/bhauman/clojure-mcp-light.git --tag "v${CLOJURE_MCP_LIGHT_VERSION}" --as clj-nrepl-eval --main-opts '["-m" "clojure-mcp-light.nrepl-eval"]'; \
    bbin install https://github.com/bhauman/clojure-mcp-light.git --tag "v${CLOJURE_MCP_LIGHT_VERSION}" --as clj-paren-repair --main-opts '["-m" "clojure-mcp-light.paren-repair"]'

ARG CLJ_SURGEON_SHA=8325ff425410569ddc7ecd2fd01afaf8f6a8b140
RUN bbin install https://github.com/realgenekim/clj-surgeon.git --git/sha "${CLJ_SURGEON_SHA}"; \
    curl -fsSL "https://raw.githubusercontent.com/realgenekim/clj-surgeon/${CLJ_SURGEON_SHA}/resources/clj-kondo-admission.py" -o /usr/local/bin/clj-kondo-admission; \
    chmod +x /usr/local/bin/clj-kondo-admission; \
    clj-surgeon --version
# :ls, :declares, and :fix-declares run clj-kondo through this gate and refuse without it.
ENV CLJ_SURGEON_CLJ_KONDO_ADMISSION=/usr/local/bin/clj-kondo-admission
# <<< clojure (managed) <<<

# >>> knot (managed) >>>
# Prebuilt binary (bundles its own babashka runtime); the installer verifies SHA256SUMS.
ARG KNOT_VERSION=0.15.0
RUN curl -fsSL https://raw.githubusercontent.com/UniSoma/knot/main/install.sh \
      | KNOT_VERSION="${KNOT_VERSION}" KNOT_INSTALL_DIR=/usr/local/bin sh; \
    knot --version
# <<< knot (managed) <<<
