#!/usr/bin/env bash
# Collect screenshots while the emulator is still running; preserve the test exit code.
set -u
# A cold Gradle build can outlast the emulator's screen timeout and leave Espresso behind
# the keyguard. Prepare the test device explicitly; never change the app's lock-screen flags.
adb shell settings put system screen_off_timeout 1800000
adb shell svc power stayon true
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
# Finish CPU-heavy compilation before driving the UI. On cold CI boots, Quickstep can
# ANR during compilation and retain a system modal over every ActivityScenario window.
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest --stacktrace
build_status=$?
if [ "$build_status" -ne 0 ]; then exit "$build_status"; fi
# Only reset the emulator launcher, never FrameCoach or its error dialogs.
adb shell am force-stop com.android.launcher3
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
./gradlew :app:connectedDebugAndroidTest --stacktrace
test_status=$?
mkdir -p verification
if [ "$test_status" -ne 0 ]; then
  mkdir -p verification/screenshots
  adb shell dumpsys window > verification/window-state.txt
  adb shell screencap -p /sdcard/FrameCoach-test-failure.png
  adb pull /sdcard/FrameCoach-test-failure.png verification/screenshots/failure.png
fi
adb pull /sdcard/Pictures/FrameCoachTestScreenshots verification/screenshots
capture_status=$?
if [ "$test_status" -ne 0 ]; then exit "$test_status"; fi
exit "$capture_status"
