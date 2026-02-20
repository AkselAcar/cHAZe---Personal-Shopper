package com.epfl.esl.chaze.data.model

/**
 * Represents an optimized store option for a basket
 */
data class StoreOption(
    val store: StoreModel,
    val totalPrice: Double,
    val distanceKm: Double,
    val durationMinutes: Int?,
    val unavailableProducts: List<String> = emptyList(), // List of product names that are not available at this store
    val bioSubstitutions: Map<String, String> = emptyMap(), // Map of bio product name -> non-bio product name that was substituted
    val totalSavings: Double = 0.0, // Total amount saved from discounts
    val bioUpgrades: Map<String, String> = emptyMap() // Map of non-bio product name -> bio product name that was upgraded to (if bio is cheaper)
)
