// This file was written with the help of AI
package com.epfl.esl.chaze.features.chatbot

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.epfl.esl.chaze.BuildConfig
import com.epfl.esl.chaze.data.model.BasketItemModel
import com.epfl.esl.chaze.data.model.RecommendedProductModel
import com.epfl.esl.chaze.data.model.StoreOption
import com.epfl.esl.chaze.data.model.TransportMode
import com.epfl.esl.chaze.data.repository.BasketRepository
import com.epfl.esl.chaze.services.OptimizationService
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.FunctionDeclaration
import com.google.ai.client.generativeai.type.Schema
import com.google.ai.client.generativeai.type.Tool
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.FunctionResponsePart
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Locale
import kotlin.String

// Optimization type enum
enum class OptimizationType {
    CHEAPEST,
    CLOSEST
}

// Message types for chat
sealed class ChatMessage(open val isFromUser: Boolean) {
    data class TextMessage(val text: String, override val isFromUser: Boolean) : ChatMessage(isFromUser)
    data class OptimizationMessage(
        val productName: String,
        val storeOption: StoreOption,
        val optimizedItems: List<BasketItemModel>,
        val optimizationType: OptimizationType = OptimizationType.CHEAPEST,
        override val isFromUser: Boolean = false
    ) : ChatMessage(isFromUser)
    
    // Dual optimization message when bio products need alternatives
    data class DualOptimizationMessage(
        val productName: String,
        val exactMatchOption: StoreOption?,        // Option with exact bio products (may be incomplete)
        val exactMatchItems: List<BasketItemModel>,
        val alternativeOption: StoreOption?,       // Option with non-bio alternatives (complete list)
        val alternativeItems: List<BasketItemModel>,
        val optimizationType: OptimizationType = OptimizationType.CHEAPEST,
        override val isFromUser: Boolean = false
    ) : ChatMessage(isFromUser)
}

class ChatbotViewModel(application: Application) : AndroidViewModel(application) {

    companion object {

        // Session-level storage (screen persists across navigation but clears on app restart)
        private val sessionMessages = mutableListOf<ChatMessage>()
    }

    private val optimizationService = OptimizationService.getInstance()
    
    // Messages are session-only
    private val _messages = MutableLiveData<List<ChatMessage>>(sessionMessages.toList())
    val messages: LiveData<List<ChatMessage>> = _messages
    
    // Location for optimization
    private var userLatitude: Double = 0.0
    private var userLongitude: Double = 0.0
    private var userTransportMode: TransportMode = TransportMode.DRIVING
    private var userMaxDistanceKm: Double = 10.0
    
    // Current optimization result for action buttons
    private val _currentOptimizationResult = MutableLiveData<ChatMessage.OptimizationMessage?>(null)

    // Loading state for optimization
    private val _isOptimizing = MutableLiveData(false)
    val isOptimizing: LiveData<Boolean> = _isOptimizing

    // Define the addProduct function declaration
    private val addProductDeclaration = FunctionDeclaration(
        name = "addProduct",
        description = "Add a product to the user's current shopping basket. Use this when the user wants to add an item to their cart.",
        parameters = listOf(
            Schema.str(name = "productName", description = "The name of the product to add. IMPORTANT: If the user mentions 'bio', 'organic', or 'biologique', you MUST include it in the product name (e.g., 'apples bio', 'chicken bio', 'milk bio')")
        ),
        requiredParameters = listOf("productName")
    )
    
    // Define the findBestPrice function declaration
    private val findBestPriceDeclaration = FunctionDeclaration(
        name = "findBestPrice",
        description = "Find the cheapest store for one or more products. Use this when the user asks where to find the best price, cheapest option, or which store has the lowest price for products. Can handle multiple products at once.",
        parameters = listOf(
            Schema.str(name = "productNames", description = "A comma-separated list of product names to find the best prices for. Example: 'chicken bio, carrots, apples bio'. IMPORTANT: If the user mentions 'bio', 'organic', or 'biologique' for a product, you MUST include 'bio' after that product name (e.g., 'chicken bio, carrots' for bio chicken and regular carrots).")
        ),
        requiredParameters = listOf("productNames")
    )
    
