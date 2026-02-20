package com.epfl.esl.chaze.navigation

/**
 * Sealed class defining all navigation routes in the app.
 * This provides type-safe navigation and centralizes route definitions.
 */
sealed class Screen(val route: String) {
    // Authentication
    data object Login : Screen("login")
    
    // Main screens with parameters
    data object Home : Screen("home/{username}/{imageUri}") {
        fun createRoute(username: String, imageUri: String) = "home/$username/$imageUri"
    }
    
    data object CurrentCart : Screen("currentCart/{username}/{imageUri}") {
        fun createRoute(username: String, imageUri: String) = "currentCart/$username/$imageUri"
    }
    
    data object Analytics : Screen("analytics/{username}/{imageUri}") {
        fun createRoute(username: String, imageUri: String) = "analytics/$username/$imageUri"
    }
    
    data object Profile : Screen("profile/{username}/{imageUri}") {
        fun createRoute(username: String, imageUri: String) = "profile/$username/$imageUri"
    }
    
    // Detail screens with parameters
    data object PastBasket : Screen("pastBasket/{basketId}") {
        fun createRoute(basketId: String) = "pastBasket/$basketId"
    }
    
    data object FavoriteBasket : Screen("favoriteBasket/{basketId}") {
        fun createRoute(basketId: String) = "favoriteBasket/$basketId"
    }
    
    // Simple screens without parameters
    data object Search : Screen("search")
    data object Localization : Screen("localization")
    data object ShoppingList : Screen("shoppingList")
    data object Chatbot : Screen("chatbot")
    data object Settings : Screen("settings")
    data object About : Screen("about")
    data object CreateBasket : Screen("createBasket")
}

/**
 * Navigation argument keys
 */
object NavArgs {
    const val USERNAME = "username"
    const val IMAGE_URI = "imageUri"
    const val BASKET_ID = "basketId"
}
