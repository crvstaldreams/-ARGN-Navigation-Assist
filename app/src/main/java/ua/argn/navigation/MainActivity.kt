package ua.argn.navigation

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
 private val locationRequestCode = 101
 private val notificationRequestCode = 102
 private var pendingStart: String? = null

 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  val density = resources.displayMetrics.density
  fun dp(value: Int) = (value * density + 0.5f).toInt()
  val root = LinearLayout(this).apply {
   orientation = LinearLayout.VERTICAL
   setPadding(dp(20), dp(28), dp(20), dp(28))
  }
  val scroll = ScrollView(this).apply { isFillViewport = true }
  scroll.addView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
  val title = TextView(this).apply {
   text = "ARGN Navigation Assist v0.4"
   textSize = 23f
   setPadding(0, 0, 0, dp(12))
  }
  val explanation = TextView(this).apply {
   text = "Київ та Київська область. Експериментальна перевірка GNSS під час повітряних тривог. Не використовуйте як єдине джерело навігації."
   textSize = 15f
   setPadding(0, 0, 0, dp(20))
  }
  val endpoint = EditText(this).apply {
   hint = "HTTPS адреса сервера тривог"
   inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
   setSingleLine(true)
   setText(getPreferences(0).getString("endpoint", ""))
  }
  val start = Button(this).apply { text = "Увімкнути захист" }
  val stop = Button(this).apply { text = "Вимкнути захист" }
  val note = TextView(this).apply {
   text = "Підтримка Android 10+. Для роботи потрібен дозвіл на точну геолокацію. На Android 13+ додатково запитується дозвіл на сповіщення."
   textSize = 13f
   setPadding(0, dp(20), 0, 0)
  }
  listOf(title, explanation, endpoint, start, stop, note).forEach { root.addView(it) }
  setContentView(scroll)
  start.setOnClickListener {
   val url = endpoint.text.toString().trim()
   if (!url.startsWith("https://")) {
    endpoint.error = "Потрібна HTTPS адреса"
   } else {
    getPreferences(0).edit().putString("endpoint", url).apply()
    if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
     pendingStart = url
     requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), locationRequestCode)
    } else {
     launchService(url)
    }
   }
  }
  stop.setOnClickListener {
   pendingStart = null
   stopService(Intent(this, NavigationService::class.java))
  }
 }

 private fun launchService(url: String) {
  if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
  startForegroundService(Intent(this, NavigationService::class.java).putExtra("endpoint", url))
  if (Build.VERSION.SDK_INT >= 33 &&
   checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
   requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), notificationRequestCode)
  }
 }

 override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
  super.onRequestPermissionsResult(requestCode, permissions, grantResults)
  if (requestCode == locationRequestCode) {
   val url = pendingStart
   pendingStart = null
   if (url != null && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
    launchService(url)
   }
  }
 }
}
