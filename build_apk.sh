#!/bin/bash
set -e

echo "Building Live-Gig-Player Pro debug APK..."
./gradlew assembleDebug

APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
echo ""
echo "Done! APK is at: $APK_PATH"
echo "Copy to phone: adb install $APK_PATH"
