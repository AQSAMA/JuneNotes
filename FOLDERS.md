# Folders in June

Open the folder icon in the existing bottom bar. Folders are independent of spaces, people, and topics: filing a note never changes its tags or removes it from the journal, timeline, or tag views.

- Use the folder-plus icon to create a folder at the current level.
- Use June’s existing bottom + button to write a note in the open folder.
- Use the note icon beside the folder title to file an existing note here.
- The existing-note picker searches titles, content, and tags. Tap the original note card or its + action to file it. Placement is saved before the picker closes.
- Long-press a note in Home, Tags, Timeline, or the editor, then use the folder action in June’s existing note menu. The shared destination sheet supports nested navigation, folder search, and creating a destination without leaving the sheet.
- Tap a folder to open it. The path at the top and Android Back move between levels.
- Move a grip to drag immediately, or hold it to begin a stationary drag. The labeled preview follows your finger; the source row stays in place. Drop in a folder’s center to move inside it; hold for 650 ms to open it. Drop on an ancestor in the path, including Folders, to move out. The upper and lower 22 dp of folder rows, and the upper/lower halves of note cards, are insertion targets marked by a line at the chosen boundary. The end zone appends. Hold near the list’s upper or lower edge to scroll continuously; speed increases toward the edge.
- Tap the grip for a folder destination sheet. This also supports moving to any depth without dragging, with TalkBack or a keyboard.
- Folder menus offer rename, move, and delete. Deleting a folder promotes its direct notes and child folders into its parent; it never deletes notes. Note deletion uses June’s existing confirmation and Bin.

Folder and note order is stored separately; folders appear first, then notes. Unfiled notes appear at the folder root. The original journal feed is unchanged. Folder rows show separate folder and note counts. Insertion feedback overlays existing rows during a drag without changing their measurements. Scrolling and drop detection work independently, including newly visible lazy rows. Hover opening pauses while the list scrolls to avoid entering folders moving under a stationary finger. Hover navigation retains the same touch session and does not automatically descend again until the finger moves.

## Backups and sync

Room migration 5→6 adds `folders`, `folder_journals`, and a local sync acknowledgment table without altering `journals` or tags. UUID folder IDs and nullable parent IDs allow arbitrary depth; moves and ordering are transactional. Parent validation prevents cycles. Imported or concurrent cycles are repaired deterministically. Deleted folders and detached note memberships retain versioned records so older sync snapshots cannot resurrect them.

Full JSON ZIP, full Markdown ZIP, and individual note ZIP exports include a separate `folders.json` sidecar. Import accepts backups from original June with no sidecar, including legacy `journal_data.json`. Both ZIP restore and Markdown ZIP import merge folder metadata. Notes, media, song files, and Markdown frontmatter use the original June formats. Original June ignores the extra sidecar and imports notes normally; folder structure is naturally lost in that direction. Single-note exports contain only that note’s ancestor folders and membership.

WebDAV and Play Google Drive both use the existing sync manifest. Schema version 5 adds an optional `folderData` field carrying folder metadata. The schema version is always written to the manifest, including when there are no folders. Per-record modification times with deterministic tie-breaks merge changes from both devices, retaining deleted containers and removed memberships. A stored hash acknowledges exactly the folder snapshot uploaded, so imported old records and edits during an upload remain pending. Folder-only edits mark sync dirty and schedule automatic sync. Edits made during sync remain pending. Android backup/device transfer includes the same Room database under the app’s existing rules.

Original June clients supporting schema version 4 reject the version 5 cloud manifest before making changes, preventing loss of folder metadata. Existing version 1–4 manifests remain readable and are upgraded to version 5 on a successful sync. Use a schema version 5 client on every device sharing this cloud database.

## Preview APK

PR Actions → **Preview APK** → **Preview APK** artifact → unzip `June-Preview.apk`.

The `fossPreview` build type inherits release shrinking and optimization, is non-debuggable, and uses application ID `com.denser.june.preview`, label **June Preview**, and a preview version suffix. It installs alongside original FOSS June and Play June. FileProvider authorities use the application ID. No production release secrets are required. CI selects the existing Preview key and checks both its pinned fingerprint and the built APK certificate. It fails if that key is unavailable instead of silently generating a replacement. `JUNE_PREVIEW_KEYSTORE_BASE64` can restore that same keystore from a repository Actions secret if the cache expires; its alias and passwords are the existing Preview `androiddebugkey` / `android` values.

**Existing signing rotation:** the first main-branch build after PR #1 merged generated a new key because GitHub PR caches cannot be read by main. PR #1’s final APK fingerprint was `303742b45361c9918d2fe3ac11e25db0357dc09a46bd1f7e1024094ea7913404`; main’s fingerprint is `8550b3514bf050d00665eea91f3a8cc939ec398576359fe73bc8725f05861448`, which this workflow retains. An installation from PR #1 needs a one-time export → uninstall Preview → install → import to use main’s key. Original June is unaffected. Subsequent APKs signed with main’s pinned key can update in place.

Local build: `bash gradlew :app:assembleFossPreview`. The default APK targets arm64, as the original project does. Use `-PenableAbiSplits=true` for additional ABIs and a universal APK. CI supplies `-PpreviewStoreFile` to select its cached key; local builds fall back to the normal debug key. Optional `-PpreviewVersionCode=102011` sets an increasing installation version code; CI derives it from the workflow run number.

## Validation

The preview workflow runs core release and app preview unit tests and builds the optimized APK. A separate Android emulator job validates the Room migration, deep moves, ordering, cycle rejection, folder deletion with promotion, and retained memberships across note trash/restore. Unit tests cover Unicode sidecars, malformed metadata, deterministic concurrent merges, original-format compatibility, and folder-only cloud sync.

A UI emulator job injects real Android MotionEvents to verify long-press and immediate dragging, cancellation, stationary-edge scrolling in both directions, lazy rows appearing during a drag, actual folder and note ordering (including adjacent no-op drops), two-level hover navigation, subtree retention, and dropping on the root breadcrumb. It also tests tap-to-move, searching/selecting the original note cards, and filing a note from its long-press menu while retaining tags. Reports and screenshots are published as **Folder UI tests and screenshots**.

Manual device checks: import an original June backup; create three nested folders; save and reopen a note; drag it and its folder to root and back; reorder siblings; delete a container; restore a deleted note from Bin; export JSON and Markdown ZIPs and import them into the preview and original June; sync between two fork installations; verify portrait/landscape, large text, Arabic RTL, and TalkBack.
