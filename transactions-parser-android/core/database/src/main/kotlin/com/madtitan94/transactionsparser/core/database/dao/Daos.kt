package com.madtitan94.transactionsparser.core.database.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.madtitan94.transactionsparser.core.database.entity.CategoryEntity
import com.madtitan94.transactionsparser.core.database.entity.PayeeEntity
import com.madtitan94.transactionsparser.core.database.entity.SessionEntity
import com.madtitan94.transactionsparser.core.database.entity.TransactionEntity
import com.madtitan94.transactionsparser.core.database.entity.UploadLogEntity
import kotlinx.coroutines.flow.Flow

data class CategoryLinkCount(val categoryId: Long, val count: Int)

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE ownerId = :ownerId AND isDeleted = 0 ORDER BY name COLLATE NOCASE")
    fun observeAll(ownerId: String): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE ownerId = :ownerId AND isDeleted = 1 ORDER BY deletedAtMillis DESC")
    fun observeDeleted(ownerId: String): Flow<List<CategoryEntity>>

    @Query(
        """
        SELECT categoryId AS categoryId, COUNT(*) AS count
        FROM payees
        WHERE ownerId = :ownerId AND isDeleted = 0
        GROUP BY categoryId
        """
    )
    fun observeLinkedCounts(ownerId: String): Flow<List<CategoryLinkCount>>

    @Insert
    suspend fun insert(category: CategoryEntity): Long

    @Query("UPDATE categories SET name = :name WHERE id = :id AND ownerId = :ownerId")
    suspend fun rename(ownerId: String, id: Long, name: String)

    @Query(
        "UPDATE categories SET isDeleted = 1, deletedAtMillis = :deletedAtMillis " +
            "WHERE id = :id AND ownerId = :ownerId"
    )
    suspend fun softDelete(ownerId: String, id: Long, deletedAtMillis: Long)

    @Query(
        "UPDATE categories SET isDeleted = 0, deletedAtMillis = NULL " +
            "WHERE id = :id AND ownerId = :ownerId"
    )
    suspend fun restore(ownerId: String, id: Long)

    @Query("SELECT COUNT(*) FROM payees WHERE ownerId = :ownerId AND isDeleted = 0 AND categoryId = :id")
    suspend fun linkedPayeeCount(ownerId: String, id: Long): Int
}

@Dao
interface PayeeDao {
    @Query("SELECT * FROM payees WHERE ownerId = :ownerId AND isDeleted = 0 ORDER BY alias COLLATE NOCASE")
    fun observeAll(ownerId: String): Flow<List<PayeeEntity>>

    @Query(
        "SELECT * FROM payees WHERE ownerId = :ownerId AND isDeleted = 0 " +
            "AND normalizedName = :normalizedName LIMIT 1"
    )
    suspend fun findByNormalizedName(ownerId: String, normalizedName: String): PayeeEntity?

    @Insert
    suspend fun insert(payee: PayeeEntity): Long

    @Update
    suspend fun update(payee: PayeeEntity)
}

data class SessionSummaryRow(
    @Embedded val session: SessionEntity,
    val transactionCount: Int,
    val mappedCount: Int
)

@Dao
interface SessionDao {
    @Query(
        """
        SELECT s.*,
               COUNT(t.id) AS transactionCount,
               COUNT(t.payeeId) AS mappedCount
        FROM sessions s
        LEFT JOIN transactions t ON t.sessionId = s.id AND t.isDeleted = 0
        WHERE s.ownerId = :ownerId AND s.isDeleted = 0 AND s.status = :status
        GROUP BY s.id
        ORDER BY s.uploadedAtMillis DESC
        """
    )
    fun observeSummaries(ownerId: String, status: String): Flow<List<SessionSummaryRow>>

    @Query("SELECT * FROM sessions WHERE id = :id AND ownerId = :ownerId AND isDeleted = 0")
    suspend fun getById(ownerId: String, id: Long): SessionEntity?

    @Insert
    suspend fun insert(session: SessionEntity): Long

    @Query("UPDATE sessions SET status = :status WHERE id = :id AND ownerId = :ownerId")
    suspend fun updateStatus(ownerId: String, id: Long, status: String)
}

@Dao
interface TransactionDao {
    @Query(
        "SELECT * FROM transactions WHERE ownerId = :ownerId AND isDeleted = 0 " +
            "AND sessionId = :sessionId ORDER BY dateTimeUtcMillis DESC"
    )
    fun observeBySession(ownerId: String, sessionId: Long): Flow<List<TransactionEntity>>

    @Insert
    suspend fun insertAll(transactions: List<TransactionEntity>)

    @Query(
        "UPDATE transactions SET payeeId = :payeeId WHERE ownerId = :ownerId AND isDeleted = 0 " +
            "AND sessionId = :sessionId AND normalizedPayee = :normalizedPayee"
    )
    suspend fun assignPayee(ownerId: String, sessionId: Long, normalizedPayee: String, payeeId: Long)

    @Query(
        "SELECT COUNT(*) FROM transactions WHERE ownerId = :ownerId AND isDeleted = 0 " +
            "AND sessionId = :sessionId AND payeeId IS NULL"
    )
    suspend fun unmappedCount(ownerId: String, sessionId: Long): Int
}

@Dao
interface UploadLogDao {
    @Query(
        "SELECT * FROM upload_logs WHERE ownerId = :ownerId AND isDeleted = 0 " +
            "ORDER BY uploadedAtMillis DESC"
    )
    fun observeAll(ownerId: String): Flow<List<UploadLogEntity>>

    @Insert
    suspend fun insert(log: UploadLogEntity)
}

@Dao
interface LegacyOwnershipDao {
    @Query("UPDATE categories SET ownerId = :ownerId WHERE ownerId = :legacyOwnerId")
    suspend fun claimCategories(legacyOwnerId: String, ownerId: String)

    @Query("UPDATE payees SET ownerId = :ownerId WHERE ownerId = :legacyOwnerId")
    suspend fun claimPayees(legacyOwnerId: String, ownerId: String)

    @Query("UPDATE sessions SET ownerId = :ownerId WHERE ownerId = :legacyOwnerId")
    suspend fun claimSessions(legacyOwnerId: String, ownerId: String)

    @Query("UPDATE transactions SET ownerId = :ownerId WHERE ownerId = :legacyOwnerId")
    suspend fun claimTransactions(legacyOwnerId: String, ownerId: String)

    @Query("UPDATE upload_logs SET ownerId = :ownerId WHERE ownerId = :legacyOwnerId")
    suspend fun claimUploadLogs(legacyOwnerId: String, ownerId: String)
}
