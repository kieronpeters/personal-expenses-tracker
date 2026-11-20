package com.personalexpenses.tracker.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.personalexpenses.tracker.data.model.TransactionType

/**
 * A spending or income category. Category names are unique per type so that
 * an expense category and an income category may share a name.
 */
@Entity(
    tableName = "categories",
    indices = [Index(value = ["name", "type"], unique = true)]
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val type: TransactionType,
    val colorHex: String,
    /** Emoji shown inside the colored category badge. */
    val iconKey: String,
    val isDefault: Boolean = false,
    val sortOrder: Int = 0
)
