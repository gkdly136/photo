#!/usr/bin/env bash
# Collect screenshots while the emulator is still running; preserve the test exit code.
set -u
./gradlew :app:connectedDebugAndroidTest --stacktrace
test_status=$?
mkdir -p verification
adb pull /sdcard/Download/FrameCoachTestScreenshots verification/screenshots
capture_status=$?
if [ "$test_status" -ne 0 ]; then exit "$test_status"; fi
exit "$capture_status"
