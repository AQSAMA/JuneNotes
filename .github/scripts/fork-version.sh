#!/usr/bin/env bash
set -euo pipefail
# Keep upstream M mm pp b literals; only configured forks use the CI sequence.
if [[ -n "${JUNE_VERSION_CODE_BASE:-}" ]]; then
  [[ "$JUNE_VERSION_CODE_BASE" =~ ^[0-9]+$ ]]
  (( GITHUB_RUN_ATTEMPT > 0 && GITHUB_RUN_ATTEMPT < 100 ))
  code=$((JUNE_VERSION_CODE_BASE + GITHUB_RUN_NUMBER * 100 + GITHUB_RUN_ATTEMPT))
  (( code >= 100000 && code <= 2100000000 ))
  echo "ORG_GRADLE_PROJECT_juneVersionCode=$code" >> "$GITHUB_ENV"
else
  code=$(sed -n 's/^val appVersionCode = \([0-9]*\)$/\1/p' app/build.gradle.kts)
  [[ "$code" =~ ^[0-9]+$ ]]
fi
echo "JUNE_BUILD_VERSION_CODE=$code" >> "$GITHUB_ENV"
echo "APK versionCode: $code"
if [[ -n "${GITHUB_OUTPUT:-}" ]]; then echo "version_code=$code" >> "$GITHUB_OUTPUT"; fi
