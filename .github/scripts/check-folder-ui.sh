#!/usr/bin/env bash
# Keep failure screenshots: emulator-runner executes multiline input as separate commands.
set +e
# Compile before clearing the emulator launcher: it can ANR while Gradle is building
# and leave a system dialog that intercepts instrumentation MotionEvents.
bash gradlew :app:assembleFossDebug :app:assembleFossDebugAndroidTest -PenableAbiSplits=true --stacktrace
folder_ui_build_status=$?
if [ "$folder_ui_build_status" -ne 0 ]; then exit "$folder_ui_build_status"; fi
adb shell am force-stop com.google.android.apps.nexuslauncher
bash gradlew :app:connectedFossDebugAndroidTest -PenableAbiSplits=true --stacktrace
folder_ui_status=$?
adb pull /sdcard/Download/june-ui app/build/ui-checks
exit "$folder_ui_status"
