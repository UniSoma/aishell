# Aishell Base Image
# Debian-based container with dynamic user creation, gosu for user switching,
# and basic development tools for agentic AI harnesses.

FROM debian:bookworm-slim

# Avoid prompts during package installation
ENV DEBIAN_FRONTEND=noninteractive

# Install required packages in single layer
RUN apt-get update && apt-get install -y --no-install-recommends \
    bash \
    ca-certificates \
    curl \
    git \
    jq \
    less \
    ripgrep \
    sudo \
    vim \
    && rm -rf /var/lib/apt/lists/*

# Install gosu 1.19 for proper user switching
# Source: https://github.com/tianon/gosu
RUN set -eux; \
    dpkgArch="$(dpkg --print-architecture)"; \
    curl -fsSL "https://github.com/tianon/gosu/releases/download/1.19/gosu-${dpkgArch}" -o /usr/local/bin/gosu; \
    chmod +x /usr/local/bin/gosu; \
    gosu --version; \
    gosu nobody true

# Copy entrypoint script
COPY entrypoint.sh /usr/local/bin/entrypoint.sh
RUN chmod +x /usr/local/bin/entrypoint.sh

# Copy custom bashrc
COPY bashrc.aishell /etc/bash.aishell

ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
CMD ["/bin/bash"]
