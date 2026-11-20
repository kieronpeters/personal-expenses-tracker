package com.personalexpenses.tracker.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.personalexpenses.tracker.data.entity.TransactionEntity
import com.personalexpenses.tracker.data.model.CategorySpend
import com.personalexpenses.tracker.data.model.TransactionRow
import com.personalexpenses.tracker.data.model.TransactionType
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Insert
    suspend fun insert(transaction: TransactionEntity): Long

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun findById(id: Long): TransactionEntity?

    @Query(
        """
        SELECT t.id AS id,
               t.amountCents AS amountCents,
               t.type AS type,
               t.categoryId AS categoryId,
               t.date AS date,
               t.note AS note,
               c.name AS categoryName,
               c.colorHex AS categoryColor,
               c.iconKey AS categoryIcon
        FROM transactions t
        INNER JOIN categories c ON c.id = t.categoryId
        WHERE t.date >= :startInclusive AND t.date < :endExclusive
          AND (:type IS NULL OR t.type = :type)
          AND (:categoryId IS NULL OR t.categoryId = :categoryId)
          AND (:query = '' OR t.note LIKE '%' || :query || '%' OR c.name LIKE '%' || :query || '%')
        ORDER BY t.date DESC, t.id DESC
        """
    )
    fun observeFiltered(
        startInclusive: Long,
        endExclusive: Long,
        type: TransactionType?,
        categoryId: Long?,
        query: String
    ): Flow<List<TransactionRow>>

    @Query(
        """
        SELECT t.id AS id,
               t.amountCents AS amountCents,
               t.type AS type,
               t.categoryId AS categoryId,
               t.date AS date,
               t.note AS note,
               c.name AS categoryName,
               c.colorHex AS categoryColor,
               c.iconKey AS categoryIcon
        FROM transactions t
        INNER JOIN categories c ON c.id = t.categoryId
        WHERE t.date >= :startInclusive AND t.date < :endExclusive
          AND (:type IS NULL OR t.type = :type)
          AND (:categoryId IS NULL OR t.categoryId = :categoryId)
          AND (:query = '' OR t.note LIKE '%' || :query || '%' OR c.name LIKE '%' || :query || '%')
        ORDER BY t.date DESC, t.id DESC
        """
    )
    suspend fun findFiltered(
        startInclusive: Long,
        endExclusive: Long,
        type: TransactionType?,
        categoryId: Long?,
        query: String
    ): List<TransactionRow>

    @Query(
        """
        SELECT t.id AS id,
               t.amountCents AS amountCents,
               t.type AS type,
               t.categoryId AS categoryId,
               t.date AS date,
               t.note AS note,
               c.name AS categoryName,
               c.colorHex AS categoryColor,
               c.iconKey AS categoryIcon
        FROM transactions t
        INNER JOIN categories c ON c.id = t.categoryId
        WHERE t.id = :id
        """
    )
    suspend fun findRowById(id: Long): TransactionRow?

    @Query(
        """
        SELECT COALESCE(SUM(amountCents), 0)
        FROM transactions
        WHERE type = :type AND date >= :startInclusive AND date < :endExclusive
        """
    )
    suspend fun sumByType(type: TransactionType, startInclusive: Long, endExclusive: Long): Long

    @Query(
        """
        SELECT categoryId AS categoryId,
               SUM(amountCents) AS totalCents,
               COUNT(*) AS transactionCount
        FROM transactions
        WHERE type = :type AND date >= :startInclusive AND date < :endExclusive
        GROUP BY categoryId
        ORDER BY totalCents DESC
        """
    )
    suspend fun categoryTotals(
        type: TransactionType,
        startInclusive: Long,
        endExclusive: Long
    ): List<CategorySpend>

    @Query(
        """
        SELECT t.id AS id,
               t.amountCents AS amountCents,
               t.type AS type,
               t.categoryId AS categoryId,
               t.date AS date,
               t.note AS note,
               c.name AS categoryName,
               c.colorHex AS categoryColor,
               c.iconKey AS categoryIcon
        FROM transactions t
        INNER JOIN categories c ON c.id = t.categoryId
        ORDER BY t.date ASC, t.id ASC
        """
    )
    suspend fun findAllRows(): List<TransactionRow>

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun count(): Int

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()
}
