package com.madtitan94.transactionsparser.core.domain.backup

import com.madtitan94.transactionsparser.core.domain.datasource.BackupLocalDataSource
import com.madtitan94.transactionsparser.core.domain.datasource.DocumentReader
import com.madtitan94.transactionsparser.core.domain.datasource.SessionStorage
import com.madtitan94.transactionsparser.core.domain.util.Result
import kotlinx.coroutines.flow.first

/**
 * Reads a backup file and works out whether it can be restored — without writing anything.
 *
 * The half of the restore that comes before the user has agreed to it. Splitting it out is what
 * lets the flow show real numbers on the confirmation screen: the file is fully parsed and fully
 * validated by the time the user is asked, so "1,143 transactions from someone@example.com" is a
 * fact rather than an estimate, and every way a restore can fail has already been ruled out.
 */
class ReadBackupUseCase(
    private val documentReader: DocumentReader,
    private val backups: BackupLocalDataSource,
    private val sessionStorage: SessionStorage
) {

    suspend operator fun invoke(source: String): Result<BackupPreview, BackupError> {
        val text = when (val read = documentReader.read(source)) {
            is Result.Error -> return Result.Error(BackupError.CouldNotRead)
            is Result.Success -> read.data
        }

        // A database that cannot report its own version cannot be restored into either, so this
        // failure is not worth working around.
        val supportedSchemaVersion = when (val version = backups.schemaVersion()) {
            is Result.Error -> return Result.Error(BackupError.CouldNotRead)
            is Result.Success -> version.data
        }

        val file = when (val validated = BackupValidator.validate(text, supportedSchemaVersion)) {
            is Result.Error -> return Result.Error(validated.error)
            is Result.Success -> validated.data
        }

        val signedInGoogleId = sessionStorage.observeSession().first()?.googleId
        return Result.Success(
            BackupPreview(
                file = file,
                summary = file.summary(),
                exportedAtMillis = file.exportedAtMillis,
                appVersionName = file.app.versionName,
                account = file.account,
                // A file with no account recorded gets no prompt: there is nothing to warn about,
                // and stopping a restore over missing metadata would be worse than the surprise.
                isDifferentAccount = file.account != null &&
                    signedInGoogleId != null &&
                    file.account.googleId != signedInGoogleId
            )
        )
    }
}

internal fun BackupFile.summary() = BackupSummary(
    categories = categories.size,
    payees = payees.size,
    payeeIdentifiers = payeeIdentifiers.size,
    sessions = sessions.size,
    transactions = transactions.size,
    uploadLogs = uploadLogs.size
)
