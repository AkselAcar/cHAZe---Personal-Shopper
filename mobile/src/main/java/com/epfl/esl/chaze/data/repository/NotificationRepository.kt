package com.epfl.esl.chaze.data.repository

import com.epfl.esl.chaze.data.model.NotificationData
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

object NotificationRepository {
    private val db: FirebaseFirestore = Firebase.firestore
    private val auth = Firebase.auth


//    Get real-time notifications for the current user
    fun getNotifications(): Flow<List<NotificationData>> = callbackFlow {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listener = db.collection("users")
            .document(userId)
            .collection("notifications")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(50) // Limit to last 50 notifications
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val notifications = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(NotificationData::class.java)?.copy(id = doc.id)
                    }
                    trySend(notifications)
                } else {
                    trySend(emptyList())
                }
            }

        awaitClose { listener.remove() }
    }

//  Save a notification to Firestore
    suspend fun saveNotification(notification: NotificationData) {
        val userId = auth.currentUser?.uid ?: return

        try {
            db.collection("users")
                .document(userId)
                .collection("notifications")
                .add(notification)
                .await()
        } catch (e: Exception) {
            println("Error saving notification: $e")
        }
    }

//  Mark a notification as read
    suspend fun markAsRead(notificationId: String) {
        val userId = auth.currentUser?.uid ?: return

        try {
            db.collection("users")
                .document(userId)
                .collection("notifications")
                .document(notificationId)
                .update("isUnread", false)
                .await()
        } catch (e: Exception) {
            println("Error marking notification as read: $e")
        }
    }

//  Delete a notification
    suspend fun deleteNotification(notificationId: String) {
        val userId = auth.currentUser?.uid ?: return

        try {
            db.collection("users")
                .document(userId)
                .collection("notifications")
                .document(notificationId)
                .delete()
                .await()
        } catch (e: Exception) {
            println("Error deleting notification: $e")
        }
    }
}
