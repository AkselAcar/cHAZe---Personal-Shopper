package com.epfl.esl.chaze.features.search

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.epfl.esl.chaze.data.model.RecommendedProductModel
import com.epfl.esl.chaze.data.repository.BasketRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

data class SearchResultItem(
    val product: RecommendedProductModel,
    var quantity: Double = 0.0
)

class SearchViewModel : ViewModel() {

    private val _searchResults = MutableLiveData<List<SearchResultItem>>()
    val searchResults: LiveData<List<SearchResultItem>> = _searchResults

    private val _isSearching = MutableLiveData(false)
    val isSearching: LiveData<Boolean> = _isSearching

    private var searchJob: Job? = null
    
    // Firestore instance
    private val db: FirebaseFirestore = Firebase.firestore

    init {
        // Observe basket changes to update search results quantities
        try {
            BasketRepository.basketItems.observeForever { basketItems ->
                val currentResults = _searchResults.value ?: return@observeForever
                val updatedResults = currentResults.map { item ->
                    val found = basketItems.find { it.id == item.product.id }
                    val count = found?.quantity ?: 0.0
                    if (item.quantity != count) {
                        item.copy(quantity = count)
                    } else {
                        item
                    }
                }
                if (updatedResults != currentResults) {
                    _searchResults.value = updatedResults
                }
            }
        } catch (e: Exception) {
            println("Error in SearchViewModel init: ${e.message}")
            // Handle case where BasketRepository might not be initialized
        }
    }

    fun searchProducts(query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (query.isBlank()) {
                _searchResults.value = emptyList()
                return@launch
            }
            
            _isSearching.value = true
            delay(500)

            val lowerCaseQuery = query.lowercase().trim()
            
            db.collection("product_types")
                .whereArrayContains("search_keywords", lowerCaseQuery)
                .get()
                .addOnSuccessListener { documents ->
                    val productResults = documents.map { doc ->
                        RecommendedProductModel(
                            id = doc.getString("product_type") ?: doc.id,
                            name = doc.getString("display_name") ?: "Unknown Product",
                            category = doc.getString("category") ?: "",
                            imageUrl = doc.getString("image_url") ?: "",
                            unit = doc.getString("unit") ?: ""
                        )
                    }
                    
                    val basketItems = try { BasketRepository.basketItems.value.orEmpty() } catch (_: Exception) { emptyList() }
                    
                     _searchResults.value = productResults.map { product ->
                         SearchResultItem(
                             product = product,
                             quantity = basketItems.find { it.id == product.id }?.quantity ?: 0.0
                         )
                    }
                    _isSearching.value = false
                }
                .addOnFailureListener {
                     _searchResults.value = emptyList()
                     _isSearching.value = false
                }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _searchResults.value = emptyList()
    }
//  These functions are called "increment" and "decrement" because
//  previously, it was possible to add more than one item, but this purpose
//  has been changed over time but the function names did not change
    fun incrementQuantity(item: SearchResultItem) {
        BasketRepository.addProduct(item.product)
    }

    fun decrementQuantity(item: SearchResultItem) {
        BasketRepository.removeProduct(item.product.id)
    }

    fun addToCart(item: SearchResultItem) {
        incrementQuantity(item)
    }
}
