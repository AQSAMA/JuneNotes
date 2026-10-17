#!/usr/bin/env bash
set -euo pipefail
release_base="$(sed -nE 's/^val appVersionCode = ([0-9]+)$/\1/p' "${1:-app/build.gradle.kts}")"
run_number="${GITHUB_RUN_NUMBER:-}"
if [[ ! "$release_base" =~ ^[0-9]+$ || ! "$run_number" =~ ^[0-9]+$ ]]; then
  echo 'A literal appVersionCode and numeric GITHUB_RUN_NUMBER are required.' >&2
  exit 1
fi
printf '%s\n' "$((10#$release_base + 10#$run_number))"
