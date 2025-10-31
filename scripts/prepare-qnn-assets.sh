#!/usr/bin/env bash
set -euo pipefail

# Copy required Qualcomm QNN runtime libraries into app assets for packaging.
# Usage:
#   export QNN_SDK_ROOT=/path/to/QNN_SDK_2.xx
#   ./scripts/prepare-qnn-assets.sh

if [[ -z "${QNN_SDK_ROOT:-}" ]]; then
  echo "[ERROR] QNN_SDK_ROOT is not set. Please export QNN_SDK_ROOT to your Qualcomm QNN SDK root directory." >&2
  exit 1
fi

DEST_DIR="$(cd "$(dirname "$0")"/.. && pwd)/app/src/main/assets/qnnlibs"
mkdir -p "$DEST_DIR"

copy() {
  local src="$1"; shift
  local dst="$DEST_DIR/$(basename "$src")"
  if [[ -f "$src" ]]; then
    install -m 0644 "$src" "$dst"
    echo "Copied $(basename "$src")"
  else
    echo "[WARN] Missing: $src" >&2
  fi
}

# Core Android (arm64) libs
copy "$QNN_SDK_ROOT/lib/aarch64-android/libQnnHtp.so"
copy "$QNN_SDK_ROOT/lib/aarch64-android/libQnnSystem.so"

# Stub and Hexagon HTP libs for multiple SoC generations
for v in 68 69 73 75 79 81; do
  copy "$QNN_SDK_ROOT/lib/aarch64-android/libQnnHtpV${v}Stub.so"
  copy "$QNN_SDK_ROOT/lib/hexagon-v${v}/unsigned/libQnnHtpV${v}.so"
  copy "$QNN_SDK_ROOT/lib/hexagon-v${v}/unsigned/libQnnHtpV${v}Skel.so"
done

echo "\nQNN libraries prepared in: $DEST_DIR"
echo "Package an APK now, or run Gradle assemble tasks."
