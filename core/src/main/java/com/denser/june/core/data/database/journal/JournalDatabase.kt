package com.denser.june.core.data.database.journal

import com.denser.june.core.data.database.folders.*
import androidx.room.Database
import androidx.room.RoomDatabase

import com.denser.june.core.data.database.song.SongLibraryDao
import com.denser.june.core.data.database.song.SongLibraryEntity

@Database(
    entities = [
        JournalEntity::class,
        TagEntity::class,
        JournalTagCrossRef::class,
        DeletedJournalTombstone::class,
        SongLibraryEntity::class,
        FolderEntity::class, PlacementEntity::class, FolderSyncState::class
    ],
    version = JournalDatabase.VERSION,
    exportSchema = true
)
abstract class JournalDatabase : RoomDatabase() {
    abstract fun folderDao(): FolderDao
    abstract fun journalDao(): JournalDao
    abstract fun songLibraryDao(): SongLibraryDao

    companion object {
        const val VERSION = 6
        const val DB_NAME = "journal_database"
    }
}