    // Define the findClosestStore function declaration
    private val findClosestStoreDeclaration = FunctionDeclaration(
        name = "findClosestStore",
        description = "Find the closest store for one or more products. Use this when the user asks for the closest store, nearest option, or shortest distance. Can handle multiple products at once.",
        parameters = listOf(
            Schema.str(name = "productNames", description = "A comma-separated list of product names to find the closest store for. Example: 'chicken bio, carrots, apples bio'. IMPORTANT: If the user mentions 'bio', 'organic', or 'biologique' for a product, you MUST include 'bio' after that product name (e.g., 'chicken bio, carrots' for bio chicken and regular carrots).")
        ),
        requiredParameters = listOf("productNames")
    )

    // Initialize the model with the tools
    private val generativeModel = GenerativeModel(
        modelName = "gemini-2.0-flash",
        apiKey = BuildConfig.GOOGLE_API_KEY,
        tools = listOf(Tool(listOf(addProductDeclaration, findBestPriceDeclaration, findClosestStoreDeclaration)))
    )

    private val chat = generativeModel.startChat()
    
    /**
     * Update user location and preferences (called from screen/activity)
     */
    fun updateLocation(
        latitude: Double,
        longitude: Double,
        transportMode: TransportMode = TransportMode.DRIVING,
        maxDistanceKm: Double = 10.0
    ) {
        userLatitude = latitude
        userLongitude = longitude
        userTransportMode = transportMode
        userMaxDistanceKm = maxDistanceKm
    }
    
    /**
     * Add a message to both LiveData and session storage
     */
    private fun addMessage(message: ChatMessage) {
        sessionMessages.add(message)
        _messages.value = sessionMessages.toList()
    }

    /**
     * Check if an exception is a rate limit error (429/RESOURCE_EXHAUSTED)
     */
    private fun isRateLimitError(e: Exception): Boolean {
        val message = e.message ?: ""
        return message.contains("429") || 
               message.contains("RESOURCE_EXHAUSTED") ||
               message.contains("exhausted", ignoreCase = true)
    }

    /**
     * Send a message to the AI model and add the response to the chat
     */
    fun sendMessage(text: String) {
        // Add user message
        val userMessage = ChatMessage.TextMessage(text, true)
        addMessage(userMessage)

        viewModelScope.launch {
            val maxRetries = 3
            var lastException: Exception? = null
            
            for (attempt in 0 until maxRetries) {
                try {
                    if (attempt > 0) {
                        // Exponential backoff: 2s, 4s, 8s
                        val delayMs = (1L shl attempt) * 1000L
                        delay(delayMs)
                    }
                    
                    // Send message and get response
                    var response = chat.sendMessage(text)

                    // Check for function calls
                    while (response.functionCalls.isNotEmpty()) {
                        // Build responses for ALL function calls
                        val functionResponses = response.functionCalls.map { functionCall ->
                            // Convert Map<String, String?> to Map<String, String> with empty string defaults
                            val safeArgs = functionCall.args.mapValues { it.value ?: "" }
                            when (functionCall.name) {
                                "addProduct" -> handleAddProduct(safeArgs)
                                "findBestPrice" -> handleFindBestPrice(safeArgs)
                                "findClosestStore" -> handleFindClosestStore(safeArgs)
                                else -> FunctionResponsePart(functionCall.name, JSONObject(mapOf("error" to "Unknown function")))
                            }
                        }

                        // Send ALL function responses back in a single message
                        response = chat.sendMessage(
                            content("function") {
                                functionResponses.forEach { part(it) }
                            }
                        )
                    }

                    val responseText = response.text ?: "I couldn't generate a response."
                    val botMessage = ChatMessage.TextMessage(responseText, false)
                    addMessage(botMessage)
                    return@launch // if Success, then exit the retry loop
                    
                } catch (e: Exception) {
                    lastException = e
                    
                    // Only retry on rate limit errors
                    if (!isRateLimitError(e)) {
                        break // Don't retry for other errors
                    }
                    
                    
                    // If this was the last attempt, break to show error
                    if (attempt == maxRetries - 1) {
                        break
                    }
                }
            }
            
            // All retries failed or non-retryable error
            val e = lastException ?: Exception("Unknown error")
            val errorText = when {
                isRateLimitError(e) ->
                    "The AI service is busy. Please wait a moment and try again."
                e.message?.contains("403") == true ->
                    "API access denied. Please check your API key configuration."
                e.message?.contains("network") == true || e.message?.contains("connect") == true ->
                    "Network error. Please check your internet connection."
                else -> "Error: ${e.message ?: "Unknown error"}"
            }
            val errorMessage = ChatMessage.TextMessage(errorText, false)
            addMessage(errorMessage)
        }
    }

