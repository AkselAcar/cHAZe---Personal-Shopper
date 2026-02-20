package com.epfl.esl.chaze

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.epfl.esl.chaze.data.model.BasketItemModel
import com.epfl.esl.chaze.data.repository.NotificationRepository
import com.epfl.esl.chaze.navigation.AppNavGraph
import com.epfl.esl.chaze.notifications.DiscountNotificationWorker
import com.epfl.esl.chaze.ui.components.ChazeBottomNavigationBar
import com.epfl.esl.chaze.ui.components.HomeHeader
import com.epfl.esl.chaze.ui.theme.CHAZeTheme
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private lateinit var dataClient: DataClient
    private var username by mutableStateOf("")
    private var imageUri by mutableStateOf<Uri?>(null)
    private var uriString by mutableStateOf("")

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission granted, notifications will work
        } else {
            // Permission denied, notifications won't be shown
        }
    }

    private val requestLocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (fineLocationGranted || coarseLocationGranted) {
            Log.d("MainActivity", "Location permission granted")
        } else {
            Log.w("MainActivity", "Location permission denied - map features may not work")
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        dataClient = Wearable.getDataClient(this)

        // Request notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Request location permissions for map functionality
        val locationPermissionsNeeded = mutableListOf<String>()
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            locationPermissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            locationPermissionsNeeded.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (locationPermissionsNeeded.isNotEmpty()) {
            requestLocationPermissionLauncher.launch(locationPermissionsNeeded.toTypedArray())
        }

        // Schedule hourly discount notifications
        scheduleHourlyDiscountNotifications()

        setContent {
            CHAZeTheme {
                val navController = rememberNavController()
                var isNotificationDrawerOpen by remember { mutableStateOf(false) }
                // State to track if the user is in active shopping mode
                var isShoppingActive by remember { mutableStateOf(false) }
                // State to hold the optimized shopping list items
                var shoppingListItems by remember { mutableStateOf<List<BasketItemModel>>(emptyList()) }
                // State to hold selected store information for past basket
                var selectedStoreName by remember { mutableStateOf("") }
                var selectedRetailerId by remember { mutableStateOf("") }

                val coroutineScope = rememberCoroutineScope()

                // Collect notifications from Firestore
                val notifications by NotificationRepository.getNotifications().collectAsState(initial = emptyList())

                Surface {
                    Box(modifier = Modifier.fillMaxSize()) {
                        val navBackStackEntry by navController.currentBackStackEntryAsState()
                        val currentRoute = navBackStackEntry?.destination?.route

                        Scaffold(
                            topBar = {
                                if (currentRoute?.startsWith("home") == true ||
                                    currentRoute?.startsWith("currentCart") == true ||
                                    currentRoute == "chatbot") {
                                    HomeHeader(
                                        onNotificationClick = { isNotificationDrawerOpen = true },
                                        onSearchClick = { navController.navigate("search") },
                                        onLocationClick = { navController.navigate("localization") }
                                    )
                                }
                            },
                            bottomBar = {
                                if (currentRoute != "login" && currentRoute != "search" && currentRoute?.startsWith("pastBasket") != true) {
                                    ChazeBottomNavigationBar(
                                        navController = navController,
                                        currentRoute = currentRoute,
                                        username = username,
                                        uriString = uriString,
                                        isShoppingActive = isShoppingActive
                                    )
                                }
                            },
                            content = {
                                AppNavGraph(
                                    navController = navController,
                                    dataClient = dataClient,
                                    modifier = Modifier.padding(it),
                                    onUsernameChange = { username = it },
                                    onUriStringChange = { uriString = it },
                                    username = username,
                                    uriString = uriString,
                                    shoppingListItems = shoppingListItems,
                                    onShoppingListItemsChange = { shoppingListItems = it },
                                    onShoppingActiveChange = { isShoppingActive = it },
                                    selectedStoreName = selectedStoreName,
                                    onSelectedStoreNameChange = { selectedStoreName = it },
                                    selectedRetailerId = selectedRetailerId,
                                    onSelectedRetailerIdChange = { selectedRetailerId = it },
                                    onOpenMapsIntent = { intent ->
                                        if (intent.resolveActivity(packageManager) != null) {
                                            startActivity(intent)
                                        }
                                    }
                                )
                            }
                        )

                        if (isNotificationDrawerOpen) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.5f))
                                    .clickable { isNotificationDrawerOpen = false }
                            )
                        }

                        AnimatedVisibility(
                            visible = isNotificationDrawerOpen,
                            enter = slideInVertically(initialOffsetY = { -it }),
                            exit = slideOutVertically(targetOffsetY = { -it })
                        ) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shadowElevation = 12.dp,
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
                            ) {
                                Column(modifier = Modifier.padding(vertical = 16.dp)) {
                                    // Header
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 20.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Notifications,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(28.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = "Notifications",
                                            style = MaterialTheme.typography.headlineSmall,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(onClick = { isNotificationDrawerOpen = false }) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Close",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    // Notifications list
                                    if (notifications.isNotEmpty()) {
                                        val scope = rememberCoroutineScope()
                                        LazyColumn(
                                            modifier = Modifier.padding(horizontal = 16.dp)
                                        ) {
                                            items(
                                                count = notifications.size,
                                                key = { index -> notifications[index].id }
                                            ) { index ->
                                                val notification = notifications[index]
                                                val dismissState = rememberSwipeToDismissBoxState(
                                                    confirmValueChange = { dismissValue ->
                                                        if (dismissValue == SwipeToDismissBoxValue.EndToStart) {
                                                            scope.launch {
                                                                NotificationRepository.deleteNotification(notification.id)
                                                            }
                                                            true
                                                        } else {
                                                            false
                                                        }
                                                    }
                                                )

                                                SwipeToDismissBox(
                                                    state = dismissState,
                                                    backgroundContent = {
                                                        // Delete background - only show when swiping
                                                        val color = if (dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                                                            MaterialTheme.colorScheme.errorContainer
                                                        } else {
                                                            MaterialTheme.colorScheme.surface
                                                        }
                                                        
                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxSize()
                                                                .background(color)
                                                                .padding(horizontal = 20.dp),
                                                            contentAlignment = Alignment.CenterEnd
                                                        ) {
                                                            if (dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                                                                Icon(
                                                                    imageVector = Icons.Default.Delete,
                                                                    contentDescription = "Delete",
                                                                    tint = MaterialTheme.colorScheme.error
                                                                )
                                                            }
                                                        }
                                                    },
                                                    enableDismissFromStartToEnd = false,
                                                    enableDismissFromEndToStart = true
                                                ) {
                                                    NotificationItem(
                                                        title = notification.title,
                                                        message = notification.message,
                                                        timestamp = formatTimestamp(notification.timestamp),
                                                        isUnread = notification.isUnread,
                                                        onClick = {
                                                            if (notification.isUnread) {
                                                                scope.launch {
                                                                    NotificationRepository.markAsRead(notification.id)
                                                                }
                                                            }
                                                        }
                                                    )
                                                }
                                                if (index < notifications.size - 1) {
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                }
                                            }
                                        }
                                    } else {
                                        // Empty state
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 48.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.NotificationsNone,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(64.dp)
                                            )
                                            Spacer(modifier = Modifier.height(16.dp))
                                            Text(
                                                text = "No notifications",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = "You're all caught up!",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun scheduleHourlyDiscountNotifications() {
        val workRequest = PeriodicWorkRequestBuilder<DiscountNotificationWorker>(
            1, TimeUnit.HOURS,
            15, TimeUnit.MINUTES // Flex interval - allows execution within 15 minutes of the target time
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "hourly_discount_notifications",
            ExistingPeriodicWorkPolicy.UPDATE, // Changed to UPDATE to ensure new schedule is applied
            workRequest
        )

        Log.d("MainActivity", "Scheduled hourly discount notifications with 1 hour interval")
    }
}

@Composable
fun NotificationItem(
    title: String,
    message: String,
    timestamp: String,
    isUnread: Boolean = false,
    onClick: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isUnread) 
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)
            else 
                Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ShoppingBag,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Content
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (isUnread) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    if (isUnread) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = timestamp,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

/**
 * Format timestamp to relative time string
 */
fun formatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < 60_000 -> "Just now"
        diff < 3600_000 -> "${diff / 60_000}m ago"
        diff < 86400_000 -> "${diff / 3600_000}h ago"
        diff < 604800_000 -> "${diff / 86400_000}d ago"
        else -> "${diff / 604800_000}w ago"
    }
}
