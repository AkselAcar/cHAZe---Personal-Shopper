package com.epfl.esl.chaze.features.basket

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DirectionsTransit
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.epfl.esl.chaze.R
import com.epfl.esl.chaze.data.model.BasketItemModel
import com.epfl.esl.chaze.data.model.StoreModel
import com.epfl.esl.chaze.data.model.TransportMode
import com.epfl.esl.chaze.data.model.StoreOption
import com.epfl.esl.chaze.features.localization.LocalizationViewModel
import com.epfl.esl.chaze.ui.theme.CHAZeTheme
import com.epfl.esl.chaze.utils.DistanceCalculator
import java.util.Locale

// Emoji for transport mode
fun getTransportEmoji(mode: TransportMode): String {
    return when (mode) {
        TransportMode.WALKING -> "🚶"
        TransportMode.BICYCLING -> "🚴"
        TransportMode.TRANSIT -> "🚇"
        TransportMode.DRIVING -> "🚗"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
/**
 * Main basket screen displaying current basket items and optimization results.
 * 
 * Three main states:
 * 1. EMPTY - User hasn't added any products yet (Start buying)
 * 2. CURRENT - User has items but hasn't validated yet (Validate Basket)
 * 3. OPTIMIZATION_RESULTS - After optimization, shows best store options and items (Start Shopping)
 * 
 * Flow:
 * - User adds items -> CURRENT state (swipe to dismiss, add to favorites)
 * - User clicks Validate -> Shows transport mode dialog → Calls optimizer
 * - Optimizer returns best stores -> OPTIMIZATION_RESULTS state
 * - If multiple equal options exist -> StoreOptionsSelectionDialog lets user choose
 * - User confirms -> onStartShoppingClick called to begin shopping
 *
 */
fun CurrentBasketScreen(
    modifier: Modifier = Modifier,
    currentBasketViewModel: CurrentBasketViewModel = viewModel(),
    localizationViewModel: LocalizationViewModel = viewModel(),
    onStartBuyingClick: () -> Unit = {},
    onStartShoppingClick: (List<BasketItemModel>) -> Unit = {},
    onOpenMapsClick: (StoreModel, TransportMode) -> Unit = { _, _ -> }
) {
    val basketItems by currentBasketViewModel.basketItems.observeAsState(initial = emptyList())
    val isOptimizing by currentBasketViewModel.isOptimizing.observeAsState(false)
    val optimizationResult by currentBasketViewModel.optimizationResult.observeAsState(null)
    val totalPrice by currentBasketViewModel.totalPrice.observeAsState(0.0)
    val selectedStore by currentBasketViewModel.selectedStore.observeAsState(null)
    val selectedStoreOption by currentBasketViewModel.selectedStoreOption.observeAsState(null)
    val optimizationError by currentBasketViewModel.optimizationError.observeAsState(null)
    val userTransportMode by currentBasketViewModel.transportMode.collectAsState()

    val context = LocalContext.current

    // Show Toast when optimization error occurs
    LaunchedEffect(optimizationError) {
        optimizationError?.let { error ->
            // Only show toast for actual errors, not for partial solution warnings
            if (!error.startsWith("⚠️ Partial solution") && !error.startsWith("⚠️ Search range increased")) {
                Toast.makeText(context, error, Toast.LENGTH_LONG).show()
            }
        }
    }

    // New optimization options
    val closestStoreOption by currentBasketViewModel.closestStoreOption.observeAsState(null)
    val cheapestStoreOption by currentBasketViewModel.cheapestStoreOption.observeAsState(null)
    val closestStoreOptionWithAlternatives by currentBasketViewModel.closestStoreOptionWithAlternatives.observeAsState(null)
    val cheapestStoreOptionWithAlternatives by currentBasketViewModel.cheapestStoreOptionWithAlternatives.observeAsState(null)
    val needsUserSelection by currentBasketViewModel.needsUserSelection.observeAsState(false)
    val hasBioAlternatives by currentBasketViewModel.hasBioAlternatives.observeAsState(false)

    // Get user's address from LocalizationViewModel
    val chosenAddress by localizationViewModel.chosenAddress.collectAsState()

    // State for basket name
    var basketName by remember { mutableStateOf("Basket") }

    // Transport mode dialog state
    var showTransportDialog by remember { mutableStateOf(false) }
    var selectedTransportMode by remember { mutableStateOf(userTransportMode) }

    // Update selected transport mode when user's preference changes
    LaunchedEffect(userTransportMode) {
        selectedTransportMode = userTransportMode
    }
//  Start display
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
//      Text at top of the screen
        Text(
            text = stringResource(id = R.string.current_basket_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Show option selection UI when optimization finds both options
        if (needsUserSelection && closestStoreOption != null && cheapestStoreOption != null) {
            StoreOptionsSelectionDialog(
                closestOption = closestStoreOption!!,
                cheapestOption = cheapestStoreOption!!,
                closestOptionWithAlternatives = closestStoreOptionWithAlternatives,
                cheapestOptionWithAlternatives = cheapestStoreOptionWithAlternatives,
                hasBioAlternatives = hasBioAlternatives,
                transportMode = userTransportMode,
                onOptionSelected = { selectedOption ->
                    currentBasketViewModel.selectStoreOption(selectedOption)
                }
            )
        }

        if (optimizationResult != null) {
            // === OPTIMIZATION RESULTS VIEW ===
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back button to return to store selection
                if (closestStoreOption != null && cheapestStoreOption != null) {
                    TextButton(
                        onClick = { currentBasketViewModel.backToStoreSelection() }
                    ) {
                        Text("← Change Store")
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "Optimization Results",
                    style = MaterialTheme.typography.titleLarge
                )
            }

            // Display selected store
            selectedStore?.let { store ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Store,
                            contentDescription = "Store",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${store.name} - ${store.locationText}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = store.address,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                            chosenAddress?.let { userAddr ->
                                val distance = DistanceCalculator.calculateDistance(
                                    userAddr.latitude, userAddr.longitude,
                                    store.latitude, store.longitude
                                )
                                Text(
                                    text = "Distance: ${DistanceCalculator.formatDistance(distance)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // Display error if any (hide partial solution warning if selected basket is complete with alternatives)
            optimizationError?.let { error ->
                val shouldShowError = if (error.startsWith("⚠️ Partial solution")) {
                    // Only show partial solution warning if the selected option has unavailable products
                    selectedStoreOption?.unavailableProducts?.isNotEmpty() == true
                } else {
                    true
                }

                if (shouldShowError) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                }

                items(optimizationResult!!) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Product Image
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!item.imageUrl.isNullOrEmpty()) {
                                    AsyncImage(
                                        model = item.imageUrl,
                                        contentDescription = item.name,
                                        modifier = Modifier.size(60.dp),
                                        contentScale = ContentScale.Crop
                                    )
                                } else if (item.imageRes != 0) {
                                    Image(
                                        painter = painterResource(id = item.imageRes),
                                        contentDescription = item.name,
                                        modifier = Modifier.size(60.dp),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.ShoppingCart,
                                        contentDescription = null,
                                        modifier = Modifier.size(30.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Product Info
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = item.productName.ifEmpty { item.name },
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2
                                )
                                if (item.quantity > 1) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Quantity: ${item.quantity}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Price (display per unit if available)
                            Text(
                                text = if (item.unit.isNotEmpty()) {
                                    String.format(Locale.getDefault(), "%.2f CHF/%s", item.price * item.quantity, item.unit)
                                } else {
                                    String.format(Locale.getDefault(), "%.2f CHF", item.price * item.quantity)
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1
                            )
                        }
                    }
                }

                // Display unavailable products if any
                selectedStoreOption?.let { storeOption ->
                    if (storeOption.unavailableProducts.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "⚠️ Unavailable Products (${storeOption.unavailableProducts.size})",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                    storeOption.unavailableProducts.forEach { productName ->
                                        Text(
                                            text = "• $productName",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.padding(vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = String.format(Locale.getDefault(), "Total: CHF %.2f", totalPrice),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )

                    // Display savings if any discounts were applied
                    if (selectedStoreOption?.totalSavings != null && selectedStoreOption!!.totalSavings > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = String.format(Locale.getDefault(), "💸 With discounts, you saved: CHF %.2f", selectedStoreOption!!.totalSavings),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.tertiary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(onClick = {
                        selectedStoreOption?.store?.let { store ->
                            onOpenMapsClick(store, userTransportMode)
                        }
                    }) {
                        Icon(Icons.Default.Map, contentDescription = null)
                        Spacer(modifier = Modifier.padding(4.dp))
                        Text("See store on Maps")
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            currentBasketViewModel.clearBasket()
                            onStartShoppingClick(optimizationResult!!)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Validate & Start Shopping")
                    }
                }
            }

        } else if (basketItems.isEmpty()) {
            // === EMPTY BASKET VIEW ===
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Add products to start building a basket",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "Once you added products, they will be displayed here.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Button(onClick = onStartBuyingClick) {
                    Text(text = "Start buying")
                }
            }
        } else {
            // === CURRENT BASKET VIEW ===
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                }

                items(basketItems, key = { it.id }) { item ->
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = {
                            if (it == SwipeToDismissBoxValue.EndToStart) {
                                currentBasketViewModel.removeBasketItem(item)
                                true
                            } else {
                                false
                            }
                        }
                    )
//                  Swipe-to-dismiss function
                    SwipeToDismissBox(
                        state = dismissState,
                        enableDismissFromStartToEnd = false,
                        backgroundContent = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete Icon",
                                    tint = Color.Red,
                                    modifier = Modifier.padding(end = 16.dp)
                                )
                            }
                        }
                    ) {
                        BasketItem(item = item)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Basket Name Input
            OutlinedTextField(
                value = basketName,
                onValueChange = { basketName = it },
                label = { Text("Basket Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))
//          Row containing the "Add to Favorites" and "Validate Basket" buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(
                    onClick = { currentBasketViewModel.addToFavorites(basketName) },
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Icon(Icons.Default.Favorite, contentDescription = "Add to Favorites")
                    Spacer(modifier = Modifier.padding(4.dp))
                    Text("Add to Favorites")
                }

                Spacer(modifier = Modifier.weight(0.1f))

                if (isOptimizing) {
                    CircularProgressIndicator()
                } else {
                    Button(
                        onClick = {
                            selectedTransportMode = userTransportMode
                            showTransportDialog = true
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Validate")
                        Spacer(modifier = Modifier.padding(4.dp))
                        Text("Validate Basket")
                    }
                }
            }
        }
    }

    // Transport mode selection dialog
    if (showTransportDialog) {
        AlertDialog(
            onDismissRequest = { showTransportDialog = false },
            title = { Text("Select Transport Mode") },
            text = {
                Column {
                    Text(
                        text = "Choose your preferred transport mode for distance calculation:",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    TransportMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedTransportMode == mode,
                                onClick = { selectedTransportMode = mode }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = when (mode) {
                                    TransportMode.DRIVING -> Icons.Default.DirectionsCar
                                    TransportMode.WALKING -> Icons.AutoMirrored.Filled.DirectionsWalk
                                    TransportMode.BICYCLING -> Icons.AutoMirrored.Filled.DirectionsBike
                                    TransportMode.TRANSIT -> Icons.Default.DirectionsTransit
                                },
                                contentDescription = null,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(text = mode.displayName)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val userLat = chosenAddress?.latitude ?: 0.0
                        val userLon = chosenAddress?.longitude ?: 0.0
                        // Transport mode affects distance calculation for store optimization
                        currentBasketViewModel.validateBasket(userLat, userLon, selectedTransportMode)
                        showTransportDialog = false
                    }
                ) {
                    Text("Optimize")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTransportDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}


@Composable
fun StoreOptionsSelectionDialog(
    closestOption: StoreOption,
    cheapestOption: StoreOption,
    closestOptionWithAlternatives: StoreOption?,
    cheapestOptionWithAlternatives: StoreOption?,
    hasBioAlternatives: Boolean,
    transportMode: TransportMode,
    onOptionSelected: (StoreOption) -> Unit
) {
    var showStrictBio by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = { },
        title = {
            Column {
                Text(
                    text = "Choose Your Store",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                if (hasBioAlternatives) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        TextButton(
                            onClick = { showStrictBio = true },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = if (showStrictBio) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        ) {
                            Text("Desired Products", fontWeight = if (showStrictBio) FontWeight.Bold else FontWeight.Normal)
                        }
                        TextButton(
                            onClick = { showStrictBio = false },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = if (!showStrictBio) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        ) {
                            Text("With Alternatives", fontWeight = if (!showStrictBio) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }
        },
        text = {
            val displayClosest = if (hasBioAlternatives && !showStrictBio && closestOptionWithAlternatives != null) closestOptionWithAlternatives else closestOption
            val displayCheapest = if (hasBioAlternatives && !showStrictBio && cheapestOptionWithAlternatives != null) cheapestOptionWithAlternatives else cheapestOption

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "We found two optimal options for your basket:",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Closest Option Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${getTransportEmoji(transportMode)} CLOSEST OPTION",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = displayClosest.store.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = displayClosest.store.locationText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Distance",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
                                )
                                Text(
                                    text = String.format(Locale.getDefault(), "%.2f km", displayClosest.distanceKm),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                displayClosest.durationMinutes?.let {
                                    Text(
                                        text = "(~$it min)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                    )
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "Total Price",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
                                )
                                Text(
                                    text = String.format(Locale.getDefault(), "%.2f CHF", displayClosest.totalPrice),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                if (displayClosest.totalSavings > 0) {
                                    Text(
                                        text = String.format(Locale.getDefault(), "💸Total Discounts: %.2f CHF", displayClosest.totalSavings),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.tertiary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        if (displayClosest.unavailableProducts.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "⚠️ Unavailable Products:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            displayClosest.unavailableProducts.forEach { productName ->
                                Text(
                                    text = "• $productName",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(start = 8.dp, top = 2.dp)
                                )
                            }
                        }

                        if (displayClosest.bioSubstitutions.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "🔄 Bio Substitutions:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            displayClosest.bioSubstitutions.forEach { (bioProduct, nonBioProduct) ->
                                Text(
                                    text = "• $bioProduct → $nonBioProduct (non-bio)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.padding(start = 8.dp, top = 2.dp)
                                )
                            }
                        }

                        if (displayClosest.bioUpgrades.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "⬆️ Bio Upgrades:",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF2E7D32),
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            displayClosest.bioUpgrades.forEach { (nonBioProduct, bioProduct) ->
                                Text(
                                    text = "• $nonBioProduct → $bioProduct (bio cheaper!)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF2E7D32),
                                    modifier = Modifier.padding(start = 8.dp, top = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = { onOptionSelected(displayClosest) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text("Select Closest")
                        }
                    }
                }

                // Cheapest Option Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "💸 CHEAPEST OPTION",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = displayCheapest.store.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = displayCheapest.store.locationText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Distance",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.6f)
                                )
                                Text(
                                    text = String.format(Locale.getDefault(), "%.2f km", displayCheapest.distanceKm),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                displayCheapest.durationMinutes?.let {
                                    Text(
                                        text = "(~$it min)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                                    )
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "Total Price",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.6f)
                                )
                                Text(
                                    text = String.format(Locale.getDefault(), "%.2f CHF", displayCheapest.totalPrice),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                                if (displayCheapest.totalSavings > 0) {
                                    Text(
                                        text = String.format(Locale.getDefault(), "💸Total Discounts: %.2f CHF", displayCheapest.totalSavings),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.tertiary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        if (displayCheapest.unavailableProducts.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "⚠️ Unavailable Products:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            displayCheapest.unavailableProducts.forEach { productName ->
                                Text(
                                    text = "• $productName",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(start = 8.dp, top = 2.dp)
                                )
                            }
                        }

                        if (displayCheapest.bioSubstitutions.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "🔄 Bio Substitutions:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            displayCheapest.bioSubstitutions.forEach { (bioProduct, nonBioProduct) ->
                                Text(
                                    text = "• $bioProduct → $nonBioProduct (non-bio)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.padding(start = 8.dp, top = 2.dp)
                                )
                            }
                        }

                        if (displayCheapest.bioUpgrades.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "⬆️ Bio Upgrades:",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF2E7D32),
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            displayCheapest.bioUpgrades.forEach { (nonBioProduct, bioProduct) ->
                                Text(
                                    text = "• $nonBioProduct → $bioProduct (bio cheaper!)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF2E7D32),
                                    modifier = Modifier.padding(start = 8.dp, top = 2.dp)
                                )
                            }
                        }

                        // Show savings if different from closest
                        if (displayClosest.store.storeId != displayCheapest.store.storeId) {
                            val savings = displayClosest.totalPrice - displayCheapest.totalPrice
                            if (savings > 0) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "💵 Save ${String.format(Locale.getDefault(), "%.2f CHF", savings)} compared to closest option!",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = { onOptionSelected(displayCheapest) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiary
                            )
                        ) {
                            Text("Select Cheapest")
                        }
                    }
                }

                // Note if both options are the same store
                if (displayClosest.store.storeId == displayCheapest.store.storeId) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "✨ Great news! The closest store also has the best prices!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = { }
    )
}

@Preview(showBackground = true)
@Composable
fun CurrentBasketScreenPreview() {
    CHAZeTheme {
        CurrentBasketScreen()
    }
}
