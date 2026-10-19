# JuneNotes: Codex Cloud handoff

Prepared 2026-10-19. Read this file, `.agents/AGENTS.md`, and `FORK.md` before editing.

## Current state and user instructions

- Repository: https://github.com/AQSAMA/JuneNotes
- Upstream: https://github.com/DenserMeerkat/June
- Handoff branch: `codex/cloud-handoff-20261019`.
- Code baseline: `13b941257dafc0815205595f5ee6486791af4054`.
- Source working branch and existing PR head: `codex/upstream-folder-gestures`.
- PR #5: https://github.com/AQSAMA/JuneNotes/pull/5 — OPEN, DRAFT.
- PR #6: https://github.com/AQSAMA/JuneNotes/pull/6 — already merged under earlier explicit authorization.
- Latest instruction is to preserve the work, publish this handoff branch, and stop. **Do not merge any PR or push to main.** Earlier permission to merge PR #6 does not authorize merging PR #5.
- The local working tree was clean at baseline. All implementation changes were already committed and pushed to the PR #5 branch. This handoff adds documentation without changing that implementation.
- The user requested both `@codex review` and an independent review method. Both were used. Review and CI are NOT clean; six remaining findings are listed below.

## Original goal and requirements

Make JuneNotes an independently installable, permanently updateable Android fork while preserving June's architecture, Kotlin namespaces/packages, Gradle flavors/build types, upstream defaults, history, and clean upstream contribution paths.

Required FOSS application IDs are Production `com.aqsama.junenotes`, Preview `com.aqsama.junenotes.preview`, and Debug `com.aqsama.junenotes.debug`. Identity and update repository must be configurable at build time; an unconfigured upstream checkout must still build the original June identity and use upstream releases. Preview and Production must install and update independently.

Use separate permanent Production/Preview keys, inspect existing keys before generating any, never silently rotate or overwrite a published identity, keep private backups outside Git, configure GitHub using `gh`, and disclose only public certificate fingerprints. Untrusted PR code must not receive permanent signing secrets. Keep useful temporary-signed PR artifacts and trusted, permanently signed Preview releases. Remove signing-key dependency on Actions Cache.

Preserve FOSS/Preview/Debug/Play mechanisms and sensible version conventions. Do not publish to upstream's Play package or require Play credentials for GitHub APK releases. Version codes must increase for each update. Configure the upstream remote and PR-based synchronization; contributions upstream must start from clean upstream branches without fork configuration. Respect existing local work and never reset fork history.

Inspect and test actual configuration, variants, APK identity/certificates/version codes where possible; distinguish successful checks from environmental blockers. Work through a dedicated PR, inspect CI and review findings, and fix them. Later instructions authorized merging PR #6 and continuing PR #5; the latest handoff instruction now prohibits any further merge.

The original environment was an Android phone running Termux. Lightweight packages were allowed; heavy builds/emulators were delegated to GitHub Actions. No manual authentication request was made.

## Independent fork setup: completed in PR #6

PR #6 reviewed head was `fb273800d62727d86e42e0e7f844d3c863671219`; Codex reported no findings. It merged as `2e8e62a5d7ef06cd3cc1fac1b84277de56a6bd79`. Main was changed through that PR merge, never by a direct push.

- Gradle inputs `juneApplicationId`, `juneAppName`, and `juneUpdateRepository` supply fork identity. Upstream defaults remain `com.denser.june`, `June`, and `DenserMeerkat/June`.
- Kotlin namespace/source packages remain unchanged. FOSS/Play flavors and Release/Preview/Debug build types remain. Play retains its additional `.play` suffix.
- FOSS Production checks the fork's latest stable GitHub release. Fork Preview checks the `preview` prerelease. Release notes carry `<!-- junenotes-version-code: N -->` so equal upstream version names can still update by numeric version code. Upstream's default release/semantic-version behavior remains available.
- Separate stable Production/Preview PKCS12 keys were generated with local `keytool` after checking for reusable keys. They are RSA 4096 with long validity. Private files were never committed.
- Eight signing secrets are in a protected GitHub **environment**, `signing`, restricted to branch `main` and tags `v*`. No repository-level signing secrets remain. A temporary implementation-branch exception used for verification was removed.
- PR builds use temporary signing, the `ci` environment, and useful APK/test artifacts. Main/trusted release jobs use permanent signing. No permanent keystore is stored in Actions Cache.
- Fork version codes use `JUNE_VERSION_CODE_BASE + GITHUB_RUN_NUMBER * 100 + GITHUB_RUN_ATTEMPT` (attempts 1–99). Preserve workflow counters and never lower the base. Preview and Production have independent IDs/counters.
- Play publishing is disabled for the fork. GitHub FOSS APK releases do not need Play credentials.
- Weekly/manual upstream sync merges upstream into a new branch and opens a PR, without resetting main. It explicitly dispatches temporary-signed tests because GITHUB_TOKEN-created PRs do not automatically trigger PR workflows.
- `FORK.md` documents operations, recovery, and upstream contribution worktrees. `README.md` links it; signing extensions are ignored.

