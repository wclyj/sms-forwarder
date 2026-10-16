package com.yu.smsforwarder

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)
    requestNeededPermissions()

    val webhook = findViewById<EditText>(R.id.webhook)
    val device = findViewById<EditText>(R.id.device)
    val smtpHost = findViewById<EditText>(R.id.smtpHost)
    val smtpPort = findViewById<EditText>(R.id.smtpPort)
    val smtpUser = findViewById<EditText>(R.id.smtpUser)
    val smtpPass = findViewById<EditText>(R.id.smtpPass)
    val mailTo = findViewById<EditText>(R.id.mailTo)
    webhook.setText(Prefs.webhook(this))
    device.setText(Prefs.device(this))
    smtpHost.setText(Prefs.smtpHost(this))
    smtpPort.setText(Prefs.smtpPort(this))
    smtpUser.setText(Prefs.smtpUser(this))
    smtpPass.setText(Prefs.smtpPass(this))
    mailTo.setText(Prefs.mailTo(this))

    findViewById<Button>(R.id.save).setOnClickListener {
      val hook = webhook.text.toString().trim()
      val host = smtpHost.text.toString().trim()
      val port = smtpPort.text.toString().trim().ifBlank { "465" }
      val user = smtpUser.text.toString().trim()
      val pass = smtpPass.text.toString().trim()
      val recipient = mailTo.text.toString().trim()
      val name = device.text.toString().trim().ifBlank { Build.MODEL }
      val emailFields = listOf(host, user, pass, recipient)
      val emailStarted = emailFields.any { it.isNotEmpty() }
      val emailComplete = emailFields.all { it.isNotEmpty() }
      if (hook.isNotEmpty() && !hook.startsWith("https://")) {
        Toast.makeText(this, "企业微信 Webhook 需要以 https:// 开头", Toast.LENGTH_LONG).show()
        return@setOnClickListener
      }
      if (emailStarted && !emailComplete) {
        Toast.makeText(this, "邮箱请填完整：服务器、账号、授权码、收件人", Toast.LENGTH_LONG).show()
        return@setOnClickListener
      }
      if (hook.isEmpty() && !emailComplete) {
        Toast.makeText(this, "至少填写企业微信 Webhook，或完整的邮箱", Toast.LENGTH_LONG).show()
        return@setOnClickListener
      }
      if (port.toIntOrNull() == null) {
        Toast.makeText(this, "邮箱端口需要是数字", Toast.LENGTH_LONG).show()
        return@setOnClickListener
      }
      Prefs.save(this, hook, name, host, port, user, pass, recipient)
      try {
        KeepAliveService.start(this)
        Toast.makeText(this, "已保存并启动", Toast.LENGTH_LONG).show()
      } catch (_: Exception) {
        Toast.makeText(this, "已保存，但后台服务没有启动", Toast.LENGTH_LONG).show()
      }
    }

    findViewById<Button>(R.id.test).setOnClickListener {
      if (!Prefs.ready(this)) {
        Toast.makeText(this, "请先保存企业微信或邮箱", Toast.LENGTH_SHORT).show()
        return@setOnClickListener
      }
      Thread {
        val result = try {
          SmsReceiver.sendTest(this)
        } catch (error: Exception) {
          PushResult("error", error.message ?: "error")
        }
        runOnUiThread {
          Toast.makeText(this, result.summary(), Toast.LENGTH_LONG).show()
        }
      }.start()
    }
  }

  private fun requestNeededPermissions() {
    val permissions = mutableListOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_PHONE_STATE)
    if (Build.VERSION.SDK_INT >= 26) permissions += Manifest.permission.READ_PHONE_NUMBERS
    if (Build.VERSION.SDK_INT >= 33) permissions += Manifest.permission.POST_NOTIFICATIONS
    val missing = permissions.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
    if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), 1)
  }

  override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    if (requestCode == 1 && grantResults.any { it != PackageManager.PERMISSION_GRANTED }) {
      Toast.makeText(this, "有权限被拒绝，可能无法接收短信或识别双卡", Toast.LENGTH_LONG).show()
    }
  }
}
