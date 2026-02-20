package com.epfl.esl.chaze.presentation

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.ToggleChip
import androidx.wear.tooling.preview.devices.WearDevices
import com.epfl.esl.chaze.presentation.theme.CHAZeTheme
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable

data class WearBasketItem(
    val id: String,
    val name: String,
    val isChecked: Boolean = false
)

data class WearNotification(
    val title: String,
    val message: String,
    val product: String,
    val percentage: Int,
    val price: Double,
    val timestamp: Long
)

class MainActivity : ComponentActivity(), DataClient.OnDataChangedListener {

    private val _shoppingList = mutableStateListOf<WearBasketItem>()
    private val _latestNotification = mutableStateOf<WearNotification?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(android.R.style.Theme_DeviceDefault)

        setContent {
//          Main app on smart watch
            WearApp(
                shoppingList = _shoppingList,
                latestNotification = _latestNotification.value,
                onItemToggle = { item ->
                    val index = _shoppingList.indexOfFirst { it.id == item.id }
                    if (index != -1) {
                        _shoppingList[index] = item.copy(isChecked = !item.isChecked)
                        syncDataToPhone()
                    }
                },
                onDismissNotification = {
                    _latestNotification.value = null
                }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        Wearable.getDataClient(this).addListener(this)
    }

    override fun onPause() {
        super.onPause()
        Wearable.getDataClient(this).removeListener(this)
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type == DataEvent.TYPE_CHANGED) {
                val path = event.dataItem.uri.path
                when (path) {
                    "/shopping_list" -> {
                        val dataMapItem = DataMapItem.fromDataItem(event.dataItem)
                        val itemsList = dataMapItem.dataMap.getStringArrayList("items") ?: emptyList<String>()
                        
                        val newItems = itemsList.map {
                            val parts = it.split("|")
                            if (parts.size >= 3) {
                                WearBasketItem(parts[0], parts[1], parts[2].toBoolean())
                            } else {
                                WearBasketItem(parts[0], parts[0]) // Fallback
                            }
                        }
                        
                        _shoppingList.clear()
                        _shoppingList.addAll(newItems)
                    }
                    "/discount_notification" -> {
                        val dataMapItem = DataMapItem.fromDataItem(event.dataItem)
                        val notification = WearNotification(
                            title = dataMapItem.dataMap.getString("title") ?: "",
                            message = dataMapItem.dataMap.getString("message") ?: "",
                            product = dataMapItem.dataMap.getString("product") ?: "",
                            percentage = dataMapItem.dataMap.getInt("percentage"),
                            price = dataMapItem.dataMap.getDouble("price"),
                            timestamp = dataMapItem.dataMap.getLong("timestamp")
                        )
                        _latestNotification.value = notification
                        Log.d("WearMainActivity", "Received notification: ${notification.message}")
                    }
                }
            }
        }
    }

    private fun syncDataToPhone() {
        val putDataReq = PutDataMapRequest.create("/shopping_list_update").run {
            val itemsAsString = _shoppingList.map { "${it.id}|${it.name}|${it.isChecked}" }
            dataMap.putStringArrayList("items", ArrayList(itemsAsString))
            dataMap.putLong("timestamp", System.currentTimeMillis())
            asPutDataRequest()
        }
        Wearable.getDataClient(this).putDataItem(putDataReq)
    }
}

@Composable
fun WearApp(
    shoppingList: List<WearBasketItem>,
    latestNotification: WearNotification?,
    onItemToggle: (WearBasketItem) -> Unit,
    onDismissNotification: () -> Unit
) {
    CHAZeTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                timeText = { TimeText() },
                positionIndicator = { PositionIndicator(scalingLazyListState = rememberScalingLazyListState()) }
            ) {
                if (shoppingList.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Please start shopping to unleash the full potential of cHAZe",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.body1
                        )
                    }
                } else {
                    ScalingLazyColumn {
                        item {
                            Text(
                                text = "Shopping List",
                                style = MaterialTheme.typography.title2,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                        }
                        items(shoppingList) { item ->
                            ToggleChip(
                                checked = item.isChecked,
                                onCheckedChange = { onItemToggle(item) },
                                label = { Text(item.name) },
                                toggleControl = {
                                    if (item.isChecked) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Checked"
                                        )
                                    }
                                }
                            )
                        }
                    }
                }

                // Show notification overlay if there's a new notification
                if (latestNotification != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colors.background.copy(alpha = 0.95f))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = latestNotification.title,
                                style = MaterialTheme.typography.title3,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = latestNotification.product,
                                style = MaterialTheme.typography.title2,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colors.primary
                            )
                            Text(
                                text = "${latestNotification.percentage}% OFF",
                                style = MaterialTheme.typography.display1,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colors.secondary
                            )
                            Text(
                                text = "Now CHF ${String.format("%.2f", latestNotification.price)}",
                                style = MaterialTheme.typography.body1,
                                textAlign = TextAlign.Center
                            )
                            androidx.wear.compose.material.Button(
                                onClick = onDismissNotification,
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
fun DefaultPreview() {
    WearApp(
        shoppingList = listOf(
        ),
        latestNotification = null,
        onItemToggle = {},
        onDismissNotification = {}
    )
}
