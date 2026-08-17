package com.madtitan94.transactionsparser.feature.upload.domain

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.madtitan94.transactionsparser.core.domain.model.Transaction
import com.madtitan94.transactionsparser.core.domain.model.TransactionKey
import com.madtitan94.transactionsparser.core.domain.model.TransactionType
import org.junit.jupiter.api.Test

class DuplicateDetectorTest {

    private fun txn(
        ref: String? = null,
        utr: String? = null,
        payee: String = "swiggy",
        amountPaise: Long = 25000,
        atMillis: Long = 1_000L
    ) = Transaction(
        sessionId = 2L,
        dateTimeUtcMillis = atMillis,
        rawPayee = payee,
        normalizedPayee = payee,
        amountPaise = amountPaise,
        type = TransactionType.DEBIT,
        transactionRef = ref,
        utr = utr,
        payeeId = null
    )

    private fun key(
        id: Long,
        ref: String? = null,
        utr: String? = null,
        payee: String = "swiggy",
        amountPaise: Long = 25000,
        atMillis: Long = 1_000L
    ) = TransactionKey(
        id = id,
        transactionRef = ref,
        utr = utr,
        normalizedPayee = payee,
        amountPaise = amountPaise,
        dateTimeUtcMillis = atMillis
    )

    @Test
    fun `matching transactionRef flags the row and points at the original`() {
        val result = DuplicateDetector.flag(
            candidates = listOf(txn(ref = "T123")),
            existingKeys = listOf(key(id = 7L, ref = "T123"))
        )

        assertThat(result.single().isDuplicate).isTrue()
        assertThat(result.single().duplicateOfTransactionId).isEqualTo(7L)
    }

    @Test
    fun `flagged rows are excluded so totals are correct straight after import`() {
        val result = DuplicateDetector.flag(
            candidates = listOf(txn(ref = "T123")),
            existingKeys = listOf(key(id = 7L, ref = "T123"))
        )

        assertThat(result.single().isExcluded).isTrue()
    }

    @Test
    fun `matching utr flags the row even when the ref differs`() {
        val result = DuplicateDetector.flag(
            candidates = listOf(txn(ref = "T-new", utr = "998877")),
            existingKeys = listOf(key(id = 3L, ref = "T-old", utr = "998877"))
        )

        assertThat(result.single().isDuplicate).isTrue()
        assertThat(result.single().duplicateOfTransactionId).isEqualTo(3L)
    }

    @Test
    fun `a genuinely new transaction is left untouched`() {
        val result = DuplicateDetector.flag(
            candidates = listOf(txn(ref = "T-new")),
            existingKeys = listOf(key(id = 1L, ref = "T-old"))
        )

        assertThat(result.single().isDuplicate).isFalse()
        assertThat(result.single().isExcluded).isFalse()
        assertThat(result.single().duplicateOfTransactionId).isNull()
    }

    @Test
    fun `rows with no ref or utr fall back to payee, amount and timestamp`() {
        val result = DuplicateDetector.flag(
            candidates = listOf(txn(payee = "zomato", amountPaise = 45000, atMillis = 5_000L)),
            existingKeys = listOf(key(id = 9L, payee = "zomato", amountPaise = 45000, atMillis = 5_000L))
        )

        assertThat(result.single().isDuplicate).isTrue()
        assertThat(result.single().duplicateOfTransactionId).isEqualTo(9L)
    }

    @Test
    fun `the composite fallback does not fire when the amount differs`() {
        val result = DuplicateDetector.flag(
            candidates = listOf(txn(payee = "zomato", amountPaise = 45000, atMillis = 5_000L)),
            existingKeys = listOf(key(id = 9L, payee = "zomato", amountPaise = 45001, atMillis = 5_000L))
        )

        assertThat(result.single().isDuplicate).isFalse()
    }

    @Test
    fun `an existing refless row never matches an incoming row that has a ref`() {
        // The incoming row is identifiable, so the weak composite key must not be consulted —
        // two distinct payments to the same shop for the same amount would collide otherwise.
        val result = DuplicateDetector.flag(
            candidates = listOf(txn(ref = "T-new", payee = "zomato", amountPaise = 45000, atMillis = 5_000L)),
            existingKeys = listOf(key(id = 9L, payee = "zomato", amountPaise = 45000, atMillis = 5_000L))
        )

        assertThat(result.single().isDuplicate).isFalse()
    }

    @Test
    fun `a statement that repeats a payment inside itself flags only the second copy`() {
        val result = DuplicateDetector.flag(
            candidates = listOf(txn(ref = "T500"), txn(ref = "T500")),
            existingKeys = emptyList()
        )

        assertThat(result[0].isDuplicate).isFalse()
        assertThat(result[1].isDuplicate).isTrue()
        // The original is in this same batch and has no id yet, so there is nothing to link to.
        assertThat(result[1].duplicateOfTransactionId).isNull()
    }

    @Test
    fun `a third copy still points at the stored original rather than a flagged sibling`() {
        val result = DuplicateDetector.flag(
            candidates = listOf(txn(ref = "T500"), txn(ref = "T500")),
            existingKeys = listOf(key(id = 42L, ref = "T500"))
        )

        assertThat(result.map { it.duplicateOfTransactionId }).isEqualTo(listOf(42L, 42L))
    }

    @Test
    fun `order is preserved so flagged rows line up with their input`() {
        val result = DuplicateDetector.flag(
            candidates = listOf(txn(ref = "A"), txn(ref = "B"), txn(ref = "C")),
            existingKeys = listOf(key(id = 1L, ref = "B"))
        )

        assertThat(result.map { it.transactionRef }).isEqualTo(listOf("A", "B", "C"))
        assertThat(result.map { it.isDuplicate }).isEqualTo(listOf(false, true, false))
    }
}
