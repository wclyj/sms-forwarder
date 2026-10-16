package com.yu.smsforwarder

import android.content.Context
import java.security.MessageDigest

object Prefs {
  private fun prefs(context: Context) = context.getSharedPreferences("cfg", Context.MODE_PRIVATE)

  fun webhook(context: Context) = prefs(context).getString("webhook", "")!!

  fun device(context: Context) = prefs(context).getString("device", android.os.Build.MODEL)!!

  fun smtpHost(context: Context) = prefs(context).getString("smtpHost", "")!!

  fun smtpPort(context: Context) = prefs(context).getString("smtpPort", "465")!!

  fun smtpUser(context: Context) = prefs(context).getString("smtpUser", "")!!

  fun smtpPass(context: Context) = prefs(context).getString("smtpPass", "")!!

  fun mailTo(context: Context) = prefs(context).getString("mailTo", "")!!

  fun save(
    context: Context,
    webhook: String,
    device: String,
    smtpHost: String,
    smtpPort: String,
    smtpUser: String,
    smtpPass: String,
    mailTo: String,
  ) {
    prefs(context).edit()
      .putString("webhook", webhook)
      .putString("device", device)
      .putString("smtpHost", smtpHost)
      .putString("smtpPort", smtpPort.ifBlank { "465" })
      .putString("smtpUser", smtpUser)
      .putString("smtpPass", smtpPass)
      .putString("mailTo", mailTo)
      .apply()
  }

  fun emailReady(context: Context): Boolean {
    return smtpHost(context).isNotBlank() &&
      smtpUser(context).isNotBlank() &&
      smtpPass(context).isNotBlank() &&
      mailTo(context).isNotBlank()
  }

  fun ready(context: Context): Boolean = webhook(context).isNotBlank() || emailReady(context)

  fun mail(context: Context): MailSettings? {
    if (!emailReady(context)) return null
    val port = smtpPort(context).toIntOrNull() ?: 465
    return MailSettings(smtpHost(context), port, smtpUser(context), smtpPass(context), mailTo(context))
  }

  fun alreadyForwarded(context: Context, key: String): Boolean {
    return seen(context).contains(digest(key))
  }

  fun markForwarded(context: Context, key: String) {
    val next = seen(context).toMutableList()
    next.add(digest(key))
    prefs(context).edit().putString("seen", next.takeLast(50).joinToString("\n")).apply()
  }

  private fun seen(context: Context): List<String> {
    return prefs(context).getString("seen", "")!!.split('\n').filter { it.isNotBlank() }
  }

  private fun digest(value: String): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { "%02x".format(it) }
  }

  fun lastSms(context: Context) = prefs(context).getString("lastSms", "还没有")!!

  fun lastForward(context: Context) = prefs(context).getString("lastForward", "还没有")!!

  fun setLastSms(context: Context, value: String) {
    prefs(context).edit().putString("lastSms", value.take(80)).apply()
  }

  fun setLastForward(context: Context, value: String) {
    prefs(context).edit().putString("lastForward", value.take(120)).apply()
  }

  fun askedBattery(context: Context) = prefs(context).getBoolean("askedBattery", false)

  fun markAskedBattery(context: Context) {
    prefs(context).edit().putBoolean("askedBattery", true).apply()
  }

  val KEYWORDS = Regex(
    "验证码|校验码|动态码|动态密码|确认码|激活码|验证代码|一次性密码|登录码|安全码|短信口令|口令|驗證碼|verification|passcode|(?<![A-Za-z])(?:code|otp|pin)(?![A-Za-z])",
    RegexOption.IGNORE_CASE,
  )
}
