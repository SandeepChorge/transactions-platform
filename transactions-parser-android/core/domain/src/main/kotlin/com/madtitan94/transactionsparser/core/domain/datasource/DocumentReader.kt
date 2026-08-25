package com.madtitan94.transactionsparser.core.domain.datasource

import com.madtitan94.transactionsparser.core.domain.util.DataError
import com.madtitan94.transactionsparser.core.domain.util.Result

/**
 * Reads text from a source the user picked, addressed by an opaque string — the mirror of
 * [DocumentWriter], and a `content://` URI on Android for the same reason.
 *
 * Whole-file rather than streaming: a backup is parsed as one JSON document, so there is nothing
 * useful to do with half of it, and holding one in memory is cheap next to the PDFs this app
 * already parses.
 */
interface DocumentReader {
    /**
     * Reads all of [source] as UTF-8 text.
     *
     * The picker is the permission grant, so this cannot fail for a missing runtime permission —
     * but the source can be gone by the time the read starts, or turn out not to be text at all.
     */
    suspend fun read(source: String): Result<String, DataError.Local>
}
