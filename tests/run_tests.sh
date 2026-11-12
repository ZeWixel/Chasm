#!/usr/bin/env bash
# Headless regression tests for pickups, visibility and rendering.
# Builds the suite first (releases/ChasmTheRift.jar), then runs the tests on the JDK 8
# that build.sh downloaded, with the J2ME stub classes on the classpath.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JDK_HOME="/tmp/jdk8"
SUITE_DIR="/tmp/LLM-J2ME-SUITE"
OUT_DIR="/tmp/chasm_tests"
BOOT_CP="${SUITE_DIR}/uei/cldc11.jar:${SUITE_DIR}/uei/midp21.jar:${SUITE_DIR}/uei/nokiaui.jar:${SUITE_DIR}/uei/jsr135.jar"

cd "${ROOT_DIR}"
python3 tests/test_pvs.py
./build.sh

rm -rf "${OUT_DIR}"
mkdir -p "${OUT_DIR}"
"${JDK_HOME}/bin/javac" -encoding UTF-8 \
    -cp "releases/ChasmTheRift.jar:${BOOT_CP}" \
    -d "${OUT_DIR}" tests/chasm/*.java

status=0
for t in PickupTest LevelItemTest KeyPosTest RenderProfileTest ViewportScalerTest EntityBucketsTest FlatRasterTest WallFrustumTest; do
    echo "=== ${t} ==="
    if ! "${JDK_HOME}/bin/java" -cp "${OUT_DIR}:releases/ChasmTheRift.jar:${BOOT_CP}" "chasm.${t}"; then
        status=1
    fi
done
if [ "${status}" -ne 0 ]; then
    echo "=== TESTS FAILED ==="
    exit "${status}"
fi
echo "=== ALL TESTS PASSED ==="
