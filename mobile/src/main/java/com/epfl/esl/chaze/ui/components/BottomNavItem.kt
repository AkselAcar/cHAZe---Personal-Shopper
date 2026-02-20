package com.epfl.esl.chaze.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import java.net.URLEncoder
import java.nio.charset.StandardCharsets


@Composable
fun ChazeBottomNavigationBar(
    navController: NavController,
    currentRoute: String?,
    username: String,
    uriString: String,
    isShoppingActive: Boolean = false
) {
    // List of navigation items
    val navItems = listOf(
        BottomNavItem.Home,
        if (isShoppingActive) BottomNavItem.List else BottomNavItem.Cart, // Change based on shopping status
        BottomNavItem.Chatbot,
        BottomNavItem.Analytics,
        BottomNavItem.Settings
    )
    val cornerShape = RoundedCornerShape(25.dp)

    // Get navigation bar height
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // Bottom bar
    // White background with shadow
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = navBarPadding + 16.dp)
            .height(60.dp)
            .shadow(elevation = 8.dp, shape = cornerShape, clip = false)
            .clip(cornerShape)
            .background(Color.White)
    ) {
        // Row + Column structure replaces NavigationBar to allow full control
        Row(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            navItems.forEach { item ->
                val isSelected = currentRoute?.startsWith(item.route) == true
                val contentColor = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable {
                            navController.navigate(item.buildRoute(username, uriString))
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        tint = contentColor
                    )
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor
                    )
                }
            }
        }
    }
}

// A sealed class to represent the different navigation destinations
sealed class BottomNavItem(
    val route: String,
    val icon: ImageVector,
    val label: String
) {
    // Construct navigation route with URL encoding
    fun buildRoute(username: String, imageUri: String): String {
        val encodedUsername = if (username.isNotBlank()) {
            URLEncoder.encode(username, StandardCharsets.UTF_8.toString())
        } else {
            "User"
        }
        val encodedUri = if (imageUri.isNotBlank()) {
            URLEncoder.encode(imageUri, StandardCharsets.UTF_8.toString())
        } else {
            "no_image"
        }

        return when (this) {
            Home, Cart, Analytics -> "$route/$encodedUsername/$encodedUri"
            Chatbot, Settings, List -> route // Chatbot, Settings, List don't need arguments in the main nav
        }
    }

    object Home : BottomNavItem("home", Icons.Filled.Home, "Home")
    object Cart : BottomNavItem("currentCart", Icons.Filled.ShoppingCart, "Cart")
    object List : BottomNavItem("shoppingList", Icons.AutoMirrored.Filled.List, "List")
    object Chatbot : BottomNavItem("chatbot", Icons.Filled.SmartToy, "cHAZeBot")
    object Analytics : BottomNavItem("analytics", Icons.Filled.Analytics, "Analytics")
    object Settings : BottomNavItem("settings", Icons.Filled.Settings, "Settings")
}
