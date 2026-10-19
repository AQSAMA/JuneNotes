package com.denser.june.core.data.database.journal

import androidx.room.Database
import androidx.room.RoomDatabase

import com.denser.june.core.data.database.song.SongLibraryDao
import com.denser.june.core.data.database.song.SongLibraryEntity

@Database(
    entities = [
        JournalEntity::class,
        PendingMediaChangeEntity::class,
        TagEntity::class,
        JournalTagCrossRef::class,
        DeletedJournalTombstone::class,
        SongLibraryEntity::class,
        com.denser.june.core.data.database.folder.FolderEntity::class,
        com.denser.june.core.data.database.folder.FolderJournalEntity::class,
        com.denser.june.core.data.database.folder.FolderSyncStateEntity::class
    ],
    version = JournalDatabase.VERSION,
    exportSchema = true
)
abstract class JournalDatabase : RoomDatabase() {
    abstract fun folderDao(): com.denser.june.core.data.database.folder.FolderDao
    abstract fun journalDao(): JournalDao
    abstract fun songLibraryDao(): SongLibraryDao

    companion object {
        const val VERSION = 7
        const val DB_NAME = "journal_database"
    }
}