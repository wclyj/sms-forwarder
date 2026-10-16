package com.yu.smsforwarder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Telephony
import android.telephony.SubscriptionManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SmsReceiver : BroadcastReceiver() {
  override fun onReceive(ctx: Context, intent: Intent) {
    if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
    if (!Prefs.ready(ctx)) return
    val msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
    if (msgs.isEmpty()) return
    val sender = msgs[0].originatingAddress ?: "-"
    val body = msgs.joinToString("") { it.messageBody ?: "" }
    val at = msgs[0].timestampMillis
    val (sim, phone) = simInfo(ctx, intent)
    try {
      KeepAliveService.start(ctx)
    } catch (_: Exception) {
    }
    val pending = goAsync()
    Thread {
      try {
        Forwarder.consider(ctx, sender, body, at, sim, phone)
      } finally {
        pending.finish()
      }
    }.start()
  }

  companion object {
    fun sendTest(ctx: Context): PushResult {
      val stamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(Date())
      val note = SmsNote(
        device = Prefs.device(ctx),
        phone = null,
        sim = "test",
        sender = "test",
        content = "【测试】您的验证码是 123456",
        receivedAt = stamp,
      )
      return Push.send(Prefs.webhook(ctx), Prefs.mail(ctx), note)
    }
  }
}

private fun extraInt(extras: Bundle, key: String): Int? {
  if (!extras.containsKey(key)) return null
  return when (val value = extras.get(key)) {
    is Int -> value
    is Long -> value.toInt()
    is String -> value.toIntOrNull()
    else -> null
  }
}

private fun simInfo(ctx: Context, intent: Intent): Pair<String, String?> {
  val extras = intent.extras
  var sim = "unknown"
  var phone: String? = null
  try {
    val manager = ctx.getSystemService(SubscriptionManager::class.java) ?: return sim to phone
    val subId = extras?.let { bundle ->
      listOf("android.telephony.extra.SUBSCRIPTION_ID", "subscription", "subId", "sub_id")
        .firstNotNullOfOrNull { extraInt(bundle, it) }
        ?.takeIf { it >= 0 }
    }
    val slot = extras?.let { bundle ->
      listOf("android.telephony.extra.SLOT_INDEX", "android.telephony.extra.SUBSCRIPTION_INDEX", "slot")
        .firstNotNullOfOrNull { extraInt(bundle, it) }
        ?.takeIf { it >= 0 }
    }
    val info = when {
      subId != null -> manager.getActiveSubscriptionInfo(subId)
      slot != null -> manager.getActiveSubscriptionInfoForSimSlotIndex(slot)
      else -> null
    }
    if (info != null) {
      sim = "SIM${info.simSlotIndex + 1}(${info.carrierName})"
      phone = info.number?.takeIf { it.isNotBlank() }
    } else if (slot != null) {
      sim = "SIM${slot + 1}"
    } else if (subId != null) {
      sim = "sub$subId"
    }
  } catch (_: SecurityException) {
  }
  return sim to phone
}
