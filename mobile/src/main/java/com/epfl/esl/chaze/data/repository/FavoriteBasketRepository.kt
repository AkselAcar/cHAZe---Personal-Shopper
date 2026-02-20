package com.epfl.esl.chaze.data.repository

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.epfl.esl.chaze.R
import com.epfl.esl.chaze.data.model.BasketItemModel
import com.epfl.esl.chaze.data.model.PastBasketModel
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.toObject
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await

// --- Data classes for safe Firestore mapping ---
/**
 * These data classes mirror the structure of a favorite basket document in Firestore.
 * Using them with Firestore's .toObject() method allows for automatic, type-safe parsing.
 * This avoids the "Unchecked cast" warning and prevents potential crashes if the
 * data in the database is ever malformed.
 */
data class FavoriteBasketFirestore(
    val name: String = "",
    val items: List<BasketItemFirestore> = emptyList(),
    val timestamp: Long = 0
)


data class BasketItemFirestore(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val imageUrl: String = "",
    val quantity: Double = 1.0,
    val price: Double = 0.0,
    val originalPrice: Double = 0.0,
    val productName: String = "",
    val unit: String = "",
    val retailerProductId: String = "" // Retailer-specific document ID
)


/**
 * We converted from 'object' TO 'class'.
 * This repository is now a 'class' instead of a singleton 'object'.
 * This was done to prevent a memory leak. An 'object' lives for the entire application lifecycle. If it
 * holds a reference to a Context (which Firebase does internally), that Context can never be
 * garbage collected and uses lots of memory.
 * By making it a class, we create instances only when needed (e.g., in a ViewModel), allowing
 * them to be properly destroyed and garbage collected along with the ViewModel.
 */
class FavoriteBasketRepository {

    private val db = Firebase.firestore
    private val auth = Firebase.auth // gets the authenticated user

    /**
    *Saves a basket as a favorite to Firestore under the current user's favoriteBaskets subcollection.
    */
    suspend fun saveFavoriteBasket(
        basketName: String,
        basketItems: List<BasketItemModel>,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            onError("User not authenticated")
            return
        }

        try {
            val favoriteBasketData = mapOf(
                "name" to basketName,
                "items" to basketItems.map { item ->
                    mapOf(
                        "id" to item.id,
                        "name" to item.name,
                        "description" to item.description,
                        "imageUrl" to (item.imageUrl ?: "")
                    )
                },
                "timestamp" to System.currentTimeMillis()
            )

            // We add the basket to the user's favoriteBaskets subcollection.
            db.collection("users").document(userId).collection("favoriteBaskets")
                .add(favoriteBasketData)
                .await()

            onSuccess()
        } catch (e: Exception) {
            onError(e.message ?: "Unknown error")
        }
    }

    /**
     * Sets up a real-time listener for favorite baskets.
     * This is the primary method for reading favorite baskets, it provides automatic updates.
     */
    fun getFavoriteBasketsLiveData(): LiveData<List<PastBasketModel>> {
        val liveData = MutableLiveData<List<PastBasketModel>>()
        val userId = auth.currentUser?.uid

        // We verify that the user is authenticated before starting to listen.
        if (userId == null) {
            liveData.value = emptyList()
            return liveData
        }

        // We use snapshot listener to listen for changes in the favoriteBaskets subcollection.
        db.collection("users").document(userId).collection("favoriteBaskets")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    return@addSnapshotListener
                }

                val favoriteBaskets = snapshot?.mapNotNull { doc ->
                    try {
                        // Using .toObject<>() for safe, automatic parsing of the data.
                        val firestoreBasket = doc.toObject<FavoriteBasketFirestore>()
                        // Map the parsed data to the model class used by the UI.
                        PastBasketModel(
                            id = doc.id,
                            name = firestoreBasket.name,
                            timestamp = firestoreBasket.timestamp,
                            items = firestoreBasket.items.map { firestoreItem ->
                                BasketItemModel(
                                    id = firestoreItem.id,
                                    name = firestoreItem.name,
                                    description = firestoreItem.description,
                                    imageUrl = firestoreItem.imageUrl,
                                    imageRes = R.drawable.chaze_logo // Provide a default value
                                )
                            }
                        )
                    } catch (e: Exception) {
                        println("Error parsing favorite basket: ${e.message}")
                        null
                    }
                }
                liveData.value = favoriteBaskets ?: emptyList()
            }
        return liveData
    }

    /**
     * Deletes a favorite basket from Firestore.
     */
    suspend fun deleteFavoriteBasket(basketId: String, onSuccess: () -> Unit, onError: (String) -> Unit) { // onError to handle errors if needed
        val userId = auth.currentUser?.uid
        if (userId == null) {
            onError("User not authenticated")
            return
        }

        try {
            db.collection("users").document(userId).collection("favoriteBaskets").document(basketId)
                .delete()
                .await()
            onSuccess()
        } catch (e: Exception) {
            onError(e.message ?: "Unknown error")
        }
    }

}
