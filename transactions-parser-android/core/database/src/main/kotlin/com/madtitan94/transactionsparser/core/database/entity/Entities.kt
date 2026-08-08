package com.madtitan94.transactionsparser.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "categories",
    indices = [Index(value = ["name"], unique = true)]
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String
)

@Entity(
    tableName = "payees",
    indices = [
        Index(value = ["normalizedName"], unique = true),
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
    val rawName: String,
    val normalizedName: String,
    val alias: String,
    val categoryId: Long
)

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val fileName: String,
    val source: String,
    val uploadedAtMillis: Long,
    val periodStartMillis: Long?,
    val periodEndMillis: Long?,
    val status: String
)

@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["sessionId"]),
        Index(value = ["payeeId"]),
        Index(value = ["normalizedPayee"])
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
    val sessionId: Long,
    val dateTimeUtcMillis: Long,
    val rawPayee: String,
    val normalizedPayee: String,
    val amountPaise: Long,
    val type: String,
    val transactionRef: String?,
    val utr: String?,
    val payeeId: Long?
)

@Entity(tableName = "upload_logs")
data class UploadLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val fileName: String,
    val uploadedAtMillis: Long,
    val success: Boolean,
    val source: String?,
    val failureReason: String?,
    val sessionId: Long?
)
