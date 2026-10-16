package com.yu.smsforwarder

import android.util.Base64
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

data class SmsNote(
  val device: String,
  val phone: String?,
  val sim: String?,
  val sender: String,
  val content: String,
  val receivedAt: String,
) {
  val platform: String? = Extract.platform(content)
  val code: String? = Extract.code(content)
}

data class PushResult(val wecom: String, val email: String) {
  fun summary(): String = "企业微信 $wecom，邮箱 $email"

  fun delivered(webhookReady: Boolean, emailReady: Boolean): Boolean {
    val wecomDone = !webhookReady || wecom == "ok"
    val emailDone = !emailReady || email == "ok"
    return (webhookReady || emailReady) && wecomDone && emailDone
  }
}

object Push {
  private val smtpLock = Any()
  @Volatile private var smtpBlockedUntil = 0L

  fun send(webhook: String, mail: MailSettings?, note: SmsNote): PushResult {
    val wecom = if (webhook.isBlank()) "skipped" else pushWecom(webhook, note)
    val email = if (mail == null) "skipped" else pushEmail(mail, note)
    return PushResult(wecom, email)
  }

  private fun pushWecom(url: String, note: SmsNote): String {
    val payload = JSONObject()
      .put("msgtype", "markdown")
      .put("markdown", JSONObject().put("content", markdown(note)))
    return postJson(url, payload)
  }

  private fun pushEmail(mail: MailSettings, note: SmsNote): String {
    val now = System.currentTimeMillis()
    if (now < smtpBlockedUntil) return "error: 邮箱登录被限制，请过一会儿再试一次"
    synchronized(smtpLock) {
      if (System.currentTimeMillis() < smtpBlockedUntil) return "error: 邮箱登录被限制，请过一会儿再试一次"
      var last = "error"
      repeat(3) { attempt ->
        try {
          sendSmtp(mail, note)
          return "ok"
        } catch (error: Exception) {
          last = "error: ${error.message ?: "smtp"}".take(180)
          if (isLoginRejected(error)) {
            smtpBlockedUntil = System.currentTimeMillis() + 20 * 60 * 1000L
            return last
          }
          if (attempt < 2) Thread.sleep(1000L * (attempt + 1))
        }
      }
      return last
    }
  }

  private fun isLoginRejected(error: Exception): Boolean {
    val message = error.message ?: return false
    return message.contains("535") || message.contains("Login fail", ignoreCase = true)
  }

  private fun markdown(note: SmsNote): String {
    return listOf(
      "### 验证码 <font color=\"warning\">${note.code ?: "未识别"}</font>",
      "> 平台：${note.platform ?: "-"}",
      "> 设备：${oneLine(note.device)}",
      "> 号码/卡：${oneLine(note.phone ?: note.sim ?: "-")}",
      "> 发送方：${oneLine(note.sender)}",
      "> 时间：${oneLine(note.receivedAt)}",
      "",
      note.content.take(1500),
    ).joinToString("\n")
  }

  private fun oneLine(value: String): String = value.replace(Regex("[\\r\\n]+"), " ").trim()

  private fun postJson(url: String, json: JSONObject): String {
    var last = "error"
    repeat(3) { attempt ->
      try {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.connectTimeout = 8000
        connection.readTimeout = 8000
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        connection.setRequestProperty("User-Agent", "SmsForwarder/1.1")
        connection.outputStream.use { it.write(json.toString().toByteArray(Charsets.UTF_8)) }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        connection.disconnect()
        if (code !in 200..299) {
          last = "error $code"
          if (code in 400..499) return last
        } else {
          val errcode = try {
            JSONObject(text).optInt("errcode", 0)
          } catch (_: Exception) {
            0
          }
          if (errcode != 0) return "error $errcode"
          return "ok"
        }
      } catch (error: Exception) {
        last = "error: ${error.message ?: "network"}".take(180)
      }
      if (attempt < 2) Thread.sleep(1000L * (attempt + 1))
    }
    return last
  }

