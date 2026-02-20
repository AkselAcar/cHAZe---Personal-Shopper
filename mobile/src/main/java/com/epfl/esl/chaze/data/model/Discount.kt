package com.epfl.esl.chaze.data.model

data class Discount(
    val discount_active: Boolean = false,
    val discount_end: String = "",
    val discount_percentage: Int = 0,
    val discount_start: String = "",
    val discounted_price: Double = 0.0,
    val product_id: String = "",
    val product_name: String = ""
)
