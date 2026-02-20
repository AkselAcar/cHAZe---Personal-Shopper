package com.epfl.esl.chaze.features.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import com.epfl.esl.chaze.data.model.PastBasketModel
import com.epfl.esl.chaze.data.repository.BasketRepository
import com.epfl.esl.chaze.data.repository.FavoriteBasketRepository
import com.epfl.esl.chaze.data.repository.PastBasketRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow


class HomeViewModel : ViewModel() {

    // Repository instances
    private val favoriteBasketRepository = FavoriteBasketRepository()
    private val pastBasketRepository = PastBasketRepository()

    // StateFlow for favorite and past baskets
    private val _favoriteBaskets = MutableStateFlow<List<PastBasketModel>>(emptyList())
    val favoriteBaskets: StateFlow<List<PastBasketModel>> = _favoriteBaskets.asStateFlow()

    private val _pastBaskets = MutableStateFlow<List<PastBasketModel>>(emptyList())
    val pastBaskets: StateFlow<List<PastBasketModel>> = _pastBaskets.asStateFlow()

    // LiveData observers for cleanup
    private var favoriteBasketsLiveData: LiveData<List<PastBasketModel>>? = null
    private val favoriteBasketsObserver: Observer<List<PastBasketModel>> =
        Observer { baskets ->
            _favoriteBaskets.value = baskets
            // Update BasketRepository cache for getPastBasket() to work
            BasketRepository.updateFavoriteBasketsFromFirestore(baskets)
        }

    private var pastBasketsLiveData: LiveData<List<PastBasketModel>>? = null
    private val pastBasketsObserver: Observer<List<PastBasketModel>> =
        Observer { baskets ->
            _pastBaskets.value = baskets
            // Update BasketRepository cache for getPastBasket() to work
            BasketRepository.updatePastBasketsFromFirestore(baskets)
        }

    init {
        // Set up real-time listeners for baskets from Firestore
        favoriteBasketsLiveData = favoriteBasketRepository.getFavoriteBasketsLiveData()
        favoriteBasketsLiveData?.observeForever(favoriteBasketsObserver)
        
        pastBasketsLiveData = pastBasketRepository.getPastBasketsLiveData()
        pastBasketsLiveData?.observeForever(pastBasketsObserver)
    }

    override fun onCleared() {
        super.onCleared()
        // Clean up observers when ViewModel is destroyed
        favoriteBasketsLiveData?.removeObserver(favoriteBasketsObserver)
        pastBasketsLiveData?.removeObserver(pastBasketsObserver)
    }

}
