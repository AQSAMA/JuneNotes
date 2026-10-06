#!/usr/bin/env bash
# Keep failure screenshots: emulator-runner executes multiline input as separate commands.
set +e
bash gradlew :app:connectedFossDebugAndroidTest -PenableAbiSplits=true --stacktrace
folder_ui_status=$?
adb pull /sdcard/Download/june-ui app/build/ui-checks
exit "$folder_ui_status"
