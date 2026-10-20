package com.baidu.bridge.server

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.voicegeely.app.VoiceAssistantService

class AIDLService : Service() {
    companion object { private const val NOTIFICATION_CHANNEL_ID = "AIDLServiceChannel"; private const val NOTIFICATION_ID = 1001 }
    override fun onCreate() { super.onCreate(); createNotificationChannel(); startForeground() }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundService(Intent(applicationContext, VoiceAssistantService::class.java))
        return START_STICKY
    }
    override fun onBind(intent: Intent?): IBinder? = null
    private fun createNotificationChannel() {
        val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, "AIDL Service Channel", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
    private fun startForeground() {
        val notification = Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("THT Voice service")
            .setContentText("Voice service is running")
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }
}