    /**
     * Add a product to the basket
     */
    private suspend fun handleAddProduct(args: Map<String, String>): FunctionResponsePart {
        val productName = args["productName"] ?: ""
        
        val resultJson = try {
            val product = BasketRepository.findProductTypeByName(productName)
            if (product != null) {
                BasketRepository.addProduct(product)
                mapOf<String, Any>("success" to true, "message" to "Added ${product.name} to basket")
            } else {
                mapOf<String, Any>("success" to false, "message" to "Product type '$productName' not found")
            }
        } catch (e: Exception) {
            mapOf<String, Any>("success" to false, "message" to "Error: ${e.message}")
        }
        
        return FunctionResponsePart("addProduct", JSONObject(resultJson))
    }

    /**
     * Find the cheapest store for one or more products
     */
    private suspend fun handleFindBestPrice(args: Map<String, String>): FunctionResponsePart {
        // Support both old "productName" and new "productNames" parameter
        val productNamesInput = args["productNames"] ?: args["productName"] ?: ""
        
        // Parse comma-separated product names and trim whitespace
        val productNamesList = productNamesInput.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        
        if (productNamesList.isEmpty()) {
            return FunctionResponsePart("findBestPrice", JSONObject(mapOf("success" to false, "message" to "No product names provided")))
        }
        
        
        val resultJson = try {
            // Find all products
            val foundProducts = mutableListOf<Pair<String, RecommendedProductModel>>()
            val notFoundProducts = mutableListOf<String>()
            
            for (productName in productNamesList) {
                val product = BasketRepository.findProductTypeByName(productName)
                if (product != null) {
                    foundProducts.add(productName to product)
                } else {
                    notFoundProducts.add(productName)
                }
            }
            
            if (foundProducts.isEmpty()) {
                mapOf<String, Any>(
                    "success" to false, 
                    "message" to "None of the requested products were found in our database: ${notFoundProducts.joinToString(", ")}"
                )
            } else {
                // Show loading message
                _isOptimizing.value = true
                val productsList = foundProducts.joinToString(", ") { it.second.name }
                val loadingMessage = ChatMessage.TextMessage("🔍 Searching for the cheapest store for $productsList...", false)
                addMessage(loadingMessage)
                
                // Create basket items for all found products
                val basketItems = foundProducts.map { (_, product) ->
                    BasketItemModel(
                        id = product.id,
                        name = product.name,
                        description = product.category,
                        imageRes = 0,
                        imageUrl = product.imageUrl,
                        quantity = 1.0,
                        price = 0.0
                    )
                }
                
                // Check if any bio products are requested
                val hasBioRequest = basketItems.any { it.id.endsWith(" bio") }
                
                if (hasBioRequest) {
                    // Run dual optimization for bio products
                    val dualResult = optimizationService.findCheapestStoreWithAlternatives(
                        basketItems = basketItems,
                        userLat = userLatitude,
                        userLon = userLongitude,
                        transportMode = userTransportMode,
                        maxDistanceKm = userMaxDistanceKm
                    )
                    
                    _isOptimizing.value = false
                    
                    if (dualResult.needsDualDisplay) {
                        // Show dual options: exact bio match vs alternatives
                        val dualMessage = ChatMessage.DualOptimizationMessage(
                            productName = productsList,
                            exactMatchOption = dualResult.exactMatchResult.cheapestOption,
                            exactMatchItems = dualResult.exactMatchResult.optimizedItems,
                            alternativeOption = dualResult.alternativeResult.cheapestOption,
                            alternativeItems = dualResult.alternativeResult.optimizedItems,
                            optimizationType = OptimizationType.CHEAPEST
                        )
                        
                        addMessage(dualMessage)
                        
                        // Add products to basket
                        foundProducts.forEach { (_, product) ->
                            BasketRepository.addProduct(product)
                        }
                        
                        // Build response message
                        val responseMsg = buildString {
                            if (dualResult.exactMatchResult.cheapestOption != null) {
                                append("I found two options for you:\n")
                                append("1️⃣ **With exact bio products**: ${dualResult.exactMatchResult.cheapestOption.store.name}")
                                append(" (${dualResult.exactMatchResult.optimizedItems.size} items, ")
                                append(String.format(Locale.getDefault(), "%.2f", dualResult.exactMatchResult.cheapestOption.totalPrice))
                                append(" CHF)\n")
                            } else {
                                append("No stores have all the exact bio products you requested.\n")
                            }
                            
                            if (dualResult.alternativeResult.cheapestOption != null) {
                                val subs = dualResult.alternativeResult.cheapestOption.bioSubstitutions
                                if (subs.isNotEmpty()) {
                                    append("2️⃣ **With alternatives**: ${dualResult.alternativeResult.cheapestOption.store.name}")
                                    append(" (${dualResult.alternativeResult.optimizedItems.size} items, ")
                                    append(String.format(Locale.getDefault(), "%.2f", dualResult.alternativeResult.cheapestOption.totalPrice))
                                    append(" CHF) - substitutes: ${subs.entries.joinToString { "${it.key} → ${it.value}" }}")
                                }
                            }
                            append("\nI've displayed both options for you to choose.")
                        }
                        
                        mapOf<String, Any>("success" to true, "message" to responseMsg)
                    } else {
                        // Single result - use exact match if available, otherwise alternative
                        val result = if (dualResult.exactMatchResult.cheapestOption != null) 
                            dualResult.exactMatchResult else dualResult.alternativeResult
                        
                        if (result.cheapestOption != null && result.optimizedItems.isNotEmpty()) {
                            val optimizationMessage = ChatMessage.OptimizationMessage(
                                productName = productsList,
                                storeOption = result.cheapestOption,
                                optimizedItems = result.optimizedItems,
                                optimizationType = OptimizationType.CHEAPEST
                            )
                            
                            addMessage(optimizationMessage)
                            _currentOptimizationResult.value = optimizationMessage
                            
                            foundProducts.forEach { (_, product) ->
                                BasketRepository.addProduct(product)
                            }
                            
                            val itemsCount = result.optimizedItems.size
                            val responseMsg = buildString {
                                append("Found the cheapest store for $itemsCount item${if (itemsCount > 1) "s" else ""}: ")
                                append("${result.cheapestOption.store.name} at ")
                                append(String.format(Locale.getDefault(), "%.2f", result.cheapestOption.totalPrice))
                                append(" CHF total.")
                                if (notFoundProducts.isNotEmpty()) {
                                    append(" Note: Could not find: ${notFoundProducts.joinToString(", ")}.")
                                }
                                append(" I've displayed the result with options to validate or see on maps.")
                            }
                            
                            mapOf<String, Any>("success" to true, "message" to responseMsg)
                        } else {
                            mapOf<String, Any>(
                                "success" to false,
                                "message" to (result.error ?: "Could not find any stores with the requested products")
                            )
                        }
                    }
                } else {
                    // No bio products - run simple optimization
                    val result = optimizationService.findCheapestStore(
                        basketItems = basketItems,
                        userLat = userLatitude,
                        userLon = userLongitude,
                        transportMode = userTransportMode,
                        maxDistanceKm = userMaxDistanceKm
                    )
                    
                    _isOptimizing.value = false
                    
                    if (result.cheapestOption != null && result.optimizedItems.isNotEmpty()) {
                        val optimizationMessage = ChatMessage.OptimizationMessage(
                            productName = productsList,
                            storeOption = result.cheapestOption,
                            optimizedItems = result.optimizedItems,
                            optimizationType = OptimizationType.CHEAPEST
                        )
                        
                        addMessage(optimizationMessage)
                        _currentOptimizationResult.value = optimizationMessage
                        
                        foundProducts.forEach { (_, product) ->
                            BasketRepository.addProduct(product)
                        }
                        
                        val itemsCount = result.optimizedItems.size
                        val responseMsg = buildString {
                            append("Found the cheapest store for $itemsCount item${if (itemsCount > 1) "s" else ""}: ")
                            append("${result.cheapestOption.store.name} at ")
                            append(String.format(Locale.getDefault(), "%.2f", result.cheapestOption.totalPrice))
                            append(" CHF total.")
                            if (notFoundProducts.isNotEmpty()) {
                                append(" Note: Could not find: ${notFoundProducts.joinToString(", ")}.")
                            }
                            append(" I've displayed the result with options to validate or see on maps.")
                        }
                        
                        mapOf<String, Any>("success" to true, "message" to responseMsg)
                    } else {
                        mapOf<String, Any>(
                            "success" to false,
                            "message" to (result.error ?: "Could not find any stores with the requested products")
                        )
                    }
                }
            }
        } catch (e: Exception) {
            _isOptimizing.value = false
            mapOf<String, Any>("success" to false, "message" to "Error: ${e.message}")
        }
        
        return FunctionResponsePart("findBestPrice", JSONObject(resultJson))
    }

