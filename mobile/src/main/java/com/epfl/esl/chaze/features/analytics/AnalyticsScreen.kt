// File written with the help of AI
package com.epfl.esl.chaze.features.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.ShoppingBasket
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.epfl.esl.chaze.ui.theme.CHAZeTheme
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottomAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStartAxis
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import java.util.Locale

/**
 * Main analytics screen displaying shopping insights and spending trends.
 * 
 * Features:
 * - Summary cards with key metrics (total spent, saved, average basket, total orders)
 * - Monthly spending bar chart
 * - Daily spending trend line chart
 * - Category spending pie chart
 */
@Composable
fun AnalyticsScreen(
    modifier: Modifier = Modifier,
    viewModel: AnalyticsViewModel = viewModel()
) {
    val analyticsData by viewModel.analyticsData.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    text = "Shopping Analytics",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Track your spending patterns",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        when {
            analyticsData.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            analyticsData.error != null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Error: ${analyticsData.error}",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            analyticsData.totalBaskets == 0 -> {
                // Empty state when no purchase history exists
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.ShoppingBasket,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No purchase history yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            else -> {
                AnalyticsContent(analyticsData)
            }
        }
    }
}

@Composable
private fun AnalyticsContent(data: AnalyticsData) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Summary cards section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SummaryCard(
                title = "Total Spent",
                value = "CHF ${String.format(Locale.getDefault(),"%.2f", data.totalSpent)}",
                icon = Icons.Default.Receipt,
                modifier = Modifier.weight(1f)
            )
            SummaryCard(
                title = "Total Saved",
                value = "CHF ${String.format(Locale.getDefault(),"%.2f", data.totalSaved)}",
                icon = Icons.Default.Savings,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SummaryCard(
                title = "Avg Basket",
                value = "CHF ${String.format(Locale.getDefault(),"%.2f", data.averageBasketSize)}",
                icon = Icons.Default.ShoppingBasket,
                modifier = Modifier.weight(1f)
            )
            SummaryCard(
                title = "Total Orders",
                value = data.totalBaskets.toString(),
                icon = Icons.AutoMirrored.Filled.TrendingUp,
                modifier = Modifier.weight(1f)
            )
        }

        // Charts section
        if (data.monthlySpending.isNotEmpty()) {
            ChartCard(title = "Monthly Spending") {
                MonthlySpendingChart(data.monthlySpending)
            }
        }

        if (data.dailySpending.isNotEmpty()) {
            ChartCard(title = "Daily Spending Trend") {
                DailySpendingChart(data.dailySpending)
            }
        }

        if (data.categorySpending.isNotEmpty()) {
            ChartCard(title = "Spending by Category") {
                CategoryPieChart(data.categorySpending)
            }
        }
    }
}

@Composable
private fun SummaryCard(
    title: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    /**
     * Card component displaying a single metric.
     */
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun ChartCard(
    title: String,
    content: @Composable () -> Unit
) {
    /**
     * Card wrapper for charts.
     */
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
private fun MonthlySpendingChart(monthlyData: List<MonthlySpending>) {
    /**
     * Bar chart showing total spending per month.
     * LaunchedEffect ensures chart updates when data changes, not on every recomposition.
     */
    val modelProducer = remember { CartesianChartModelProducer() }
    
    LaunchedEffect(monthlyData) {
        modelProducer.runTransaction {
            columnSeries {
                series(monthlyData.map { it.totalSpent })
            }
        }
    }

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberColumnCartesianLayer(),
            startAxis = rememberStartAxis(),
            bottomAxis = rememberBottomAxis(
                valueFormatter = { value, _, _ ->
                    monthlyData.getOrNull(value.toInt())?.month ?: ""
                }
            )
        ),
        modelProducer = modelProducer,
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp)
    )
}

@Composable
private fun DailySpendingChart(dailyData: List<DailySpending>) {
    /**
     * Line chart showing spending trend over days.
     * X-axis labels show every 5th day to avoid too much clutter.
     */
    val modelProducer = remember { CartesianChartModelProducer() }
    
    LaunchedEffect(dailyData) {
        modelProducer.runTransaction {
            lineSeries {
                series(dailyData.map { it.amount })
            }
        }
    }

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(),
            startAxis = rememberStartAxis(),
            bottomAxis = rememberBottomAxis(
                valueFormatter = { value, _, _ ->
                    // Show labels every 5 days to reduce visual clutter
                    if (value.toInt() % 5 == 0) {
                        dailyData.getOrNull(value.toInt())?.dateLabel ?: ""
                    } else ""
                }
            )
        ),
        modelProducer = modelProducer,
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp)
    )
}

@Composable
private fun CategoryPieChart(categories: List<CategorySpending>) {
    /**
     * Pie chart visualizing spending distribution by category.
     * Includes pie chart with legend showing category names and percentages with amounts in CHF.
     */
    val colors = listOf(
        Color(0xFF6200EE),
        Color(0xFF03DAC6),
        Color(0xFFFF6F00),
        Color(0xFFE91E63),
        Color(0xFF4CAF50),
        Color(0xFF2196F3),
        Color(0xFFFF9800),
        Color(0xFF9C27B0)
    )

    Column {
        // Pie chart drawing on Canvas
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val radius = minOf(canvasWidth, canvasHeight) / 2.5f
            val centerX = canvasWidth / 2
            val centerY = canvasHeight / 2

            // Draw pie slices
            var startAngle = -90f
            categories.forEachIndexed { index, category ->
                val sweepAngle = (category.percentage / 100f) * 360f
                drawArc(
                    color = colors[index % colors.size],
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = true,
                    topLeft = Offset(centerX - radius, centerY - radius),
                    size = Size(radius * 2, radius * 2)
                )
                startAngle += sweepAngle
            }

            // Draw white border around pie chart for visual separation
            drawArc(
                color = Color.White,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = 4f),
                topLeft = Offset(centerX - radius, centerY - radius),
                size = Size(radius * 2, radius * 2)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Legend (category names, percentages, amounts in CHF)
        categories.forEachIndexed { index, category ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Color indicator box matching pie chart slice
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .background(
                            colors[index % colors.size],
                            RoundedCornerShape(4.dp)
                        )
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = category.category,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                // Display percentage and amount in CHF
                Text(
                    text = "${category.percentage.toInt()}% (CHF ${String.format(Locale.getDefault(),"%.2f", category.amount)})",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AnalyticsScreenPreview() {
    CHAZeTheme {
        AnalyticsScreen()
    }
}

