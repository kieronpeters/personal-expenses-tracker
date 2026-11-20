package com.personalexpenses.tracker.data.model

/** Result of attempting to write a category. */
sealed class CategorySaveResult {
    data class Success(val id: Long) : CategorySaveResult()
    data object DuplicateName : CategorySaveResult()
}

/** Result of attempting to delete a category. */
enum class CategoryDeleteResult {
    DELETED,
    IN_USE,
    NOT_FOUND
}
