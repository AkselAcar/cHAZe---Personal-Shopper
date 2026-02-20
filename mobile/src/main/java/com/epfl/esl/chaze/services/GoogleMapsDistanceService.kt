//This file was written with the help of AI
package com.epfl.esl.chaze.services

import com.epfl.esl.chaze.data.model.TransportMode
import com.epfl.esl.chaze.utils.DistanceCalculator
import com.google.maps.DistanceMatrixApi
import com.google.maps.GeoApiContext
import com.google.maps.model.DistanceMatrix
import com.google.maps.model.LatLng
import com.google.maps.model.TrafficModel
import com.google.maps.model.TravelMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Service for calculating real-world distances and travel times using Google Maps Distance Matrix API.
 */
class GoogleMapsDistanceService(private val apiKey: String) {
    
    /**
     * Google Maps API context (singleton per service instance).
     */
    private val context: GeoApiContext by lazy {
        GeoApiContext.Builder()
            .apiKey(apiKey)
            .build()
    }
    
    /**
     * Calculate distance and travel time between two coordinates.
     */
    suspend fun getDistance(
        originLat: Double,
        originLon: Double,
        destLat: Double,
        destLon: Double,
        mode: TransportMode
    ): DistanceResult = withContext(Dispatchers.IO) {
        try {
            
            val origin = LatLng(originLat, originLon)
            val destination = LatLng(destLat, destLon)
            
            // Convert TransportMode to Google Maps TravelMode
            // Each transport method uses different routing algorithms and has different availability
            val travelMode = when (mode) {
                TransportMode.DRIVING -> TravelMode.DRIVING
                TransportMode.WALKING -> TravelMode.WALKING
                TransportMode.BICYCLING -> TravelMode.BICYCLING
                TransportMode.TRANSIT -> TravelMode.TRANSIT
            }
            
            // Build request with transport mode
            // Distance Matrix API returns fastest/best route for given mode
            val request = DistanceMatrixApi.newRequest(context)
                .origins(origin)
                .destinations(destination)
                .mode(travelMode)
            
            // Add traffic model for driving mode to account for current congestion
            if (mode == TransportMode.DRIVING) {
                request.departureTime(java.time.Instant.now())
                request.trafficModel(TrafficModel.BEST_GUESS)
            }
            
            // Execute API request (await suspends until response received)
            val result: DistanceMatrix = request.await()
            
            // Parse response: check if we have valid distance data
            if (result.rows.isNotEmpty() && result.rows[0].elements.isNotEmpty()) {
                val element = result.rows[0].elements[0]
                
                if (element.distance != null && element.duration != null) {
                    val distanceKm = element.distance.inMeters / 1000.0
                    val durationMin = (element.duration.inSeconds / 60).toInt()
                    
                    return@withContext DistanceResult(
                        distanceKm = distanceKm,
                        durationMinutes = durationMin,
                        source = DistanceSource.GOOGLE_MAPS_API
                    )
                }
            }
            
            // If no valid result, fall back to local Haversine calculation
            fallbackToHaversine(originLat, originLon, destLat, destLon)
            
        } catch (e: Exception) {
            // API errors (network failure, invalid key, quota exceeded, etc.)
            // Fall back to Haversine rather than throwing exception
            fallbackToHaversine(originLat, originLon, destLat, destLon)
        }
    }
    
    /**
     * Fallback distance calculation using Haversine formula.
     */
    private fun fallbackToHaversine(
        originLat: Double,
        originLon: Double,
        destLat: Double,
        destLon: Double
    ): DistanceResult {
        val distanceKm = DistanceCalculator.calculateDistance(
            originLat, originLon,
            destLat, destLon
        )
        return DistanceResult(
            distanceKm = distanceKm,
            durationMinutes = null,
            source = DistanceSource.HAVERSINE_FALLBACK
        )
    }

}

/**
 * Result of a distance calculation query.
 */
data class DistanceResult(
    val distanceKm: Double,
    val durationMinutes: Int?,
    val source: DistanceSource
)

/**
 * Source tracking for distance calculations.
 */
enum class DistanceSource {
    GOOGLE_MAPS_API,
    HAVERSINE_FALLBACK,
}
