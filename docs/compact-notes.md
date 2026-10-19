# Compact notes

Enable **Settings → Appearance → Compact notes (experimental)**. Existing installations keep the original layout until enabled. The option controls the home search bar, note previews in All notes/search/folders, and the folder sidebar. Disabling it restores the original presentation without changing notes or memberships.

The layout button in the search bar switches between variable-height side-by-side cards and a single column. Appearance also offers short (4 lines), balanced (8), and long (14) previews. These preferences persist locally, including while the feature is disabled. Large system fonts automatically reduce the number of columns.

## Design direction

Keep June's existing font (Google Sans Flex or the user's selection), Material color palette, and bottom navigation. Use quieter chrome so the note, rather than the application name or an empty thumbnail, leads the screen. A note's height follows its content up to the chosen preview length. Only real media receives an image area. Dates and bookmark actions remain secondary. Text follows its own language direction.

The cyan reference palette is inherited through Material roles: background `#0D1716`, low surface `#172220`, outline `#465552`, primary `#80CCD9`, primary text `#DEE5E2`, secondary text `#BCC8C4`. These are reference values, not hardcoded overrides; dynamic colors, light mode, AMOLED, and custom palettes remain authoritative. Cards use 18 dp corners and 10 dp gaps. Search uses a 20 dp surface. Folder selection uses 12 dp corners and 48 dp minimum rows.

Compared with the supplied references, the design preserves June's folder hierarchy, draft indicators, dates, music attachments, bookmarks, and navigation. The sidebar has a functional Folders title, search, All notes/Unfiled destinations, inline hierarchy, an organize action, and a reachable New folder button. Zero counts disappear in compact mode. Existing drag, insertion, expansion, move, and rename controls are retained.

## Review scenarios

- Switch the feature off/on and restart; settings and original presentation should be retained.
- Browse short/long, titled/untitled, English/Arabic, image/video, draft and bookmarked notes in both layouts.
- Search and filter, open a note, toggle its bookmark, open long-press options, and export/delete.
- Select a nested folder; reorder its notes via grips, move notes/folders, and search the hierarchy.
- Check 360 dp width, large fonts, both themes and RTL; cards must remain readable and actions reachable.

No database, backup, sync, signing, or application identity changes are required.
