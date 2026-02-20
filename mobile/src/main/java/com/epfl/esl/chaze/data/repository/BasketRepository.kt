package com.epfl.esl.chaze.data.repository

import android.annotation.SuppressLint
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.epfl.esl.chaze.data.model.BasketItemModel
import com.epfl.esl.chaze.data.model.PastBasketModel
import com.epfl.esl.chaze.data.model.RecommendedProductModel
import com.epfl.esl.chaze.R
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await

object BasketRepository {

    @SuppressLint("StaticFieldLeak")
    private val db = Firebase.firestore

    private val _basketItems = MutableLiveData<List<BasketItemModel>>(emptyList())
    val basketItems: LiveData<List<BasketItemModel>> = _basketItems

    private val _pastBaskets = MutableLiveData<List<PastBasketModel>>(emptyList())
    val pastBaskets: LiveData<List<PastBasketModel>> = _pastBaskets

    private val _favoriteBaskets = MutableLiveData<List<PastBasketModel>>(emptyList())
    val favoriteBaskets: LiveData<List<PastBasketModel>> = _favoriteBaskets

    fun addProduct(product: RecommendedProductModel) {
        val currentList = _basketItems.value.orEmpty().toMutableList()
        val existingItemIndex = currentList.indexOfFirst { it.id == product.id }

        if (existingItemIndex != -1) {
            // Product type already in basket -> stay at 1
            return
        } else {
            // Add item
            val newItem = BasketItemModel(
                id = product.id,
                name = product.name,
                description = product.category,
                imageRes = R.drawable.chaze_logo,
                imageUrl = product.imageUrl,
                quantity = 1.0
            )
            currentList.add(newItem)
        }
        _basketItems.value = currentList
    }

    fun addAllItems(items: List<BasketItemModel>) {
        val currentList = _basketItems.value.orEmpty().toMutableList()
        
        items.forEach { item ->
            val existingItemIndex = currentList.indexOfFirst { it.id == item.id }
            if (existingItemIndex != -1) {
                val existingItem = currentList[existingItemIndex]
                val updatedItem = existingItem.copy(quantity = existingItem.quantity + item.quantity)
                currentList[existingItemIndex] = updatedItem
            } else {
                currentList.add(item)
            }
        }
        _basketItems.value = currentList
    }

    fun removeProduct(productId: String) {
        val currentList = _basketItems.value.orEmpty().toMutableList()
        val existingItemIndex = currentList.indexOfFirst { it.id == productId }

        if (existingItemIndex != -1) {
            val existingItem = currentList[existingItemIndex]
            if (existingItem.quantity > 1) {
                // Decrement quantity (this code was written when there could be more than one instance of a single product in the basket)
                val updatedItem = existingItem.copy(quantity = existingItem.quantity - 1)
                currentList[existingItemIndex] = updatedItem
            } else {
                // Remove item if quantity becomes 0
                currentList.removeAt(existingItemIndex)
            }
            _basketItems.value = currentList
        }
    }
    
    fun removeBasketItem(item: BasketItemModel) {
        val currentList = _basketItems.value.orEmpty().toMutableList()
        currentList.removeAll { it.id == item.id }
        _basketItems.value = currentList
    }

    fun clearBasket() {
        _basketItems.value = emptyList()
    }

    fun getPastBasket(id: String): PastBasketModel? {
        // Search in past baskets first (loaded from Firestore)
        return _pastBaskets.value.orEmpty().find { it.id == id } 
            ?: _favoriteBaskets.value.orEmpty().find { it.id == id } // Then search in favorite baskets
    }

    fun deletePastBasket(basketId: String) {
        _pastBaskets.value = _pastBaskets.value.orEmpty().filter { it.id != basketId }
    }

    fun updatePastBasketsFromFirestore(pastBaskets: List<PastBasketModel>) {
        _pastBaskets.value = pastBaskets
    }

    fun updateFavoriteBasketsFromFirestore(favoriteBaskets: List<PastBasketModel>) {
        _favoriteBaskets.value = favoriteBaskets
    }

