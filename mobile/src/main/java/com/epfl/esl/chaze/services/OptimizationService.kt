package com.epfl.esl.chaze.services

import com.epfl.esl.chaze.BuildConfig
import com.epfl.esl.chaze.data.model.BasketItemModel
import com.epfl.esl.chaze.data.model.StoreModel
import com.epfl.esl.chaze.data.model.StoreOption
import com.epfl.esl.chaze.data.model.TransportMode
import com.epfl.esl.chaze.utils.DistanceCalculator
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await

/**
 * Shared optimization service that can find the best stores for a basket.
 * Used by both CurrentBasketViewModel (full basket) and ChatbotViewModel (single product)
 */
class OptimizationService {
    
    private val db = Firebase.firestore
    private val mapsService = GoogleMapsDistanceService(BuildConfig.GOOGLE_API_KEY)
    
    companion object {

        // Singleton instance
        @Volatile
        private var INSTANCE: OptimizationService? = null
        
        fun getInstance(): OptimizationService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: OptimizationService().also { INSTANCE = it }
            }
        }
    }
    
    /**
     * Price info for a product at a store
     */
    data class PriceInfo(
        val finalPrice: Double,
        val originalPrice: Double,
        val hasDiscount: Boolean,
        val productName: String,
        val unit: String,
        val productImage: String? = null
    )
    
    /**
     * Result of an optimization query
     */
    data class OptimizationResult(
        val cheapestOption: StoreOption?,
        val closestOption: StoreOption?,
        val optimizedItems: List<BasketItemModel>,
        val error: String? = null
    )
    
    /**
     * Dual result for when exact bio products aren't available everywhere
     */
    data class DualOptimizationResult(
        val exactMatchResult: OptimizationResult,    // Result with exact bio products (may be incomplete)
        val alternativeResult: OptimizationResult,   // Result with non-bio alternatives (complete list)
        val needsDualDisplay: Boolean                // True if results differ significantly
    )
    
    /**
     * Find the cheapest store for a basket of items
     * Returns just the cheapest option (simpler for chatbot use)
     */
    suspend fun findCheapestStore(
        basketItems: List<BasketItemModel>,
        userLat: Double,
        userLon: Double,
        transportMode: TransportMode,
        maxDistanceKm: Double = 10.0
    ): OptimizationResult {

        // Check if user is requesting bio products
        val hasBioRequest = basketItems.any { it.id.endsWith(" bio") }
        
        // First try: Find stores with exact products (no bio substitutions)
        // This ensures bio requests get bio products when available
        val exactResult = if (hasBioRequest) {
            findOptimalStores(
                userLat = userLat,
                userLon = userLon,
                transportMode = transportMode,
                maxDistanceKm = maxDistanceKm,
                basketItems = basketItems,
                allowNonBioAlternatives = false
            )
        } else null
        
        // If we found stores with exact bio products, use those
        val result = if (exactResult != null) {
            exactResult
        } else {
            // Second try: Allow non-bio alternatives if no stores have bio products
            findOptimalStores(
                userLat = userLat,
                userLon = userLon,
                transportMode = transportMode,
                maxDistanceKm = maxDistanceKm,
                basketItems = basketItems,
                allowNonBioAlternatives = true
            )
        }
        
        if (result == null) {
            return OptimizationResult(
                cheapestOption = null,
                closestOption = null,
                optimizedItems = emptyList(),
                error = "No stores were found within ${maxDistanceKm}km that carry the requested products."
            )
        }
        
        val (closestOption, cheapestOption) = result
        
        // Build optimized items with prices from cheapest store
        val optimizedItems = buildOptimizedItems(basketItems, cheapestOption)
        
        return OptimizationResult(
            cheapestOption = cheapestOption,
            closestOption = closestOption,
            optimizedItems = optimizedItems,
            error = null
        )
    }
    
    /**
     * Find the closest store for a basket of items
     * Returns just the closest option
     */
    suspend fun findClosestStore(
        basketItems: List<BasketItemModel>,
        userLat: Double,
        userLon: Double,
        transportMode: TransportMode,
        maxDistanceKm: Double = 10.0
    ): OptimizationResult {

        // Check if user is requesting bio products
        val hasBioRequest = basketItems.any { it.id.endsWith(" bio") }
        
        // First try: Find stores with exact products (no bio substitutions)
        val exactResult = if (hasBioRequest) {
            findOptimalStores(
                userLat = userLat,
                userLon = userLon,
                transportMode = transportMode,
                maxDistanceKm = maxDistanceKm,
                basketItems = basketItems,
                allowNonBioAlternatives = false
            )
        } else null
        
        // If we found stores with exact bio products, use those
        val result = if (exactResult != null) {
            exactResult
        } else {
            findOptimalStores(
                userLat = userLat,
                userLon = userLon,
                transportMode = transportMode,
                maxDistanceKm = maxDistanceKm,
                basketItems = basketItems,
                allowNonBioAlternatives = true
            )
        }
        
        if (result == null) {
            return OptimizationResult(
                cheapestOption = null,
                closestOption = null,
                optimizedItems = emptyList(),
                error = "No stores were found within ${maxDistanceKm}km that carry the requested products."
            )
        }
        
        val (closestOption, cheapestOption) = result
        
        // Build optimized items with prices from CLOSEST store (not cheapest)
        val optimizedItems = buildOptimizedItems(basketItems, closestOption)
        
        return OptimizationResult(
            cheapestOption = cheapestOption,
            closestOption = closestOption,
            optimizedItems = optimizedItems,
            error = null
        )
    }
    
    /**
     * Find closest store with dual results for bio products
     * Returns both exact match (bio only) and alternative (with substitutions) results
     * Uses closest store instead of cheapest store
     */
    suspend fun findClosestStoreWithAlternatives(
        basketItems: List<BasketItemModel>,
        userLat: Double,
        userLon: Double,
        transportMode: TransportMode,
        maxDistanceKm: Double = 10.0
    ): DualOptimizationResult {

        val hasBioRequest = basketItems.any { it.id.endsWith(" bio") }
        
        // Get exact match result (no bio substitutions)
        val exactResult = findOptimalStores(
            userLat = userLat,
            userLon = userLon,
            transportMode = transportMode,
            maxDistanceKm = maxDistanceKm,
            basketItems = basketItems,
            allowNonBioAlternatives = false
        )
        
        val exactOptimizationResult = if (exactResult != null) {
            val (closestOption, cheapestOption) = exactResult
            // Use CLOSEST option for closest optimization
            val optimizedItems = buildOptimizedItems(basketItems, closestOption)
            OptimizationResult(
                cheapestOption = cheapestOption,
                closestOption = closestOption,
                optimizedItems = optimizedItems,
                error = null
            )
        } else {
            OptimizationResult(
                cheapestOption = null,
                closestOption = null,
                optimizedItems = emptyList(),
                error = "No stores found with exact products"
            )
        }
        
        // Get alternative result (with bio substitutions allowed)
        val alternativeResult = if (hasBioRequest) {
            val altResult = findOptimalStores(
                userLat = userLat,
                userLon = userLon,
                transportMode = transportMode,
                maxDistanceKm = maxDistanceKm,
                basketItems = basketItems,
                allowNonBioAlternatives = true
            )
            
            if (altResult != null) {
                val (closestOption, cheapestOption) = altResult
                // Use CLOSEST option for closest optimization
                val optimizedItems = buildOptimizedItems(basketItems, closestOption)
                OptimizationResult(
                    cheapestOption = cheapestOption,
                    closestOption = closestOption,
                    optimizedItems = optimizedItems,
                    error = null
                )
            } else {
                OptimizationResult(
                    cheapestOption = null,
                    closestOption = null,
                    optimizedItems = emptyList(),
                    error = "No stores found within ${maxDistanceKm}km"
                )
            }
        } else {
            // No bio request, alternative is same as exact
            exactOptimizationResult
        }
        
        // Determine if we need dual display
        // Show both if: 
        // 1. Bio was requested
        // 2. Exact result has fewer items than alternative (bio substitution happened)
        // 3. Or exact result is null but alternative exists
        val needsDualDisplay = hasBioRequest && (
            (exactOptimizationResult.closestOption == null && alternativeResult.closestOption != null) ||
            (exactOptimizationResult.optimizedItems.size < alternativeResult.optimizedItems.size) ||
            (alternativeResult.closestOption?.bioSubstitutions?.isNotEmpty() == true)
        )

        return DualOptimizationResult(
            exactMatchResult = exactOptimizationResult,
            alternativeResult = alternativeResult,
            needsDualDisplay = needsDualDisplay
        )
    }
    
    /**
     * Find cheapest store with dual results for bio products
     * Returns both exact match (bio only) and alternative (with substitutions) results
     */
    suspend fun findCheapestStoreWithAlternatives(
        basketItems: List<BasketItemModel>,
        userLat: Double,
        userLon: Double,
        transportMode: TransportMode,
        maxDistanceKm: Double = 10.0
    ): DualOptimizationResult {

        val hasBioRequest = basketItems.any { it.id.endsWith(" bio") }
        
        // Get exact match result (no bio substitutions)
        val exactResult = findOptimalStores(
            userLat = userLat,
            userLon = userLon,
            transportMode = transportMode,
            maxDistanceKm = maxDistanceKm,
            basketItems = basketItems,
            allowNonBioAlternatives = false
        )
        
        val exactOptimizationResult = if (exactResult != null) {
            val (closestOption, cheapestOption) = exactResult
            val optimizedItems = buildOptimizedItems(basketItems, cheapestOption)
            OptimizationResult(
                cheapestOption = cheapestOption,
                closestOption = closestOption,
                optimizedItems = optimizedItems,
                error = null
            )
        } else {
            OptimizationResult(
                cheapestOption = null,
                closestOption = null,
                optimizedItems = emptyList(),
                error = "No stores found with exact products"
            )
        }
        
        // Get alternative result (with bio substitutions allowed)
        val alternativeResult = if (hasBioRequest) {
            val altResult = findOptimalStores(
                userLat = userLat,
                userLon = userLon,
                transportMode = transportMode,
                maxDistanceKm = maxDistanceKm,
                basketItems = basketItems,
                allowNonBioAlternatives = true
            )
            
            if (altResult != null) {
                val (closestOption, cheapestOption) = altResult
                val optimizedItems = buildOptimizedItems(basketItems, cheapestOption)
                OptimizationResult(
                    cheapestOption = cheapestOption,
                    closestOption = closestOption,
                    optimizedItems = optimizedItems,
                    error = null
                )
            } else {
                OptimizationResult(
                    cheapestOption = null,
                    closestOption = null,
                    optimizedItems = emptyList(),
                    error = "No stores found within ${maxDistanceKm}km"
                )
            }
        } else {
            // No bio request, alternative is same as exact
            exactOptimizationResult
        }
        
        // Determine if we need dual display
        // Show both if: 
        // 1. Bio was requested
        // 2. Exact result has fewer items than alternative (bio substitution happened)
        // 3. Or exact result is null but alternative exists
        val needsDualDisplay = hasBioRequest && (
            (exactOptimizationResult.cheapestOption == null && alternativeResult.cheapestOption != null) ||
            (exactOptimizationResult.optimizedItems.size < alternativeResult.optimizedItems.size) ||
            (alternativeResult.cheapestOption?.bioSubstitutions?.isNotEmpty() == true)
        )

        return DualOptimizationResult(
            exactMatchResult = exactOptimizationResult,
            alternativeResult = alternativeResult,
            needsDualDisplay = needsDualDisplay
        )
    }
    
    /**
     * Build optimized items with prices from selected store
     */
    private suspend fun buildOptimizedItems(
        basketItems: List<BasketItemModel>,
        storeOption: StoreOption
    ): List<BasketItemModel> {
        val allProductIds = mutableListOf<String>()
        basketItems.forEach { item ->
            val wasSubstituted = storeOption.bioSubstitutions.keys.any { it == item.name }
            if (wasSubstituted && item.id.endsWith(" bio")) {
                allProductIds.add(item.id.removeSuffix(" bio"))
            } else if (storeOption.bioUpgrades.keys.any { it == item.name }) {
                allProductIds.add(item.id + " bio")
            } else {
                allProductIds.add(item.id)
            }
        }
        
        val priceMap = fetchProductPricesForRetailer(storeOption.store.retailerId, allProductIds)
        
        return basketItems.mapNotNull { item ->
            val bioSubstitutionMatch = storeOption.bioSubstitutions.entries.find { it.key == item.name }
            if (bioSubstitutionMatch != null && item.id.endsWith(" bio")) {
                val nonBioId = item.id.removeSuffix(" bio")
                val priceInfo = priceMap[nonBioId]
                if (priceInfo != null) {
                    item.copy(
                        id = nonBioId,
                        price = priceInfo.finalPrice,
                        productName = priceInfo.productName,
                        unit = priceInfo.unit,
                        imageUrl = priceInfo.productImage
                    )
                } else null
            } else {
                val bioUpgradeMatch = storeOption.bioUpgrades.entries.find { it.key == item.name }
                if (bioUpgradeMatch != null && !item.id.endsWith(" bio")) {
                    val bioId = item.id + " bio"
                    val priceInfo = priceMap[bioId]
                    if (priceInfo != null) {
                        item.copy(
                            id = bioId,
                            price = priceInfo.finalPrice,
                            productName = priceInfo.productName,
                            unit = priceInfo.unit,
                            imageUrl = priceInfo.productImage
                        )
                    } else null
                } else {
                    val priceInfo = priceMap[item.id]
                    if (priceInfo != null) {
                        item.copy(
                            price = priceInfo.finalPrice,
                            productName = priceInfo.productName,
                            unit = priceInfo.unit,
                            imageUrl = priceInfo.productImage
                        )
                    } else null
                }
            }
        }
    }
    
    /**
     * Find both closest and cheapest store options
     */
    private suspend fun findOptimalStores(
        userLat: Double,
        userLon: Double,
        transportMode: TransportMode,
        maxDistanceKm: Double,
        basketItems: List<BasketItemModel>,
        allowNonBioAlternatives: Boolean = false
    ): Pair<StoreOption, StoreOption>? {
        val (stepSize, maxAdditionalDistance) = when (transportMode) {
            TransportMode.WALKING -> Pair(0.1, 0.5)
            TransportMode.BICYCLING -> Pair(0.5, 2.0)
            TransportMode.TRANSIT -> Pair(0.5, 2.0)
            TransportMode.DRIVING -> Pair(1.0, 5.0)
        }
        
        var currentMaxDistance = maxDistanceKm
        val maxSearchRadius = maxDistanceKm + maxAdditionalDistance
        
        while (currentMaxDistance <= maxSearchRadius) {
            val result = findOptimalStoresAtDistance(
                userLat, userLon, transportMode, currentMaxDistance, basketItems, allowNonBioAlternatives
            )
            
            if (result != null) {
                return result
            }
            
            currentMaxDistance += stepSize
        }
        
        return findBestPartialStores(userLat, userLon, transportMode, maxSearchRadius, basketItems, allowNonBioAlternatives)
    }
    
    private suspend fun findOptimalStoresAtDistance(
        userLat: Double,
        userLon: Double,
        transportMode: TransportMode,
        maxDistanceKm: Double,
        basketItems: List<BasketItemModel>,
        allowNonBioAlternatives: Boolean = false
    ): Pair<StoreOption, StoreOption>? {
        val effectiveLat = if (userLat == 0.0) 46.5196535 else userLat
        val effectiveLon = if (userLon == 0.0) 6.5669707 else userLon
        
        return try {
            val storesSnapshot = db.collectionGroup("stores").get().await()
            
            if (storesSnapshot.isEmpty) return null
            
            val storesWithDistance = storesSnapshot.documents.mapNotNull { doc ->
                try {
                    val store = StoreModel.fromDocument(doc) ?: return@mapNotNull null
                    if (store.latitude == 0.0 && store.longitude == 0.0) return@mapNotNull null
                    
                    val distance = DistanceCalculator.calculateDistance(
                        effectiveLat, effectiveLon,
                        store.latitude, store.longitude
                    )
                    Pair(store, distance)
                } catch (e: Exception) {
                    null
                }
            }
            
            val storesWithinRange = storesWithDistance.filter { it.second <= maxDistanceKm }
            if (storesWithinRange.isEmpty()) {
                return null
            }
            
            val storeOptionsWithPrices = mutableListOf<StoreOption>()
            
            for ((store, _) in storesWithinRange) {
                val distanceResult = mapsService.getDistance(
                    effectiveLat, effectiveLon,
                    store.latitude, store.longitude,
                    transportMode
                )
                
                val storeOption = calculateBasketPriceForStore(
                    store, basketItems, distanceResult.distanceKm, distanceResult.durationMinutes, allowNonBioAlternatives
                )
                
                if (storeOption.unavailableProducts.isEmpty()) {
                    storeOptionsWithPrices.add(storeOption)
                }
            }
            
            if (storeOptionsWithPrices.isEmpty()) {
                return null
            }
            
            val storesByRetailer = storeOptionsWithPrices.groupBy { it.store.retailerId }
            val filteredStores = storesByRetailer.map { (_, stores) ->
                if (stores.size > 1) stores.minByOrNull { it.distanceKm }!! else stores.first()
            }
            
            val closestOption = filteredStores.minByOrNull { it.distanceKm }!!
            val cheapestOption = filteredStores.minByOrNull { it.totalPrice }!!
            
            Pair(closestOption, cheapestOption)
            
        } catch (e: Exception) {
            println("Error finding optimal stores: ${e.message}")
            null
        }
    }
    
    private suspend fun findBestPartialStores(
        userLat: Double,
        userLon: Double,
        transportMode: TransportMode,
        maxDistanceKm: Double,
        basketItems: List<BasketItemModel>,
        allowNonBioAlternatives: Boolean = false
    ): Pair<StoreOption, StoreOption>? {
        val effectiveLat = if (userLat == 0.0) 46.5196535 else userLat
        val effectiveLon = if (userLon == 0.0) 6.5669707 else userLon
        
        return try {
            val storesSnapshot = db.collectionGroup("stores").get().await()
            if (storesSnapshot.isEmpty) return null
            
            val storesWithDistance = storesSnapshot.documents.mapNotNull { doc ->
                try {
                    val store = StoreModel.fromDocument(doc) ?: return@mapNotNull null
                    if (store.latitude == 0.0 && store.longitude == 0.0) return@mapNotNull null
                    
                    val distance = DistanceCalculator.calculateDistance(
                        effectiveLat, effectiveLon, store.latitude, store.longitude
                    )
                    Pair(store, distance)
                } catch (e: Exception) {
                    null
                }
            }
            
            val storesWithinRange = storesWithDistance.filter { it.second <= maxDistanceKm }
            if (storesWithinRange.isEmpty()) {
                return null
            }
            
            val allStoreOptions = mutableListOf<StoreOption>()
            
            for ((store, _) in storesWithinRange) {
                val distanceResult = mapsService.getDistance(
                    effectiveLat, effectiveLon, store.latitude, store.longitude, transportMode
                )
                
                val storeOption = calculateBasketPriceForStore(
                    store, basketItems, distanceResult.distanceKm, distanceResult.durationMinutes, allowNonBioAlternatives
                )
                allStoreOptions.add(storeOption)
            }
            
            val maxAvailableProducts = allStoreOptions.maxOfOrNull { storeOption ->
                basketItems.size - storeOption.unavailableProducts.size
            } ?: 0
            
            if (maxAvailableProducts == 0) {
                return null
            }
            
            val storesWithMaxProducts = allStoreOptions.filter { storeOption ->
                (basketItems.size - storeOption.unavailableProducts.size) == maxAvailableProducts
            }
            
            val storesByRetailer = storesWithMaxProducts.groupBy { it.store.retailerId }
            val filteredStores = storesByRetailer.map { (_, stores) ->
                if (stores.size > 1) stores.minByOrNull { it.distanceKm }!! else stores.first()
            }
            
            val closestOption = filteredStores.minByOrNull { it.distanceKm }!!
            val cheapestOption = filteredStores.minByOrNull { it.totalPrice }!!
            
            Pair(closestOption, cheapestOption)
            
        } catch (e: Exception) {
            println("Error finding optimal stores: ${e.message}")
            null
        }
    }
    
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
                var usedBioUpgrade = false
                if (!item.id.endsWith(" bio")) {
                    val bioType = item.id + " bio"
                    val bioPriceInfo = fetchProductPricesForRetailer(store.retailerId, listOf(bioType))[bioType]
                    
                    if (bioPriceInfo != null && bioPriceInfo.finalPrice < priceInfo.finalPrice) {
                        totalPrice += bioPriceInfo.finalPrice * item.quantity
                        if (bioPriceInfo.hasDiscount) {
                            totalSavings += (bioPriceInfo.originalPrice - bioPriceInfo.finalPrice) * item.quantity
                        }
                        bioUpgrades[item.name] = bioPriceInfo.productName
                        usedBioUpgrade = true
                    }
                }
                
                if (!usedBioUpgrade) {
                    totalPrice += priceInfo.finalPrice * item.quantity
                    if (priceInfo.hasDiscount) {
                        totalSavings += (priceInfo.originalPrice - priceInfo.finalPrice) * item.quantity
                    }
                }
            } else {
                var foundAlternative = false
                if (allowNonBioAlternatives && item.id.endsWith(" bio")) {
                    val nonBioType = item.id.removeSuffix(" bio")
                    val nonBioPriceInfo = fetchProductPricesForRetailer(store.retailerId, listOf(nonBioType))[nonBioType]
                    
                    if (nonBioPriceInfo != null) {
                        totalPrice += nonBioPriceInfo.finalPrice * item.quantity
                        if (nonBioPriceInfo.hasDiscount) {
                            totalSavings += (nonBioPriceInfo.originalPrice - nonBioPriceInfo.finalPrice) * item.quantity
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
    
    private suspend fun fetchProductPricesForRetailer(retailerId: String, productTypes: List<String>): Map<String, PriceInfo> {
        val priceMap = mutableMapOf<String, PriceInfo>()

        try {
            for (productType in productTypes) {
                try {
                    val productsSnapshot = db.collection("retailer")
                        .document(retailerId)
                        .collection("products_per_retailer")
                        .whereEqualTo("product_type", productType)
                        .get()
                        .await()
                    
                    if (!productsSnapshot.isEmpty) {
                        val cheapestProduct = productsSnapshot.documents
                            .filter { doc ->
                                val inStock = doc.getBoolean("in_stock") ?: false
                                val price = (doc.get("price") as? Number)?.toDouble()
                                inStock && price != null && price > 0
                            }
                            .minByOrNull { doc ->
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
                            
                            if (originalPrice != null) {
                                val finalPrice = discountedPrice ?: originalPrice
                                val hasDiscount = discountedPrice != null && discountedPrice < originalPrice
                                
                                priceMap[productType] = PriceInfo(
                                    finalPrice = finalPrice,
                                    originalPrice = originalPrice,
                                    hasDiscount = hasDiscount,
                                    productName = productName,
                                    unit = unit,
                                    productImage = productImage
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    println("Error fetching product prices: ${e.message}")
                }
            }
        } catch (e: Exception) {
            println("Error fetching product prices: ${e.message}")
        }
        
        return priceMap
    }
}
