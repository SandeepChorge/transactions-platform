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
    @Query("SELECT * FROM categories ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT categoryId AS categoryId, COUNT(*) AS count FROM payees GROUP BY categoryId")
    fun observeLinkedCounts(): Flow<List<CategoryLinkCount>>

    @Insert
    suspend fun insert(category: CategoryEntity): Long

    @Query("UPDATE categories SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM payees WHERE categoryId = :id")
    suspend fun linkedPayeeCount(id: Long): Int
}

@Dao
interface PayeeDao {
    @Query("SELECT * FROM payees ORDER BY alias COLLATE NOCASE")
    fun observeAll(): Flow<List<PayeeEntity>>

    @Query("SELECT * FROM payees WHERE normalizedName = :normalizedName LIMIT 1")
    suspend fun findByNormalizedName(normalizedName: String): PayeeEntity?

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
        LEFT JOIN transactions t ON t.sessionId = s.id
        WHERE s.status = :status
        GROUP BY s.id
        ORDER BY s.uploadedAtMillis DESC
        """
    )
    fun observeSummaries(status: String): Flow<List<SessionSummaryRow>>

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getById(id: Long): SessionEntity?

    @Insert
    suspend fun insert(session: SessionEntity): Long

    @Query("UPDATE sessions SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE sessionId = :sessionId ORDER BY dateTimeUtcMillis DESC")
    fun observeBySession(sessionId: Long): Flow<List<TransactionEntity>>

    @Insert
    suspend fun insertAll(transactions: List<TransactionEntity>)

    @Query("UPDATE transactions SET payeeId = :payeeId WHERE sessionId = :sessionId AND normalizedPayee = :normalizedPayee")
    suspend fun assignPayee(sessionId: Long, normalizedPayee: String, payeeId: Long)

    @Query("SELECT COUNT(*) FROM transactions WHERE sessionId = :sessionId AND payeeId IS NULL")
    suspend fun unmappedCount(sessionId: Long): Int
}

@Dao
interface UploadLogDao {
    @Query("SELECT * FROM upload_logs ORDER BY uploadedAtMillis DESC")
    fun observeAll(): Flow<List<UploadLogEntity>>

    @Insert
    suspend fun insert(log: UploadLogEntity)
}
