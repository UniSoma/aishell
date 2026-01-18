#!/bin/bash
# test-parse-config.sh - Unit tests for parse_run_conf function
# Tests the runtime configuration parsing in aishell
#
# Usage: ./test-parse-config.sh
#
# Exit codes:
#   0 - All tests passed
#   N - N tests failed

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Color definitions for test output
RED='\033[0;31m'
GREEN='\033[0;32m'
NC='\033[0m'

# Setup test directory
TEST_DIR="/tmp/test-aishell-$$"
mkdir -p "$TEST_DIR/.aishell"
trap "rm -rf $TEST_DIR" EXIT

PASSED=0
FAILED=0
TOTAL=0

# Include the parse_run_conf function directly (same as in aishell)
# This avoids Docker dependency from main() in aishell
readonly RUNCONF_ALLOWED_VARS="MOUNTS|ENV|PORTS|DOCKER_ARGS"

parse_run_conf() {
    local config_file="$1"
    local line_num=0

    # Initialize config variables
    declare -g CONF_MOUNTS=""
    declare -g CONF_ENV=""
    declare -g CONF_PORTS=""
    declare -g CONF_DOCKER_ARGS=""

    # No config file is valid (use defaults)
    [[ ! -f "$config_file" ]] && return 0

    while IFS= read -r line || [[ -n "$line" ]]; do
        ((line_num++))

        # Skip empty lines (including whitespace-only)
        [[ -z "${line// /}" ]] && continue

        # Skip comments
        [[ "$line" =~ ^[[:space:]]*# ]] && continue

        # Trim leading whitespace
        line="${line#"${line%%[![:space:]]*}"}"
        # Trim trailing whitespace
        line="${line%"${line##*[![:space:]]}"}"

        # Validate: ALLOWED_VAR=value or ALLOWED_VAR="value"
        if [[ "$line" =~ ^($RUNCONF_ALLOWED_VARS)=(.*)$ ]]; then
            local var_name="${BASH_REMATCH[1]}"
            local var_value="${BASH_REMATCH[2]}"

            # Remove surrounding quotes (double or single)
            if [[ "$var_value" =~ ^\"(.*)\"$ ]]; then
                var_value="${BASH_REMATCH[1]}"
            elif [[ "$var_value" =~ ^\'(.*)\'$ ]]; then
                var_value="${BASH_REMATCH[1]}"
            fi

            # Set CONF_ prefixed variable
            declare -g "CONF_${var_name}=$var_value"
        else
            # Provide helpful error with line number and context
            echo -e "${RED}Config error${NC} in $config_file line $line_num:" >&2
            echo "  $line" >&2
            echo "" >&2
            echo "Expected format: VARIABLE=value or VARIABLE=\"value with spaces\"" >&2
            echo "Allowed variables: MOUNTS, ENV, PORTS, DOCKER_ARGS" >&2
            return 1
        fi
    done < "$config_file"

    return 0
}

# Test helper
pass() {
    echo -e "${GREEN}PASSED${NC}"
    ((PASSED++))
}

fail() {
    echo -e "${RED}FAILED${NC} $1"
    ((FAILED++))
}

echo "Running parse_run_conf unit tests..."
echo ""

# ============================================================================
# Test 1: Missing config file returns 0 with empty CONF_* variables
# ============================================================================
((TOTAL++))
echo -n "Test $TOTAL: Missing config file returns 0... "
if parse_run_conf "$TEST_DIR/.aishell/nonexistent.conf" 2>/dev/null; then
    if [[ -z "$CONF_MOUNTS" && -z "$CONF_ENV" && -z "$CONF_PORTS" && -z "$CONF_DOCKER_ARGS" ]]; then
        pass
    else
        fail "(variables not empty)"
    fi
else
    fail "(non-zero exit)"
fi

# ============================================================================
# Test 2: Empty config file returns 0 with empty CONF_* variables
# ============================================================================
((TOTAL++))
echo -n "Test $TOTAL: Empty config file returns 0... "
> "$TEST_DIR/.aishell/run.conf"
if parse_run_conf "$TEST_DIR/.aishell/run.conf" 2>/dev/null; then
    if [[ -z "$CONF_MOUNTS" ]]; then
        pass
    else
        fail "(CONF_MOUNTS not empty)"
    fi
else
    fail "(non-zero exit)"
fi

# ============================================================================
# Test 3: Comments only returns 0 with empty CONF_* variables
# ============================================================================
((TOTAL++))
echo -n "Test $TOTAL: Comments only returns 0... "
cat > "$TEST_DIR/.aishell/run.conf" << 'EOF'
# This is a comment
# Another comment
   # Indented comment
EOF
if parse_run_conf "$TEST_DIR/.aishell/run.conf" 2>/dev/null; then
    if [[ -z "$CONF_MOUNTS" ]]; then
        pass
    else
        fail "(CONF_MOUNTS not empty)"
    fi
else
    fail "(non-zero exit)"
fi

# ============================================================================
# Test 4: Valid single variable (MOUNTS with double quotes)
# ============================================================================
((TOTAL++))
echo -n "Test $TOTAL: Valid single variable (double quotes)... "
cat > "$TEST_DIR/.aishell/run.conf" << 'EOF'
MOUNTS="$HOME/.ssh"
EOF
if parse_run_conf "$TEST_DIR/.aishell/run.conf" 2>/dev/null; then
    if [[ "$CONF_MOUNTS" == '$HOME/.ssh' ]]; then
        pass
    else
        fail "(got: '$CONF_MOUNTS')"
    fi
else
    fail "(non-zero exit)"
fi

# ============================================================================
# Test 5: Valid all four variables parsed correctly
# ============================================================================
((TOTAL++))
echo -n "Test $TOTAL: Valid all four variables... "
cat > "$TEST_DIR/.aishell/run.conf" << 'EOF'
MOUNTS="$HOME/.ssh $HOME/.config/git"
ENV="EDITOR DEBUG_MODE=1"
PORTS="3000:3000"
DOCKER_ARGS="--cap-add=SYS_PTRACE"
EOF
if parse_run_conf "$TEST_DIR/.aishell/run.conf" 2>/dev/null; then
    if [[ "$CONF_MOUNTS" == '$HOME/.ssh $HOME/.config/git' && \
          "$CONF_ENV" == 'EDITOR DEBUG_MODE=1' && \
          "$CONF_PORTS" == '3000:3000' && \
          "$CONF_DOCKER_ARGS" == '--cap-add=SYS_PTRACE' ]]; then
        pass
    else
        fail "(values incorrect)"
    fi
else
    fail "(non-zero exit)"
fi

# ============================================================================
# Test 6: Invalid variable name produces error (exits 1)
# ============================================================================
((TOTAL++))
echo -n "Test $TOTAL: Invalid variable name produces error... "
cat > "$TEST_DIR/.aishell/run.conf" << 'EOF'
INVALID_VAR=something
EOF
if parse_run_conf "$TEST_DIR/.aishell/run.conf" 2>/dev/null; then
    fail "(should have failed)"
else
    pass
fi

# ============================================================================
# Test 7: Unquoted value works (no quotes required for simple values)
# ============================================================================
((TOTAL++))
echo -n "Test $TOTAL: Unquoted value works... "
cat > "$TEST_DIR/.aishell/run.conf" << 'EOF'
MOUNTS=$HOME/.ssh
EOF
if parse_run_conf "$TEST_DIR/.aishell/run.conf" 2>/dev/null; then
    if [[ "$CONF_MOUNTS" == '$HOME/.ssh' ]]; then
        pass
    else
        fail "(got: '$CONF_MOUNTS')"
    fi
else
    fail "(non-zero exit)"
fi

# ============================================================================
# Test 8: Single quotes work
# ============================================================================
((TOTAL++))
echo -n "Test $TOTAL: Single quotes work... "
cat > "$TEST_DIR/.aishell/run.conf" << 'EOF'
MOUNTS='$HOME/.ssh'
EOF
if parse_run_conf "$TEST_DIR/.aishell/run.conf" 2>/dev/null; then
    if [[ "$CONF_MOUNTS" == '$HOME/.ssh' ]]; then
        pass
    else
        fail "(got: '$CONF_MOUNTS')"
    fi
else
    fail "(non-zero exit)"
fi

# ============================================================================
# Test 9: Error message includes line number
# ============================================================================
((TOTAL++))
echo -n "Test $TOTAL: Error message includes line number... "
cat > "$TEST_DIR/.aishell/run.conf" << 'EOF'
MOUNTS=$HOME/.ssh
PORTS=3000:3000
INVALID_VAR=something
EOF
output=$(parse_run_conf "$TEST_DIR/.aishell/run.conf" 2>&1) || true
if [[ "$output" == *"line 3"* ]]; then
    pass
else
    fail "(line number not in error)"
fi

# ============================================================================
# Test 10: Error message includes allowed variables
# ============================================================================
((TOTAL++))
echo -n "Test $TOTAL: Error message lists allowed variables... "
cat > "$TEST_DIR/.aishell/run.conf" << 'EOF'
BAD=value
EOF
output=$(parse_run_conf "$TEST_DIR/.aishell/run.conf" 2>&1) || true
if [[ "$output" == *"MOUNTS, ENV, PORTS, DOCKER_ARGS"* ]]; then
    pass
else
    fail "(allowed variables not listed)"
fi

# ============================================================================
# Test 11: Whitespace handling - leading spaces
# ============================================================================
((TOTAL++))
echo -n "Test $TOTAL: Leading spaces trimmed... "
cat > "$TEST_DIR/.aishell/run.conf" << 'EOF'
  MOUNTS=$HOME/.ssh
EOF
if parse_run_conf "$TEST_DIR/.aishell/run.conf" 2>/dev/null; then
    if [[ "$CONF_MOUNTS" == '$HOME/.ssh' ]]; then
        pass
    else
        fail "(got: '$CONF_MOUNTS')"
    fi
else
    fail "(non-zero exit)"
fi

# ============================================================================
# Test 12: Whitespace handling - tabs
# ============================================================================
((TOTAL++))
echo -n "Test $TOTAL: Tab indentation trimmed... "
printf 'MOUNTS=$HOME/.ssh\n\tPORTS=3000:3000\n' > "$TEST_DIR/.aishell/run.conf"
if parse_run_conf "$TEST_DIR/.aishell/run.conf" 2>/dev/null; then
    if [[ "$CONF_PORTS" == '3000:3000' ]]; then
        pass
    else
        fail "(got: '$CONF_PORTS')"
    fi
else
    fail "(non-zero exit)"
fi

# ============================================================================
# Test 13: Mixed valid/invalid - first invalid line causes error at correct line
# ============================================================================
((TOTAL++))
echo -n "Test $TOTAL: First invalid line causes error at correct line... "
cat > "$TEST_DIR/.aishell/run.conf" << 'EOF'
MOUNTS=$HOME/.ssh
INVALID=bad
PORTS=3000:3000
EOF
output=$(parse_run_conf "$TEST_DIR/.aishell/run.conf" 2>&1) || true
if [[ "$output" == *"line 2"* ]]; then
    pass
else
    fail "(line 2 not in error)"
fi

# ============================================================================
# Test 14: Empty value allowed
# ============================================================================
((TOTAL++))
echo -n "Test $TOTAL: Empty value allowed... "
cat > "$TEST_DIR/.aishell/run.conf" << 'EOF'
MOUNTS=
EOF
if parse_run_conf "$TEST_DIR/.aishell/run.conf" 2>/dev/null; then
    if [[ "$CONF_MOUNTS" == '' ]]; then
        pass
    else
        fail "(got: '$CONF_MOUNTS')"
    fi
else
    fail "(non-zero exit)"
fi

# ============================================================================
# Test 15: Value with equals sign inside
# ============================================================================
((TOTAL++))
echo -n "Test $TOTAL: Value with equals sign inside... "
cat > "$TEST_DIR/.aishell/run.conf" << 'EOF'
ENV="DEBUG_MODE=1 LOG_LEVEL=debug"
EOF
if parse_run_conf "$TEST_DIR/.aishell/run.conf" 2>/dev/null; then
    if [[ "$CONF_ENV" == 'DEBUG_MODE=1 LOG_LEVEL=debug' ]]; then
        pass
    else
        fail "(got: '$CONF_ENV')"
    fi
else
    fail "(non-zero exit)"
fi

# ============================================================================
# Test 16: Each valid variable is independent
# ============================================================================
((TOTAL++))
echo -n "Test $TOTAL: Only specified variable is set... "
cat > "$TEST_DIR/.aishell/run.conf" << 'EOF'
PORTS=8080:80
EOF
if parse_run_conf "$TEST_DIR/.aishell/run.conf" 2>/dev/null; then
    if [[ -z "$CONF_MOUNTS" && -z "$CONF_ENV" && "$CONF_PORTS" == '8080:80' && -z "$CONF_DOCKER_ARGS" ]]; then
        pass
    else
        fail "(other variables not empty)"
    fi
else
    fail "(non-zero exit)"
fi

# ============================================================================
# Summary
# ============================================================================
echo ""
echo "============================================"
echo "Results: $PASSED passed, $FAILED failed (of $TOTAL)"
echo "============================================"

if [[ $FAILED -gt 0 ]]; then
    exit 1
fi
exit 0
