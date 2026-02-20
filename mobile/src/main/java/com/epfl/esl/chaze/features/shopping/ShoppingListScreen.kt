package com.epfl.esl.chaze.features.shopping

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.epfl.esl.chaze.data.model.BasketItemModel
import coil.compose.AsyncImage
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest

@Composable
fun ShoppingListScreen(
    dataClient: DataClient,
    initialItems: List<BasketItemModel> = emptyList(),
    onEndShoppingClick: (List<BasketItemModel>) -> Unit = {}
) {
    // Local state for ticking items off
    val shoppingItems = remember { mutableStateListOf<ShoppingListItemState>() }
    
    // Initialize state with passed items, preserving checked state if items haven't changed
    LaunchedEffect(initialItems) {
        if (shoppingItems.isEmpty() || shoppingItems.map { it.item.id } != initialItems.map { it.id }) {
            // Only reset if list is empty or items have changed
            shoppingItems.clear()
            initialItems.forEach { shoppingItems.add(ShoppingListItemState(it, false, it.quantity)) }
        }
        // If items are the same, preserve existing state
    }

    // Sync data to Wear OS
    fun syncToWear() {
        val putDataReq = PutDataMapRequest.create("/shopping_list").run {
            val itemsAsString = shoppingItems.map { 
                val displayName = it.item.productName.ifEmpty { it.item.name } 
                "${it.item.id}|$displayName|${it.isChecked}" 
            }
            dataMap.putStringArrayList("items", ArrayList(itemsAsString))
            dataMap.putLong("timestamp", System.currentTimeMillis())
            asPutDataRequest()
        }
        dataClient.putDataItem(putDataReq)
    }

    // Initial sync
    LaunchedEffect(Unit) {
        syncToWear()
    }

    // Listen for updates from Wear OS
    DisposableEffect(Unit) {
        val listener = DataClient.OnDataChangedListener { dataEvents ->
            for (event in dataEvents) {
                if (event.type == DataEvent.TYPE_CHANGED) {
                    val path = event.dataItem.uri.path
                    if (path == "/shopping_list_update") {
                        val dataMapItem = DataMapItem.fromDataItem(event.dataItem)
                        val itemsList = dataMapItem.dataMap.getStringArrayList("items") ?: emptyList<String>()
                        
                        itemsList.forEach { itemString ->
                            val parts = itemString.split("|")
                            if (parts.size >= 3) {
                                val id = parts[0]
                                val isChecked = parts[2].toBoolean()
                                val index = shoppingItems.indexOfFirst { it.item.id == id }
                                if (index != -1 && shoppingItems[index].isChecked != isChecked) {
                                    shoppingItems[index] = shoppingItems[index].copy(isChecked = isChecked)
                                }
                            }
                        }
                    }
                }
            }
        }
        dataClient.addListener(listener)
        onDispose {
            dataClient.removeListener(listener)
        }
    }
//  Frontend components
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Shopping List",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LazyColumn(
            modifier = Modifier.weight(1f)
        ) {
            items(shoppingItems) { itemState ->
                ShoppingListItem(
                    item = itemState.item,
                    isChecked = itemState.isChecked,
                    quantity = itemState.takenQuantity,
                    onCheckedChange = { isChecked ->
                        val index = shoppingItems.indexOf(itemState)
                        if (index != -1) {
                            shoppingItems[index] = itemState.copy(isChecked = isChecked)
                            syncToWear()
                        }
                    },
                    onQuantityChange = { newQuantity ->
                        val index = shoppingItems.indexOf(itemState)
                        if (index != -1) {
                            shoppingItems[index] = itemState.copy(takenQuantity = newQuantity)
                        }
                    }
                )
                HorizontalDivider()
            }
        }

        Spacer(modifier = Modifier.padding(vertical = 8.dp))

        Button(
            onClick = { 
                // Update each item's quantity to match the actual quantities taken by the user
                val itemsWithActualQuantities = shoppingItems.map { itemState ->
                    itemState.item.copy(quantity = itemState.takenQuantity)
                }
                onEndShoppingClick(itemsWithActualQuantities)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("End Shopping")
        }
    }
}

data class ShoppingListItemState(
    val item: BasketItemModel,
    val isChecked: Boolean,
    val takenQuantity: Double
)

// Each item has its own composable
@Composable
fun ShoppingListItem(
    item: BasketItemModel,
    isChecked: Boolean,
    quantity: Double,
    onCheckedChange: (Boolean) -> Unit,
    onQuantityChange: (Double) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!isChecked) }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isChecked,
            onCheckedChange = onCheckedChange
        )
        Spacer(modifier = Modifier.width(12.dp))
        
        // Product Image
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (!item.imageUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = item.imageUrl,
                    contentDescription = item.name,
                    modifier = Modifier.size(56.dp),
                    contentScale = ContentScale.Crop
                )
            } else if (item.imageRes != 0) {
                Image(
                    painter = painterResource(id = item.imageRes),
                    contentDescription = item.name,
                    modifier = Modifier.size(56.dp),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Default.ShoppingCart,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            // Display product name with unit
            val displayName = item.productName.ifEmpty { item.name }
            val unitText = if (item.unit.isNotEmpty()) " (${item.unit})" else ""
            Text(
                text = displayName + unitText,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (isChecked) TextDecoration.LineThrough else null
            )
            if (item.description.isNotEmpty()) {
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        // Use raw text state for seamless editing
        var textValue by remember(quantity) { 
            mutableStateOf(
                if (quantity > 0) {
                    if (quantity % 1.0 == 0.0) quantity.toInt().toString() else quantity.toString()
                } else ""
            ) 
        }
        
        OutlinedTextField(
            value = textValue,
            onValueChange = { newText ->
                // Allow digits, decimal point, and comma
                val filtered = newText.filter { char -> char.isDigit() || char == '.' || char == ',' }
                // Replace comma with period
                val normalized = filtered.replace(",", ".")
                // Ensure only one decimal point
                if (normalized.count { it == '.' } <= 1) {
                    textValue = normalized
                    // Parse to double if valid, otherwise keep 0
                    val parsed = normalized.toDoubleOrNull() ?: 0.0
                    onQuantityChange(parsed)
                }
            },
            label = { Text("Qty") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.width(80.dp),
            singleLine = true
        )
    }
}
