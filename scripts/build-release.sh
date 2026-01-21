#!/bin/bash
set -euo pipefail

# Build aishell uberscript for distribution
# Usage: ./scripts/build-release.sh
#
# Produces:
#   dist/aishell       - Executable uberscript with shebang
#   dist/aishell.sha256 - SHA256 checksum file
#
# Version is defined in src/aishell/cli.clj - update there before release.

OUTPUT_DIR="dist"
OUTPUT_FILE="${OUTPUT_DIR}/aishell"

echo "Building aishell uberscript..."

# Create output directory
mkdir -p "$OUTPUT_DIR"

# Remove existing uberscript (bb uberscript refuses to overwrite)
rm -f "$OUTPUT_FILE"

# Build uberscript with main namespace
# This bundles all namespaces reachable via static requires from core.clj
bb uberscript "$OUTPUT_FILE" -m aishell.core

# Add shebang for direct execution
# Platform-specific sed -i handling
if [[ "$(uname)" == "Darwin" ]]; then
    sed -i '' '1s/^/#!\/usr\/bin\/env bb\'$'\n/' "$OUTPUT_FILE"
else
    sed -i '1s/^/#!\/usr\/bin\/env bb\n/' "$OUTPUT_FILE"
fi

# Make executable
chmod +x "$OUTPUT_FILE"

# Generate checksum
# Use sha256sum on Linux, shasum on macOS
# Format: {hash}  {filename} (two spaces, relative filename for portability)
cd "$OUTPUT_DIR"
if command -v sha256sum &>/dev/null; then
    sha256sum aishell > aishell.sha256
else
    shasum -a 256 aishell > aishell.sha256
fi
cd - >/dev/null

echo ""
echo "Build complete!"
echo "  Binary:   $OUTPUT_FILE"
echo "  Checksum: ${OUTPUT_FILE}.sha256"
echo ""
cat "${OUTPUT_FILE}.sha256"