Important setup files: `app/build.gradle.kts`, FOSS updater sources under `app/src/foss`, `.github/workflows/preview.yml`, `.github/workflows/foss_build.yml`, `.github/workflows/play_release.yml`, `.github/workflows/upstream-sync.yml`, `.github/scripts/fork-version.sh`, `.github/scripts/verify-apk.py`, `FORK.md`, `.gitignore`.

## Signing and GitHub configuration

Public certificate SHA-256 fingerprints (do not rotate these identities):

| Channel | SHA-256 |
| --- | --- |
| Production | `59e21b2c5db8d0541558c39edc6414c5ab98eb609490a1a693e0dca2f4336970` |
| Preview | `fc644081f45c23bd968b602c3afe8929196bbe6dee03b93e21e8163d94652ab7` |

Environment `signing` contains these secret **names**, never their values in this file:

- `RELEASE_KEYSTORE`
- `RELEASE_STORE_PASSWORD`
- `RELEASE_KEY_ALIAS`
- `RELEASE_KEY_PASSWORD`
- `JUNE_PREVIEW_KEYSTORE_BASE64`
- `JUNE_PREVIEW_STORE_PASSWORD`
- `JUNE_PREVIEW_KEY_ALIAS`
- `JUNE_PREVIEW_KEY_PASSWORD`

Repository variables (public configuration):

| Name | Value |
| --- | --- |
| `JUNE_APPLICATION_ID` | `com.aqsama.junenotes` |
| `JUNE_APP_NAME` | `JuneNotes` |
| `JUNE_UPDATE_REPOSITORY` | `AQSAMA/JuneNotes` |
| `JUNE_VERSION_CODE_BASE` | `100000000` |
| `JUNE_ENABLE_PLAY_RELEASE` | `false` |
| `JUNE_RELEASE_CERT_SHA256` | Production fingerprint above |
| `JUNE_PREVIEW_CERT_SHA256` | Preview fingerprint above |

CI supplies `ORG_GRADLE_PROJECT_juneApplicationId`, `ORG_GRADLE_PROJECT_juneAppName`, `ORG_GRADLE_PROJECT_juneUpdateRepository`, and a generated `juneVersionCode`. `previewVersionCode` remains supported for Preview variants. Cloud PR testing needs no permanent key or secret values. Never broaden `signing` environment access to a PR or arbitrary branch, and do not use `pull_request_target` to execute untrusted code with those secrets.

Private material is **only on the Termux phone**, not in this repository or automatically available to Cloud:

- `~/.local/share/junenotes-signing/{production,preview}/signing.p12`
- Each channel's `credentials.json`, public `certificate.der`, and `SHA256.txt`.
- Backup: `~/.local/share/junenotes-signing-backup-20261018/`.
- Recovery helper: `~/.local/share/junenotes-signing/restore-github-secrets.py` (also backed up). It validates the original fingerprints and environment restrictions before piping the same credentials to `gh secret set`.
- Local private directories are mode 700; keystores/credentials are mode 600. Backup bytes and permissions were verified.
- Verified downloaded APKs: `~/.local/share/junenotes-verified-apks/`.

Recovery uses the same keys, never replacement keys. GitHub cannot return secret values. An encrypted **off-device** backup is still a required user action; a second copy on this phone does not cover device loss. Do not commit or upload any private file to Cloud or PR artifacts.

## PR #5: implementation and decisions

PR #5 synchronizes upstream June v1.3.1 (`f7501172211624bdfdc2690a8c6794d2483bfafb`) and retains folder features and the fork configuration merged from main. Main was merged into the PR branch without rebasing or resetting history. Upstream version literals remain `appVersionCode = 103010`, `appVersionName = "1.3.1"` on this branch.

Folder work includes collapsed-branch search, expand/collapse all, selected-branch reveal, per-folder scrolling, RTL navigation, contextual moves, drag ownership/feedback/edge scrolling, Back cancellation, drawer resizing/reveal behavior, and fling cleanup. `FOLDERS.md` describes behavior. This work predates the handoff; preserve it.

