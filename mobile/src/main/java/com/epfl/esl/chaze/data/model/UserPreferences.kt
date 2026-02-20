package com.epfl.esl.chaze.data.model

data class UserPreferences(
    val transportMode: String = TransportMode.DRIVING.name,
    val maxDistanceKm: Double = 10.0 // Maximum distance in kilometers the user is willing to travel
)
