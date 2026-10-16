package com.yu.smsforwarder

import android.content.Context

object Prefs {
  private fun prefs(context: Context) = context.getSharedPreferences("cfg", Context.MODE_PRIVATE)

  fun url(context: Context) = prefs(context).getString("url", "")!!

  fun token(context: Context) = prefs(context).getString("token", "")!!

  fun device(context: Context) = prefs(context).getString("device", android.os.Build.MODEL)!!

  fun save(context: Context, url: String, token: String, device: String) {
    prefs(context).edit().putString("url", url).putString("token", token).putString("device", device).apply()
  }

  val KEYWORDS = Regex(
    "验证码|校验码|动态码|确认码|激活码|驗證碼|verification|(?<![A-Za-z])(?:code|otp|pin)(?![A-Za-z])",
    RegexOption.IGNORE_CASE,
  )
}
