package com.epfl.esl.chaze.features.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.epfl.esl.chaze.features.basket.BasketItem

/**
 * Dedicated screen for displaying favorite basket details.
 * This screen is for product types ready for optimization.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoriteBasketDetailsScreen(
    basketId: String,
    onBack: () -> Unit,
    onItemsAdded: () -> Unit,
    viewModel: FavoriteBasketDetailsViewModel = viewModel()
) {
    // triggers loading of basket when basketId changes.
    LaunchedEffect(basketId) {
        viewModel.loadBasket(basketId)
    }

    val basket by viewModel.basket.observeAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = basket?.name ?: "Favorite Basket") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Delete Basket Button (Red)
                Button(
                    onClick = {
                        viewModel.deleteBasket()
                        onBack()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("Delete")
                }

                // Add to Cart Button (Green)
                Button(
                    onClick = {
                        viewModel.addItemsToCurrentBasket()
                        onItemsAdded()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50) // Green
                    )
                ) {
                    Text("Add to Cart")
                }

            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            if (basket == null) {
                Text("Basket not found.")
            } else {
                // Display items without prices using BasketItem
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(basket!!.items) { item ->
                        BasketItem(item = item)
                    }
                }
            }
        }
    }
}
