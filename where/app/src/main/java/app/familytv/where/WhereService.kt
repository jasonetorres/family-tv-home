package app.familytv.where
import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

object Net {
    fun open(url: String, readTimeout: Int = 15000) = (URL(url).openConnection() as HttpURLConnection).apply { connectTimeout = 15000; this.readTimeout = readTimeout }
}
object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("where", Context.MODE_PRIVATE)
    fun topic(c: Context): String {
        val saved = sp(c).getString("topic", "")!!.trim()
        return saved.ifBlank { app.familytv.config.FamilyConfig.whereTopic(c) }
    }
    fun setTopic(c: Context, t: String) = sp(c).edit().putString("topic", t.trim()).apply()
    fun chip(c: Context) = sp(c).getBoolean("chip", false)   // corner chip over other apps: default OFF
    fun setChip(c: Context, on: Boolean) = sp(c).edit().putBoolean("chip", on).apply()
    fun lastId(c: Context, t: String) = sp(c).getString("last_$t", "") ?: ""
    fun setLastId(c: Context, t: String, id: String) = sp(c).edit().putString("last_$t", id).apply()
}

/** Subscribes to the travel ntfy topic (commands only), optionally refreshes flight status, and shows the corner chip. */
class WhereService : Service() {
    companion object { var listener: (() -> Unit)? = null }
    private val h = Handler(Looper.getMainLooper())
    @Volatile private var running = true
    private var conn: HttpURLConnection? = null
    private var chip: TextView? = null
    override fun onBind(i: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate()
        val who = app.familytv.config.FamilyConfig.parentName(this)
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("s", "Where's $who", NotificationManager.IMPORTANCE_MIN))
        val n = Notification.Builder(this, "s").setContentTitle("Following $who's travels").setSmallIcon(android.R.drawable.ic_menu_mylocation).build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(1, n)
        thread(name = "ntfy") { loop() }
        thread(name = "flight") { while (running) {
            if (Where.get(this).mode == "flight") { Where.refresh(this); h.post { listener?.invoke() } }
            runCatching { Thread.sleep(5 * 60_000L) } } }
        h.post(chipTick)
    }
    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        if (i?.getBooleanExtra("reconnect", false) == true) conn?.disconnect()
        h.post { updateChip() }
        return START_STICKY
    }
    override fun onDestroy() { running = false; conn?.disconnect(); h.removeCallbacks(chipTick); removeChip(); super.onDestroy() }

    private fun loop() {
        var backoff = 2000L
        while (running) {
            val topic = Prefs.topic(this)
            try {
                val since = Prefs.lastId(this, topic).ifBlank { "168h" }
                app.familytv.config.FamilyConfig.open(this, app.familytv.config.FamilyConfig.server(this) + "/$topic/json?poll=1&since=$since").inputStream.bufferedReader().useLines { ls ->
                    ls.forEach { handle(it, topic) } }
                val c = app.familytv.config.FamilyConfig.open(this, app.familytv.config.FamilyConfig.server(this) + "/$topic/json", readTimeout = 90_000); conn = c
                c.inputStream.bufferedReader().useLines { ls -> backoff = 2000L; ls.forEach { handle(it, topic) } }
            } catch (_: Exception) { }
            if (!running) break
            Thread.sleep(backoff); backoff = (backoff * 2).coerceAtMost(300_000L)
        }
    }
    private fun handle(line: String, topic: String) {
        val o = runCatching { JSONObject(line) }.getOrNull() ?: return
        if (o.optString("event") != "message") return
        // /santa is reserved for Santa mode (December); everything else that isn't a command is ignored.
        if (Where.command(this, o.optString("message"))) h.post { listener?.invoke(); updateChip() }
        Prefs.setLastId(this, topic, o.getString("id"))
    }

    private val chipTick = object : Runnable { override fun run() { updateChip(); h.postDelayed(this, 60_000) } }
    /** Small non-interactive corner chip on other apps, only on a flight day, only if enabled AND overlay permission granted. */
    private fun updateChip() {
        val now = System.currentTimeMillis() / 1000
        val w = Where.get(this)
        val text = if (w.mode == "flight") w.headline(now, app.familytv.config.FamilyConfig.parentName(this), app.familytv.config.FamilyConfig.clock12(this)) else null
        if (text == null || !Prefs.chip(this) || !Settings.canDrawOverlays(this)) { removeChip(); return }
        chip?.let { it.text = "✈️ $text"; return }
        val d = resources.displayMetrics.density
        val tv = TextView(this).apply { this.text = "✈️ $text"; textSize = 16f; setTextColor(0xFF1B2A4A.toInt()); typeface = resources.getFont(R.font.patrick)
            setPadding((14 * d).toInt(), (6 * d).toInt(), (14 * d).toInt(), (6 * d).toInt())
            background = GradientDrawable().apply { setColor(0xF2FFF8E7.toInt()); cornerRadius = 999 * d; setStroke((2 * d).toInt(), 0xFF5A9BE6.toInt()) } }
        val lp = WindowManager.LayoutParams(-2, -2, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.END; x = (24 * d).toInt(); y = (20 * d).toInt() }
        runCatching { getSystemService(WindowManager::class.java).addView(tv, lp); chip = tv }
    }
    private fun removeChip() { chip?.let { runCatching { getSystemService(WindowManager::class.java).removeView(it) } }; chip = null }
}
class BootReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) { c.startForegroundService(Intent(c, WhereService::class.java)) }
}
