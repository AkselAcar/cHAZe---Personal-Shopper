// This file was written with the help of AI
package com.epfl.esl.chaze.features.chatbot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.epfl.esl.chaze.data.model.BasketItemModel
import com.epfl.esl.chaze.data.model.StoreModel
import com.epfl.esl.chaze.data.model.TransportMode
import com.epfl.esl.chaze.data.repository.BasketRepository
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
/**
 * AI-powered shopping assistant for the app.
 * 
 * Purpose: Natural language interface for basket management and store optimization.
 * 
 * Capabilities:
 * - "Find me apples" → searches and adds to basket
 * - "Best prices" → finds cheapest store for current basket
 * - "Closest store" → finds nearest store respecting transport mode
 * - Lists what bot can do for discoverability
 */
fun ChatbotScreen(
    modifier: Modifier = Modifier,
    chatbotViewModel: ChatbotViewModel,
    userLatitude: Double = 0.0,
    userLongitude: Double = 0.0,
    transportMode: TransportMode = TransportMode.DRIVING,
    maxDistanceKm: Double = 10.0,
    onStartShoppingClick: (List<BasketItemModel>) -> Unit = {},
    onOpenMapsClick: (StoreModel, TransportMode) -> Unit = { _, _ -> }
) {
    val messages by chatbotViewModel.messages.observeAsState(initial = emptyList())
    val isOptimizing by chatbotViewModel.isOptimizing.observeAsState(initial = false)
    var textState by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Update location and preferences in ViewModel
    LaunchedEffect(userLatitude, userLongitude, transportMode, maxDistanceKm) {
        chatbotViewModel.updateLocation(userLatitude, userLongitude, transportMode, maxDistanceKm)
    }

    // Auto-scroll to bottom when new message arrives
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            coroutineScope.launch {
                listState.animateScrollToItem(0)
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.SmartToy,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "cHAZeBot",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Ask me anything",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        bottomBar = {
            MessageInput(
                value = textState,
                onValueChange = { textState = it },
                onSendClick = {
                    if (textState.isNotBlank()) {
                        chatbotViewModel.sendMessage(textState)
                        textState = ""
                    }
                },
                isLoading = isOptimizing
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
        ) {
            if (messages.isEmpty()) {
                // Welcome screen when no messages
                WelcomeContent()
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    reverseLayout = true,
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(messages.reversed()) { message ->
                        when (message) {
                            is ChatMessage.TextMessage -> {
                                TextMessageBubble(message = message)
                            }
                            is ChatMessage.OptimizationMessage -> {
                                OptimizationResultCard(
                                    message = message,
                                    transportMode = transportMode,
                                    onValidateClick = {
                                        // Use the items from this specific message card
                                        BasketRepository.clearBasket()
                                        onStartShoppingClick(message.optimizedItems)
                                    },
                                    onMapsClick = {
                                        onOpenMapsClick(message.storeOption.store, transportMode)
                                    }
                                )
                            }
                            is ChatMessage.DualOptimizationMessage -> {
                                DualOptimizationResultCard(
                                    message = message,
                                    transportMode = transportMode,
                                    onSelectExactMatch = {
                                        BasketRepository.clearBasket()
                                        onStartShoppingClick(message.exactMatchItems)
                                    },
                                    onSelectAlternative = {
                                        BasketRepository.clearBasket()
                                        onStartShoppingClick(message.alternativeItems)
                                    },
                                    onMapsExactClick = {
                                        message.exactMatchOption?.let { 
                                            onOpenMapsClick(it.store, transportMode)
                                        }
                                    },
                                    onMapsAlternativeClick = {
                                        message.alternativeOption?.let {
                                            onOpenMapsClick(it.store, transportMode)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WelcomeContent() {
    /**
     * Initial onboarding screen showing chatbot capabilities.
     * Appears when no messages exist to guide user first interaction.
     * Lists three main use cases: add products, find prices, find locations.
     */
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.SmartToy,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(80.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Welcome to cHAZeBot!",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "I can help you with:",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        
        SuggestionChip(text = "• Adding products to your basket")
        SuggestionChip(text = "• Finding the best prices")
        SuggestionChip(text = "• Finding the closest products to you")

        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Type a message below to get started!",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun SuggestionChip(text: String) {
    /**
     * Suggestion chip component shown in welcome screen.
     */
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
fun TextMessageBubble(message: ChatMessage.TextMessage) {
    /**
     * Single text message bubble with optional bot avatar.
     */
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = if (message.isFromUser) Arrangement.End else Arrangement.Start
    ) {
        if (!message.isFromUser) {
            // Bot avatar
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SmartToy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }
        
        Card(
            modifier = Modifier.widthIn(max = 280.dp),
            shape = RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomStart = if (message.isFromUser) 20.dp else 4.dp,
                bottomEnd = if (message.isFromUser) 4.dp else 20.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (message.isFromUser) 
                    MaterialTheme.colorScheme.primary 
                else 
                    MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Text(
                text = message.text,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = if (message.isFromUser) 
                    MaterialTheme.colorScheme.onPrimary
                else 
                    MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun OptimizationResultCard(
    message: ChatMessage.OptimizationMessage,
    transportMode: TransportMode,
    onValidateClick: () -> Unit,
    onMapsClick: () -> Unit
) {
    /**
     * Single store optimization result.
     * Shows best store for either cheapest price or closest location.
     */
    val storeOption = message.storeOption
    val store = storeOption.store
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        // Bot avatar
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.SmartToy,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomStart = 4.dp,
                bottomEnd = 20.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // Header - changes based on optimization type
                val headerText = when (message.optimizationType) {
                    OptimizationType.CHEAPEST -> "🎯 Found the cheapest store for ${message.productName}!"
                    OptimizationType.CLOSEST -> "📍 Found the closest store for ${message.productName}!"
                }
                Text(
                    text = headerText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Store info card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Store,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = store.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = store.address.ifEmpty { store.locationText },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Price and distance info
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Price
                    Column {
                        Text(
                            text = "💸 Price",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = String.format(Locale.getDefault(), "CHF %.2f", storeOption.totalPrice),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    // Distance
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${getTransportEmoji(transportMode)} Distance",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = String.format(Locale.getDefault(), "%.1f km", storeOption.distanceKm),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        storeOption.durationMinutes?.let { duration ->
                            Text(
                                text = "${duration} min",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                // Savings if any
                if (storeOption.totalSavings > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = String.format(Locale.getDefault(), "💸 You save: CHF %.2f with discounts!", storeOption.totalSavings),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                
                // Product preview (if available) - horizontally scrollable
                if (message.optimizedItems.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        items(message.optimizedItems) { item ->
                            Card(
                                modifier = Modifier.width(180.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                shape = RoundedCornerShape(12.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (!item.imageUrl.isNullOrEmpty()) {
                                        AsyncImage(
                                            model = item.imageUrl,
                                            contentDescription = item.name,
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(RoundedCornerShape(8.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    Column(
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = item.productName.ifEmpty { item.name },
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 2
                                        )
                                        if (item.unit.isNotEmpty()) {
                                            Text(
                                                text = String.format(Locale.getDefault(), "CHF %.2f / %s", item.price, item.unit),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onMapsClick,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Map,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Maps", style = MaterialTheme.typography.labelLarge)
                    }
                    
                    Button(
                        onClick = onValidateClick,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ShoppingCart,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Start Shopping", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

private fun getTransportEmoji(mode: TransportMode): String {
    return when (mode) {
        TransportMode.WALKING -> "🚶"
        TransportMode.BICYCLING -> "🚴"
        TransportMode.TRANSIT -> "🚇"
        TransportMode.DRIVING -> "🚗"
    }
}

@Composable
fun DualOptimizationResultCard(
    message: ChatMessage.DualOptimizationMessage,
    transportMode: TransportMode,
    onSelectExactMatch: () -> Unit,
    onSelectAlternative: () -> Unit,
    onMapsExactClick: () -> Unit,
    onMapsAlternativeClick: () -> Unit
) {
    /**
     * Two store options when exact matches conflict with substitutions.
     * Left: Exact items only. Right: Items with alternatives.
     */
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        // Bot avatar
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.SmartToy,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomStart = 4.dp,
                bottomEnd = 20.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // Header that changes based on optimization type
                val headerEmoji = when (message.optimizationType) {
                    OptimizationType.CHEAPEST -> "🔍"
                    OptimizationType.CLOSEST -> "📍"
                }
                val headerText = "$headerEmoji Found options for ${message.productName}"
                Text(
                    text = headerText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                val descriptionText = when (message.optimizationType) {
                    OptimizationType.CHEAPEST -> "Not all bio products are available in one store. Here are your options:"
                    OptimizationType.CLOSEST -> "Not all bio products are available nearby. Here are your options:"
                }
                Text(
                    text = descriptionText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Option 1: Exact desired products
                if (message.exactMatchOption != null && message.exactMatchItems.isNotEmpty()) {
                    // Calculate missing products by comparing with alternative items
                    val missingProducts = if (message.alternativeOption != null) {
                        message.alternativeItems.map { it.name }.filter { altName ->
                            message.exactMatchItems.none { exactItem -> exactItem.name == altName }
                        }
                    } else {
                        message.exactMatchOption.unavailableProducts
                    }
                    
                    DualOptionCard(
                        title = "Only your Desired Products",
                        subtitle = "Option with your exact available product list",
                        storeOption = message.exactMatchOption,
                        items = message.exactMatchItems,
                        missingProducts = missingProducts,
                        transportMode = transportMode,
                        cardColor = MaterialTheme.colorScheme.primaryContainer,
                        onSelectClick = onSelectExactMatch,
                        onMapsClick = onMapsExactClick,
                        showSubstitutions = false
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                } else {
                    // No exact match available
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "❌",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "No stores nearby have all the exact bio products",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
                
                // Option 2: With alternatives
                if (message.alternativeOption != null && message.alternativeItems.isNotEmpty()) {
                    DualOptionCard(
                        title = "With Alternatives",
                        subtitle = "Complete list with substitute products",
                        storeOption = message.alternativeOption,
                        items = message.alternativeItems,
                        missingProducts = emptyList(),
                        transportMode = transportMode,
                        cardColor = MaterialTheme.colorScheme.tertiaryContainer,
                        onSelectClick = onSelectAlternative,
                        onMapsClick = onMapsAlternativeClick,
                        showSubstitutions = true
                    )
                }
            }
        }
    }
}

@Composable
private fun DualOptionCard(
    title: String,
    subtitle: String,
    storeOption: com.epfl.esl.chaze.data.model.StoreOption,
    items: List<BasketItemModel>,
    missingProducts: List<String> = emptyList(),
    transportMode: TransportMode,
    cardColor: androidx.compose.ui.graphics.Color,
    onSelectClick: () -> Unit,
    onMapsClick: () -> Unit,
    showSubstitutions: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // Title row
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Store info
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Store,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = storeOption.store.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = storeOption.store.address.ifEmpty { storeOption.store.locationText },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Price and distance
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "${items.size} item${if (items.size > 1) "s" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = String.format(Locale.getDefault(), "CHF %.2f", storeOption.totalPrice),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${getTransportEmoji(transportMode)} ${String.format(Locale.getDefault(), "%.1f km", storeOption.distanceKm)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    storeOption.durationMinutes?.let { duration ->
                        Text(
                            text = "${duration} min",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            // Show missing products if any
            if (missingProducts.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = "❌ Missing Products:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        missingProducts.forEach { product ->
                            Text(
                                text = "• $product",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
            
            // Show bio substitutions if any
            if (showSubstitutions && storeOption.bioSubstitutions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = "🔄 Bio Substitutions:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        storeOption.bioSubstitutions.forEach { (bio, nonBio) ->
                            Text(
                                text = "• $bio → $nonBio (non-bio)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onMapsClick,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Map,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Maps", style = MaterialTheme.typography.labelMedium)
                }
                
                Button(
                    onClick = onSelectClick,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ShoppingCart,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Select", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
fun MessageInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSendClick: () -> Unit,
    isLoading: Boolean = false
) {
    /**
     * Bottom input field for user messages.
     * Shows loading spinner during AI processing.
     * Disabled send button if text is blank to prevent empty messages.
     */
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                enabled = !isLoading,
                placeholder = { 
                    Text(
                        if (isLoading) "Searching..." else "Ask cHAZe anything...",
                        style = MaterialTheme.typography.bodyLarge
                    ) 
                },
                shape = RoundedCornerShape(28.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                textStyle = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.width(8.dp))
            FilledIconButton(
                onClick = onSendClick,
                enabled = value.isNotBlank() && !isLoading,
                modifier = Modifier.size(56.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send message",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

