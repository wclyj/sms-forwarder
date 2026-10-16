package com.yu.smsforwarder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action == Intent.ACTION_BOOT_COMPLETED && Prefs.ready(context)) {
      try {
        KeepAliveService.start(context)
      } catch (_: Exception) {
      }
    }
  }
}
