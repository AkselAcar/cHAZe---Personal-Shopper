package com.epfl.esl.chaze.features.localization

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.location.Geocoder
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import com.epfl.esl.chaze.data.model.Address
import com.epfl.esl.chaze.data.model.UserAddress
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import java.util.Locale

class LocalizationViewModel(application: Application) : AndroidViewModel(application) {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _chosenAddress = MutableStateFlow<Address?>(null)
    val chosenAddress: StateFlow<Address?> = _chosenAddress.asStateFlow()

    private val _savedAddresses = MutableStateFlow<List<Address>>(emptyList())
    val savedAddresses: StateFlow<List<Address>> = _savedAddresses.asStateFlow()

    private val db: FirebaseFirestore = Firebase.firestore
    private val auth = Firebase.auth

    init {
        loadChosenAddress()
        fetchSavedAddresses()
    }

    private val _searchResults = MutableStateFlow<List<Address>>(emptyList())
    val searchResults: StateFlow<List<Address>> = _searchResults.asStateFlow()

    private val _saveSuccess = MutableStateFlow<String?>(null)
    val saveSuccess: StateFlow<String?> = _saveSuccess.asStateFlow()

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        searchAddress(query)
    }

    private fun searchAddress(query: String) {
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>().applicationContext
            val gcd = Geocoder(context, Locale.getDefault())
            try {
                // Helper to safely get addresses
                @Suppress("DEPRECATION")
                val addresses = gcd.getFromLocationName(query, 5)

                if (!addresses.isNullOrEmpty()) {
                    _searchResults.value = addresses.map { addr ->
                        val fullAddr = (0..addr.maxAddressLineIndex).joinToString(", ") { i -> addr.getAddressLine(i) }
                        Address(
                            id = "search_${addr.latitude}_${addr.longitude}",
                            name = addr.featureName ?: addr.thoroughfare ?: "Unknown Place",
                            fullAddress = fullAddr,
                            latitude = addr.latitude,
                            longitude = addr.longitude
                        )
                    }
                } else {
                    _searchResults.value = emptyList()
                }
            } catch (e: Exception) {
                println("Geocoder error: ${e.message}")
                _searchResults.value = emptyList()
            }
        }
    }

    fun selectAddress(address: Address) {
        _chosenAddress.value = address
        // Save the chosen address to Firestore
        saveChosenAddress(address)
        _searchQuery.value = address.fullAddress
        _searchResults.value = emptyList()
    }
    
    private fun saveChosenAddress(address: Address) {
        val userId = auth.currentUser?.uid ?: return
        
        val userAddress = UserAddress(
            id = address.id,
            name = address.name,
            fullAddress = address.fullAddress,
            latitude = address.latitude,
            longitude = address.longitude
        )
        
        db.collection("users")
            .document(userId)
            .collection("preferences")
            .document("chosen_location")
            .set(userAddress)
    }
    
    private fun loadChosenAddress() {
        val userId = auth.currentUser?.uid
        
        if (userId == null) {
            fetchCurrentLocation()
            return
        }
        
        db.collection("users")
            .document(userId)
            .collection("preferences")
            .document("chosen_location")
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val userAddress = document.toObject(UserAddress::class.java)
                    userAddress?.let {
                        _chosenAddress.value = Address(
                            id = it.id,
                            name = it.name,
                            fullAddress = it.fullAddress,
                            latitude = it.latitude,
                            longitude = it.longitude
                        )
                    }
                } else {
                    // No saved address, fetch current location
                    fetchCurrentLocation()
                }
            }
            .addOnFailureListener { _ ->
                fetchCurrentLocation()
            }
    }

    @SuppressLint("MissingPermission")
    private fun fetchCurrentLocation() {
        val context = getApplication<Application>().applicationContext
        val locationProvider = LocationServices.getFusedLocationProviderClient(context)

        locationProvider.lastLocation.addOnCompleteListener { task: Task<Location> ->
            if (task.isSuccessful && task.result != null) {
                val location = task.result
                updateChosenAddress(context, location.latitude, location.longitude)
            } else {
                // Fallback for emulator or when location is unavailable: EPFL coordinates
                updateChosenAddress(context, 46.5196535, 6.5669707) // EPFL Lausanne
            }
        }
    }

    private fun updateChosenAddress(context: Context, latitude: Double, longitude: Double) {
        val addressString = getAddressFromCoordinates(context, latitude, longitude)
        _chosenAddress.value = Address(
            id = "current_location",
            name = "Current Location",
            fullAddress = addressString,
            latitude = latitude,
            longitude = longitude
        )
    }

    private fun getAddressFromCoordinates(context: Context, latitude: Double, longitude: Double): String {
        var addressString = "Unknown Location ($latitude, $longitude)"
        val gcd = Geocoder(context, Locale.getDefault())
        try {
            // Geocoder might fail on some emulators without Google APIs image or internet
            val addresses = gcd.getFromLocation(latitude, longitude, 1)
            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                // Combine address lines for a fuller address
                val sb = StringBuilder()
                for (i in 0..address.maxAddressLineIndex) {
                    sb.append(address.getAddressLine(i)).append("\n")
                }
                addressString = sb.toString().trim()
            }
        } catch (e: IOException) {
            println("Geocoder error: ${e.message}")
        }
        return addressString
    }

    private fun fetchSavedAddresses() {
        val userId = auth.currentUser?.uid ?: return

        db.collection("users")
            .document(userId)
            .collection("saved_addresses")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val addresses = snapshot.documents.mapNotNull { doc ->
                        val userAddress = doc.toObject(UserAddress::class.java)
                        // Convert UserAddress data model to local Address UI model
                        userAddress?.let {
                            Address(
                                id = doc.id,
                                name = it.name,
                                fullAddress = it.fullAddress,
                                latitude = it.latitude,
                                longitude = it.longitude
                            )
                        }
                    }
                    _savedAddresses.value = addresses
                }
            }
    }

    fun saveAddress(address: Address) {
        val userId = auth.currentUser?.uid ?: return

        // Check if address already exists in saved addresses
        if (_savedAddresses.value.any { it.fullAddress == address.fullAddress }) {
            _saveSuccess.value = "Address already saved"
            return
        }

        val userAddress = UserAddress(
            id = address.id,
            name = address.name,
            fullAddress = address.fullAddress,
            latitude = address.latitude,
            longitude = address.longitude
        )

        db.collection("users")
            .document(userId)
            .collection("saved_addresses")
            .add(userAddress)
            .addOnSuccessListener { _ ->
                _saveSuccess.value = "Address saved successfully"
            }
            .addOnFailureListener { _ ->
                _saveSuccess.value = "Failed to save address"
            }
    }

    fun clearSaveStatus() {
        _saveSuccess.value = null
    }
}
