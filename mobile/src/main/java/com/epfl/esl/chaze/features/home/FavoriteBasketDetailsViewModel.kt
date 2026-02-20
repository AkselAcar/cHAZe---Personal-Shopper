package com.epfl.esl.chaze.features.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.epfl.esl.chaze.data.model.PastBasketModel
import com.epfl.esl.chaze.data.repository.BasketRepository
import com.epfl.esl.chaze.data.repository.FavoriteBasketRepository
import kotlinx.coroutines.launch

/**
 * ViewModel for FavoriteBasketDetailsScreen.
 * Handles loading and deleting favorite baskets (product types for optimization).
 */
class FavoriteBasketDetailsViewModel : ViewModel() {

    private val _basket = MutableLiveData<PastBasketModel?>()
    val basket: LiveData<PastBasketModel?> = _basket

    private val favoriteBasketRepository = FavoriteBasketRepository() // Repository instance

    /**
     * Loads a favorite basket by its ID.
     */
    fun loadBasket(basketId: String) {
        // Load from favorite baskets cache
        _basket.value = BasketRepository.favoriteBaskets.value?.find { it.id == basketId }
    }

    fun addItemsToCurrentBasket() {
        val items = _basket.value?.items ?: return
        BasketRepository.addAllItems(items)
    }

    /**
     * Deletes the favorite basket from both local cache and Firestore.
     */
    fun deleteBasket() {
        val basketId = _basket.value?.id ?: return

        // Remove from local cache first (instant UI update)
        BasketRepository.deletePastBasket(basketId)

        // Delete from Firestore
        viewModelScope.launch {
            favoriteBasketRepository.deleteFavoriteBasket(
                basketId = basketId,
                onSuccess = {},
                onError = {}
            )
        }
    }
}
