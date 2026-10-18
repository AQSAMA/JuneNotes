package com.denser.june.presentation.utils

// Fork releases may share an upstream versionName; compare the APK's monotonic code.
internal fun releaseVersionCode(notes: String): Int? =
    Regex("<!-- junenotes-version-code: ([0-9]+) -->")
        .find(notes)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it > 0 }
