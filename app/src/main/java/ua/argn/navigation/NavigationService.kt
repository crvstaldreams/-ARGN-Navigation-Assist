package ua.argn.navigation

import android.app.*
import android.content.*
import android.location.*
import android.os.*
import android.hardware.*
import java.net.URL
import java.util.concurrent.Executors
import org.json.JSONObject
import kotlin.math.*

class NavigationService : Service(), SensorEventListener {
 private val handler=Handler(Looper.getMainLooper())
 private val io=Executors.newSingleThreadExecutor()
 private lateinit var lm:LocationManager
 private lateinit var sensors:SensorManager
 private var mock=false
 private var last:Location?=null
 private var lastAcceptedAt=0L
 private var blockedUntil=0L
 private var alarm=false
 private var alarmKnown=false
 private var lastAlertUpdate=0L
 private var endpoint=""
 private var heading=Double.NaN
 private var speed=0.0
 private var stopped=false
 private val listener=object:LocationListener { override fun onLocationChanged(location:Location) { accept(location) } }
 override fun onBind(intent:Intent?):IBinder?=null
 override fun onCreate() {
  super.onCreate()
  lm=getSystemService(LOCATION_SERVICE) as LocationManager
  sensors=getSystemService(SENSOR_SERVICE) as SensorManager
  val channel=NotificationChannel("argn","ARGN navigation",NotificationManager.IMPORTANCE_LOW)
  (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
  startForeground(11,notification("Starting protection"))
 }
 override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
  if(intent==null) return START_NOT_STICKY
  endpoint=intent.getStringExtra("endpoint")?:""
  if(!endpoint.startsWith("https://")) { stopSelf();return START_NOT_STICKY }
  sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)?.also { sensors.registerListener(this,it,SensorManager.SENSOR_DELAY_GAME) }
  try {
   lm.requestLocationUpdates(LocationManager.GPS_PROVIDER,1000L,0f,listener,Looper.getMainLooper())
  } catch (_:SecurityException) { stopSelf();return START_NOT_STICKY }
  // Android mock provider is opt-in; do not feed its output back into this filter.
  try { lm.addTestProvider("argn_mock",false,false,false,false,true,true,true,1,1);lm.setTestProviderEnabled("argn_mock",true);mock=true }
  catch (_:Exception) { mock=false }
  pollAlerts()
  return START_NOT_STICKY
 }
 private fun pollAlerts() {
  if(stopped) return
  io.execute {
   var result:Pair<Boolean,Boolean>?=null
   try {
    val conn=URL(endpoint).openConnection() as java.net.HttpURLConnection
    conn.connectTimeout=5000;conn.readTimeout=5000;conn.setRequestProperty("Accept","application/json")
    if(conn.responseCode==200) {
     val data=conn.inputStream.bufferedReader().use{it.readText()}
     val obj=JSONObject(data)
     if(obj.has("kyiv") && obj.has("oblast") && obj.get("kyiv") is Boolean && obj.get("oblast") is Boolean)
      result=Pair(obj.getBoolean("kyiv"),obj.getBoolean("oblast"))
    }
    conn.disconnect()
   } catch (_:Exception) {}
   val checked=result
   handler.post {
    if(stopped)return@post
    if(checked!=null) {
     val active=checked.first || checked.second
     if(alarm && !active) blockedUntil=SystemClock.elapsedRealtime()+60_000L
     alarm=active;alarmKnown=true;lastAlertUpdate=SystemClock.elapsedRealtime()
     if(active) { last=null;lastAcceptedAt=0L }
    }
    updateNotification()
    handler.postDelayed({pollAlerts()},15000L)
   }
  }
 }
 private fun accept(fix:Location) {
  if(fix.isFromMockProvider) return
  val now=SystemClock.elapsedRealtime()
  // Fail closed on stale alert status if the last confirmed state was an alarm.
  if(alarm || now<blockedUntil) return
  if(!fix.hasAccuracy() || fix.accuracy>80f || fix.time< System.currentTimeMillis()-10000L) return
  val prev=last
  if(prev!=null) {
   val dt=(fix.elapsedRealtimeNanos-prev.elapsedRealtimeNanos)/1e9
   if(dt<=0.0)return
   val meters=prev.distanceTo(fix).toDouble()
   val tolerance=(prev.accuracy+fix.accuracy).toDouble()
   if((meters-tolerance).coerceAtLeast(0.0)/dt>55.0)return
  }
  // During connectivity loss, retain the same anomaly gate; it cannot detect slow spoofing.
  last=Location(fix);lastAcceptedAt=now
  speed=if(fix.hasSpeed())fix.speed.toDouble().coerceIn(0.0,55.0) else 0.0
  if(mock) publish(fix)
  updateNotification()
 }
 private fun publish(fix:Location) {
  try {
   val out=Location("argn_mock")
   out.latitude=fix.latitude;out.longitude=fix.longitude
   out.accuracy=fix.accuracy;out.time=System.currentTimeMillis()
   out.elapsedRealtimeNanos=SystemClock.elapsedRealtimeNanos()
   if(fix.hasSpeed())out.speed=fix.speed
   if(fix.hasBearing())out.bearing=fix.bearing
   lm.setTestProviderLocation("argn_mock",out)
  } catch (_:Exception) { mock=false }
 }
 private fun notification(status:String):Notification =
  Notification.Builder(this,"argn").setContentTitle("ARGN Navigation Assist").setContentText(status)
   .setSmallIcon(android.R.drawable.ic_menu_mylocation).setOngoing(true).build()
 private fun updateNotification() {
  val age=SystemClock.elapsedRealtime()-lastAlertUpdate
  val state=when {
   alarm -> "AIR ALERT: GNSS blocked"
   SystemClock.elapsedRealtime()<blockedUntil -> "Waiting 60s after all-clear"
   !alarmKnown -> "Alert status unknown; anomaly checks only"
   age>60000 -> "Alert data stale; anomaly checks only"
   else -> "No alert; GNSS anomaly checks enabled"
  }
  (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(11,notification(state))
 }
 override fun onSensorChanged(event:SensorEvent) {
  if(event.sensor.type==Sensor.TYPE_ROTATION_VECTOR) {
   val r=FloatArray(9);val o=FloatArray(3)
   SensorManager.getRotationMatrixFromVector(r,event.values)
   SensorManager.getOrientation(r,o)
   heading=Math.toDegrees(o[0].toDouble())
  }
 }
 override fun onAccuracyChanged(sensor:Sensor?,accuracy:Int) {}
 override fun onDestroy() {
  stopped=true;handler.removeCallbacksAndMessages(null);sensors.unregisterListener(this)
  try { lm.removeUpdates(listener) }catch (_:Exception){}
  if(mock)try { lm.removeTestProvider("argn_mock") }catch (_:Exception){}
  io.shutdownNow();super.onDestroy()
 }
}
