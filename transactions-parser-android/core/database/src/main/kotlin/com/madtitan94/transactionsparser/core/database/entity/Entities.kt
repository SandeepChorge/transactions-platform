package com.madtitan94.transactionsparser.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "categories",
    indices = [Index(value = ["ownerId", "name"], unique = true)]
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val ownerId: String,
    val name: String,
    val isDeleted: Boolean = false,
    val deletedAtMillis: Long? = null
)

@Entity(
    tableName = "payees",
    indices = [
        Index(value = ["ownerId", "normalizedName"], unique = true),
        Index(value = ["categoryId"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT
        )
    ]
)
data class PayeeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val ownerId: String,
    val rawName: String,
    val normalizedName: String,
    val alias: String,
    val categoryId: Long,
    val isDeleted: Boolean = false,
    val deletedAtMillis: Long? = null
)

@Entity(
    tableName = "sessions",
    indices = [Index(value = ["ownerId"])]
)
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val ownerId: String,
    val fileName: String,
    val source: String,
    val uploadedAtMillis: Long,
    val periodStartMillis: Long?,
    val periodEndMillis: Long?,
    val status: String,
    val isDeleted: Boolean = false,
    val deletedAtMillis: Long? = null
)

@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["sessionId"]),
        Index(value = ["payeeId"]),
        Index(value = ["normalizedPayee"]),
        Index(value = ["ownerId"]),
        // Duplicate lookup on import. Deliberately non-unique: a unique index would make
        // SQLite reject or silently drop repeat rows, and we want to keep and flag them.
        Index(value = ["ownerId", "transactionRef"]),
        Index(value = ["ownerId", "utr"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = PayeeEntity::class,
            parentColumns = ["id"],
            childColumns = ["payeeId"],
            onDelete = ForeignKey.SET_NULL
        )
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val ownerId: String,
    val sessionId: Long,
    val dateTimeUtcMillis: Long,
    val rawPayee: String,
    val normalizedPayee: String,
    val amountPaise: Long,
    val type: String,
    val transactionRef: String?,
    val utr: String?,
    val payeeId: Long?,
    /** Set once at import when this row repeats an earlier one. Never user-editable. */
    val isDuplicate: Boolean = false,
    /** The earlier row this one repeats, when [isDuplicate]. */
    val duplicateOfTransactionId: Long? = null,
    /**
     * Left out of every total. Initialized to [isDuplicate] at import, then owned by the user —
     * which is why it's separate from [isDuplicate]: re-including a false positive must not
     * erase the fact that detection flagged it.
     */
    val isExcluded: Boolean = false,
    val isDeleted: Boolean = false,
    val deletedAtMillis: Long? = null
)

@Entity(
    tableName = "upload_logs",
    indices = [Index(value = ["ownerId"])]
)
data class UploadLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val ownerId: String,
    val fileName: String,
    val uploadedAtMillis: Long,
    val success: Boolean,
    val source: String?,
    val failureReason: String?,
    val sessionId: Long?,
    val isDeleted: Boolean = false,
    val deletedAtMillis: Long? = null
)
