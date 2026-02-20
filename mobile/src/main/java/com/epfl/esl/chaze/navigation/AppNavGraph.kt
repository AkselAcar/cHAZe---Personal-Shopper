package com.epfl.esl.chaze.navigation

import android.content.Intent
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.epfl.esl.chaze.data.model.BasketItemModel
import com.epfl.esl.chaze.data.model.TransportMode
import com.epfl.esl.chaze.data.repository.PastBasketRepository
import com.epfl.esl.chaze.features.analytics.AnalyticsScreen
import com.epfl.esl.chaze.features.basket.CurrentBasketScreen
import com.epfl.esl.chaze.features.basket.CurrentBasketViewModel
import com.epfl.esl.chaze.features.chatbot.ChatbotScreen
import com.epfl.esl.chaze.features.chatbot.ChatbotViewModel
import com.epfl.esl.chaze.features.home.FavoriteBasketDetailsScreen
import com.epfl.esl.chaze.features.home.HomeScreen
import com.epfl.esl.chaze.features.home.PastBasketDetailsScreen
import com.epfl.esl.chaze.features.localization.LocalizationScreen
import com.epfl.esl.chaze.features.localization.LocalizationViewModel
import com.epfl.esl.chaze.features.login.LoginProfileContent
import com.epfl.esl.chaze.features.search.SearchScreen
import com.epfl.esl.chaze.features.shopping.ShoppingListScreen
import com.epfl.esl.chaze.features.user.AboutScreen
import com.epfl.esl.chaze.features.user.SettingsScreen
import com.epfl.esl.chaze.features.user.SettingsViewModel
import com.epfl.esl.chaze.features.user.UserScreen
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.PutDataMapRequest
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Main navigation graph for the app.
 * Contains all composable destinations and their configurations.
 */
