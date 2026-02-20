package com.epfl.esl.chaze.features.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.epfl.esl.chaze.data.model.PastBasketModel
import com.epfl.esl.chaze.data.repository.BasketRepository
import com.epfl.esl.chaze.data.repository.PastBasketRepository
import kotlinx.coroutines.launch

/**
 * PastBasketDetailsViewModel manages the details view for a past basket.
 * Handles loading and deleting past baskets from Firestore.
 */
class PastBasketDetailsViewModel : ViewModel() {

    private val _basket = MutableLiveData<PastBasketModel?>()
    val basket: LiveData<PastBasketModel?> = _basket

    private val pastBasketRepository = PastBasketRepository()

    /**
     * Loads a past basket by its ID from the local cache.
     */
    fun loadBasket(basketId: String) {
        _basket.value = BasketRepository.getPastBasket(basketId)
    }

    /**
     * Deletes the current past basket from Firestore.
     * The local cache will be updated automatically by the real-time listener.
     */
    fun deleteBasket() {
        val basketId = _basket.value?.id ?: return

        viewModelScope.launch {
            pastBasketRepository.deletePastBasket(
                basketId = basketId,
                onSuccess = {},
                onError = {}
            )
        }
    }
}
