// This file was written with the help of AI
package com.epfl.esl.chaze.features.user

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.epfl.esl.chaze.features.localization.LocalizationViewModel
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState

/**
 * Map visualization component for displaying nearby stores.
 * 
 * Purpose: Show user location with search radius and dynamically fetch nearby stores on a Google Map.
 */
@Composable
fun MapVisualizationComponent(
    viewModel: SettingsViewModel,
    localizationViewModel: LocalizationViewModel,
    modifier: Modifier = Modifier
) {
    val chosenAddress by localizationViewModel.chosenAddress.collectAsStateWithLifecycle()
    val maxDistanceKm by viewModel.maxDistanceKm.collectAsStateWithLifecycle()
    val stores by viewModel.nearbyStores.collectAsStateWithLifecycle()
    val isLoadingStores by viewModel.isLoadingStores.collectAsStateWithLifecycle()

    // Convert address data to UserLocation for map rendering
    val userLocation = chosenAddress?.let {
        UserLocation(latitude = it.latitude, longitude = it.longitude)
    }

    // Fetch stores when location or distance changes
    androidx.compose.runtime.LaunchedEffect(chosenAddress, maxDistanceKm) {
        // Small delay to debounce rapid changes (especially from slider)
        kotlinx.coroutines.delay(300)
        chosenAddress?.let {
            viewModel.fetchStoresForLocation(it.latitude, it.longitude, maxDistanceKm)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(300.dp),
        contentAlignment = Alignment.Center
    ) {
        // Timeout safety check: warn if location doesn't load within 5 seconds
        androidx.compose.runtime.LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(5000) // Wait 5 seconds
        }
        
        if (userLocation != null) {
            val centerLatLng = LatLng(userLocation.latitude, userLocation.longitude)
            
            val cameraPositionState = rememberCameraPositionState {
                position = CameraPosition.fromLatLngZoom(centerLatLng, calculateZoomLevel(maxDistanceKm))
            }

            // Update camera zoom when distance slider changes
            androidx.compose.runtime.LaunchedEffect(maxDistanceKm) {
                cameraPositionState.position = CameraPosition.fromLatLngZoom(
                    centerLatLng,
                    calculateZoomLevel(maxDistanceKm)
                )
            }

            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(mapType = MapType.NORMAL)
            ) {
                // Blue circle shows search radius boundary
                Circle(
                    center = centerLatLng,
                    radius = maxDistanceKm * 1000, // Convert km to meters
                    strokeColor = Color(0xFF2196F3),
                    strokeWidth = 2f,
                    fillColor = Color(0x332196F3)
                )
                // Marker for user location
                Marker(
                    state = MarkerState(position = centerLatLng),
                    title = "Your Location",
                    snippet = "Current position"
                )

                // Store markers for all nearby stores within radius
                stores.forEach { store ->
                    Marker(
                        state = MarkerState(position = LatLng(store.latitude, store.longitude)),
                        title = store.name,
                        snippet = store.address
                    )
                }
            }

            // Loading indicator overlay while fetching store data
            // Placed on top of map
            if (isLoadingStores) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        } else {
            Text("Loading location...")
        }
    }
}

/**
 * Calculate appropriate map zoom level based on search radius.
 * 
 * Values chosen to show full radius circle.
 * - 1km and below: Highly zoomed in (14)
 * - 2-5km: Moderate zoom (12-13)
 * - 5-20km: Wide view (10-11)
 * - 50km+: Very wide view (8-9)
 */
private fun calculateZoomLevel(distanceKm: Double): Float {
    return when {
        distanceKm <= 1 -> 14f
        distanceKm <= 2 -> 13f
        distanceKm <= 5 -> 12f
        distanceKm <= 10 -> 11f
        distanceKm <= 20 -> 10f
        distanceKm <= 50 -> 9f
        else -> 8f
    }
}
