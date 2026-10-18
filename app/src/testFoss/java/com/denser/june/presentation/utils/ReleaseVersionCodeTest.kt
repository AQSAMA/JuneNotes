package com.denser.june.presentation.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReleaseVersionCodeTest {
    @Test fun readsCodeFromReleaseNotes() {
        assertEquals(100001201, releaseVersionCode("Changes\n<!-- junenotes-version-code: 100001201 -->\n"))
    }

    @Test fun ignoresAbsentMalformedAndOverflowingCodes() {
        listOf("", "v1.3.1", "<!-- junenotes-version-code: -1 -->",
            "<!-- junenotes-version-code: 0 -->", "<!-- junenotes-version-code: 99999999999 -->")
            .forEach { assertNull(releaseVersionCode(it)) }
    }
}
