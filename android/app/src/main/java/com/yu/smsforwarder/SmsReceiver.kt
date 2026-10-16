package com.yu.smsforwarder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Telephony
import android.telephony.SubscriptionManager
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SmsReceiver : BroadcastReceiver() {
  override fun onReceive(ctx: Context, intent: Intent) {
    if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
    val msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
    if (msgs.isEmpty()) return
    val sender = msgs[0].originatingAddress ?: "-"
    val body = msgs.joinToString("") { it.messageBody ?: "" }
    if (!Prefs.KEYWORDS.containsMatchIn(body)) return

    val (sim, phone) = simInfo(ctx, intent)
    val json = JSONObject()
      .put("device", Prefs.device(ctx))
      .put("sim", sim)
      .put("sender", sender)
      .put("content", body)
      .put("receivedAt", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(Date(msgs[0].timestampMillis)))
    if (!phone.isNullOrBlank()) json.put("phone", phone)

    try {
      KeepAliveService.start(ctx)
    } catch (_: Exception) {
    }
    val pending = goAsync()
    Thread {
      try {
        post(ctx, json)
      } catch (_: Exception) {
      } finally {
        pending.finish()
      }
    }.start()
  }

  companion object {
    fun post(ctx: Context, json: JSONObject): Int {
      val endpoint = Prefs.url(ctx).trim()
      if (endpoint.isBlank()) return -1
      val target = endpoint.trimEnd('/') + "/api/report"
      var last = -2
      repeat(3) { attempt ->
        try {
          val connection = URL(target).openConnection() as HttpURLConnection
          connection.requestMethod = "POST"
          connection.doOutput = true
          connection.connectTimeout = 5000
          connection.readTimeout = 5000
          connection.instanceFollowRedirects = false
          connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
          connection.setRequestProperty("x-api-token", Prefs.token(ctx))
          connection.setRequestProperty("User-Agent", "SmsForwarder/1.0")
          connection.outputStream.use { it.write(json.toString().toByteArray(Charsets.UTF_8)) }
          last = connection.responseCode
          connection.disconnect()
          if (last in 200..299 || last in 400..499) return last
        } catch (_: Exception) {
          last = -2
        }
        if (attempt < 2) Thread.sleep(1000L * (attempt + 1))
      }
      return last
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
