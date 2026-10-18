#!/usr/bin/env python3
"""Reject publication of an older CI run over an update already offered to users."""
import json
import os
import re
import subprocess
import sys

channel, code = sys.argv[1], int(sys.argv[2])
pages = json.loads(subprocess.check_output([
    "gh", "api", f"repos/{os.environ['GITHUB_REPOSITORY']}/releases", "--paginate", "--slurp"
], text=True))
for page in pages:
    for release in page:
        if release["draft"]:
            continue
        if channel == "preview":
            if release["tag_name"] != "preview":
                continue
        elif release["prerelease"]:
            continue
        match = re.search(r"<!-- junenotes-version-code: ([0-9]+) -->", release.get("body") or "")
        if match and code <= int(match[1]):
            raise SystemExit(f"Refusing versionCode {code}; {release['tag_name']} already published {match[1]}. Start a new workflow run.")
print(f"Publication versionCode {code} is newer than published {channel} updates.")
