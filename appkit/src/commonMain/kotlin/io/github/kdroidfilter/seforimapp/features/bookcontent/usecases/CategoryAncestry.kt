package io.github.kdroidfilter.seforimapp.features.bookcontent.usecases

import io.github.kdroidfilter.seforimlibrary.core.models.Category

// Guards the walk against a cycle in a corrupted catalog
private const val MAX_CATEGORY_DEPTH = 512

/** The categories from the root down to [categoryId], walking up the parents. */
fun categoryAncestry(
    categoryId: Long,
    categoriesById: Map<Long, Category>,
): List<Category> =
    generateSequence(categoriesById[categoryId]) { category -> category.parentId?.let(categoriesById::get) }
        .take(MAX_CATEGORY_DEPTH)
        .toList()
        .asReversed()
