package com.gen.keymapper

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

/** Visible, user-started lifecycle for the local shell session, including activation. */
class BreventSessionService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate()
        instance = this
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Sesi input Brevent", NotificationManager.IMPORTANCE_LOW))
        if (Build.VERSION.SDK_INT >= 34) startForeground(ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(ID, notification())
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) stopSelf()
        return START_NOT_STICKY
    }
    private fun notification(): Notification {
        val open = PendingIntent.getActivity(this, ID, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, ID, Intent(this, BreventSessionService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_stat_gen)
            .setContentTitle("GenKey · Sesi Brevent")
            .setContentText(BreventBackend.message).setContentIntent(open).setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Putuskan sesi", stop).build()).build()
    }
    fun refresh() { getSystemService(NotificationManager::class.java).notify(ID, notification()) }
    override fun onDestroy() {
        instance = null
        BreventBackend.disconnect()
        super.onDestroy()
    }
    companion object {
        private const val CHANNEL = "genkey_brevent"
        private const val ID = 8
        private const val STOP = "com.gen.keymapper.BREVENT_STOP"
        var instance: BreventSessionService? = null
            private set
    }
}
