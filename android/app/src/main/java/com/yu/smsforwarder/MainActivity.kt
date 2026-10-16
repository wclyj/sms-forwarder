package com.yu.smsforwarder

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)
    requestNeededPermissions()

    val url = findViewById<EditText>(R.id.url)
    val token = findViewById<EditText>(R.id.token)
    val device = findViewById<EditText>(R.id.device)
    url.setText(Prefs.url(this))
    token.setText(Prefs.token(this))
    device.setText(Prefs.device(this))

    findViewById<Button>(R.id.save).setOnClickListener {
      val endpoint = url.text.toString().trim()
      val apiToken = token.text.toString().trim()
      val name = device.text.toString().trim().ifBlank { Build.MODEL }
      if (!endpoint.startsWith("https://") && !endpoint.startsWith("http://")) {
        Toast.makeText(this, "服务器地址需要以 https:// 或 http:// 开头", Toast.LENGTH_LONG).show()
        return@setOnClickListener
      }
      if (apiToken.length < 16) {
        Toast.makeText(this, "API_TOKEN 至少 16 位", Toast.LENGTH_LONG).show()
        return@setOnClickListener
      }
      Prefs.save(this, endpoint, apiToken, name)
      val hint = if (endpoint.startsWith("http://")) "已保存。当前是 HTTP，验证码会明文传输" else "已保存并启动"
      try {
        KeepAliveService.start(this)
        Toast.makeText(this, hint, Toast.LENGTH_LONG).show()
      } catch (_: Exception) {
        Toast.makeText(this, "已保存，但后台服务没有启动", Toast.LENGTH_LONG).show()
      }
    }

    findViewById<Button>(R.id.test).setOnClickListener {
      if (Prefs.url(this).isBlank() || Prefs.token(this).isBlank()) {
        Toast.makeText(this, "请先保存服务器地址和 API_TOKEN", Toast.LENGTH_SHORT).show()
        return@setOnClickListener
      }
      Thread {
        val stamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(Date())
        val code = try { SmsReceiver.post(
          this,
          JSONObject()
            .put("device", Prefs.device(this))
            .put("sim", "test")
            .put("sender", "test")
            .put("content", "【测试】您的验证码是 123456")
            .put("receivedAt", stamp),
        ) } catch (_: Exception) { -2 }
        runOnUiThread {
          val text = if (code in 200..299) "测试成功" else "测试失败($code)"
          Toast.makeText(this, text, Toast.LENGTH_LONG).show()
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