  private fun sendSmtp(mail: MailSettings, note: SmsNote) {
    val subject = oneLine("【${note.platform ?: "验证码"}】${note.code ?: "未识别"} - ${note.device}").take(180)
    val body = listOf(
      "验证码：${note.code ?: "未识别"}",
      "平台：${note.platform ?: "-"}",
      "设备：${note.device}",
      "号码/卡：${note.phone ?: note.sim ?: "-"}",
      "发送方：${note.sender}",
      "时间：${note.receivedAt}",
      "",
      note.content.take(4000),
    ).joinToString("\n")
    SmtpSession(mail).use { session ->
      session.send(mail.user, mail.to, subject, body)
    }
  }
}

data class MailSettings(
  val host: String,
  val port: Int,
  val user: String,
  val pass: String,
  val to: String,
)

private class SmtpSession(private val mail: MailSettings) : AutoCloseable {
  private var socket: Socket = connect()
  private var reader = readerOf(socket)
  private var writer = writerOf(socket)

  init {
    expect(readResponse(), "220")
    expect(command("EHLO sms-forwarder"), "250")
    if (mail.port != 465) {
      expect(command("STARTTLS"), "220")
      val secure = (SSLSocketFactory.getDefault() as SSLSocketFactory)
        .createSocket(socket, mail.host, mail.port, true) as SSLSocket
      secure.startHandshake()
      socket = secure
      reader = readerOf(socket)
      writer = writerOf(socket)
      expect(command("EHLO sms-forwarder"), "250")
    }
    expect(command("AUTH LOGIN"), "334")
    expect(command(base64(mail.user)), "334")
    expect(command(base64(mail.pass)), "235")
  }

  fun send(from: String, to: String, subject: String, body: String) {
    val sender = address(from)
    val recipient = address(to)
    expect(command("MAIL FROM:<$sender>"), "250")
    expect(command("RCPT TO:<$recipient>"), "250")
    expect(command("DATA"), "354")
    val stuffed = body.replace("\r\n", "\n").replace('\r', '\n').lineSequence().joinToString("\r\n") { line ->
      if (line.startsWith(".")) ".$line" else line
    }
    writer.write(
      listOf(
        "From: $sender",
        "To: $recipient",
        "Subject: ${encodedWord(subject)}",
        "MIME-Version: 1.0",
        "Content-Type: text/plain; charset=UTF-8",
        "Content-Transfer-Encoding: 8bit",
        "",
        stuffed,
      ).joinToString("\r\n"),
    )
    writer.write("\r\n.\r\n")
    writer.flush()
    expect(readResponse(), "250")
    try {
      command("QUIT")
    } catch (_: Exception) {
    }
  }

  override fun close() {
    try {
      socket.close()
    } catch (_: Exception) {
    }
  }

  private fun connect(): Socket {
    val connected = if (mail.port == 465) {
      (SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket()
    } else {
      Socket()
    }
    connected.connect(InetSocketAddress(mail.host, mail.port), 8000)
    connected.soTimeout = 8000
    return connected
  }

  private fun command(line: String): String {
    writer.write(line)
    writer.write("\r\n")
    writer.flush()
    return readResponse()
  }

  private fun readResponse(): String {
    val text = StringBuilder()
    while (true) {
      val line = reader.readLine() ?: throw java.io.IOException("smtp closed")
      text.append(line).append('\n')
      if (line.length < 4 || line[3] != '-') break
    }
    return text.toString()
  }

  private fun expect(response: String, code: String) {
    val line = response.lineSequence().lastOrNull { it.isNotBlank() } ?: "smtp"
    if (!line.startsWith(code)) throw java.io.IOException(line.take(160))
  }

  private fun address(value: String): String {
    val trimmed = value.trim()
    if (trimmed.isEmpty() || trimmed.any { it == '\r' || it == '\n' || it == '<' || it == '>' }) {
      throw java.io.IOException("bad address")
    }
    return trimmed
  }

  private fun base64(value: String): String {
    return Base64.encodeToString(value.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
  }

  private fun encodedWord(value: String): String = "=?UTF-8?B?${base64(value)}?="

  private fun readerOf(current: Socket) = BufferedReader(InputStreamReader(current.getInputStream(), Charsets.UTF_8))

  private fun writerOf(current: Socket) = BufferedWriter(OutputStreamWriter(current.getOutputStream(), Charsets.UTF_8))
}
