package com.madtitan94.transactionsparser.core.domain.backup

import com.madtitan94.transactionsparser.core.domain.datasource.BackupLocalDataSource
import com.madtitan94.transactionsparser.core.domain.datasource.DocumentWriter
import com.madtitan94.transactionsparser.core.domain.datasource.SessionStorage
import com.madtitan94.transactionsparser.core.domain.model.AppVersion
import com.madtitan94.transactionsparser.core.domain.util.DataError
import com.madtitan94.transactionsparser.core.domain.util.Result
import kotlinx.coroutines.flow.first

/**
 * Reads the whole account, wraps it in an envelope and writes it to a destination the user picked.
 *
 * The three steps are kept in this order for a reason: the snapshot is taken before anything is
 * written, so a write that fails leaves no partial file, and the counts reported back describe the
 * file that actually landed rather than what was hoped for.
 */
class CreateBackupUseCase(
    private val backups: BackupLocalDataSource,
    private val sessionStorage: SessionStorage,
    private val documentWriter: DocumentWriter,
    private val appVersion: AppVersion,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {

    suspend operator fun invoke(destination: String): Result<BackupSummary, DataError.Local> {
        val snapshot = when (val read = backups.snapshot()) {
            is Result.Error -> return Result.Error(read.error)
            is Result.Success -> read.data
        }

        // Metadata only, and only so a restore can say whose data this is before importing it.
        // A missing session is not a reason to refuse a backup — losing the prompt is a far
        // smaller problem than refusing to let someone save their data.
        val account = sessionStorage.observeSession().first()?.let {
            BackupAccount(email = it.email, googleId = it.googleId)
        }

        val file = BackupFile(
            formatVersion = BACKUP_FORMAT_VERSION,
            schemaVersion = snapshot.schemaVersion,
            exportedAtMillis = nowMillis(),
            app = BackupApp(appVersion.versionName, appVersion.versionCode),
            account = account,
            categories = snapshot.tables.categories,
            payees = snapshot.tables.payees,
            payeeIdentifiers = snapshot.tables.payeeIdentifiers,
            sessions = snapshot.tables.sessions,
            transactions = snapshot.tables.transactions,
            uploadLogs = snapshot.tables.uploadLogs
        )

        return when (val written = documentWriter.write(destination, BackupCodec.encode(file))) {
            is Result.Error -> Result.Error(written.error)
            is Result.Success -> Result.Success(snapshot.tables.summary())
        }
    }

    private fun BackupTables.summary() = BackupSummary(
        categories = categories.size,
        payees = payees.size,
        payeeIdentifiers = payeeIdentifiers.size,
        sessions = sessions.size,
        transactions = transactions.size,
        uploadLogs = uploadLogs.size
    )
}
