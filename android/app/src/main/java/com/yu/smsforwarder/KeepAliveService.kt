package com.yu.smsforwarder

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Telephony
import java.util.concurrent.Executors

class KeepAliveService : Service() {
  private val handler = Handler(Looper.getMainLooper())
  private val executor = Executors.newSingleThreadExecutor()
  private var watching = false
  private var after = System.currentTimeMillis() - 600_000L
  private val observer = object : ContentObserver(handler) {
    override fun onChange(selfChange: Boolean) {
      try {
        executor.execute { scanInbox() }
      } catch (_: Exception) {
      }
    }
  }
  private val loop = object : Runnable {
    override fun run() {
      executor.execute { scanInbox() }
      handler.postDelayed(this, 5000)
    }
  }

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    try {
      val manager = getSystemService(NotificationManager::class.java)
      if (Build.VERSION.SDK_INT >= 26) {
        manager.createNotificationChannel(NotificationChannel("keep", "后台运行", NotificationManager.IMPORTANCE_LOW))
      }
      val notification = (if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, "keep") else Notification.Builder(this))
        .setContentTitle("验证码转发运行中")
        .setContentText("收到验证码短信后会发到企业微信或邮箱")
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
    watchInbox()
    return START_STICKY
  }

  override fun onDestroy() {
    try {
      if (watching) contentResolver.unregisterContentObserver(observer)
    } catch (_: Exception) {
    }
    handler.removeCallbacks(loop)
    executor.shutdownNow()
    watching = false
    super.onDestroy()
  }

  private fun watchInbox() {
    if (watching) return
    watching = true
    try {
      contentResolver.registerContentObserver(Uri.parse("content://sms"), true, observer)
    } catch (_: SecurityException) {
      Prefs.setLastSms(this, "没有读取短信权限")
    }
    handler.post(loop)
  }

  private fun scanInbox() {
    if (checkSelfPermission(Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
      if (Prefs.lastSms(this) != "没有读取短信权限") Prefs.setLastSms(this, "没有读取短信权限")
      return
    }
    try {
      val uri = Telephony.Sms.Inbox.CONTENT_URI
      contentResolver.query(uri, arrayOf("address", "body", "date"), "date > ?", arrayOf(after.toString()), "date ASC")?.use { cursor ->
        val address = cursor.getColumnIndex("address")
        val body = cursor.getColumnIndex("body")
        val date = cursor.getColumnIndex("date")
        while (cursor.moveToNext()) {
          val at = if (date >= 0) cursor.getLong(date) else 0L
          if (at > after) after = at
          val sender = if (address >= 0) cursor.getString(address) ?: "-" else "-"
          val text = if (body >= 0) cursor.getString(body) ?: "" else ""
          Forwarder.consider(this, sender, text, at, "inbox", null)
        }
      }
    } catch (_: SecurityException) {
      Prefs.setLastSms(this, "没有读取短信权限")
    }
  }

  companion object {
    fun start(context: Context) {
      val intent = Intent(context, KeepAliveService::class.java)
      if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
    }
  }
}
