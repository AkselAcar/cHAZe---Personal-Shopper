package com.epfl.esl.chaze.data.model

data class PastBasketModel(
    val id: String,
    val name: String,
    val items: List<BasketItemModel>,
    val timestamp: Long,
    val store: String = "",
    val retailer: String = "",
    val retailerLogoUrl: String = "", // this contains the URL to retailer logo from Firestore
    val totalPrice: Double = 0.0,
    val totalPriceBeforeDiscount: Double = 0.0,
    val purchaseDate: Long = timestamp
)