    /**
     * Find the closest store for one or more products
     */
    private suspend fun handleFindClosestStore(args: Map<String, String>): FunctionResponsePart {
        // Support both old "productName" and new "productNames" parameter
        val productNamesInput = args["productNames"] ?: args["productName"] ?: ""
        
        // Parse comma-separated product names and trim whitespace
        val productNamesList = productNamesInput.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        
        if (productNamesList.isEmpty()) {
            return FunctionResponsePart("findClosestStore", JSONObject(mapOf("success" to false, "message" to "No product names provided")))
        }
        
        
        val resultJson = try {
            // Find all products
            val foundProducts = mutableListOf<Pair<String, RecommendedProductModel>>()
            val notFoundProducts = mutableListOf<String>()
            
            for (productName in productNamesList) {
                val product = BasketRepository.findProductTypeByName(productName)
                if (product != null) {
                    foundProducts.add(productName to product)
                } else {
                    notFoundProducts.add(productName)
                }
            }
            
            if (foundProducts.isEmpty()) {
                mapOf<String, Any>(
                    "success" to false, 
                    "message" to "None of the requested products were found in our database: ${notFoundProducts.joinToString(", ")}"
                )
            } else {
                // Show loading message
                _isOptimizing.value = true
                val productsList = foundProducts.joinToString(", ") { it.second.name }
                val loadingMessage = ChatMessage.TextMessage("🔍 Searching for the closest store for $productsList...", false)
                addMessage(loadingMessage)
                
                // Create basket items for all found products
                val basketItems = foundProducts.map { (_, product) ->
                    BasketItemModel(
                        id = product.id,
                        name = product.name,
                        description = product.category,
                        imageRes = 0,
                        imageUrl = product.imageUrl,
                        quantity = 1.0,
                        price = 0.0
                    )
                }
                
                // Check if any bio products are requested
                val hasBioRequest = basketItems.any { it.id.endsWith(" bio") }
                
                if (hasBioRequest) {
                    // Run dual optimization for bio products
                    val dualResult = optimizationService.findClosestStoreWithAlternatives(
                        basketItems = basketItems,
                        userLat = userLatitude,
                        userLon = userLongitude,
                        transportMode = userTransportMode,
                        maxDistanceKm = userMaxDistanceKm
                    )
                    
                    _isOptimizing.value = false
                    
                    if (dualResult.needsDualDisplay) {
                        // Show dual options: exact bio match vs alternatives
                        val dualMessage = ChatMessage.DualOptimizationMessage(
                            productName = productsList,
                            exactMatchOption = dualResult.exactMatchResult.closestOption,
                            exactMatchItems = dualResult.exactMatchResult.optimizedItems,
                            alternativeOption = dualResult.alternativeResult.closestOption,
                            alternativeItems = dualResult.alternativeResult.optimizedItems,
                            optimizationType = OptimizationType.CLOSEST
                        )
                        
                        addMessage(dualMessage)
                        
                        // Add products to basket
                        foundProducts.forEach { (_, product) ->
                            BasketRepository.addProduct(product)
                        }
                        
                        // Build response message
                        val responseMsg = buildString {
                            if (dualResult.exactMatchResult.closestOption != null) {
                                append("I found two options for you:\n")
                                append("1️⃣ **With exact bio products**: ${dualResult.exactMatchResult.closestOption.store.name}")
                                append(" (${dualResult.exactMatchResult.optimizedItems.size} items, ")
                                append(String.format(Locale.getDefault(), "%.1f", dualResult.exactMatchResult.closestOption.distanceKm))
                                append(" km away)\n")
                            } else {
                                append("No stores nearby have all the exact bio products you requested.\n")
                            }
                            
                            if (dualResult.alternativeResult.closestOption != null) {
                                val subs = dualResult.alternativeResult.closestOption.bioSubstitutions
                                if (subs.isNotEmpty()) {
                                    append("2️⃣ **With alternatives**: ${dualResult.alternativeResult.closestOption.store.name}")
                                    append(" (${dualResult.alternativeResult.optimizedItems.size} items, ")
                                    append(String.format(Locale.getDefault(), "%.1f", dualResult.alternativeResult.closestOption.distanceKm))
                                    append(" km away) - substitutes: ${subs.entries.joinToString { "${it.key} → ${it.value}" }}")
                                }
                            }
                            append("\nI've displayed both options for you to choose.")
                        }
                        
                        mapOf<String, Any>("success" to true, "message" to responseMsg)
                    } else {
                        // Single result - use exact match if available, otherwise alternative
                        val result = if (dualResult.exactMatchResult.closestOption != null) 
                            dualResult.exactMatchResult else dualResult.alternativeResult
                        
                        if (result.closestOption != null && result.optimizedItems.isNotEmpty()) {
                            val optimizationMessage = ChatMessage.OptimizationMessage(
                                productName = productsList,
                                storeOption = result.closestOption,
                                optimizedItems = result.optimizedItems,
                                optimizationType = OptimizationType.CLOSEST
                            )
                            
                            addMessage(optimizationMessage)
                            _currentOptimizationResult.value = optimizationMessage
                            
                            foundProducts.forEach { (_, product) ->
                                BasketRepository.addProduct(product)
                            }
                            
                            val itemsCount = result.optimizedItems.size
                            val distance = String.format(Locale.getDefault(), "%.1f", result.closestOption.distanceKm)
                            val responseMsg = buildString {
                                append("Found the closest store for $itemsCount item${if (itemsCount > 1) "s" else ""}: ")
                                append("${result.closestOption.store.name} at $distance km away, ")
                                append(String.format(Locale.getDefault(), "%.2f", result.closestOption.totalPrice))
                                append(" CHF total.")
                                if (notFoundProducts.isNotEmpty()) {
                                    append(" Note: Could not find: ${notFoundProducts.joinToString(", ")}.")
                                }
                                append(" I've displayed the result with options to validate or see on maps.")
                            }
                            
                            mapOf<String, Any>("success" to true, "message" to responseMsg)
                        } else {
                            mapOf<String, Any>(
                                "success" to false,
                                "message" to (result.error ?: "Could not find any stores with the requested products nearby")
                            )
                        }
                    }
                } else {
                    // No bio products - run simple optimization
                    val result = optimizationService.findClosestStore(
                        basketItems = basketItems,
                        userLat = userLatitude,
                        userLon = userLongitude,
                        transportMode = userTransportMode,
                        maxDistanceKm = userMaxDistanceKm
                    )
                    
                    _isOptimizing.value = false
                    
                    // Use closestOption - optimizedItems are already built with closest store prices
                    if (result.closestOption != null && result.optimizedItems.isNotEmpty()) {
                        val optimizationMessage = ChatMessage.OptimizationMessage(
                            productName = productsList,
                            storeOption = result.closestOption,
                            optimizedItems = result.optimizedItems,
                            optimizationType = OptimizationType.CLOSEST
                        )
                        
                        addMessage(optimizationMessage)
                        _currentOptimizationResult.value = optimizationMessage
                        
                        foundProducts.forEach { (_, product) ->
                            BasketRepository.addProduct(product)
                        }
                        
                        val itemsCount = result.optimizedItems.size
                        val distance = String.format(Locale.getDefault(), "%.1f", result.closestOption.distanceKm)
                        val responseMsg = buildString {
                            append("Found the closest store for $itemsCount item${if (itemsCount > 1) "s" else ""}: ")
                            append("${result.closestOption.store.name} at $distance km away, ")
                            append(String.format(Locale.getDefault(), "%.2f", result.closestOption.totalPrice))
                            append(" CHF total.")
                            if (notFoundProducts.isNotEmpty()) {
                                append(" Note: Could not find: ${notFoundProducts.joinToString(", ")}.")
                            }
                            append(" I've displayed the result with options to validate or see on maps.")
                        }
                        
                        mapOf<String, Any>("success" to true, "message" to responseMsg)
                    } else {
                        mapOf<String, Any>(
                            "success" to false,
                            "message" to (result.error ?: "Could not find any stores with the requested products nearby")
                        )
                    }
                }
            }
        } catch (e: Exception) {
            _isOptimizing.value = false
            mapOf<String, Any>("success" to false, "message" to "Error: ${e.message}")
        }
        
        return FunctionResponsePart("findClosestStore", JSONObject(resultJson))
    }

}

