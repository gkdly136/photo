#!/usr/bin/env bash
# Collect screenshots while the emulator is still running; preserve the test exit code.
set -u
./gradlew :app:connectedDebugAndroidTest --stacktrace
test_status=$?
mkdir -p verification
adb pull /sdcard/Android/data/com.framecoach.app/files/screenshots verification/screenshots || true
exit "$test_status"
