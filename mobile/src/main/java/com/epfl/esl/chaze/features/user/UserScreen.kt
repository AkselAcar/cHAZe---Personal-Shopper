package com.epfl.esl.chaze.features.user

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DirectionsTransit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.epfl.esl.chaze.data.model.TransportMode
import com.epfl.esl.chaze.features.localization.LocalizationViewModel

/**
 * User profile and settings screen with location-aware store discovery.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserScreen(
    userViewModel: SettingsViewModel = viewModel(),
    localizationViewModel: LocalizationViewModel = viewModel(),
    onBack: () -> Unit
) {
    val username by userViewModel.username.observeAsState("")
    val transportMode by userViewModel.transportMode.collectAsStateWithLifecycle()
    val maxDistanceKm by userViewModel.maxDistanceKm.collectAsStateWithLifecycle()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Navigation header: back button + screen title
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
                Text(
                    text = "User Profile",
                    style = MaterialTheme.typography.headlineMedium
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Profile display card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Circular avatar with person icon
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .background(
                                    MaterialTheme.colorScheme.primary,
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Profile",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Username display with loading state fallback
                        Text(
                            text = username.ifEmpty { "Loading..." },
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Preferences card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Preferences",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(4.dp))
                        
                        // Transport mode dropdown with icon visualization
                        var isTransportExpanded by remember { mutableStateOf(false) }

                        ExposedDropdownMenuBox(
                            expanded = isTransportExpanded,
                            onExpandedChange = { isTransportExpanded = it },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = transportMode.displayName,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Default Transport Mode") },
                                leadingIcon = {
                                    // Icon reflects current selection for visual feedback
                                    Icon(
                                        imageVector = when (transportMode) {
                                            TransportMode.DRIVING -> Icons.Default.DirectionsCar
                                            TransportMode.WALKING -> Icons.AutoMirrored.Filled.DirectionsWalk
                                            TransportMode.BICYCLING -> Icons.AutoMirrored.Filled.DirectionsBike
                                            TransportMode.TRANSIT -> Icons.Default.DirectionsTransit
                                        },
                                        contentDescription = null
                                    )
                                },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isTransportExpanded) },
                                modifier = Modifier.menuAnchor().fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )

                            ExposedDropdownMenu(
                                expanded = isTransportExpanded,
                                onDismissRequest = { isTransportExpanded = false }
                            ) {
                                TransportMode.entries.forEach { mode ->
                                    DropdownMenuItem(
                                        text = { 
                                            // Menu items with icons for consistency and clarity
                                            Row(verticalAlignment = Alignment.CenterVertically) {
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
                                                Text(mode.displayName)
                                            }
                                        },
                                        onClick = {
                                            userViewModel.updateTransportMode(mode)
                                            isTransportExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        // Maximum distance input with validation
                        // Accepts 0.1 to 100 km; triggers store fetch when changed
                        OutlinedTextField(
                            value = maxDistanceKm.toString(),
                            onValueChange = { newValue ->
                                newValue.toDoubleOrNull()?.let { distance ->
                                    if (distance >= 0.1 && distance <= 100) {
                                        userViewModel.updateMaxDistance(distance)
                                    }
                                }
                            },
                            label = { Text("Maximum Distance (km)") },
                            supportingText = { Text("Search radius: 0.1 to 100 km") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }

                // Map visualization card
                // Shows nearby stores within search radius using user's chosen location
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Text(
                            text = "Store Search Area",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        
                        // Embedded map component showing search radius and store locations
                        // Uses chosenAddress from LocalizationViewModel for location awareness
                        MapVisualizationComponent(
                            viewModel = userViewModel,
                            localizationViewModel = localizationViewModel,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}
