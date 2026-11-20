package com.personalexpenses.tracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.personalexpenses.tracker.data.dao.BudgetDao
import com.personalexpenses.tracker.data.dao.CategoryDao
import com.personalexpenses.tracker.data.dao.TransactionDao
import com.personalexpenses.tracker.data.entity.BudgetEntity
import com.personalexpenses.tracker.data.entity.CategoryEntity
import com.personalexpenses.tracker.data.entity.TransactionEntity

/**
 * The application database. A single instance is shared through [getInstance];
 * the database is created with the default category set already seeded.
 */
@Database(
    entities = [CategoryEntity::class, TransactionEntity::class, BudgetEntity::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun categoryDao(): CategoryDao

    abstract fun transactionDao(): TransactionDao

    abstract fun budgetDao(): BudgetDao

    companion object {
        const val DATABASE_NAME = "personal_expenses.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        private fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, DATABASE_NAME)
                .addCallback(SeedCallback)
                .build()

        /** Inserts the default categories as part of database creation. */
        private object SeedCallback : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                SeedData.defaultCategories().forEach { category ->
                    db.execSQL(
                        "INSERT INTO categories (name, type, colorHex, iconKey, isDefault, sortOrder) " +
                            "VALUES (?, ?, ?, ?, ?, ?)",
                        arrayOf<Any?>(
                            category.name,
                            category.type.name,
                            category.colorHex,
                            category.iconKey,
                            if (category.isDefault) 1 else 0,
                            category.sortOrder
                        )
                    )
                }
            }
        }
    }
}
