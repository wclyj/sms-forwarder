package com.yu.smsforwarder

object Extract {
  private val keywords = Regex(
    "验证码|校验码|动态码|动态密码|确认码|激活码|验证代码|一次性密码|登录码|安全码|短信口令|口令|驗證碼|verification code|security code|passcode|(?<![A-Za-z])(?:code|otp|pin)(?![A-Za-z])",
    RegexOption.IGNORE_CASE,
  )
  private val invisible = Regex("[\\u00A0\\u200B\\u200C\\u200D\\uFEFF\\u2060\\u180E]")
  private val spaced = Regex("\\s+")
  private val platformPattern = Regex("[【\\[]([^】\\]]{1,20})[】\\]]")
  private val candidatePattern = Regex("(?<![A-Za-z0-9])([A-Za-z0-9]{4,8})(?![A-Za-z0-9])")

  fun platform(text: String): String? {
    return platformPattern.find(text)?.groupValues?.getOrNull(1)?.trim()?.ifBlank { null }
  }

  fun code(text: String): String? {
    val plain = text.replace(invisible, "")
    return pick(plain) ?: pick(plain.replace(spaced, "")) ?: onlyNumber(plain)
  }

  private fun onlyNumber(text: String): String? {
    val found = candidatePattern.findAll(text).map { it.groupValues[1] }.filter { value ->
      value.all { it.isDigit() } && value.length in 4..8 && !Regex("^1[3-9]\\d{9}$").matches(value)
    }.distinct().toList()
    return found.singleOrNull()
  }

  private fun pick(text: String): String? {
    val keyword = keywords.find(text) ?: return null
    val keywordStart = keyword.range.first
    val keywordEnd = keyword.range.last + 1
    val candidates = candidatePattern.findAll(text).mapNotNull { match ->
      val value = match.groupValues[1]
      if (!value.any { it.isDigit() } || value.length !in 4..8) return@mapNotNull null
      if (Regex("^1[3-9]\\d{9}$").matches(value)) return@mapNotNull null
      val distance = if (match.range.first >= keywordEnd) {
        match.range.first - keywordEnd
      } else {
        keywordStart - (match.range.first + value.length)
      }
      val preference = if (value.all { it.isDigit() }) 0 else 5
      value to (kotlin.math.abs(distance) + preference)
    }
    return candidates.minByOrNull { it.second }?.first
  }
}
