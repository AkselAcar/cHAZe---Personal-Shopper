package com.epfl.esl.chaze.features.basket

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.epfl.esl.chaze.data.model.BasketItemModel
import com.epfl.esl.chaze.data.model.RecommendedProductModel
import com.epfl.esl.chaze.data.model.StoreModel
import com.epfl.esl.chaze.data.model.StoreOption
import com.epfl.esl.chaze.data.model.TransportMode
import com.epfl.esl.chaze.data.model.UserPreferences
import com.epfl.esl.chaze.data.repository.BasketRepository
import com.epfl.esl.chaze.services.GoogleMapsDistanceService
import com.epfl.esl.chaze.BuildConfig
import com.epfl.esl.chaze.utils.DistanceCalculator
import com.epfl.esl.chaze.data.repository.FavoriteBasketRepository
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Locale

// Backend logic for CurrentBasketScreen.kt
class CurrentBasketViewModel : ViewModel() {

    // Repository instance for favorite baskets
    private val favoriteBasketRepository = FavoriteBasketRepository()

    val basketItems: LiveData<List<BasketItemModel>> = BasketRepository.basketItems

    // State for optimization process
    private val _isOptimizing = MutableLiveData(false)
    val isOptimizing: LiveData<Boolean> = _isOptimizing

    private val _optimizationResult = MutableLiveData<List<BasketItemModel>?>(null)
    val optimizationResult: LiveData<List<BasketItemModel>?> = _optimizationResult

    private val _totalPrice = MutableLiveData(0.0)
    val totalPrice: LiveData<Double> = _totalPrice

    private val _selectedStore = MutableLiveData<StoreModel?>(null)
    val selectedStore: LiveData<StoreModel?> = _selectedStore
    
    // Selected store option with unavailable products info
    private val _selectedStoreOption = MutableLiveData<StoreOption?>(null)
    val selectedStoreOption: LiveData<StoreOption?> = _selectedStoreOption
    
    // Store optimization options (closest and cheapest)
    private val _closestStoreOption = MutableLiveData<StoreOption?>(null)
    val closestStoreOption: LiveData<StoreOption?> = _closestStoreOption
    
    private val _cheapestStoreOption = MutableLiveData<StoreOption?>(null)
    val cheapestStoreOption: LiveData<StoreOption?> = _cheapestStoreOption
    
    // Store options with non-bio alternatives
    private val _closestStoreOptionWithAlternatives = MutableLiveData<StoreOption?>(null)
    val closestStoreOptionWithAlternatives: LiveData<StoreOption?> = _closestStoreOptionWithAlternatives
    
    private val _cheapestStoreOptionWithAlternatives = MutableLiveData<StoreOption?>(null)
    val cheapestStoreOptionWithAlternatives: LiveData<StoreOption?> = _cheapestStoreOptionWithAlternatives
    
    // Whether user needs to select between options
    private val _needsUserSelection = MutableLiveData(false)
    val needsUserSelection: LiveData<Boolean> = _needsUserSelection
    
    // Whether bio alternatives are available
    private val _hasBioAlternatives = MutableLiveData(false)
    val hasBioAlternatives: LiveData<Boolean> = _hasBioAlternatives
    
    // Store original basket items before optimization (to preserve user's original intent)
    private var originalBasketItems: List<BasketItemModel>? = null

    private val _optimizationError = MutableLiveData<String?>(null)
    val optimizationError: LiveData<String?> = _optimizationError

    // State for favorite basket operations
    private val _isSavingFavorite = MutableLiveData(false)

    private val _favoriteError = MutableLiveData<String?>(null)

    private val db = Firebase.firestore
    private val auth = Firebase.auth
    
    // Google Maps Distance Service
    private val mapsService = GoogleMapsDistanceService(BuildConfig.GOOGLE_API_KEY)
    
    // User's transport mode preference
    private val _transportMode = MutableStateFlow(TransportMode.DRIVING)
    val transportMode: StateFlow<TransportMode> = _transportMode
    
    // User's maximum distance preference (used internally for optimization filtering)
    private val _maxDistanceKm = MutableStateFlow(10.0) // Default 10km

