package com.madtitan94.transactionsparser.core.domain.datasource

import com.madtitan94.transactionsparser.core.domain.util.DataError
import com.madtitan94.transactionsparser.core.domain.util.EmptyResult

/**
 * Writes text to a destination the user picked, addressed by an opaque string.
 *
 * The string is a `content://` URI on Android, kept as a plain [String] so a ViewModel can be
 * tested without the platform. Existing as an interface at all is what keeps export logic out of
 * the composable that owns the file picker.
 */
interface DocumentWriter {
    /**
     * Overwrites [destination] with [content].
     *
     * The picker itself is the permission grant, so this cannot fail for a missing runtime
     * permission — but it can fail on a full disk, or if the destination went away between the
     * user choosing it and the write starting.
     */
    suspend fun write(destination: String, content: String): EmptyResult<DataError.Local>
}
