package com.epfl.esl.chaze.data.model

data class BasketItemModel(
    val id: String, // Generic product type (e.g., "chicken bio")
    val name: String, 
    val description: String, 
    val imageRes: Int,
    val imageUrl: String? = null,
    var quantity: Double = 1.0,
    var price: Double = 0.0, // Discounted price
    var originalPrice: Double = 0.0, // Price before discount
    var productName: String = "", // Specific product name (e.g., "Bio Gruyère AOP rapé")
    var unit: String = "", // Product unit (e.g., "100g")
    var retailerProductId: String = "" // Retailer-specific document ID (e.g., "migros_00000042")
)
