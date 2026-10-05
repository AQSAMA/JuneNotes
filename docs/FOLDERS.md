# Folders

The folder icon is the fourth item in June’s existing bottom row. Spaces, people,
and topics still work as tags, independently of folders. Notes stay visible in
June’s original views after being filed.

- Tap the folder-plus button to create a folder at the current level.
- Tap a folder to enter it. The existing plus button creates a note there using
  June’s original editor, including its draft autosave, media, tags and actions.
- Hold a drag handle, then drop onto a folder to file the item, onto an insertion
  strip to reorder siblings, or onto a breadcrumb to move to an ancestor/root.
  Hold over a folder or breadcrumb to open it while keeping the drag active.
  The list scrolls when dragged near its upper/lower edge.
- Tap a drag handle to use the folder picker instead. This works with TalkBack,
  keyboards and touch, and supports any destination outside the moved subtree.
- Hold a note to use June’s existing bookmark, export and delete actions. Deleted
  notes go to the existing Bin and retain their folder placement for restoration.
- Hold a folder, or use its toolbar menu, to rename, move or delete it. Removing a
  folder removes its subtree organization; its notes remain in June and become
  unfiled in the folder root. The confirmation states this explicitly.

## Persistence and backups

Room version 6 adds `note_folders`, `note_placements` and `folder_sync_state`.
Migration 5→6 leaves the original journal, tag, song and tombstone tables intact.
The placement table intentionally has no journal foreign key: June uses
`INSERT OR REPLACE` during restore/sync, which would otherwise cascade-delete
placements. Folder operations and new-note placement are transactional. Missing
or deleted destinations present their notes at the root; cycles cannot be created
locally, and simultaneous conflicting cloud moves are resolved deterministically.

ZIP and Markdown exports keep June’s original journal/media formats and add
`folders.json`. Notes remain at their original flat export paths so original June
can import them. The extra file restores nesting, order and placements in the
fork, including a backup containing only empty folders. Original ZIP, legacy ZIP
and Markdown backups still import without a folder file. A local restore replaces
included folder records and preserves records not in the backup, matching June’s
merge-style note import; it can recover folders deleted since the backup was made.

WebDAV and Google Drive use June’s existing cloud manifest, with an optional
`folders` extension. Records merge by modification time with deterministic ties;
deletions retain tombstones. Folder-only changes schedule automatic sync, appear
in sync analysis, and are only acknowledged after the exact uploaded snapshot
succeeds. An edit made during upload remains dirty. Failed manifest reads do not
replace cloud folder data. Original June does not preserve this extension when
writing a manifest; cloud folder synchronization requires the fork on those devices.
Android’s existing whole-database backup also includes the new tables.

## Preview APK

Each PR targeting `main` runs **Preview APK**. Download its **Preview APK** artifact,
extract `June-Preview.apk`, and install it. The app is **June Preview** with ID
`com.denser.june.preview`, separate storage and a separate FileProvider authority.
It is a non-debuggable, release-derived FOSS preview with shrinking enabled and an
installation signature. No production signing secrets are needed. Signing is
cached for subsequent builds of the same PR; if the cache expires, export first
and reinstall the preview to accept a changed signature. Original June is unaffected.

Import an original June backup through Settings → Sync & backup to test with
existing data. Preview exports can be imported by June; folder organization is
ignored there. The workflow also uploads test reports.

## Validation

The workflow runs the existing core regression suite and new tests for deep moves,
cycle prevention/repair, sibling ordering, note replacement, safe subtree removal,
original-database migration, folder-only sync, manifest failures, an edit during
upload, old backups, empty folders, ZIP/Markdown media round trips and restoring a
folder deleted after a backup. It compiles the existing FOSS app tests and builds
the actual `fossPreview` artifact.

Device checks: create three folder levels; add/edit a note with media and tags;
drag the deepest folder to the root and back; reorder sibling folders and notes;
hover-open an empty folder and drop there; move via the picker; cancel a drag;
rotate/relaunch; delete/restore a note through Bin; export/import both formats;
verify the original app remains installed and can import the note export.
