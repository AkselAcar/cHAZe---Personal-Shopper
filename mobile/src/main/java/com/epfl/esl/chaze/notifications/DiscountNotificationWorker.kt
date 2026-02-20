package com.epfl.esl.chaze.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.epfl.esl.chaze.R
import com.epfl.esl.chaze.data.model.Discount
import com.epfl.esl.chaze.data.model.NotificationData
import com.epfl.esl.chaze.data.repository.NotificationRepository
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlin.random.Random

class DiscountNotificationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val CHANNEL_ID = "discount_notifications"
        private const val CHANNEL_NAME = "Discount Notifications"
        private const val NOTIFICATION_ID = 1001
    }

    override suspend fun doWork(): Result {
        return try {
            // Fetch random discount from Firestore
            val discount = fetchRandomDiscount()
            
            if (discount != null) {
                // Show notification with the discount
                showDiscountNotification(discount)
                
                // Save notification to Firestore for the notification drawer
                saveNotificationToFirestore(discount)
                
                // Send notification to wear device
                sendNotificationToWear(discount)
            }
            
            Result.success()
        } catch (e: Exception) {
            println("Error in DiscountNotificationWorker: ${e.message}")
            Result.failure()
        }
    }
//  Get random discount from Firestore database
    private suspend fun fetchRandomDiscount(): Discount? {
        return try {
            val db = FirebaseFirestore.getInstance()
            
            // Fetch all active discounts
            val snapshot = db.collection("discounts")
                .whereEqualTo("discount_active", true)
                .get()
                .await()
            
            if (snapshot.isEmpty) {
                return null
            }
            
            // Convert to list of Discount objects
            val discounts = snapshot.documents.mapNotNull { doc ->
                doc.toObject(Discount::class.java)
            }
            
            // Randomly select one discount
            if (discounts.isNotEmpty()) {
                val randomDiscount = discounts[Random.nextInt(discounts.size)]
                randomDiscount
            } else {
                null
            }
        } catch (e: Exception) {
            println("Error fetching random discount: ${e.message}")
            null
        }
    }
//  Display notification with discount information
    private fun showDiscountNotification(discount: Discount) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Create notification channel for Android O and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Hourly discount notifications"
            }
            notificationManager.createNotificationChannel(channel)
        }
        
        // Build notification
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Make sure this icon exists
            .setContentTitle("🎉 Special Discount Alert!")
            .setContentText("${discount.discount_percentage}% OFF on ${discount.product_name}")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Save ${discount.discount_percentage}% on ${discount.product_name}! " +
                            "Now only CHF ${String.format("%.2f", discount.discounted_price)}. " +
                            "Don't miss out on this amazing deal!")
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
//  Save notification to database for the user
    private suspend fun saveNotificationToFirestore(discount: Discount) {
        try {
            val notificationData = NotificationData(
                title = "🎉 Special Discount Alert!",
                message = "${discount.discount_percentage}% OFF on ${discount.product_name}",
                timestamp = System.currentTimeMillis(),
                isUnread = true,
                type = "discount"
            )
            
            NotificationRepository.saveNotification(notificationData)
        } catch (e: Exception) {
            println("Error saving notification to Firestore: ${e.message}")
        }
    }
//  Send same notification to Wear OS
    private suspend fun sendNotificationToWear(discount: Discount) {
        try {
            val dataClient = Wearable.getDataClient(applicationContext)
            
            val putDataReq = PutDataMapRequest.create("/discount_notification").run {
                dataMap.putString("title", "🎉 Special Discount!")
                dataMap.putString("message", "${discount.discount_percentage}% OFF on ${discount.product_name}")
                dataMap.putString("product", discount.product_name)
                dataMap.putInt("percentage", discount.discount_percentage)
                dataMap.putDouble("price", discount.discounted_price)
                dataMap.putLong("timestamp", System.currentTimeMillis())
                asPutDataRequest()
            }
            
            dataClient.putDataItem(putDataReq).await()
        } catch (e: Exception) {
            println("Error sending notification to Wear OS: ${e.message}")
        }
    }
}
