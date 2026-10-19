package com.denser.june.core.data.database.journal

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Local-only byte ownership. Tokens prevent an older sync from acknowledging a newer mutation. */
@Entity(tableName = "pending_media_changes")
data class PendingMediaChangeEntity(@PrimaryKey val fileName: String, val generation: String)