Media work includes verified collision-safe backup extraction, targeted database updates that retain concurrent edits, retained media import/compression, Save/Discard processing barriers, safe original backups, and restoration for attachments removed from the editor before Discard. Deleted attachment backups are retained until Save commits. No Room entity, sync-manifest, or backup-format change was made in these latest fixes.

Recent commits, oldest to newest:

| Commit | Change |
| --- | --- |
| `d86a027` | Abort compression unless a complete original backup exists; preserve backup after failed restore. |
| `a027b29` | Keep deleted attachment originals until Save/Discard; wait for processing and persistence. |
| `25e4e51`, `1e54eb7` | Record explicit same-step slider choices; retain actual gesture value until finish. |
| `a28640e` | Remove superseded cached-key Preview version script after main CI merge. |
| `235b3eb` | Correct diagonal EXIF photo orientations; real JPEG quadrant/restore tests. |
| `ddfc10a` | Honor requested video-control visibility rather than toggling it. |
| `1711ab6` | Clear location-search busy state when cancelling a query. |
| `c6c5c3e` | Stable slider test tag. |
| `1ddd93bf30e58a7624b58c2973a3edb3cc9588d5` | Atomic attachment replacement, partial-write regression, correct slider-test tap. |
| `6479a1362af5791aba5da3a00847bc944368b03b` | Guard corrupt-video metadata initialization/parsing; fallback local/URI tests. |
| `122b145605ad5419f4be7401d6b3133c715966a6` | Defer journal publishing/acknowledgment around media; startup-repair barrier and version 6. **Needs fixes below.** |
| `13b941257dafc0815205595f5ee6486791af4054` | Atomic checked JPEG preview encoding, retry UI, short-edge video scaling, artwork orientation/retention, reactive backup metadata, editor callback keys, new regressions. **Needs fixes below.** |

Important files for the next pass:

- `core/src/main/java/com/denser/june/core/domain/sync/SyncManager.kt`
- `core/src/main/java/com/denser/june/core/domain/sync/MediaFileNameRepair.kt`
- `app/src/main/java/com/denser/june/JuneApplication.kt`
- `core/src/main/java/com/denser/june/core/media/compress/{MediaFileReplacement,CompressUtils,PhotoCompressor,VideoCompressor}.kt`
- `core/src/main/java/com/denser/june/core/media/exif/ExifUtils.kt`
- `core/src/main/java/com/denser/june/core/media/storage/SongMediaStorage.kt`
- `app/src/main/java/com/denser/june/presentation/components/lightbox/{LightboxCompressionState,LightboxMediaMetadataState,LightboxTopBar}.kt`
- `app/src/main/java/com/denser/june/presentation/components/JuneMediaLightbox.kt`
- `app/src/main/java/com/denser/june/presentation/screens/editor/{EditorVM,EditorScreen,EditorAction}.kt`
- `app/src/main/java/com/denser/june/presentation/screens/editor/components/{EditSongView,SongArtImportVM}.kt`
- `app/src/main/java/com/denser/june/presentation/screens/editor/screens/AddSongScreen.kt`
- Tests under `core/src/test/java/com/denser/june/core/sync`, `core/src/test/java/com/denser/june/core/media/compress`, `core/src/androidTest`, and `app/src/androidTest/java/com/denser/june/media`.

Atomic replacement stages beside the destination, checks full copy size, syncs bytes, preserves timestamps, and uses ATOMIC_MOVE with replacement. It has no destructive copy fallback. Preview JPEG encoding checks the encoder result before publishing cache output. Video scaling uses Media3's short-side Presentation API. Metadata initialization/parsing is guarded and retrievers are released in finally. These choices address real data-loss/crash findings; retain their protections while fixing regressions.

The startup repair marker is now **6**. `.agents/AGENTS.md` requires incrementing it whenever startup repair behavior changes, acknowledging completion only after success, and adding migrations/schema bumps for actual schema changes. Keep upstream app version declarations literal and never decrease published version codes.

## Tests actually run

### Latest head 13b9412: NOT green

Run: https://github.com/AQSAMA/JuneNotes/actions/runs/39128374477

- `folder-database-tests`: **SUCCESS**, all **17** core Android tests on API 35. This includes corrupt-video fallback, short-edge sizing, and JPEG/artwork orientation coverage.
- `preview`: **FAILURE**. Core release unit suite ran **151 tests, 1 failed**: `SyncFaultToleranceTest > download failure reports error and leaves already synced items safe`, assertion at line 37. Do not claim the entire APK build/identity verification job succeeded on this head.
- `folder-ui-tests`: **FAILURE**. API 35 ran **47 tests, 1 failed**: `LightboxBackupStatusTest.syncAcknowledgmentRefreshesAnOpenLightboxDespiteItsLocalCompressionMarker`, Compose wait timeout. The fixture is still a draft (see finding 6).
- `publish-preview`: **SKIPPED**, expected for a PR. No permanently signed release was published from PR code.

