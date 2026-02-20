package com.epfl.esl.chaze.utils

import kotlin.math.*
import java.util.Locale

object DistanceCalculator {
    
    /**
     * Calculate distance between two points in kilometers
     * using the Haversine formula
     * 
     * @param lat1 Latitude of first point
     * @param lon1 Longitude of first point
     * @param lat2 Latitude of second point
     * @param lon2 Longitude of second point
     * @return Distance in kilometers
     */
    fun calculateDistance(
        lat1: Double, 
        lon1: Double, 
        lat2: Double, 
        lon2: Double
    ): Double {
        val earthRadiusKm = 6371.0
        
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        
        val a = sin(dLat / 2).pow(2) + 
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * 
                sin(dLon / 2).pow(2)
        
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        
        return earthRadiusKm * c
    }
    
    /**
     * Format distance as a human-readable string
     */
    fun formatDistance(distanceKm: Double): String {
        return if (distanceKm < 1.0) {
            String.format(Locale.getDefault(), "%.0f m", distanceKm * 1000)
        } else {
            String.format(Locale.getDefault(), "%.1f km", distanceKm)
        }
    }
}