@Composable
fun AppNavGraph(
    navController: NavHostController,
    dataClient: DataClient,
    modifier: Modifier = Modifier,
    onUsernameChange: (String) -> Unit,
    onUriStringChange: (String) -> Unit,
    username: String,
    uriString: String,
    shoppingListItems: List<BasketItemModel>,
    onShoppingListItemsChange: (List<BasketItemModel>) -> Unit,
    onShoppingActiveChange: (Boolean) -> Unit,
    selectedStoreName: String,
    onSelectedStoreNameChange: (String) -> Unit,
    selectedRetailerId: String,
    onSelectedRetailerIdChange: (String) -> Unit,
    onOpenMapsIntent: (Intent) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    NavHost(
        navController = navController,
        startDestination = Screen.Login.route,
        modifier = modifier
    ) {
        // Login Screen
        composable(Screen.Login.route) {
            LoginProfileContent(
                onSignInSuccess = { name, imageUri ->
                    val encodedName = if (name.isNotBlank()) {
                        URLEncoder.encode(name, StandardCharsets.UTF_8.toString())
                    } else {
                        "User"
                    }
                    val encodedUri = if (imageUri.isNotBlank()) {
                        URLEncoder.encode(imageUri, StandardCharsets.UTF_8.toString())
                    } else {
                        "no_image"
                    }
                    navController.navigate(Screen.Home.createRoute(encodedName, encodedUri)) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        // Home Screen
        composable(Screen.Home.route) { backStackEntry ->
            val decodedUsername = URLDecoder.decode(
                backStackEntry.arguments?.getString(NavArgs.USERNAME) ?: "",
                StandardCharsets.UTF_8.toString()
            )
            onUsernameChange(decodedUsername)
            
            val imageUriArg = backStackEntry.arguments?.getString(NavArgs.IMAGE_URI) ?: ""
            onUriStringChange(imageUriArg)

            HomeScreen(
                onPastBasketClick = { basketId ->
                    navController.navigate(Screen.PastBasket.createRoute(basketId))
                },
                onFavoriteBasketClick = { basketId ->
                    navController.navigate(Screen.FavoriteBasket.createRoute(basketId))
                }
            )
        }

        // Search Screen
        composable(Screen.Search.route) {
            SearchScreen(navController = navController)
        }

        // Localization Screen
        composable(Screen.Localization.route) {
            LocalizationScreen()
        }

        // Current Cart Screen
        composable(Screen.CurrentCart.route) { backStackEntry ->
            val decodedUsername = URLDecoder.decode(
                backStackEntry.arguments?.getString(NavArgs.USERNAME) ?: "",
                StandardCharsets.UTF_8.toString()
            )
            onUsernameChange(decodedUsername)
            
            val imageUriArg = backStackEntry.arguments?.getString(NavArgs.IMAGE_URI) ?: ""
            onUriStringChange(imageUriArg)

            val viewModel: CurrentBasketViewModel = viewModel()

            CurrentBasketScreen(
                currentBasketViewModel = viewModel,
                onStartBuyingClick = { navController.navigate(Screen.Search.route) },
                onStartShoppingClick = { optimizedItems ->
                    onShoppingListItemsChange(optimizedItems)
                    onSelectedStoreNameChange(viewModel.selectedStore.value?.name ?: "Unknown Store")
                    onSelectedRetailerIdChange(viewModel.selectedStore.value?.retailerId ?: "")
                    onShoppingActiveChange(true)
                    navController.navigate(Screen.ShoppingList.route)
                },
                onOpenMapsClick = { _,_ ->
                    val gmmIntentUri = "geo:0,0?q=supermarkets".toUri()
                    val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                    mapIntent.setPackage("com.google.android.apps.maps")
                    onOpenMapsIntent(mapIntent)
                }
            )
        }

        // Past Basket Details Screen
        composable(Screen.PastBasket.route) { backStackEntry ->
            val basketId = backStackEntry.arguments?.getString(NavArgs.BASKET_ID) ?: return@composable
            PastBasketDetailsScreen(
                basketId = basketId,
                onBack = { navController.popBackStack() }
            )
        }

        // Favorite Basket Details Screen
        composable(Screen.FavoriteBasket.route) { backStackEntry ->
            val basketId = backStackEntry.arguments?.getString(NavArgs.BASKET_ID) ?: return@composable
            FavoriteBasketDetailsScreen(
                basketId = basketId,
                onBack = { navController.popBackStack() },
                onItemsAdded = {
                    val safeUri = if (uriString.isNotEmpty()) {
                        URLEncoder.encode(uriString, StandardCharsets.UTF_8.toString())
                    } else {
                        "null"
                    }
                    navController.navigate(Screen.CurrentCart.createRoute(username, safeUri)) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                    }
                }
            )
        }

        // Shopping List Screen
        composable(Screen.ShoppingList.route) {
            ShoppingListScreen(
                dataClient = dataClient,
                initialItems = shoppingListItems,
                onEndShoppingClick = { items ->
                    onShoppingActiveChange(false)
                    
                    // Clear watch list (Standby mode)
                    val putDataReq = PutDataMapRequest.create("/shopping_list").run {
                        dataMap.putStringArrayList("items", ArrayList())
                        dataMap.putLong("timestamp", System.currentTimeMillis())
                        asPutDataRequest()
                    }
                    dataClient.putDataItem(putDataReq)

                    // Calculate and save past basket
                    coroutineScope.launch {
                        savePastBasketAndNavigate(
                            items = items,
                            selectedStoreName = selectedStoreName,
                            selectedRetailerId = selectedRetailerId,
                            username = username,
                            uriString = uriString,
                            navController = navController
                        )
                    }
                }
            )
        }

        // Chatbot Screen
        composable(Screen.Chatbot.route) {
            val chatbotViewModel: ChatbotViewModel = viewModel()
            val localizationViewModel: LocalizationViewModel = viewModel()
            val settingsViewModel: SettingsViewModel = viewModel()
            val chosenAddress by localizationViewModel.chosenAddress.collectAsState()
            val userTransportMode by settingsViewModel.transportMode.collectAsState()
            val userMaxDistanceKm by settingsViewModel.maxDistanceKm.collectAsState()

            ChatbotScreen(
                chatbotViewModel = chatbotViewModel,
                userLatitude = chosenAddress?.latitude ?: 0.0,
                userLongitude = chosenAddress?.longitude ?: 0.0,
                transportMode = userTransportMode,
                maxDistanceKm = userMaxDistanceKm,
                onStartShoppingClick = { optimizedItems ->
                    onShoppingListItemsChange(optimizedItems)
                    onShoppingActiveChange(true)
                    navController.navigate(Screen.ShoppingList.route)
                },
                onOpenMapsClick = { store, mode ->
                    val gmmIntentUri = "google.navigation:q=${store.latitude},${store.longitude}&mode=${
                        when (mode) {
                            TransportMode.WALKING -> "w"
                            TransportMode.BICYCLING -> "b"
                            TransportMode.TRANSIT -> "r"
                            TransportMode.DRIVING -> "d"
                        }
                    }".toUri()
                    val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                    mapIntent.setPackage("com.google.android.apps.maps")
                    onOpenMapsIntent(mapIntent)
                }
            )
        }

        // Analytics Screen
        composable(Screen.Analytics.route) { backStackEntry ->
            val decodedUsername = URLDecoder.decode(
                backStackEntry.arguments?.getString(NavArgs.USERNAME) ?: "",
                StandardCharsets.UTF_8.toString()
            )
            onUsernameChange(decodedUsername)
            
            val imageUriArg = backStackEntry.arguments?.getString(NavArgs.IMAGE_URI) ?: ""
            onUriStringChange(imageUriArg)
            
            AnalyticsScreen()
        }

        // Settings Screen
        composable(Screen.Settings.route) {
            SettingsScreen(
                onUserSettingsClick = {
                    val safeUri = if (uriString.isNotEmpty()) {
                        URLEncoder.encode(uriString, StandardCharsets.UTF_8.toString())
                    } else {
                        "null"
                    }
                    navController.navigate(Screen.Profile.createRoute(username, safeUri))
                },
                onAboutClick = { navController.navigate(Screen.About.route) },
                onLogout = {
                    com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        // Profile Screen
        composable(Screen.Profile.route) { backStackEntry ->
            val decodedUsername = URLDecoder.decode(
                backStackEntry.arguments?.getString(NavArgs.USERNAME) ?: "",
                StandardCharsets.UTF_8.toString()
            )
            onUsernameChange(decodedUsername)
            
            val imageUriArg = backStackEntry.arguments?.getString(NavArgs.IMAGE_URI) ?: ""
            onUriStringChange(imageUriArg)
            
            UserScreen(onBack = { navController.popBackStack() })
        }

        // About Screen
        composable(Screen.About.route) {
            AboutScreen(onBack = { navController.popBackStack() })
        }

    }
}

/**
 * Helper function to save past basket and navigate back to cart
 */
private suspend fun savePastBasketAndNavigate(
    items: List<BasketItemModel>,
    selectedStoreName: String,
    selectedRetailerId: String,
    username: String,
    uriString: String,
    navController: NavHostController
) {
    val totalPriceAfterDiscount = items.sumOf { it.price * it.quantity }
    val totalDiscount = items.sumOf { item ->
        val discountPerUnit = item.originalPrice - item.price
        discountPerUnit * item.quantity
    }
    val totalPriceBeforeDiscount = String.format("%.2f", totalPriceAfterDiscount + totalDiscount).toDouble()

    Log.d("AppNavGraph", "Total after discount: $totalPriceAfterDiscount")
    Log.d("AppNavGraph", "Total discount: $totalDiscount")
    Log.d("AppNavGraph", "Total before discount: $totalPriceBeforeDiscount")

    val timestamp = System.currentTimeMillis()
    val dateFormat = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
    val dateString = dateFormat.format(java.util.Date(timestamp))
    val basketName = "Purchase $dateString"

    val pastBasketRepository = PastBasketRepository()
    pastBasketRepository.savePastBasket(
        basketName = basketName,
        basketItems = items,
        store = selectedStoreName,
        retailer = selectedRetailerId,
        totalPrice = totalPriceAfterDiscount,
        totalPriceBeforeDiscount = totalPriceBeforeDiscount,
        onSuccess = {
            Log.d("AppNavGraph", "Past basket saved successfully")
        },
        onError = { error ->
            Log.e("AppNavGraph", "Error saving past basket: $error")
        }
    )

    val safeUri = if (uriString.isNotEmpty()) {
        URLEncoder.encode(uriString, StandardCharsets.UTF_8.toString())
    } else {
        "null"
    }
    
    navController.navigate(Screen.CurrentCart.createRoute(username, safeUri)) {
        popUpTo(navController.graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
