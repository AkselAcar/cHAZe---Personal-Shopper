package com.epfl.esl.chaze.features.analytics

/**
 * AnalyticsViewModel manages shopping analytics data aggregation and state management.
 * 
 * Purpose: Aggregate past basket data into spending
 *
 */

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.epfl.esl.chaze.data.model.PastBasketModel
import com.epfl.esl.chaze.data.repository.PastBasketRepository
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Aggregated spending data for a single month.
 */
data class MonthlySpending(
    val month: String,
    val year: Int,
    val totalSpent: Double,
    val totalSaved: Double,
    val basketCount: Int
)

/**
 * Aggregated spending for a single day.
 */
data class DailySpending(
    val date: Long,
    val dateLabel: String,
    val amount: Double
)

/**
 * Spending breakdown for a single product category.
 */
data class CategorySpending(
    val category: String,
    val amount: Double,
    val percentage: Float
)

data class AnalyticsData(
    val monthlySpending: List<MonthlySpending> = emptyList(),
    val dailySpending: List<DailySpending> = emptyList(),
    val categorySpending: List<CategorySpending> = emptyList(),
    val totalSpent: Double = 0.0,
    val totalSaved: Double = 0.0,
    val averageBasketSize: Double = 0.0,
    val totalBaskets: Int = 0,
    val isLoading: Boolean = true,
    val error: String? = null
)

class AnalyticsViewModel : ViewModel() {
    private val pastBasketRepository = PastBasketRepository()
    private val auth = Firebase.auth

    private val _analyticsData = MutableStateFlow(AnalyticsData())
    val analyticsData: StateFlow<AnalyticsData> = _analyticsData.asStateFlow()

    init {
        loadAnalyticsData()
    }

    fun loadAnalyticsData() {
        viewModelScope.launch {
            _analyticsData.value = _analyticsData.value.copy(isLoading = true, error = null)
            
            val userId = auth.currentUser?.uid
            if (userId == null) {
                _analyticsData.value = _analyticsData.value.copy(
                    isLoading = false,
                    error = "User not authenticated"
                )
                return@launch
            }

            try {
                val baskets = pastBasketRepository.loadPastBaskets()

                if (baskets.isEmpty()) {
                    _analyticsData.value = AnalyticsData(isLoading = false)
                    return@launch
                }

                // Process data
                val monthlyData = calculateMonthlySpending(baskets)
                val dailyData = calculateDailySpending(baskets)
                val categoryData = calculateCategorySpending(baskets)
                
                val totalSpent = baskets.sumOf { it.totalPrice }
                val totalSaved = baskets.sumOf { it.totalPriceBeforeDiscount - it.totalPrice }
                val averageBasket = if (baskets.isNotEmpty()) totalSpent / baskets.size else 0.0

                _analyticsData.value = AnalyticsData(
                    monthlySpending = monthlyData,
                    dailySpending = dailyData,
                    categorySpending = categoryData,
                    totalSpent = totalSpent,
                    totalSaved = totalSaved,
                    averageBasketSize = averageBasket,
                    totalBaskets = baskets.size,
                    isLoading = false
                )
            } catch (e: Exception) {
                _analyticsData.value = _analyticsData.value.copy(
                    isLoading = false,
                    error = e.message ?: "Unknown error occurred"
                )
            }
        }
    }

    /**
     * Aggregates baskets into monthly spending data.
     * Returns last 12 months to show yearly trend.
     */
    private fun calculateMonthlySpending(baskets: List<PastBasketModel>): List<MonthlySpending> {
        val monthFormat = SimpleDateFormat("MMM", Locale.getDefault())
        val calendar = Calendar.getInstance()

        val monthlyMap = baskets.groupBy { basket ->
            calendar.timeInMillis = basket.purchaseDate
            val month = monthFormat.format(Date(basket.purchaseDate))
            val year = calendar.get(Calendar.YEAR)
            Pair(month, year)
        }.map { (key, baskets) ->
            val (month, year) = key
            MonthlySpending(
                month = month,
                year = year,
                totalSpent = baskets.sumOf { it.totalPrice },
                totalSaved = baskets.sumOf { it.totalPriceBeforeDiscount - it.totalPrice },
                basketCount = baskets.size
            )
        }.sortedBy { it.year * 12 + getMonthNumber(it.month) }

        // Get last 12 months
        return monthlyMap.takeLast(12)
    }

    /**
     * Aggregates baskets into daily spending data.
     * Returns last 30 days.
     * Sorted chronologically for line chart trend visualization.
     */
    private fun calculateDailySpending(baskets: List<PastBasketModel>): List<DailySpending> {
        val dateFormat = SimpleDateFormat("MMM dd", Locale.getDefault())
        
        val dailyMap = baskets.groupBy { basket ->
            // Normalize date to midnight (00:00:00) for daily grouping.
            // Without this, two purchases on same day at different times would be in separate groups.
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = basket.purchaseDate
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            calendar.timeInMillis
        }.map { (date, baskets) ->
            DailySpending(
                date = date,
                dateLabel = dateFormat.format(Date(date)),
                amount = baskets.sumOf { it.totalPrice }
            )
        }.sortedBy { it.date }

        // Get last 30 days
        return dailyMap.takeLast(30)
    }

    /**
     * Aggregates items across all baskets into category spending.
     * Limits to top 8 categories to keep pie chart readable.
     * Sorted by descending amount so largest categories appear first.
     */
    private fun calculateCategorySpending(baskets: List<PastBasketModel>): List<CategorySpending> {
        // Group items by category (description field)
        val categoryMap = mutableMapOf<String, Double>()
        
        baskets.forEach { basket ->
            basket.items.forEach { item ->
                val category = item.description
                categoryMap[category] = categoryMap.getOrDefault(category, 0.0) + 
                    (item.price * item.quantity)
            }
        }

        val total = categoryMap.values.sum()
        
        // Calculate percentage for each category to display in legend.
        return categoryMap.map { (category, amount) ->
            CategorySpending(
                category = category,
                amount = amount,
                percentage = if (total > 0) (amount / total * 100).toFloat() else 0f
            )
        }.sortedByDescending { it.amount }
            .take(8) // Top 8 categories
    }

    /**
     * Helper to convert month abbreviation to numeric value for sorting.
     * Used in calculateMonthlySpending to sort months chronologically.
     */
    private fun getMonthNumber(monthName: String): Int {
        return when (monthName) {
            "Jan" -> 1
            "Feb" -> 2
            "Mar" -> 3
            "Apr" -> 4
            "May" -> 5
            "Jun" -> 6
            "Jul" -> 7
            "Aug" -> 8
            "Sep" -> 9
            "Oct" -> 10
            "Nov" -> 11
            "Dec" -> 12
            else -> 0
        }
    }
}

