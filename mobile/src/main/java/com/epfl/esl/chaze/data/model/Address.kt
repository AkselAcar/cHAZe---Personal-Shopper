package com.epfl.esl.chaze.data.model

data class Address(
    val id: String, 
    val name: String, 
    val fullAddress: String,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0
)