    data class PriceInfo(
        val finalPrice: Double,
        val originalPrice: Double,
        val hasDiscount: Boolean,
        val productName: String,
        val unit: String,
        val productImage: String? = null,
        val retailerProductId: String = "" // Firestore document ID (e.g., "migros_00000042")
    )
    
    init {
        loadUserPreferences()
    }

    fun removeBasketItem(item: BasketItemModel) {
        BasketRepository.removeBasketItem(item)
    }

    /**
     * Validates the current basket and finds optimal stores.
     * Does NOT save to history - history is saved after shopping with actual purchased products.
     */
//  Validates the current basket and finds optimal stores.
    fun validateBasket(userLatitude: Double = 0.0, userLongitude: Double = 0.0, transportMode: TransportMode = TransportMode.DRIVING) {
        val currentItems = basketItems.value ?: return
        if (currentItems.isEmpty()) return
        
        // Store original basket items to preserve user's intent
        originalBasketItems = currentItems.toList()
        
        // Update the transport mode to the one selected by the user for this optimization
        _transportMode.value = transportMode

        _isOptimizing.value = true
        _optimizationError.value = null

        viewModelScope.launch {
            try {
    
                // Find both closest and cheapest store options
                val storeOptions = findOptimalStores(userLatitude, userLongitude, transportMode, _maxDistanceKm.value, currentItems, allowNonBioAlternatives = false)
                
                if (storeOptions != null) {
                    _closestStoreOption.value = storeOptions.first
                    _cheapestStoreOption.value = storeOptions.second
                    
                    // Check if there are any bio products in the basket
                    val hasBioProducts = currentItems.any { it.id.endsWith(" bio") }
                    
                    if (hasBioProducts) {
                        // Try to find options with non-bio alternatives
                        val storeOptionsWithAlternatives = findOptimalStores(userLatitude, userLongitude, transportMode, _maxDistanceKm.value, currentItems, allowNonBioAlternatives = true)
                        
                        if (storeOptionsWithAlternatives != null) {
                            _closestStoreOptionWithAlternatives.value = storeOptionsWithAlternatives.first
                            _cheapestStoreOptionWithAlternatives.value = storeOptionsWithAlternatives.second
                            
                            // Check if alternatives actually helped
                            val strictHasUnavailable = storeOptions.first.unavailableProducts.isNotEmpty() || storeOptions.second.unavailableProducts.isNotEmpty()
                            val alternativesHelpful = storeOptionsWithAlternatives.first.bioSubstitutions.isNotEmpty() || 
                                                      storeOptionsWithAlternatives.second.bioSubstitutions.isNotEmpty()
                            
                            _hasBioAlternatives.value = strictHasUnavailable && alternativesHelpful
                        }
                    } else {
                        _hasBioAlternatives.value = false
                    }
                    
                    _needsUserSelection.value = true
                } else {
                    // Error message already set by findOptimalStores or its helper functions
                    // Only set a fallback message if none was set
                    if (_optimizationError.value == null) {
                        _optimizationError.value = "No stores found within ${String.format(Locale.getDefault(), "%.1f", _maxDistanceKm.value)}km that carry the requested products."
                    }
                }

                // Don't set optimization result yet - wait for user to select an option
                _optimizationResult.value = null
                _totalPrice.value = 0.0

            } catch (e: Exception) {
                _optimizationError.value = "Failed to optimize basket: ${e.message}"
            } finally {
                _isOptimizing.value = false
            }
        }
    }
    

//  User selects one of the optimization options (closest or cheapest)
    fun selectStoreOption(option: StoreOption) {
        _selectedStore.value = option.store
        _selectedStoreOption.value = option
        _totalPrice.value = option.totalPrice
        _needsUserSelection.value = false
        
        // Update basket items with actual prices from selected store
        viewModelScope.launch {
            try {
                // Use original basket items (before any optimization modifications)
                val currentItems = originalBasketItems ?: basketItems.value ?: return@launch
                
                // Build a list of all product IDs we need to fetch, including substitutions and upgrades
                val allProductIds = mutableListOf<String>()
                currentItems.forEach { item ->
                    // Check if this item was substituted (bio -> non-bio)
                    val wasSubstituted = option.bioSubstitutions.keys.any { it == item.name }
                    if (wasSubstituted && item.id.endsWith(" bio")) {
                        // Add the non-bio version instead
                        allProductIds.add(item.id.removeSuffix(" bio"))
                    } else if (option.bioUpgrades.keys.any { it == item.name }) {
                        // This item was upgraded to bio, add the bio version
                        allProductIds.add(item.id + " bio")
                    } else {
                        // Normal product
                        allProductIds.add(item.id)
                    }
                }
                
                val priceMap = fetchProductPricesForRetailer(option.store.retailerId, allProductIds)
                
                // Update basket items with actual prices and handle substitutions/upgrades
                val updatedItems = currentItems.mapNotNull { item ->
                    // Check if this item was substituted to non-bio
                    val bioSubstitutionMatch = option.bioSubstitutions.entries.find { it.key == item.name }
                    if (bioSubstitutionMatch != null && item.id.endsWith(" bio")) {
                        // Bio product was substituted with non-bio
                        val nonBioId = item.id.removeSuffix(" bio")
                        val priceInfo = priceMap[nonBioId]
                        if (priceInfo != null) {
                            item.copy(
                                id = nonBioId,
                                price = priceInfo.finalPrice,
                                originalPrice = priceInfo.originalPrice,
                                productName = priceInfo.productName,
                                unit = priceInfo.unit,
                                imageUrl = priceInfo.productImage,
                                retailerProductId = priceInfo.retailerProductId
                            )
                        } else {
                            null
                        }
                    } else {
                        // Check if this item was upgraded to bio
                        val bioUpgradeMatch = option.bioUpgrades.entries.find { it.key == item.name }
                        if (bioUpgradeMatch != null && !item.id.endsWith(" bio")) {
                            // Non-bio product was upgraded to bio
                            val bioId = item.id + " bio"
                            val priceInfo = priceMap[bioId]
                            if (priceInfo != null) {
                                item.copy(
                                    id = bioId,
                                    price = priceInfo.finalPrice,
                                    originalPrice = priceInfo.originalPrice,
                                    productName = priceInfo.productName,
                                    unit = priceInfo.unit,
                                    imageUrl = priceInfo.productImage,
                                    retailerProductId = priceInfo.retailerProductId
                                )
                            } else {
                                null
                            }
                        } else {
                            // Normal product - no substitution or upgrade
                            val priceInfo = priceMap[item.id]
                            if (priceInfo != null) {
                                item.copy(
                                    price = priceInfo.finalPrice,
                                    originalPrice = priceInfo.originalPrice,
                                    productName = priceInfo.productName,
                                    unit = priceInfo.unit,
                                    imageUrl = priceInfo.productImage,
                                    retailerProductId = priceInfo.retailerProductId
                                )
                            } else {
                                null
                            }
                        }
                    }
                }
                
                _optimizationResult.value = updatedItems
                
                // Update repository with the optimized basket
                BasketRepository.clearBasket()
                updatedItems.forEach { item ->
                    // Add item with proper quantity
                    val productModel = RecommendedProductModel(
                        id = item.id,
                        name = item.name,
                        category = item.description,
                        imageUrl = item.imageUrl ?: "",
                        unit = ""
                    )
                    // Add product first
                    BasketRepository.addProduct(productModel)
                    // Update the quantity and price in the newly added basket item
                    val currentItems = BasketRepository.basketItems.value ?: emptyList()
                    val basketItem = currentItems.find { it.id == item.id }
                    if (basketItem != null) {
                        basketItem.quantity = item.quantity
                        basketItem.price = item.price
                    }
                }
                
            } catch (e: Exception) {
                _optimizationError.value = "Failed to update basket prices: ${e.message}"
            }
        }
    }
    
//  Go back to store selection
    fun backToStoreSelection() {
        // Reset selection state to show the dialog again
        // Keep the optimization result and basket items intact
        _needsUserSelection.value = true
        _selectedStore.value = null
        _selectedStoreOption.value = null
    }

//  Find both closest and cheapest store options
    private suspend fun findOptimalStores(
        userLat: Double,
        userLon: Double,
        transportMode: TransportMode,
        maxDistanceKm: Double,
        basketItems: List<BasketItemModel>,
        allowNonBioAlternatives: Boolean = false
    ): Pair<StoreOption, StoreOption>? {
        // Get transport-specific parameters
        val (stepSize, maxAdditionalDistance) = when (transportMode) {
            TransportMode.WALKING -> Pair(0.1, 0.5)     // Step: 0.1km, Max additional: 0.5km
            TransportMode.BICYCLING -> Pair(0.5, 2.0)   // Step: 0.5km, Max additional: 2km
            TransportMode.TRANSIT -> Pair(0.5, 2.0)     // Step: 0.5km, Max additional: 2km
            TransportMode.DRIVING -> Pair(1.0, 5.0)     // Step: 1km, Max additional: 5km
        }
        
        var currentMaxDistance = maxDistanceKm
        val maxSearchRadius = maxDistanceKm + maxAdditionalDistance
        
        // Try to find stores with all products, increasing range if needed
        while (currentMaxDistance <= maxSearchRadius) {
            val result = findOptimalStoresAtDistance(
                userLat, userLon, transportMode, currentMaxDistance, basketItems, allowNonBioAlternatives
            )
            
            if (result != null) {
                // Check if we had to increase the range
                if (currentMaxDistance > maxDistanceKm) {
                    val warning = "⚠️ Search range increased to ${String.format(Locale.getDefault(), "%.1f", currentMaxDistance)}km to find stores with all products"
                    _optimizationError.value = warning
                }
                return result
            }
            
            // Increase range by step size and try again
            currentMaxDistance += stepSize
        }
        
        // If we've exceeded max search radius, find best partial solution
        return findBestPartialStores(userLat, userLon, transportMode, maxSearchRadius, basketItems, allowNonBioAlternatives)
    }

//  Find best partial stores when no store has all products within max range, returns stores with most products available
    private suspend fun findBestPartialStores(
        userLat: Double,
        userLon: Double,
        transportMode: TransportMode,
        maxDistanceKm: Double,
        basketItems: List<BasketItemModel>,
        allowNonBioAlternatives: Boolean = false
    ): Pair<StoreOption, StoreOption>? {
        // Use provided coordinates or fallback to EPFL
        val effectiveLat: Double
        val effectiveLon: Double
        
        if (userLat == 0.0 && userLon == 0.0) {
            effectiveLat = 46.5196535
            effectiveLon = 6.5669707
        } else {
            effectiveLat = userLat
            effectiveLon = userLon
        }
        
        return try {
            // Fetch all stores and filter by distance
            val storesSnapshot = db.collectionGroup("stores").get().await()
            
            if (storesSnapshot.isEmpty) {
                _optimizationError.value = "No stores found in database"
                return null
            }
            
            // Parse stores and calculate distances
            val storesWithDistance = storesSnapshot.documents.mapNotNull { doc ->
                try {
                    val store = StoreModel.fromDocument(doc) ?: return@mapNotNull null

                    // Skip stores with invalid coordinates
                    if (store.latitude == 0.0 && store.longitude == 0.0) {
                        return@mapNotNull null
                    }
                    
                    val distance = DistanceCalculator.calculateDistance(
                        effectiveLat, effectiveLon,
                        store.latitude, store.longitude
                    )
                    
                    Pair(store, distance)
                } catch (e: Exception) {
                    println(e.message)
                    null
                }
            }
            
            val storesWithinRange = storesWithDistance.filter { it.second <= maxDistanceKm }
            
            if (storesWithinRange.isEmpty()) {
                _optimizationError.value = "No stores found within ${String.format(Locale.getDefault(), "%.1f", maxDistanceKm)}km"
                return null
            }
            
            // Calculate prices for all stores (including partial baskets)
            val allStoreOptions = mutableListOf<StoreOption>()
            
            for ((store, _) in storesWithinRange) {
                // Get real distance using Maps API
                val distanceResult = mapsService.getDistance(
                    effectiveLat, effectiveLon,
                    store.latitude, store.longitude,
                    transportMode
                )
                
                // Calculate basket price for this store (allows partial baskets)
                val storeOption = calculateBasketPriceForStore(
                    store,
                    basketItems,
                    distanceResult.distanceKm,
                    distanceResult.durationMinutes,
                    allowNonBioAlternatives
                )

                allStoreOptions.add(storeOption)
            }
            
            // Find maximum number of available products
            val maxAvailableProducts = allStoreOptions.maxOfOrNull { storeOption ->
                basketItems.size - storeOption.unavailableProducts.size
            } ?: 0
            
            if (maxAvailableProducts == 0) {
                _optimizationError.value = "No stores carry any of the requested products"
                return null
            }
            
            // Filter to only stores with maximum number of products
            val storesWithMaxProducts = allStoreOptions.filter { storeOption ->
                (basketItems.size - storeOption.unavailableProducts.size) == maxAvailableProducts
            }
            
            // If multiple stores from the same retailer, keep only the closest one
            val storesByRetailer = storesWithMaxProducts.groupBy { it.store.retailerId }
            val filteredStores = storesByRetailer.map { (_, stores) ->
                if (stores.size > 1) {
                    val closest = stores.minByOrNull { it.distanceKm }!!
                    closest
                } else {
                    stores.first()
                }
            }
            
            // Find cheapest and closest among stores with max products
            val closestOption = filteredStores.minByOrNull { it.distanceKm }!!
            val cheapestOption = filteredStores.minByOrNull { it.totalPrice }!!
            
            val missingProducts = basketItems.size - maxAvailableProducts
            _optimizationError.value = "⚠️ Partial solution: Best stores have ${maxAvailableProducts}/${basketItems.size} products (${missingProducts} unavailable)"
            
            Pair(closestOption, cheapestOption)
            
        } catch (e: Exception) {
            _optimizationError.value = "Failed to search for stores: ${e.message}"
            null
        }
    }

//  Internal func to find optimal stores at specific distance, only considers stores that have all baskets items available
    private suspend fun findOptimalStoresAtDistance(
        userLat: Double,
        userLon: Double,
        transportMode: TransportMode,
        maxDistanceKm: Double,
        basketItems: List<BasketItemModel>,
        allowNonBioAlternatives: Boolean = false
    ): Pair<StoreOption, StoreOption>? {
        // Use provided coordinates or fallback to EPFL
        val effectiveLat: Double
        val effectiveLon: Double
        
        if (userLat == 0.0 && userLon == 0.0) {
            effectiveLat = 46.5196535
            effectiveLon = 6.5669707
        } else {
            effectiveLat = userLat
            effectiveLon = userLon
        }
        
        return try {
            // Fetch all stores and filter by distance
            val storesSnapshot = db.collectionGroup("stores").get().await()
            
            if (storesSnapshot.isEmpty) {
                return null
            }
            
            // Parse stores and calculate distances
            val storesWithDistance = storesSnapshot.documents.mapNotNull { doc ->
                try {
                    val store = StoreModel.fromDocument(doc) ?: return@mapNotNull null

                    // Skip stores with invalid coordinates
                    if (store.latitude == 0.0 && store.longitude == 0.0) {
                        return@mapNotNull null
                    }
                    
                    val distance = DistanceCalculator.calculateDistance(
                        effectiveLat, effectiveLon,
                        store.latitude, store.longitude
                    )
                    
                    Pair(store, distance)
                } catch (e: Exception) {
                    println(e.message)
                    null
                }
            }
            
            val storesWithinRange = storesWithDistance.filter { it.second <= maxDistanceKm }
            
            if (storesWithinRange.isEmpty()) {
                return null
            }
            
            // Calculate prices and filter for stores with ALL products
            val storeOptionsWithPrices = mutableListOf<StoreOption>()
            
            for ((store, _) in storesWithinRange) {
                // Get real distance using Maps API
                val distanceResult = mapsService.getDistance(
                    effectiveLat, effectiveLon,
                    store.latitude, store.longitude,
                    transportMode
                )
                
                // Calculate basket price for this store
                val storeOption = calculateBasketPriceForStore(
                    store,
                    basketItems,
                    distanceResult.distanceKm,
                    distanceResult.durationMinutes,
                    allowNonBioAlternatives
                )
                
                // ONLY include stores that have ALL products
                if (storeOption.unavailableProducts.isEmpty()) {
                    storeOptionsWithPrices.add(storeOption)
                }
            }
            
            if (storeOptionsWithPrices.isEmpty()) {
                return null
            }
            
            // If multiple stores from the same retailer, keep only the closest one
            val storesByRetailer = storeOptionsWithPrices.groupBy { it.store.retailerId }
            val filteredStores = storesByRetailer.map { (_, stores) ->
                if (stores.size > 1) {
                    val closest = stores.minByOrNull { it.distanceKm }!!
                    closest
                } else {
                    stores.first()
                }
            }
            
            // Optimize among stores with all products
            val closestOption = filteredStores.minByOrNull { it.distanceKm }!!
            val cheapestOption = filteredStores.minByOrNull { it.totalPrice }!!
            
            Pair(closestOption, cheapestOption)
            
        } catch (e: Exception) {
            println(e.message)
            null
        }
    }


//  Fetch product prices for a specific retailer
    private suspend fun fetchProductPricesForRetailer(retailerId: String, productTypes: List<String>): Map<String, PriceInfo> {
        val priceMap = mutableMapOf<String, PriceInfo>()
        
        try {
            for (productType in productTypes) {
                try {
                    // Query products_per_retailer collection for matching product_type
                    val productsSnapshot = db.collection("retailer")
                        .document(retailerId)
                        .collection("products_per_retailer")
                        .whereEqualTo("product_type", productType)
                        .get()
                        .await()
                    
                    if (!productsSnapshot.isEmpty) {
                        // Find the cheapest product with this product_type that is in stock
                        val cheapestProduct = productsSnapshot.documents
                            .filter { doc ->
                                val inStock = doc.getBoolean("in_stock") ?: false
                                val price = (doc.get("price") as? Number)?.toDouble()
                                inStock && price != null && price > 0
                            }
                            .minByOrNull { doc ->
                                // Use discounted price if available for comparison
                                val hasDiscount = doc.get("has_discount") as? Map<*, *>
                                val discountedPrice = (hasDiscount?.get("discounted_price") as? Number)?.toDouble()
                                val regularPrice = (doc.get("price") as? Number)?.toDouble() ?: Double.MAX_VALUE
                                discountedPrice ?: regularPrice
                            }
                        
                        if (cheapestProduct != null) {
                            val originalPrice = (cheapestProduct.get("price") as? Number)?.toDouble()
                            val hasDiscountMap = cheapestProduct.get("has_discount") as? Map<*, *>
                            val discountedPrice = (hasDiscountMap?.get("discounted_price") as? Number)?.toDouble()
                            val productName = cheapestProduct.getString("product_name") ?: ""
                            val unit = cheapestProduct.getString("unit") ?: ""
                            val productImage = cheapestProduct.getString("product_image")
                            val retailerProductId = cheapestProduct.id
                            
                            if (originalPrice != null) {
                                val finalPrice = discountedPrice ?: originalPrice
                                val hasDiscount = discountedPrice != null && discountedPrice < originalPrice
                                
                                priceMap[productType] = PriceInfo(
                                    finalPrice = finalPrice,
                                    originalPrice = originalPrice,
                                    hasDiscount = hasDiscount,
                                    productName = productName,
                                    unit = unit,
                                    productImage = productImage,
                                    retailerProductId = retailerProductId
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    println(e.message)
                }
            }
            
        } catch (e: Exception) {
            println(e.message)
        }
        
        return priceMap
    }
    

//  Calculate total basket price for a specific store
    private suspend fun calculateBasketPriceForStore(
        store: StoreModel,
        basketItems: List<BasketItemModel>,
        distanceKm: Double,
        durationMinutes: Int?,
        allowNonBioAlternatives: Boolean = false
    ): StoreOption {
        val productTypes = basketItems.map { it.id }
        val priceMap = fetchProductPricesForRetailer(store.retailerId, productTypes)
        
        var totalPrice = 0.0
        var totalSavings = 0.0
        val unavailableProducts = mutableListOf<String>()
        val bioSubstitutions = mutableMapOf<String, String>()
        val bioUpgrades = mutableMapOf<String, String>()
        
        for (item in basketItems) {
            val priceInfo = priceMap[item.id]
            if (priceInfo != null) {
                // Check if this is a non-bio product and if a cheaper bio version exists
                var usedBioUpgrade = false
                if (!item.id.endsWith(" bio")) {
                    val bioType = item.id + " bio"
                    val bioPriceInfo = fetchProductPricesForRetailer(store.retailerId, listOf(bioType))[bioType]
                    
                    if (bioPriceInfo != null && bioPriceInfo.finalPrice < priceInfo.finalPrice) {
                        // Bio version is cheaper! Use it instead
                        totalPrice += bioPriceInfo.finalPrice * item.quantity
                        if (bioPriceInfo.hasDiscount) {
                            val itemSavings = (bioPriceInfo.originalPrice - bioPriceInfo.finalPrice) * item.quantity
                            totalSavings += itemSavings
                        }
                        bioUpgrades[item.name] = bioPriceInfo.productName
                        usedBioUpgrade = true
                    }
                }
                
                if (!usedBioUpgrade) {
                    totalPrice += priceInfo.finalPrice * item.quantity
                    if (priceInfo.hasDiscount) {
                        val itemSavings = (priceInfo.originalPrice - priceInfo.finalPrice) * item.quantity
                        totalSavings += itemSavings
                    }
                }
            } else {
                // Try to find non-bio alternative if allowed and product is bio
                var foundAlternative = false
                if (allowNonBioAlternatives && item.id.endsWith(" bio")) {
                    val nonBioType = item.id.removeSuffix(" bio")
                    val nonBioPriceInfo = fetchProductPricesForRetailer(store.retailerId, listOf(nonBioType))[nonBioType]
                    
                    if (nonBioPriceInfo != null) {
                        totalPrice += nonBioPriceInfo.finalPrice * item.quantity
                        if (nonBioPriceInfo.hasDiscount) {
                            val itemSavings = (nonBioPriceInfo.originalPrice - nonBioPriceInfo.finalPrice) * item.quantity
                            totalSavings += itemSavings
                        }
                        val nonBioName = item.name.replace(" Bio", "").replace(" bio", "")
                        bioSubstitutions[item.name] = nonBioName
                        foundAlternative = true
                    }
                }
                
                if (!foundAlternative) {
                    unavailableProducts.add(item.name)
                }
            }
        }
        
        return StoreOption(
            store = store,
            totalPrice = totalPrice,
            distanceKm = distanceKm,
            durationMinutes = durationMinutes,
            unavailableProducts = unavailableProducts,
            bioSubstitutions = bioSubstitutions,
            totalSavings = totalSavings,
            bioUpgrades = bioUpgrades
        )
    }
    
    private fun loadUserPreferences() {
        val userId = auth.currentUser?.uid ?: return
        
        viewModelScope.launch {
            try {
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
                println(e.message)
            }
        }
    }


    fun clearBasket() {
        BasketRepository.clearBasket()
    }

//  Add to user's favorites
    fun addToFavorites(basketName: String) {
        val currentItems = basketItems.value
        if (currentItems.isNullOrEmpty()) {
            return
        }

        _isSavingFavorite.value = true
        _favoriteError.value = null

        viewModelScope.launch {
            favoriteBasketRepository.saveFavoriteBasket(
                basketName = basketName,
                basketItems = currentItems,
                onSuccess = {
                    _isSavingFavorite.value = false
                },
                onError = { errorMessage ->
                    _favoriteError.value = errorMessage
                    _isSavingFavorite.value = false
                }
            )
        }
    }
}
