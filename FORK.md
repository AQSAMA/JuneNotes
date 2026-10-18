# JuneNotes fork operations

JuneNotes preserves June's namespaces, Kotlin packages, FOSS/Play flavors and
Release/Preview/Debug build types. The checked-in Gradle defaults still build June.
Fork identity is supplied by repository variables, or equivalent Gradle properties:

| Repository variable | Gradle property | JuneNotes value |
| --- | --- | --- |
| `JUNE_APPLICATION_ID` | `juneApplicationId` | `com.aqsama.junenotes` |
| `JUNE_APP_NAME` | `juneAppName` | `JuneNotes` |
| `JUNE_UPDATE_REPOSITORY` | `juneUpdateRepository` | `AQSAMA/JuneNotes` |

For example, `bash gradlew assembleFossDebug -PjuneApplicationId=com.aqsama.junenotes
-PjuneAppName=JuneNotes -PjuneUpdateRepository=AQSAMA/JuneNotes` (on one line).
Production is `com.aqsama.junenotes`; Preview and Debug add `.preview` and `.debug`.
Play retains its additional `.play` suffix. FileProvider authorities already use
`${applicationId}`. These apps have separate private data; use June's export/import
for migration from the original application. Never change a published ID or key.

## Signing and recovery

Permanent credentials are **environment secrets** in the `signing` environment,
not repository secrets. Its selected deployment refs must remain `main` (branch)
and `v*` (tags). Do not allow PR merge refs, wildcards for arbitrary branches, or
`pull_request_target` jobs that execute PR code. A temporary exact branch policy
may be used to verify a reviewed implementation before merge, then removed.
GitHub checks these restrictions before releasing environment secrets:
https://docs.github.com/en/actions/reference/workflows-and-actions/deployments-and-environments

Production uses `RELEASE_KEYSTORE` (base64 PKCS12), `RELEASE_STORE_PASSWORD`,
`RELEASE_KEY_ALIAS`, and `RELEASE_KEY_PASSWORD`. Preview uses
`JUNE_PREVIEW_KEYSTORE_BASE64`, `JUNE_PREVIEW_STORE_PASSWORD`,
`JUNE_PREVIEW_KEY_ALIAS`, and `JUNE_PREVIEW_KEY_PASSWORD`.
`JUNE_RELEASE_CERT_SHA256` and `JUNE_PREVIEW_CERT_SHA256` are public repository
variables checked against the actual APK certificates. Keys never use Actions Cache.
PR artifacts use ephemeral debug signing and are labelled as test builds. They
cannot update permanent Preview installs. Main and trusted manual builds use the
permanent Preview key; main publishes only after all existing test jobs succeed.

Local private material is outside Git:

- `~/.local/share/junenotes-signing/{production,preview}/signing.p12`
- Each channel's `credentials.json` contains the alias and generated passwords.
- Public `certificate.der` and `SHA256.txt` identify each signing certificate.
- Local backup: `~/.local/share/junenotes-signing-backup-20261018/`.

Directories are mode 700 and private files mode 600. Keep an **encrypted off-device
backup** of both directories before uninstalling Termux or resetting this phone.
The second copy on this phone does not protect against device loss. GitHub cannot
return secret values. Never generate a replacement key for a published channel.

To restore, copy the same channel directory with permissions preserved, compare
`certificate.der` with `SHA256.txt`, and export its credential values only inside a
local script. Pipe base64 of `signing.p12` into
`gh secret set RELEASE_KEYSTORE -R AQSAMA/JuneNotes --env signing`; pipe each
credential into its matching `gh secret set` command. Use the Preview names for
Preview. Do not pass passwords in command arguments or print them. Verify the
certificate using `keytool -exportcert -storepass:env` with a process-local password
environment variable and SHA-256; never upload private files as artifacts.

## Versions and releases

Upstream literal `appVersionCode` / `appVersionName` and the M mm pp b convention
remain unchanged. Without fork variables, CI and local builds keep those defaults.
`previewVersionCode` remains supported and applies only to Preview variants.
For JuneNotes CI, `JUNE_VERSION_CODE_BASE=100000000` supplies a per-channel code:
`base + GITHUB_RUN_NUMBER * 100 + GITHUB_RUN_ATTEMPT` (attempts 1–99).
FOSS Release and Preview have independent workflow counters and independent IDs.
Never delete/recreate these workflows or lower the base after publication. If
moving CI, choose a new base above every published APK's versionCode. Local builds
intended to update CI installs must pass a higher `-PjuneVersionCode=...` and the
same signing key. Publishing refuses a code at or below an already published
channel update, including reruns of older builds. Ordinary local builds remain
development builds.

- Production: dispatch **FOSS Release Build** on main for a signed artifact, or
  push a new `v*` tag from reviewed main history to create a stable GitHub Release.
  No Google Play account or credentials are needed. Do not reuse upstream's tags
  for fork releases; use a new tag such as `v1.3.1-junenotes.1` for a reviewed v1.3.1 base.
- Preview: main builds replace the `preview` prerelease APK. The tag identifies the
  channel; the release notes identify the exact source SHA and APK versionCode.
  Dispatch **Preview APK** on main to rebuild it. `test_only=true` enables tests
  and temporary signatures on sync branches without entering `signing`.
- Updates: FOSS Production reads this fork's latest stable release; Preview reads
  `/releases/tags/preview`. Fork notes carry `<!-- junenotes-version-code: N -->`
  so builds sharing an upstream versionName can still detect updates. With no
  repository override, June keeps its original latest-release/semantic comparison.
- Play: disabled on the fork with `JUNE_ENABLE_PLAY_RELEASE=false`. Deliberately
  enabling it requires a new Play listing for the fork's `.play` package and its
  own credentials. The upstream publishing defaults remain available upstream.

Android's identity/signing requirements are documented at
https://developer.android.com/build/configure-app-module.

## Upstream sync and clean contributions

`upstream` points to `https://github.com/DenserMeerkat/June.git`. The weekly/manual
**Propose upstream sync** workflow merges upstream/main into a new sync branch and
opens a PR, without rewriting main or automatically merging. It explicitly
starts Preview in `test_only` mode because PRs created with GITHUB_TOKEN do not
trigger pull_request CI. Review that dispatched run and the merge before merging.
Conflicts stop the workflow without modifying main; resolve them locally:

```sh
git fetch origin
git fetch upstream
git switch -c sync/upstream-manual origin/main
git merge upstream/main
# Resolve conflicts, retain fork inputs, run checks, and commit the merge.
git push -u origin HEAD
gh pr create --repo AQSAMA/JuneNotes --base main
```

Upstream changes enter the existing fork history through reviewed merge PRs.
Sync commits may need small conflict resolutions in the Gradle/workflow files
listed here; retain the fork inputs and protected signing rules when resolving them.

Start upstream contributions from the upstream tree, never from JuneNotes main:

```sh
git fetch upstream
git worktree add -b contribute/my-fix ../june-upstream-fix upstream/main
cd ../june-upstream-fix
# Implement only the focused fix, or cherry-pick a commit without fork configuration.
git diff upstream/main...HEAD --stat
git push origin HEAD
gh pr create --repo DenserMeerkat/June --base main --head AQSAMA:contribute/my-fix
```

Do not merge JuneNotes main into contribution branches. Build input support and
the update hook are generic; fork variables, signing configuration and operations
can stay in this repository. No Room, sync-manifest or backup formats change here.