Job IDs for logs: preview `117678225879`, UI `117678225628`, core Android `117678225948`.

### Earlier successful PR #5 baseline

Run https://github.com/AQSAMA/JuneNotes/actions/runs/39127514893 on `6479a13` passed Preview/unit/APK checks, core Android/database tests, and all **44** then-existing UI tests. It predates the newest two commits and does NOT prove their correctness.

Earlier run `39126965015` had successful unit/APK and core Android jobs, but was cancelled after a newer push; do not call it an overall success. Earlier temporary failures involved JUnit return types, a nonexistent Compose matcher, and a slider test tapping Original while expecting High; those were fixed before the successful 6479a13 run.

### Permanent signing/release verification already completed

- Production success: https://github.com/AQSAMA/JuneNotes/actions/runs/39102128321 — package `com.aqsama.junenotes`, versionCode `100000201`, permanent Production certificate, attestation generated and verified locally.
- Trusted Preview success: https://github.com/AQSAMA/JuneNotes/actions/runs/39102130280 — package `com.aqsama.junenotes.preview`, versionCode `100005901`, permanent Preview certificate verified locally.
- PR #6 test CI success: https://github.com/AQSAMA/JuneNotes/actions/runs/39102131978 — temporary signing, permanent key restore skipped, upstream/fork variant identity checks, unit/core/UI checks.
- Post-merge main success: https://github.com/AQSAMA/JuneNotes/actions/runs/39103274214 — all jobs and Preview publication passed.
- Published Preview: https://github.com/AQSAMA/JuneNotes/releases/tag/preview — versionCode `100006101`. The released APK's permanent certificate and asset digest were verified locally. Asset SHA-256: `e3b8225af96196f0c4436cc360e69edc0ef63162235a34ac595d7d5ad720b437`.
- No new stable Production release tag was created. A reviewed `v*` tag is needed to publish one; do not create it without further task authorization.

Local Termux checks passed for Gradle configuration, generated BuildConfig variants, signingReport, workflow/actionlint, and diff whitespace. Full local Android builds/tests were blocked by the absent Android SDK. Native Termux aapt/aapt2 also failed libc++ linking; apksigner worked. No full SDK/emulator was installed on the phone. Newest fixes were checked by GitHub CI as reported above, not by an imagined local Android build.

## Reviews and six remaining findings

