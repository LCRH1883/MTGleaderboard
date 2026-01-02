package com.intagri.mtgleader.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.intagri.mtgleader.R
import com.intagri.mtgleader.persistence.Datastore
import com.intagri.mtgleader.persistence.notifications.NotificationsRepository
import com.intagri.mtgleader.persistence.sync.SyncScheduler
import com.intagri.mtgleader.ui.MainActivity
import com.intagri.mtgleader.ui.SplashActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

@AndroidEntryPoint
class FriendRequestMessagingService : FirebaseMessagingService() {

    @Inject lateinit var datastore: Datastore
    @Inject lateinit var notificationsRepository: NotificationsRepository

    override fun onMessageReceived(message: RemoteMessage) {
        if (!datastore.friendRequestNotificationsEnabled) {
            return
        }
        val data = message.data
        val eventType = data["type"] ?: data["event"]
        if (eventType != EVENT_FRIEND_REQUEST) {
            return
        }
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            return
        }
        showFriendRequestNotification(message)
        SyncScheduler.enqueueNow(applicationContext)
    }

    private fun showFriendRequestNotification(message: RemoteMessage) {
        val title = message.notification?.title
            ?: getString(R.string.notification_friend_request_title)
        val body = message.notification?.body ?: buildBody(message.data)
        val intent = Intent(this, SplashActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_OPEN_FRIENDS, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            REQUEST_CODE_FRIEND_REQUEST,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        ensureChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_FRIEND_REQUESTS)
            .setSmallIcon(R.drawable.ic_skull)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(this)
            .notify(NOTIFICATION_ID_FRIEND_REQUEST, notification)
    }

    override fun onNewToken(token: String) {
        CoroutineScope(Dispatchers.IO).launch {
            notificationsRepository.registerToken(token)
        }
    }

    private fun buildBody(data: Map<String, String>): String {
        val name = data["display_name"]
            ?: data["username"]
            ?: data["user"]
        return if (!name.isNullOrBlank()) {
            getString(R.string.notification_friend_request_body_named, name)
        } else {
            getString(R.string.notification_friend_request_body)
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val existing = manager.getNotificationChannel(CHANNEL_FRIEND_REQUESTS)
        if (existing != null) {
            return
        }
        val channel = NotificationChannel(
            CHANNEL_FRIEND_REQUESTS,
            getString(R.string.notification_channel_friend_requests),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = getString(R.string.notification_channel_friend_requests_description)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_FRIEND_REQUESTS = "friend_requests"
        private const val EVENT_FRIEND_REQUEST = "friend_request"
        private const val REQUEST_CODE_FRIEND_REQUEST = 2001
        private const val NOTIFICATION_ID_FRIEND_REQUEST = 3001
    }
}
