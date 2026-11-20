package com.personalexpenses.tracker.data

import androidx.room.TypeConverter
import com.personalexpenses.tracker.data.model.TransactionType

/** Room type converters for the small set of custom column types we use. */
class Converters {

    @TypeConverter
    fun fromTransactionType(type: TransactionType?): String? = type?.name

    @TypeConverter
    fun toTransactionType(value: String?): TransactionType? =
        if (value == null) null else TransactionType.valueOf(value)
}
