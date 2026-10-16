package com.yu.smsforwarder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

class KeepAliveService : Service() {
  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    try {
      val manager = getSystemService(NotificationManager::class.java)
      if (Build.VERSION.SDK_INT >= 26) {
        manager.createNotificationChannel(NotificationChannel("keep", "后台运行", NotificationManager.IMPORTANCE_LOW))
      }
      val notification = (if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, "keep") else Notification.Builder(this))
        .setContentTitle("验证码转发运行中")
        .setContentText("收到验证码短信后会上报到你的服务器")
        .setSmallIcon(android.R.drawable.ic_dialog_email)
        .setOngoing(true)
        .build()
      if (Build.VERSION.SDK_INT >= 34) {
        startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
      } else {
        startForeground(1, notification)
      }
    } catch (_: Exception) {
      stopSelf()
      return START_NOT_STICKY
    }
    return START_STICKY
  }

  companion object {
    fun start(context: Context) {
      val intent = Intent(context, KeepAliveService::class.java)
      if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
    }
  }
}