GitHub `@codex review` on **13b9412 completed with findings**, not a clean review. Its latest unresolved finding is [4307067920](https://github.com/AQSAMA/JuneNotes/pull/5#discussion_r4307067920).

A separate local, fresh-context, read-only Codex CLI review examined the full PR against `origin/main`. It found seven issues: sync retry ownership, startup cleanup/repair race, partial JPEG caching, portrait-video scaling, unhandled preview errors, diagonal artwork EXIF, and stale backup status. Commits 122b145/13b9412 attempted to address all seven. Do not equate implemented fixes with successful validation.

A fresh independent follow-up, `codex review --base 6479a1362af5791aba5da3a00847bc944368b03b`, completed and found five additional issues. Combined with the latest GitHub finding, the remaining work is:

1. **P1 — Media-only sync acknowledgement still occurs before manifest commit.** In `SyncManager.kt` around line 1188, the branch matching the old journal content hash calls `updateSyncStatus` before `updateManifest`. A failed manifest write can leave changed media falsely clean, preventing automatic retry. A stale snapshot may also acknowledge a concurrent edit. Queue these acknowledgements with the other deferred acknowledgements; apply only after successful manifest commit and conditionally on the expected note revision. Add failed-manifest and concurrent-edit regressions.
2. **P2 — Unrelated download failure blocks every queued journal upload.** The global `if (failedCount == 0)` around the delayed upload loop (about line 1150) blocks text-only backups if another remote item cannot download. This causes the existing SyncFaultToleranceTest failure. Gate each upload on its relevant attachment failures, preserving the safe retry behavior for that note without blocking unrelated notes.
3. **P2 — Purged journals remain in the delayed upload snapshot.** `purgeOldBin()` now runs before that upload loop. An unsynced note older than 30 days can be hard-deleted locally, then uploaded from stale `toUpload`, recreating cloud JSON without a tombstone; a later sync restores the purged note. Exclude purged IDs or plan after purge; add a regression.
4. **P2 — Explicit artwork Cancel/back must invalidate the retained result.** `SongArtImportVM` is keyed by song/edit scope. Cancel returns to Library without clearing that ViewModelStore, so reopening the same song can consume an import from the cancelled edit. Distinguish explicit cancellation from recreation, clean unconsumed files, and add a cancel/reopen test while retaining the passing recreation behavior.
5. **P2 — Saved Trimmer step lacks saved trimmer song.** `AddSongScreen.kt` now saves `currentStep`, but `activeTrimmerSong` is only remembered. Process recreation can restore Trimmer with no song and a blank, nonfunctional page. Save the matching song or safely fall back to Library; add restoration coverage.
6. **P2 — Backup-status test fixture is a draft.** `LightboxBackupStatusTest.kt` constructs Journal without `isDraft = false`; its default is true. A sync status update does not publish a draft, so backed-up status can never become true. Make this a published-note fixture, rerun it, and check the product behavior rather than hiding the failure.

The independent review log files are local-only on the phone at `~/.local/share/junenotes-pr5-independent-review.log`, `junenotes-pr5-independent-review.md`, and `junenotes-pr5-independent-followup.log`. Their substantive findings are preserved above for Cloud. Earlier GitHub review threads have code fixes/replies and were resolved; inspect latest threads before resolving any new finding.

## Recommended Cloud continuation

1. Open this handoff branch and read the repository rules and files above. Check `git status`, `gh auth status`, repository permissions, and current PR/CI state. Preserve any new changes made since this snapshot. Do not request user authentication or change credentials speculatively; report an exact blocker if existing Cloud credentials are insufficient.
2. Fetch origin; add/fetch upstream if missing. The phone's origin uses SSH, but Cloud should use its provided repository credentials/remote configuration; do not copy the phone's SSH private key.
3. Fix the six remaining findings with minimal changes. Prioritize sync acknowledgement/retry/purge safety, then the test fixture and song-edit lifecycle. Keep the new data-safety checks and tests. Avoid restructuring unrelated code or changing schemas without the required migrations.
4. Run targeted unit/Android regressions, then the existing workflow checks. Use Linux Java 21 and the Gradle wrapper. Full Android builds need SDK platform 36, suitable build tools/platform-tools and accepted licenses; emulator tests use API 35/x86_64. GitHub Actions provides the heavy build/KVM environment. Do not install a large SDK on the phone to work around this handoff.
5. Continue the existing PR #5 instead of opening a duplicate. From this handoff branch, after committing fixes, a normal fast-forward push can update its head:

   ```sh
   git fetch origin
   git merge-base --is-ancestor origin/codex/upstream-folder-gestures HEAD
   git push origin HEAD:refs/heads/codex/upstream-folder-gestures
   ```

   If ancestry fails because someone updated that branch, inspect and preserve their work; never force-push or reset it. Do not push to main. Keep the handoff branch updated as appropriate.
6. If testing this branch before updating the PR head, dispatch `gh workflow run preview.yml --repo AQSAMA/JuneNotes --ref codex/cloud-handoff-20261019 -f test_only=true`. Permanent signing on this branch must remain blocked. Ordinary PR updates trigger the useful PR tests automatically.
7. Read actual CI failures, fix causes, and iterate. Request a new `@codex review` on the updated PR head and use a fresh independent review. Check that the reported review commit matches the code being delivered. Require passing checks and no outstanding genuine findings before marking ready. Update the PR body, which still describes earlier pending verification. **Leave PR #5 unmerged.**
8. Do not alter keys, secret values, environment restrictions, workflow counters or Play settings during ordinary PR fixes. User action still needed outside this coding work: encrypted off-device signing backup.

Useful read-only commands (always paginate review comments):

```sh
gh pr view 5 --repo AQSAMA/JuneNotes
gh pr checks 5 --repo AQSAMA/JuneNotes
gh run view 39128374477 --repo AQSAMA/JuneNotes
gh api 'repos/AQSAMA/JuneNotes/pulls/5/comments?per_page=100' --paginate
gh api 'repos/AQSAMA/JuneNotes/issues/5/comments?per_page=100' --paginate
gh secret list --repo AQSAMA/JuneNotes --env signing
gh variable list --repo AQSAMA/JuneNotes
```

Core verification commands used by CI include `bash gradlew :core:testReleaseUnitTest :app:testFossPreviewUnitTest :app:assembleFossPreview :app:assembleFossDebug :app:assemblePlayDebug :app:signingReport`, `bash gradlew :core:connectedDebugAndroidTest`, and `bash .github/scripts/check-folder-ui.sh`. Let the workflow supply fork variables and temporary signing for PR builds. Never claim a build or review passed unless the exact run/head actually did.