    suspend fun findProductTypeByName(query: String): RecommendedProductModel? {
        val lowerCaseQuery = query.lowercase().trim()
        
        // Check if user is requesting a bio product
        val isBioRequest = lowerCaseQuery.contains("bio") || 
                           lowerCaseQuery.contains("organic") ||
                           lowerCaseQuery.contains("biologique")
        
        // Extract the base product name (remove bio/organic keywords)
        val baseProductName = lowerCaseQuery
            .replace("bio", "")
            .replace("organic", "")
            .replace("biologique", "")
            .trim()

        try {
            if (isBioRequest && baseProductName.isNotEmpty()) {
                // Strategy 1: Search directly for "{base} bio" as product_type
                val bioProductType = "$baseProductName bio"

                var snapshot = db.collection("product_types")
                    .whereEqualTo("product_type", bioProductType)
                    .limit(1)
                    .get()
                    .await()
                
                if (!snapshot.isEmpty) {
                    val doc = snapshot.documents[0]
                    return RecommendedProductModel(
                        id = doc.getString("product_type") ?: doc.id,
                        name = doc.getString("display_name") ?: "Unknown Product",
                        category = doc.getString("category") ?: "",
                        imageUrl = doc.getString("image_url") ?: "",
                        unit = doc.getString("unit") ?: ""
                    )
                }
                
                // Strategy 2: Search for the full query in search_keywords
                snapshot = db.collection("product_types")
                    .whereArrayContains("search_keywords", lowerCaseQuery)
                    .limit(1)
                    .get()
                    .await()
                
                if (!snapshot.isEmpty) {
                    val doc = snapshot.documents[0]
                    return RecommendedProductModel(
                        id = doc.getString("product_type") ?: doc.id,
                        name = doc.getString("display_name") ?: "Unknown Product",
                        category = doc.getString("category") ?: "",
                        imageUrl = doc.getString("image_url") ?: "",
                        unit = doc.getString("unit") ?: ""
                    )
                }
                
                // Strategy 3: Search for base product and filter for bio variant
                snapshot = db.collection("product_types")
                    .whereArrayContains("search_keywords", baseProductName)
                    .get()
                    .await()
                
                // Prefer bio variant if available
                for (doc in snapshot.documents) {
                    val productType = doc.getString("product_type") ?: ""
                    if (productType.endsWith(" bio")) {
                        return RecommendedProductModel(
                            id = productType,
                            name = doc.getString("display_name") ?: "Unknown Product",
                            category = doc.getString("category") ?: "",
                            imageUrl = doc.getString("image_url") ?: "",
                            unit = doc.getString("unit") ?: ""
                        )
                    }
                }
                
                // Strategy 4: If no bio variant found, use non-bio but with bio ID
                if (!snapshot.isEmpty) {
                    val doc = snapshot.documents[0]
                    val baseId = doc.getString("product_type") ?: doc.id
                    return RecommendedProductModel(
                        id = "$baseId bio",
                        name = (doc.getString("display_name") ?: "Unknown Product") + " Bio",
                        category = doc.getString("category") ?: "",
                        imageUrl = doc.getString("image_url") ?: "",
                        unit = doc.getString("unit") ?: ""
                    )
                }
            }
            
            // Standard search by exact keyword match
            val snapshot = db.collection("product_types")
                .whereArrayContains("search_keywords", lowerCaseQuery)
                .limit(1)
                .get()
                .await()

            if (!snapshot.isEmpty) {
                val doc = snapshot.documents[0]
                return RecommendedProductModel(
                    id = doc.getString("product_type") ?: doc.id,
                    name = doc.getString("display_name") ?: "Unknown Product",
                    category = doc.getString("category") ?: "",
                    imageUrl = doc.getString("image_url") ?: "",
                    unit = doc.getString("unit") ?: ""
                )
            }
            
            return null
        } catch (e: Exception) {
            println("Error finding product type: $e")
            return null
        }
    }
}
