package com.denser.june.folders

import com.denser.june.core.domain.model.Journal
import com.denser.june.presentation.screens.home.folders.matchingFolderNotes
import org.junit.Assert.*
import org.junit.Test

class FolderNoteSearchTest {
    private val notes = listOf(
        Journal("a", "Meeting", "Budget review", tags = listOf("@Ahmed"), createdAt = 1, updatedAt = null, dateTime = 1),
        Journal("b", "أدوية", "ملاحظات العلاج", tags = listOf("#صيدلة"), createdAt = 2, updatedAt = null, dateTime = 2)
    )
    @Test fun searchesTitleContentAndTagsWithMultipleTermsWithoutChangingOrder() {
        assertEquals(listOf("a"), matchingFolderNotes(notes, "meeting AHMED").map { it.id })
        assertEquals(listOf("a"), matchingFolderNotes(notes, "budget").map { it.id })
        assertEquals(listOf("b"), matchingFolderNotes(notes, "أدوية صيدلة").map { it.id })
        assertEquals(notes, matchingFolderNotes(notes, "  "))
        assertTrue(matchingFolderNotes(notes, "unknown").isEmpty())
    }
}
