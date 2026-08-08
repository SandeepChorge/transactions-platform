package com.madtitan94.transactionsparser.feature.upload.domain

import com.madtitan94.transactionsparser.core.domain.parsing.ParseError
import com.madtitan94.transactionsparser.core.domain.util.Result

data class PickedFileMetadata(
    val displayName: String,
    val sizeBytes: Long
)

/** Access to the user-picked document (SAF uri) and the temporary parse copy. */
interface StatementFileDataSource {
    suspend fun metadata(uriString: String): Result<PickedFileMetadata, ParseError>
    /** Copies the picked document into app cache; returns the temp file path. */
    suspend fun copyToCache(uriString: String): Result<String, ParseError>
    /** Deletes the temporary copy. Returns true when the file no longer exists. */
    suspend fun deleteTempFile(path: String): Boolean
}
