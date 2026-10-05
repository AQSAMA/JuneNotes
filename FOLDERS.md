# Folders in June

Open the folder icon in the existing bottom bar. Folders are independent of spaces, people, and topics: filing a note never changes its tags or removes it from the journal, timeline, or tag views.

- Use the folder-plus icon to create a folder at the current level.
- Use June’s existing bottom + button to write a note in the open folder.
- Use the note icon beside the folder title to file an existing note here.
- Tap a folder to open it. The path at the top and Android Back move between levels.
- Hold a grip to drag a folder or note. Drop on a folder to move inside it; hold over a folder for 800 ms to open it during a drag. Drop on any ancestor in the path, including Folders, to move out. Drop in the gap above a sibling to reorder; the end zone moves to the end. Hold in the upper or lower arrow zone to scroll a long list.
- Tap the grip for a folder destination sheet. This also supports moving to any depth without dragging, with TalkBack or a keyboard.
- Folder menus offer rename, move, and delete. Deleting a folder promotes its direct notes and child folders into its parent; it never deletes notes. Note deletion uses June’s existing confirmation and Bin.

Folder and note order is stored separately; folders appear first, then notes. Unfiled notes appear at the folder root. The original journal feed is unchanged.

## Backups and sync

Room migration 5→6 adds `folders`, `folder_journals`, and a local sync acknowledgment table without altering `journals` or tags. UUID folder IDs and nullable parent IDs allow arbitrary depth; moves and ordering are transactional. Parent validation prevents cycles. Imported or concurrent cycles are repaired deterministically. Deleted folders and detached note memberships retain versioned records so older sync snapshots cannot resurrect them.

Full JSON ZIP, full Markdown ZIP, and individual note ZIP exports include a separate `folders.json` sidecar. Import accepts backups from original June with no sidecar, including legacy `journal_data.json`. Both ZIP restore and Markdown ZIP import merge folder metadata. Notes, media, song files, and Markdown frontmatter use the original June formats. Original June ignores the extra sidecar and imports notes normally; folder structure is naturally lost in that direction. Single-note exports contain only that note’s ancestor folders and membership.

WebDAV and Play Google Drive both use the existing sync manifest. An optional `folderData` field carries folder metadata; original June’s tolerant reader ignores it. Per-record modification times with deterministic tie-breaks merge changes from both devices, retaining deleted containers and removed memberships. A stored hash acknowledges exactly the folder snapshot uploaded, so imported old records and edits during an upload remain pending. Folder-only edits mark sync dirty and schedule automatic sync. Edits made during sync remain pending. Android backup/device transfer includes the same Room database under the app’s existing rules.

An original June client may rewrite a cloud manifest without folder metadata; the fork retains and republishes its local folder records on the next sync. Use the fork on each device where you want folder organization.

## Preview APK

PR Actions → **Preview APK** → **Preview APK** artifact → unzip `June-Preview.apk`.

The `fossPreview` build type inherits release shrinking and optimization, is non-debuggable, and uses application ID `com.denser.june.preview`, label **June Preview**, and a preview version suffix. It installs alongside original FOSS June and Play June. FileProvider authorities use the application ID. No production release secrets are required. The test signing key is cached across runs; if the cache is deliberately reset or expires, Android may require uninstalling an older preview before installing a newly signed one. Export your preview data first in that case.

Local build: `bash gradlew :app:assembleFossPreview`. The default APK targets arm64, as the original project does. Use `-PenableAbiSplits=true` for additional ABIs and a universal APK. Optional `-PpreviewVersionCode=102011` sets an increasing installation version code; CI derives it from the workflow run number.

## Validation

The preview workflow runs core release and app preview unit tests and builds the optimized APK. A separate Android emulator job validates the Room migration, deep moves, ordering, cycle rejection, folder deletion with promotion, and retained memberships across note trash/restore. Unit tests cover Unicode sidecars, malformed metadata, deterministic concurrent merges, original-format compatibility, and folder-only cloud sync.

Manual device checks: import an original June backup; create three nested folders; save and reopen a note; drag it and its folder to root and back; reorder siblings; delete a container; restore a deleted note from Bin; export JSON and Markdown ZIPs and import them into the preview and original June; sync between two fork installations; verify portrait/landscape, large text, Arabic RTL, and TalkBack.
