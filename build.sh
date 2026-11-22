#!/usr/bin/env bash
# Build script for Antigravity Terminal Bridge APK
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "${SCRIPT_DIR}"

ANDROID_JAR="${ANDROID_JAR:-/data/data/com.termux/files/home/.gemini/antigravity-cli/scratch/android-34.jar}"
BUILD_DIR="${SCRIPT_DIR}/build"
CLASSES_DIR="${BUILD_DIR}/classes"
GEN_DIR="${BUILD_DIR}/gen"

mkdir -p "${CLASSES_DIR}" "${GEN_DIR}"

echo "[*] Generating R.java..."
aapt package -m -J "${GEN_DIR}" -M AndroidManifest.xml -S res -I "${ANDROID_JAR}"

echo "[*] Compiling Java sources..."
javac -cp "${ANDROID_JAR}" -d "${CLASSES_DIR}" \
    src/com/android/virtualization/terminal/*.java \
    $(find "${GEN_DIR}" -name "*.java" 2>/dev/null)

echo "[*] Compiling DEX bytecode with d8..."
d8 --output "${BUILD_DIR}/" "${CLASSES_DIR}/com/android/virtualization/terminal/"*.class

echo "[*] Packaging APK resources..."
rm -f "${BUILD_DIR}/unsigned.apk" "${BUILD_DIR}/mock-terminal-provider.apk"
aapt package -f -M AndroidManifest.xml -S res -I "${ANDROID_JAR}" \
    --min-sdk-version 26 --target-sdk-version 34 \
    -F "${BUILD_DIR}/unsigned.apk"

echo "[*] Adding classes.dex to APK..."
cd "${BUILD_DIR}"
aapt add unsigned.apk classes.dex
cd "${SCRIPT_DIR}"

echo "[*] Signing APK..."
if [ ! -f debug.keystore ]; then
    keytool -genkeypair -v -keystore debug.keystore -alias androiddebugkey \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -storepass android -keypass android \
        -dname "CN=Android Debug,O=Android,C=US"
fi

apksigner sign --ks debug.keystore --ks-pass pass:android \
    --out "${BUILD_DIR}/mock-terminal-provider.apk" "${BUILD_DIR}/unsigned.apk"

echo "[✓] APK successfully built at: ${BUILD_DIR}/mock-terminal-provider.apk"
