#!/usr/bin/env python3
"""Verify the actual APK, rather than assuming Gradle selected the expected variant."""
import glob
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path

apk, expected_id, expected_code, *cert = sys.argv[1:]

def tool(name):
    installed = shutil.which(name)
    if installed:
        return installed
    sdk = os.environ.get("ANDROID_HOME") or os.environ["ANDROID_SDK_ROOT"]
    candidates = glob.glob(f"{sdk}/build-tools/*/{name}")
    return sorted(candidates, key=lambda p: [int(n) for n in re.findall(r"\d+", str(Path(p).parent.name))])[-1]

badging = subprocess.check_output([tool("aapt"), "dump", "badging", apk], text=True)
package = re.search(r"^package: name='([^']+)' versionCode='([0-9]+)'", badging)
assert package, "APK package metadata missing"
assert package[1] == expected_id, (package[1], expected_id)
assert package[2] == expected_code, (package[2], expected_code)
output = subprocess.check_output([tool("apksigner"), "verify", "--verbose", "--print-certs", apk], text=True)
fingerprint = re.search(r"Signer #1 certificate SHA-256 digest: ([a-fA-F0-9]+)", output)
assert fingerprint, "APK signing certificate missing"
if cert and cert[0]:
    assert fingerprint[1].lower() == cert[0].replace(":", "").lower(), "Unexpected signing certificate"
print(f"Verified {apk}: package={package[1]} versionCode={package[2]} SHA-256={fingerprint[1]}")
