package com.epfl.esl.chaze.data.model

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.PropertyName

/**
 * Data model representing a store/supermarket in the app.
 * Contains store information and coordinates for map display.
 *
 * It uses manual deserialization for coordinates due to Firebase limitations
 * with nested Map structures.
 */
data class StoreModel(
    // Firebase field mappings - maps Kotlin property names to Firestore document field names
    @get:PropertyName("store_id") @set:PropertyName("store_id")
    var storeId: String = "",
    
    val name: String = "",
    val type: String = "",
    
    @get:PropertyName("location_text") @set:PropertyName("location_text")
    var locationText: String = "",
    
    val address: String = "",
    
    @get:PropertyName("retailer_id") @set:PropertyName("retailer_id")
    var retailerId: String = "",
    
    @get:PropertyName("owner_id") @set:PropertyName("owner_id")
    var ownerId: String = "",
    
    // (m) Store latitude - extracted manually from nested Firestore coordinates map
    // Stored as flat properties instead of nested to simplify distance calculations and map operations
    var latitude: Double = 0.0,
    
    // (m) Store longitude - extracted manually from nested Firestore coordinates map
    // Stored as flat properties instead of nested to simplify distance calculations and map operations
    var longitude: Double = 0.0
) {

    companion object {
        /**
         * Create a StoreModel from a Firestore DocumentSnapshot.
         * 
         * This function manually extracts coordinates from the nested Firestore structure
         * instead of relying on toObject() because Firebase's automatic deserialization
         * doesn't reliably handle nested Maps containing numeric values that may come from
         * different Firestore types (Double, Long, Number).
         *
         * Parameters:
         * doc (DocumentSnapshot): The Firestore document containing store data
         *
         * Returns:
         * StoreModel? : A StoreModel instance if successfully parsed, null if coordinates are missing or invalid
         *
         * Type handling rationale:
         * Firestore can serialize numbers as Double, Long, or other Number types depending on the source.
         * We explicitly check for all possible numeric types to ensure robust parsing across different
         * data upload scenarios and prevent null pointer exceptions from type mismatches.
         */
        fun fromDocument(doc: DocumentSnapshot): StoreModel? {
            val data = doc.data ?: return null
            
            // Extract and validate coordinates structure
            val coordsRaw = data["coordinates"]
            
            // Type cast to Map to access nested fields
            val coordsMap = coordsRaw as? Map<*, *>
            if (coordsMap == null) {
                android.util.Log.w("StoreModel", "Store ${doc.id} - coordinates is not a Map!")
                return null
            }
            
            // Extract latitude with type checking (Firestore may return Double, Long, or other Number types)
            // Using when expression ensures we handle all possible numeric types and provide a safe fallback
            val lat = when (val latVal = coordsMap["latitude"]) {
                is Double -> latVal
                is Long -> latVal.toDouble()
                is Number -> latVal.toDouble()
                else -> {
                    android.util.Log.w("StoreModel", "Store ${doc.id} - latitude is not a number: $latVal")
                    0.0
                }
            }
            
            // Extract longitude with type checking (same rationale as latitude)
            val lng = when (val lngVal = coordsMap["longitude"]) {
                is Double -> lngVal
                is Long -> lngVal.toDouble()
                is Number -> lngVal.toDouble()
                else -> {
                    android.util.Log.w("StoreModel", "Store ${doc.id} - longitude is not a number: $lngVal")
                    0.0
                }
            }
            
            // Construct StoreModel with extracted data, using document ID as fallback for missing store_id
            return StoreModel(
                storeId = (data["store_id"] as? String) ?: doc.id,
                name = (data["name"] as? String) ?: "",
                type = (data["type"] as? String) ?: "",
                locationText = (data["location_text"] as? String) ?: "",
                address = (data["address"] as? String) ?: "",
                retailerId = (data["retailer_id"] as? String) ?: "",
                ownerId = (data["owner_id"] as? String) ?: "",
                latitude = lat,
                longitude = lng
            )
        }
    }
}
