package com.epfl.esl.chaze.features.user

import android.annotation.SuppressLint
import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.epfl.esl.chaze.data.model.StoreModel
import com.epfl.esl.chaze.data.model.TransportMode
import com.epfl.esl.chaze.data.model.UserPreferences
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin

/**
 * Data class representing user geographic coordinates.
 */
data class UserLocation(
    val latitude: Double,
    val longitude: Double
)

/**
 * ViewModel managing user settings, preferences, and location-based store discovery.
 * Store Filtering: Haversine formula calculates distance; includes only stores <= maxDistanceKm
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    
    private val db = Firebase.firestore
    private val auth = Firebase.auth

    private val _username = MutableLiveData("JohnDoe")
    val username: LiveData<String> = _username

    // Transport mode determines API used for distance calculations (e.g., walking vs driving times)
    private val _transportMode = MutableStateFlow(TransportMode.DRIVING)
    val transportMode: StateFlow<TransportMode> = _transportMode
    
    // Maximum distance (km) for store search radius (dynamic)
    private val _maxDistanceKm = MutableStateFlow(10.0)
    val maxDistanceKm: StateFlow<Double> = _maxDistanceKm
    
    // Device GPS location: primary source but may be overridden by MapComponent's chosen address
    private val _userLocation = MutableStateFlow<UserLocation?>(null)

    // Stores within max distance radius: rendered by MapVisualizationComponent
    private val _nearbyStores = MutableStateFlow<List<StoreModel>>(emptyList())
    val nearbyStores: StateFlow<List<StoreModel>> = _nearbyStores
    
    // Loading indicators for store fetch and preferences load
    private val _isLoadingStores = MutableStateFlow(false)
    val isLoadingStores: StateFlow<Boolean> = _isLoadingStores
    
    private val _isLoading = MutableStateFlow(false)

    init {
        loadUserPreferences()
        fetchUserLocation()
        fetchUsername()
    }

    /**
     * Fetch username from Firestore user document on init.
     */
    private fun fetchUsername() {
        val userId = auth.currentUser?.uid ?: return
        
        viewModelScope.launch {
            try {
                val doc = db.collection("users")
                    .document(userId)
                    .get()
                    .await()
                
                if (doc.exists()) {
                    val username = doc.getString("username")
                    if (username != null) {
                        _username.value = username
                    }
                }
            } catch (e: Exception) {
                println("Error fetching username: $e")
            }
        }
    }
    
    /**
     * Load user transport mode and max distance preferences from Firestore.
     */
    private fun loadUserPreferences() {
        val userId = auth.currentUser?.uid ?: return
        
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val doc = db.collection("users")
                    .document(userId)
                    .collection("preferences")
                    .document("settings")
                    .get()
                    .await()
                
                if (doc.exists()) {
                    val prefs = doc.toObject(UserPreferences::class.java)
                    prefs?.let {
                        _transportMode.value = TransportMode.fromString(it.transportMode)
                        _maxDistanceKm.value = it.maxDistanceKm
                    }
                }
            } catch (e: Exception) {
                println("Error loading user preferences: $e")
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Update transport mode preference in Firestore and UI.
     */
    fun updateTransportMode(mode: TransportMode) {
        val userId = auth.currentUser?.uid ?: return
        
        viewModelScope.launch {
            try {
                _transportMode.value = mode
                
                val prefs = UserPreferences(
                    transportMode = mode.name,
                    maxDistanceKm = _maxDistanceKm.value
                )
                db.collection("users")
                    .document(userId)
                    .collection("preferences")
                    .document("settings")
                    .set(prefs)
                    .await()
            } catch (e: Exception) {
                println("Error updating transport mode: $e")
            }
        }
    }
    
    /**
     * Update maximum search distance and persist to Firestore.
     */
    fun updateMaxDistance(distance: Double) {
        val userId = auth.currentUser?.uid ?: return
        
        viewModelScope.launch {
            try {
                _maxDistanceKm.value = distance
                
                val prefs = UserPreferences(
                    transportMode = _transportMode.value.name,
                    maxDistanceKm = distance
                )
                db.collection("users")
                    .document(userId)
                    .collection("preferences")
                    .document("settings")
                    .set(prefs)
                    .await()

            } catch (e: Exception) {
                println("Error updating max distance: $e")
            }
        }
    }
    
    /**
     * Fetch device GPS location via FusedLocationProviderClient.
     */
    @SuppressLint("MissingPermission")
    private fun fetchUserLocation() {
        val context = getApplication<Application>().applicationContext
        
        try {
            val locationProvider = LocationServices.getFusedLocationProviderClient(context)
            
            locationProvider.lastLocation
                .addOnSuccessListener { location ->
                    if (location != null) {
                        _userLocation.value = UserLocation(
                            latitude = location.latitude,
                            longitude = location.longitude
                        )
                        fetchNearbyStores()
                    } else {
                        useFallbackLocation()
                    }
                }
                .addOnFailureListener { e ->
                    useFallbackLocation()
                }
                .addOnCompleteListener { task ->
                    if (!task.isSuccessful || task.result == null) {
                        useFallbackLocation()
                    }
                }
        } catch (e: Exception) {
            useFallbackLocation()
        }
    }
    
    /**
     * Use fallback EPFL location when GPS is unavailable.
     */
    private fun useFallbackLocation() {
        // Fallback: EPFL coordinates
        _userLocation.value = UserLocation(
            latitude = 46.5196535,
            longitude = 6.5669707
        )
        fetchNearbyStores()
    }
    
    /**
     * Public method to fetch stores for a specific location.
     */
    fun fetchStoresForLocation(latitude: Double, longitude: Double, maxDistanceKm: Double) {
        viewModelScope.launch {
            fetchNearbyStoresInternal(latitude, longitude, maxDistanceKm)
        }
    }
    
    /**
     * Fetch nearby stores using device GPS location.
     */
    private fun fetchNearbyStores() {
        val location = _userLocation.value ?: return
        val maxDistance = _maxDistanceKm.value
        
        viewModelScope.launch {
            fetchNearbyStoresInternal(location.latitude, location.longitude, maxDistance)
        }
    }
    
    /**
     * Core store fetching logic with extensive debugging.
     */
    private suspend fun fetchNearbyStoresInternal(latitude: Double, longitude: Double, maxDistance: Double) {
            try {
                _isLoadingStores.value = true
                
                // Check Firebase Auth state
                val currentUser = auth.currentUser
                Log.d("SettingsViewModel", "Current Firebase user: ${currentUser?.uid ?: "NOT LOGGED IN"}")
                Log.d("SettingsViewModel", "Firebase project: ${db.app.options.projectId}")
                
                val allStores = mutableListOf<StoreModel>()
                
                // Debug: Try to check what collections exist
                Log.d("SettingsViewModel", "Attempting to fetch retailers...")
                Log.d("SettingsViewModel", "Firestore instance: $db")
                
                // First, let's try a simple test query to verify connection
                try {
                    Log.d("SettingsViewModel", "Testing Firestore connection with users collection...")
                    val testQuery = db.collection("users").limit(1).get().await()
                    Log.d("SettingsViewModel", "Users collection test: ${if (testQuery.isEmpty) "empty" else "has data"}")
                } catch (e: Exception) {
                    Log.e("SettingsViewModel", "Error testing users collection: ${e.message}")
                }
                
                // Try the exact path from your screenshot: retailer/{retailerId}/stores
                Log.d("SettingsViewModel", "Trying direct path: retailer/aldi/stores")
                try {
                    val aldiStoresTest = db.collection("retailer")
                        .document("aldi")
                        .collection("stores")
                        .limit(5)
                        .get()
                        .await()
                    
                    Log.d("SettingsViewModel", "Direct test - aldi stores count: ${aldiStoresTest.documents.size}")
                    if (!aldiStoresTest.isEmpty) {
                        Log.d("SettingsViewModel", "✓ Found aldi stores directly! Sample: ${aldiStoresTest.documents.firstOrNull()?.id}")
                        aldiStoresTest.documents.forEach { doc ->
                            Log.d("SettingsViewModel", "  Store: ${doc.id}, fields: ${doc.data?.keys}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("SettingsViewModel", "Error accessing retailer/aldi/stores: ${e.message}", e)
                }
                
                // Try multiple possible collection names
                val collectionNames = listOf("retailer", "retailers", "Retailer", "Retailers")
                var retailersSnapshot: com.google.firebase.firestore.QuerySnapshot? = null
                var workingCollectionName: String? = null
                
                for (collectionName in collectionNames) {
                    try {
                        Log.d("SettingsViewModel", "Trying collection: '$collectionName'")
                        val snapshot = db.collection(collectionName)
                            .get()
                            .await()
                        
                        Log.d("SettingsViewModel", "Collection '$collectionName': ${snapshot.documents.size} documents, isEmpty: ${snapshot.isEmpty}")
                        
                        if (!snapshot.isEmpty) {
                            Log.d("SettingsViewModel", "✓ Found data in '$collectionName' collection: ${snapshot.documents.size} documents")
                            snapshot.documents.forEach { doc ->
                                Log.d("SettingsViewModel", "  Document ID: ${doc.id}")
                            }
                            retailersSnapshot = snapshot
                            workingCollectionName = collectionName
                            break
                        } else {
                            Log.d("SettingsViewModel", "✗ '$collectionName' collection is empty")
                        }
                    } catch (e: Exception) {
                        Log.e("SettingsViewModel", "✗ Error accessing '$collectionName': ${e.message}", e)
                    }
                }
                
                // If no collection found, try hardcoded retailer IDs
                if (retailersSnapshot == null || retailersSnapshot.isEmpty) {
                    Log.w("SettingsViewModel", "No retailer collection found via query. Trying hardcoded retailer IDs...")
                    val hardcodedRetailers = listOf("aldi", "aligro", "coop", "denner", "migros")
                    
                    for (retailerId in hardcodedRetailers) {
                        try {
                            Log.d("SettingsViewModel", "Fetching stores for hardcoded retailer: $retailerId")
                            val storesSnapshot = db.collection("retailer")
                                .document(retailerId)
                                .collection("stores")
                                .get()
                                .await()
                            
                            Log.d("SettingsViewModel", "Hardcoded retailer '$retailerId' has ${storesSnapshot.documents.size} stores")
                            
                            for (storeDoc in storesSnapshot.documents) {
                                try {
                                    val data = storeDoc.data
                                    Log.d("SettingsViewModel", "Store ${storeDoc.id} fields: ${data?.keys}")
                                    
                                    // Use manual extraction to properly handle coordinates
                                    val store = StoreModel.fromDocument(storeDoc)
                                    if (store != null) {
                                        allStores.add(store.copy(storeId = storeDoc.id, retailerId = retailerId))
                                        Log.d("SettingsViewModel", "✓ Added store: ${store.name} at (${store.latitude}, ${store.longitude})")
                                    }
                                } catch (e: Exception) {
                                    Log.e("SettingsViewModel", "Error parsing store ${storeDoc.id}: ${e.message}", e)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("SettingsViewModel", "Error fetching stores for $retailerId: ${e.message}", e)
                        }
                    }
                } else {
                    // Original logic for when we find retailers via query
                    Log.d("SettingsViewModel", "Using collection: '$workingCollectionName' with ${retailersSnapshot.documents.size} retailers")
                    
                    for (retailerDoc in retailersSnapshot.documents) {
                        val retailerId = retailerDoc.id
                        Log.d("SettingsViewModel", "Processing retailer: $retailerId")
                        
                        try {
                            val storesSnapshot = db.collection(workingCollectionName!!)
                                .document(retailerId)
                                .collection("stores")
                                .get()
                                .await()
                            
                            Log.d("SettingsViewModel", "Retailer '$retailerId' has ${storesSnapshot.documents.size} stores")
                            
                            for (storeDoc in storesSnapshot.documents) {
                                try {
                                    val data = storeDoc.data
                                    Log.d("SettingsViewModel", "Store ${storeDoc.id} fields: ${data?.keys}")
                                    
                                    // Use manual extraction to properly handle coordinates
                                    val store = StoreModel.fromDocument(storeDoc)
                                    if (store != null) {
                                        allStores.add(store.copy(storeId = storeDoc.id, retailerId = retailerId))
                                        Log.d("SettingsViewModel", "✓ Added store: ${store.name} at (${store.latitude}, ${store.longitude})")
                                    }
                                } catch (e: Exception) {
                                    Log.e("SettingsViewModel", "Error parsing store ${storeDoc.id}: ${e.message}", e)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("SettingsViewModel", "Error fetching stores for retailer $retailerId: ${e.message}", e)
                        }
                    }
                }
                
                Log.d("SettingsViewModel", "Total stores fetched: ${allStores.size}")
                
                // Filter stores within the max distance radius
                val nearbyStores = allStores.filter { store ->
                    if (store.latitude != 0.0 && store.longitude != 0.0) {
                        val distance = calculateDistance(
                            latitude,
                            longitude,
                            store.latitude,
                            store.longitude
                        )
                        Log.d("SettingsViewModel", "Store ${store.name} at distance: ${"%.2f".format(distance)}km")
                        distance <= maxDistance
                    } else {
                        Log.w("SettingsViewModel", "Store ${store.name} (${store.storeId}) has invalid coordinates: (${store.latitude}, ${store.longitude})")
                        false
                    }
                }
                
                _nearbyStores.value = nearbyStores
                Log.d("SettingsViewModel", "Found ${nearbyStores.size} stores within $maxDistance km")
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "Error fetching nearby stores: ${e.message}", e)
                e.printStackTrace()
            } finally {
                _isLoadingStores.value = false
            }
        }
    }
    
    /**
     * Calculate distance between two geographic coordinates using Haversine formula.
     * 
     * Formula: Great-circle distance accounting for Earth's curvature.
     * Purpose: More accurate than simple Euclidean distance for large distances.
     * Result: Distance in kilometers.
     */
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371.0 // Earth's radius in kilometers
        
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val lon1Rad = Math.toRadians(lon1)
        val lon2Rad = Math.toRadians(lon2)
        
        // Central angle using spherical law of cosines
        val centralAngle = acos(
            sin(lat1Rad) * sin(lat2Rad) +
            cos(lat1Rad) * cos(lat2Rad) * cos(lon2Rad - lon1Rad)
        )
        
        return earthRadius * centralAngle
    }
