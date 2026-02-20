package com.epfl.esl.chaze.data.repository

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.epfl.esl.chaze.R
import com.epfl.esl.chaze.data.model.BasketItemModel
import com.epfl.esl.chaze.data.model.PastBasketModel
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.toObject
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await
import java.util.Locale

// Data classes for safe Firestore mapping
data class PastBasketFirestore(
    val name: String = "",
    val items: List<BasketItemFirestore> = emptyList(),
    val store: String = "",
    val retailer: String = "",
    val retailerLogoUrl: String = "",
    val totalPrice: Double = 0.0,
    val totalPriceBeforeDiscount: Double = 0.0,
    val purchaseTimeStamp: Long = 0
)

// Note: BasketItemFirestore is imported from FavoriteBasketRepository (defined there)

/**
 * Repository for managing past baskets in Firestore.
 * Past baskets are completed shopping trips that are saved to the cloud.
 */
class PastBasketRepository {

    private val db = Firebase.firestore
    private val auth = Firebase.auth

    /**
     * Saves a past basket to Firestore under the current user's pastBaskets subcollection.
     */
    suspend fun savePastBasket(
        basketName: String,
        basketItems: List<BasketItemModel>,
        store: String,
        retailer: String,
        totalPrice: Double,
        totalPriceBeforeDiscount: Double,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            onError("User not authenticated")
            return
        }

        try {
            // Fetch retailer logo URL from retailer collection
            val retailerLogoUrl = try {
                val retailerDoc = db.collection("retailer")
                    .document(retailer)
                    .get()
                    .await()
                retailerDoc.getString("logo_url") ?: ""
            } catch (e: Exception) {
                println("Error fetching retailer logo URL: ${e.message}")
            }
            
            val purchaseTimeStamp = System.currentTimeMillis() // we get the current time in milliseconds
            // round the price to 2 decimals
            val roundedTotalPrice = String.format(Locale.getDefault(),"%.2f", totalPrice).toDouble()
            val roundedTotalPriceBeforeDiscount = String.format(Locale.getDefault(),"%.2f", totalPriceBeforeDiscount).toDouble()
            val pastBasketData = mapOf(
                "name" to basketName,
                "items" to basketItems.map { item ->
                    mapOf(
                        "id" to item.id,
                        "name" to item.name,
                        "description" to item.description,
                        "imageUrl" to (item.imageUrl ?: ""),
                        "quantity" to item.quantity,
                        "price" to item.price,
                        "originalPrice" to item.originalPrice,
                        "productName" to item.productName,
                        "unit" to item.unit,
                        "retailerProductId" to item.retailerProductId
                    )
                },
                "store" to store,
                "retailer" to retailer,
                "retailerLogoUrl" to retailerLogoUrl,
                "totalPrice" to roundedTotalPrice,
                "totalPriceBeforeDiscount" to roundedTotalPriceBeforeDiscount,
                "purchaseTimeStamp" to purchaseTimeStamp
            )

            db.collection("users").document(userId).collection("pastBaskets")
                .add(pastBasketData)
                .await()

            onSuccess()
        } catch (e: Exception) {
            onError(e.message ?: "Unknown error")
        }
    }

    /**
     * Loads all past baskets for the current user from Firestore.
     * throw Exception if there's an error loading baskets or if user is not authenticated
     */
    suspend fun loadPastBaskets(): List<PastBasketModel> {
        val userId = auth.currentUser?.uid
            ?: throw IllegalStateException("User not authenticated")

        val documents = db.collection("users").document(userId).collection("pastBaskets")
            .orderBy("purchaseTimeStamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .get()
            .await()

        // Convert documents to PastBasketModel objects. The mapNotNull is used to filter out any null objects.
        return documents.mapNotNull { doc ->
            try {
                val firestoreBasket = doc.toObject<PastBasketFirestore>()
                PastBasketModel(
                    id = doc.id,
                    name = firestoreBasket.name,
                    timestamp = firestoreBasket.purchaseTimeStamp,
                    store = firestoreBasket.store,
                    retailer = firestoreBasket.retailer,
                    retailerLogoUrl = firestoreBasket.retailerLogoUrl,
                    totalPrice = firestoreBasket.totalPrice,
                    totalPriceBeforeDiscount = firestoreBasket.totalPriceBeforeDiscount,
                    purchaseDate = firestoreBasket.purchaseTimeStamp,
                    items = firestoreBasket.items.map { firestoreItem ->
                        BasketItemModel(
                            id = firestoreItem.id,
                            name = firestoreItem.name,
                            description = firestoreItem.description,
                            imageUrl = firestoreItem.imageUrl,
                            imageRes = R.drawable.chaze_logo,
                            quantity = firestoreItem.quantity,
                            price = firestoreItem.price,
                            originalPrice = firestoreItem.originalPrice,
                            productName = firestoreItem.productName,
                            unit = firestoreItem.unit,
                            retailerProductId = firestoreItem.retailerProductId
                        )
                    }
                )
            } catch (e: Exception) {
                println("Error parsing past basket: ${e.message}")
                null
            }
        }
    }

    /**
     * Deletes a past basket from Firestore.
     */
    suspend fun deletePastBasket(basketId: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            onError("User not authenticated")
            return
        }

        try {
            db.collection("users").document(userId).collection("pastBaskets").document(basketId)
                .delete()
                .await()
            onSuccess()
        } catch (e: Exception) {
            onError(e.message ?: "Unknown error")
        }
    }

    /**
     * Sets up a real-time listener for past baskets.
     */
    fun getPastBasketsLiveData(): LiveData<List<PastBasketModel>> {
        val liveData = MutableLiveData<List<PastBasketModel>>()
        val userId = auth.currentUser?.uid

        if (userId == null) {
            liveData.value = emptyList()
            return liveData
        }

        db.collection("users").document(userId).collection("pastBaskets")
            .orderBy("purchaseTimeStamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    return@addSnapshotListener
                }

                val pastBaskets = snapshot?.mapNotNull { doc ->
                    try {
                        val firestoreBasket = doc.toObject<PastBasketFirestore>()
                        PastBasketModel(
                            id = doc.id,
                            name = firestoreBasket.name,
                            timestamp = firestoreBasket.purchaseTimeStamp,
                            store = firestoreBasket.store,
                            retailer = firestoreBasket.retailer,
                            retailerLogoUrl = firestoreBasket.retailerLogoUrl,
                            totalPrice = firestoreBasket.totalPrice,
                            totalPriceBeforeDiscount = firestoreBasket.totalPriceBeforeDiscount,
                            purchaseDate = firestoreBasket.purchaseTimeStamp,
                            items = firestoreBasket.items.map { firestoreItem ->
                                BasketItemModel(
                                    id = firestoreItem.id,
                                    name = firestoreItem.name,
                                    description = firestoreItem.description,
                                    imageUrl = firestoreItem.imageUrl,
                                    imageRes = R.drawable.chaze_logo,
                                    quantity = firestoreItem.quantity,
                                    price = firestoreItem.price,
                                    originalPrice = firestoreItem.originalPrice,
                                    productName = firestoreItem.productName,
                                    unit = firestoreItem.unit,
                                    retailerProductId = firestoreItem.retailerProductId
                                )
                            }
                        )
                    } catch (e: Exception) {
                        println("Error parsing past basket: ${e.message}")
                        null
                    }
                }
                liveData.value = pastBaskets ?: emptyList()
            }
        return liveData
    }
}
