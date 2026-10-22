package ua.argn.navigation

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  val root = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(28,48,28,20) }
  val title = TextView(this).apply { text="ARGN Navigation Assist v0.3\nKyiv + Kyiv oblast anti-spoof\nExperimental: never rely on this for safety-critical navigation."; textSize=19f }
  val endpoint = EditText(this).apply { hint="HTTPS alert endpoint (JSON kyiv/oblast)"; setSingleLine(true); setText(getPreferences(0).getString("endpoint","")) }
  val start = Button(this).apply { text="Start protection" }
  val stop = Button(this).apply { text="Stop protection" }
  root.addView(title); root.addView(endpoint); root.addView(start); root.addView(stop); setContentView(root)
  if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED)
    requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION),1)
  if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),2)
  start.setOnClickListener {
   val url=endpoint.text.toString().trim()
   if(!url.startsWith("https://")) { endpoint.error="HTTPS required"; return@setOnClickListener }
   getPreferences(0).edit().putString("endpoint",url).apply()
   val intent=Intent(this,NavigationService::class.java).putExtra("endpoint",url)
   if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED) startForegroundService(intent)
   else requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),1)
  }
  stop.setOnClickListener { stopService(Intent(this,NavigationService::class.java)) }
 }
}
