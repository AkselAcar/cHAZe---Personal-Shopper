package com.epfl.esl.chaze.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Custom header component for HomeScreen with full control over layout and theming
@Composable
fun HomeHeader(
    modifier: Modifier = Modifier,
    onNotificationClick: () -> Unit,
    onSearchClick: () -> Unit,
    onLocationClick: () -> Unit
) {
    val cornerShape = RoundedCornerShape(25.dp)

    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Row(
        modifier = modifier
            .fillMaxWidth()
            // Use dynamic status bar height
            .padding(start = 16.dp, end = 16.dp, top = statusBarPadding + 16.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
//      Localization button
        Box(
            modifier = Modifier
                .weight(1f)
                .height(35.dp)
                .shadow(elevation = 4.dp, shape = cornerShape, clip = false)
                .clip(cornerShape)
                .background(Color.White)
                .clickable(onClick = onLocationClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = "Location",
                tint = MaterialTheme.colorScheme.primary
            )
        }

//      Search bar
        Row(
            modifier = Modifier
                .weight(3f)
                .height(35.dp)
                .shadow(elevation = 4.dp, shape = cornerShape, clip = false)
                .clip(cornerShape)
                .background(Color.White)
                .clickable(onClick = onSearchClick)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Search",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }

//      Notification button
        Box(
            modifier = Modifier
                .weight(1f)
                .height(35.dp)
                .shadow(elevation = 4.dp, shape = cornerShape, clip = false)
                .clip(cornerShape)
                .background(Color.White)
                .clickable(onClick = onNotificationClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = "Notifications",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
