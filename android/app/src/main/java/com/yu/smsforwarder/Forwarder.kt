package com.yu.smsforwarder

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Forwarder {
  private val lock = Any()
  private val inflight = HashSet<String>()
  private val invisible = Regex("[\\u00A0\\u200B\\u200C\\u200D\\uFEFF\\u2060\\u180E]")
  private val spaced = Regex("\\s+")
  private val shortCode = Regex("(?<![A-Za-z0-9])(\\d{4,8})(?![A-Za-z0-9])")
  private val bareDigits = Regex("^\\d{4,8}$")
  private val bareToken = Regex("^(?=.*\\d)[A-Za-z0-9]{4,8}$")

  fun consider(ctx: Context, sender: String, body: String, atMillis: Long, sim: String, phone: String?) {
    val app = ctx.applicationContext
    val text = body.trim().take(4000)
    if (text.isEmpty() || !Prefs.ready(app)) return
    val preview = text.replace(spaced, " ").take(40)
    Prefs.setLastSms(app, "$sender $preview")
    if (!looksLikeCode(text)) return
    val identity = "$sender\u0000$text\u0000$atMillis"
    synchronized(lock) {
      if (!inflight.add(identity)) return
    }
    try {
      if (Prefs.alreadyForwarded(app, identity)) return
      val receivedAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(Date(atMillis))
      val note = SmsNote(Prefs.device(app), phone, sim, sender, text, receivedAt)
      val result = withWakeLock(app) { Push.send(Prefs.webhook(app), Prefs.mail(app), note) }
      if (result.delivered(Prefs.webhook(app).isNotBlank(), Prefs.emailReady(app))) {
        Prefs.markForwarded(app, identity)
        Prefs.setLastForward(app, "已转发 $sender")
        notify(app, "验证码已转发", "$sender 的短信已发出")
        Log.i("码到", "forwarded $sender")
      } else {
        Prefs.setLastForward(app, "失败 ${result.summary()}")
        notify(app, "验证码转发失败", result.summary())
        Log.i("码到", "forward failed $sender ${result.summary()}")
      }
    } catch (error: Exception) {
      val reason = error.message ?: "未知错误"
      Prefs.setLastForward(app, "失败 $reason")
      Log.i("码到", "forward error $sender $reason")
    } finally {
      synchronized(lock) { inflight.remove(identity) }
    }
  }

  private fun looksLikeCode(text: String): Boolean {
    val plain = text.replace(invisible, "").trim()
    if (plain.isEmpty()) return false
    val compact = plain.replace(spaced, "")
    if (Prefs.KEYWORDS.containsMatchIn(plain) || Prefs.KEYWORDS.containsMatchIn(compact)) return true
    val bare = compact.replace(Regex("[-—_－]"), "")
    if (bareDigits.matches(bare) || bareToken.matches(bare)) return true
    if (plain.length > 80) return false
    val codes = shortCode.findAll(plain).map { it.groupValues[1] }.distinct().toList()
    if (codes.isEmpty()) return false
    if (plain.contains("【") || plain.contains("[")) return true
    return plain.length <= 64 && codes.size == 1
  }

  private fun withWakeLock(ctx: Context, block: () -> PushResult): PushResult {
    val pm = ctx.getSystemService(PowerManager::class.java) ?: return block()
    val wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "smsforwarder:send")
    wake.setReferenceCounted(false)
    wake.acquire(25_000L)
    return try {
      block()
    } finally {
      if (wake.isHeld) wake.release()
    }
  }

  private fun notify(ctx: Context, title: String, text: String) {
    if (Build.VERSION.SDK_INT >= 33 && ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
    val manager = ctx.getSystemService(NotificationManager::class.java) ?: return
    if (Build.VERSION.SDK_INT >= 26) {
      manager.createNotificationChannel(NotificationChannel("forward", "转发结果", NotificationManager.IMPORTANCE_DEFAULT))
    }
    val notification = (if (Build.VERSION.SDK_INT >= 26) Notification.Builder(ctx, "forward") else Notification.Builder(ctx))
      .setContentTitle(title)
      .setContentText(text)
      .setSmallIcon(android.R.drawable.ic_dialog_email)
      .setAutoCancel(true)
      .build()
    manager.notify(2, notification)
  }
}
