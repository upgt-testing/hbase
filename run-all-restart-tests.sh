#!/bin/bash

# Script to run HBase restart tests using the Restart Testing Framework
# This script will:
# 1. Run restart-test:run for the hbase-server module
# 2. Collect all outputs to a timestamped directory
#
# Usage: ./run-all-restart-tests.sh [config-file]
#   config-file: Optional path to restart configuration JSON file
#                If not provided, uses the default configuration

set -e  # Exit on error

# Parse command line arguments
CONFIG_FILE=""
if [ $# -gt 0 ]; then
    if [ "$1" == "-h" ] || [ "$1" == "--help" ]; then
        echo "Usage: $0 [config-file]"
        echo ""
        echo "Arguments:"
        echo "  config-file    Optional path to restart configuration JSON file"
        echo "                 If not provided, uses the default configuration"
        echo ""
        echo "Examples:"
        echo "  $0"
        echo "  $0 hbase-server/restart-config.json"
        echo "  $0 hbase-server/restarts-config/restart-config-master.json"
        exit 0
    fi
    CONFIG_FILE="$1"
    if [ ! -f "${CONFIG_FILE}" ]; then
        echo "Error: Configuration file not found: ${CONFIG_FILE}"
        exit 1
    fi
fi

# Generate timestamp for output directory
TIMESTAMP=$(date +"%Y%m%d-%H%M%S")
OUTPUT_DIR="restart-test-output-${TIMESTAMP}"

echo "=========================================="
echo "HBase Restart Testing Framework"
echo "=========================================="
echo "Output directory: ${OUTPUT_DIR}"
if [ -n "${CONFIG_FILE}" ]; then
    echo "Config file: ${CONFIG_FILE}"
else
    echo "Config file: Default configuration"
fi
echo ""

# Create output directory
mkdir -p "${OUTPUT_DIR}"

# Define the module with restart tests
MODULE="hbase-server"

echo "Running restart tests for: ${MODULE}"
echo ""

# Track overall results
PASSED=0
FAILED=0

echo "=========================================="
echo "Module: ${MODULE}"
echo "=========================================="

# Create module-specific output directory
MODULE_OUTPUT="${OUTPUT_DIR}/${MODULE}"
mkdir -p "${MODULE_OUTPUT}"

# Log file for this module
LOG_FILE="${MODULE_OUTPUT}/test-execution.log"

echo "Running restart tests..."
echo "Log file: ${LOG_FILE}"
echo "Start time: $(date '+%H:%M:%S')"
echo ""
echo "Maven output:"
echo "------------------------------------------"

# Record start time
MODULE_START=$(date +%s)

# Build Maven command with optional config file
MVN_CMD="mvn restart-test:run -Drestart.failOnError=false"
if [ -n "${CONFIG_FILE}" ]; then
    # Convert to absolute path if relative
    ABS_CONFIG_FILE=$(realpath "${CONFIG_FILE}")
    MVN_CMD="${MVN_CMD} -Drestart.config=${ABS_CONFIG_FILE}"
fi

# Run the restart tests and capture full Maven output
# Use tee to both display and log all output
if (cd "${MODULE}" && ${MVN_CMD} 2>&1) | tee "${LOG_FILE}"; then
    MODULE_END=$(date +%s)
    MODULE_DURATION=$((MODULE_END - MODULE_START))
    echo ""
    echo "✓ Module tests completed successfully (took ${MODULE_DURATION}s)"
    PASSED=1

    # Copy report files if they exist
    if [ -d "${MODULE}/target/restart-reports" ]; then
        cp -r "${MODULE}/target/restart-reports"/* "${MODULE_OUTPUT}/" 2>/dev/null || true
        echo "  Reports copied to ${MODULE_OUTPUT}/"
    fi
else
    MODULE_END=$(date +%s)
    MODULE_DURATION=$((MODULE_END - MODULE_START))
    echo ""
    echo "✗ Module tests failed or had errors (took ${MODULE_DURATION}s)"
    FAILED=1

    # Still copy report files even if tests failed
    if [ -d "${MODULE}/target/restart-reports" ]; then
        cp -r "${MODULE}/target/restart-reports"/* "${MODULE_OUTPUT}/" 2>/dev/null || true
        echo "  Reports copied to ${MODULE_OUTPUT}/"
    fi
fi

echo ""

# Generate summary report
SUMMARY_FILE="${OUTPUT_DIR}/SUMMARY.txt"
cat > "${SUMMARY_FILE}" << EOF
========================================
HBase Restart Test Summary
========================================
Timestamp: ${TIMESTAMP}
Date: $(date)

Module: ${MODULE}
Config: $([ -n "${CONFIG_FILE}" ] && echo "${CONFIG_FILE}" || echo "Default")
Status: $([ ${PASSED} -eq 1 ] && echo "PASSED" || echo "FAILED")
Duration: ${MODULE_DURATION}s

EOF

echo ""
cat "${SUMMARY_FILE}"

echo ""
echo "=========================================="
echo "Test Execution Complete"
echo "=========================================="
echo "All outputs saved to: ${OUTPUT_DIR}/"
echo "Summary: ${SUMMARY_FILE}"
echo ""

# List all HTML reports
echo "HTML Reports:"
find "${OUTPUT_DIR}" -name "*.html" -type f | while read -r report; do
    echo "  - ${report}"
done

echo ""

# Exit with error if module failed
if [ ${FAILED} -gt 0 ]; then
    echo "⚠ Warning: Module had test failures"
    echo "Check module logs and reports for details"
    exit 1
else
    echo "✓ Module completed successfully"
    exit 0
fi
