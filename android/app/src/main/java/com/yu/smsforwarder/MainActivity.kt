package com.yu.smsforwarder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
  private var testing = false
  private val handler = Handler(Looper.getMainLooper())
  private val refresh = object : Runnable {
    override fun run() {
      renderStatus()
      handler.postDelayed(this, 2000)
    }
  }

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
      askBattery()
    }

    findViewById<Button>(R.id.test).setOnClickListener {
      if (!Prefs.ready(this)) {
        Toast.makeText(this, "请先保存企业微信或邮箱", Toast.LENGTH_SHORT).show()
        return@setOnClickListener
      }
      if (testing) {
        Toast.makeText(this, "上一次测试还在发送", Toast.LENGTH_SHORT).show()
        return@setOnClickListener
      }
      testing = true
      Thread {
        val result = try {
          SmsReceiver.sendTest(this)
        } catch (error: Exception) {
          PushResult("error", error.message ?: "error")
        }
        runOnUiThread {
          testing = false
          Prefs.setLastForward(this, "测试 ${result.summary()}")
          renderStatus()
          Toast.makeText(this, result.summary(), Toast.LENGTH_LONG).show()
        }
      }.start()
    }
  }

  override fun onResume() {
    super.onResume()
    handler.post(refresh)
  }

  override fun onPause() {
    handler.removeCallbacks(refresh)
    super.onPause()
  }

  private fun requestNeededPermissions() {
    val permissions = mutableListOf(
      Manifest.permission.RECEIVE_SMS,
      Manifest.permission.READ_SMS,
      Manifest.permission.READ_PHONE_STATE,
    )
    if (Build.VERSION.SDK_INT >= 26) permissions += Manifest.permission.READ_PHONE_NUMBERS
    if (Build.VERSION.SDK_INT >= 33) permissions += Manifest.permission.POST_NOTIFICATIONS
    val missing = permissions.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
    if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), 1) else onPermissionsReady()
  }

  override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    if (requestCode == 1 && grantResults.any { it != PackageManager.PERMISSION_GRANTED }) {
      Toast.makeText(this, "有权限被拒绝，真实短信收不到", Toast.LENGTH_LONG).show()
    }
    onPermissionsReady()
  }

  private fun onPermissionsReady() {
    if (!Prefs.ready(this)) return
    try {
      KeepAliveService.start(this)
    } catch (_: Exception) {
    }
    if (!Prefs.askedBattery(this)) {
      Prefs.markAskedBattery(this)
      askBattery()
    }
  }

  private fun askBattery() {
    val pm = getSystemService(PowerManager::class.java) ?: return
    if (pm.isIgnoringBatteryOptimizations(packageName)) return
    try {
      startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
    } catch (_: Exception) {
      Toast.makeText(this, "请到系统电池设置里把码到设为无限制", Toast.LENGTH_LONG).show()
    }
  }

  private fun renderStatus() {
    val sms = checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED &&
      checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
    val pm = getSystemService(PowerManager::class.java)
    val battery = pm?.isIgnoringBatteryOptimizations(packageName) == true
    findViewById<TextView>(R.id.watch).text = "短信权限：${if (sms) "已允许" else "未允许"}，电池：${if (battery) "无限制" else "会被系统停掉"}"
    findViewById<TextView>(R.id.lastSms).text = "最近短信：${Prefs.lastSms(this)}"
    findViewById<TextView>(R.id.lastForward).text = "最近转发：${Prefs.lastForward(this)}"
  }
}
