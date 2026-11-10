package app.familytv.notes
import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.media.RingtoneManager
import android.os.*
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.thread

object Net {
    fun open(url: String, readTimeout: Int = 15000) = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15000; this.readTimeout = readTimeout
    }
    fun publish(c: Context, topic: String, body: String, title: String? = null) = thread {
        runCatching { app.familytv.config.FamilyConfig.open(c, app.familytv.config.FamilyConfig.server(c) + "/" + topic).apply {
            requestMethod = "POST"; doOutput = true; title?.let { setRequestProperty("Title", it) }
            outputStream.use { it.write(body.toByteArray()) }; responseCode; disconnect() } }
    }
    fun bitmap(url: String) = runCatching { open(url).inputStream.use { BitmapFactory.decodeStream(it) } }.getOrNull()
    fun fmt(t: Long): String = SimpleDateFormat("EEE MMM d, h:mm a", Locale.getDefault()).format(Date(t * 1000))
}

class NotesService : Service() {
    private val IGNORED = Regex("^/(flight|where|trip|santa)\\b", RegexOption.IGNORE_CASE)
    companion object { var listener: (() -> Unit)? = null }
    private val h = Handler(Looper.getMainLooper())
    @Volatile private var running = true
    private var conn: HttpURLConnection? = null
    override fun onBind(i: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate()
        val who = app.familytv.config.FamilyConfig.parentName(this)
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("s", "Notes from $who", NotificationManager.IMPORTANCE_MIN))
        val n = Notification.Builder(this, "s").setContentTitle("Listening for notes from $who").setSmallIcon(android.R.drawable.ic_dialog_email).build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(1, n)
        Sfx.preload(this, R.raw.note_pop, R.raw.sparkle, R.raw.whoosh)
        thread(name = "ntfy") { loop() }
    }
    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        if (i?.getBooleanExtra("reconnect", false) == true) conn?.disconnect()   // topic changed
        return START_STICKY
    }
    override fun onDestroy() { running = false; conn?.disconnect(); super.onDestroy() }

    private fun loop() {
        var backoff = 2000L
        while (running) {
            val topic = Store.topic(this)
            try {
                // History first (ntfy.sh caches ~12h; 168h asks for up to 7 days).
                // Resume after the last message we saw so cleared/deleted notes aren't re-fetched on restart.
                val since = Store.lastId(this, topic).ifBlank { "168h" }
                app.familytv.config.FamilyConfig.open(this, app.familytv.config.FamilyConfig.server(this) + "/$topic/json?poll=1&since=$since").inputStream.bufferedReader().useLines { ls ->
                    ls.mapNotNull { Note.fromNtfy(it) }.forEach { handle(it, popup = false); Store.setLastId(this, topic, it.id) } }
                // Long-lived stream; ntfy sends keepalives every ~45s, so 90s read timeout detects dead links.
                val c = app.familytv.config.FamilyConfig.open(this, app.familytv.config.FamilyConfig.server(this) + "/$topic/json", readTimeout = 90_000); conn = c
                c.inputStream.bufferedReader().useLines { ls ->
                    backoff = 2000L
                    ls.forEach { line -> Note.fromNtfy(line)?.let { handle(it, popup = true); Store.setLastId(this, topic, it.id) } }
                }
            } catch (_: Exception) { }
            if (!running) break
            Thread.sleep(backoff); backoff = (backoff * 2).coerceAtMost(300_000L)
        }
    }
    private fun handle(n: Note, popup: Boolean) {
        // Travel and Santa commands belong to the travel app; never show them as notes.
        if (IGNORED.containsMatchIn(n.body.trimStart())) return
        when (n.body.trim().lowercase()) {
            "/clear" -> { Store.clear(this); h.post { listener?.invoke() }; return }
            "/undo" -> { Store.undo(this); h.post { listener?.invoke() }; return }
        }
        val home = Regex("^/home\\s+(\\d{4}-\\d{2}-\\d{2})").find(n.body.trim())
        if (home != null) { Store.setHomeDate(this, home.groupValues[1]); h.post { listener?.invoke() }; return }
        if (Store.add(this, n)) { h.post { listener?.invoke(); if (popup) popup(n) } }
    }
    private fun popup(n: Note) {
        if (!Settings.canDrawOverlays(this)) return
        val wm = getSystemService(WindowManager::class.java)
        val d = resources.displayMetrics.density
        lateinit var dismiss: () -> Unit
        lateinit var love: () -> Unit
        lateinit var reply: () -> Unit
        val card = PopupCard(this, n).apply { scaleX = 0.6f; scaleY = 0.6f; alpha = 0f }
        // Focusable scrim: OK/center = Love it!, any other key (incl. BACK) dismisses without sending.
        val scrim = object : android.widget.FrameLayout(this) {
            override fun dispatchKeyEvent(e: android.view.KeyEvent): Boolean {
                if (e.action != android.view.KeyEvent.ACTION_UP) return true
                when (e.keyCode) { android.view.KeyEvent.KEYCODE_DPAD_CENTER, android.view.KeyEvent.KEYCODE_ENTER,
                    android.view.KeyEvent.KEYCODE_NUMPAD_ENTER, android.view.KeyEvent.KEYCODE_BUTTON_A -> if (card.btnFocus) love() else reply()
                    android.view.KeyEvent.KEYCODE_DPAD_LEFT -> { card.btnFocus = true; card.invalidate(); h.removeCallbacksAndMessages("pop"); h.postAtTime({ dismiss() }, "pop", SystemClock.uptimeMillis() + 20_000) }
                    android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> { card.btnFocus = false; card.invalidate(); h.removeCallbacksAndMessages("pop"); h.postAtTime({ dismiss() }, "pop", SystemClock.uptimeMillis() + 20_000) }
                    else -> dismiss() }
                return true
            }
        }.apply { setBackgroundColor(0x99000000.toInt()); alpha = 0f; isFocusable = true; isFocusableInTouchMode = true }
        scrim.addView(card, android.widget.FrameLayout.LayoutParams((620 * d).toInt(), (440 * d).toInt(), Gravity.CENTER))
        val lp = WindowManager.LayoutParams(-1, -1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT).apply { alpha = 1f; dimAmount = 0f }
        var gone = false; var loved = false
        dismiss = { if (!gone) { gone = true; h.removeCallbacksAndMessages("pop")
            card.animate().scaleX(0.85f).scaleY(0.85f).alpha(0f).setDuration(280).start()
            scrim.animate().alpha(0f).setDuration(300).withEndAction { runCatching { wm.removeView(scrim) } }.start() } }
        love = { if (!gone && !loved) { loved = true; h.removeCallbacksAndMessages("pop")
            val snip = (n.title.ifBlank { n.body }).replace("\n", " ").let { if (it.length > 40) it.take(40).trimEnd() + "…" else it }
            Net.publish(this, app.familytv.config.FamilyConfig.repliesTopic(this), "❤️ loved: $snip", "Love it!")
            Sfx.play(this, R.raw.sparkle, 450)
            android.animation.ValueAnimator.ofFloat(0f, 1f).apply { duration = 900
                addUpdateListener { card.burst = it.animatedValue as Float }
                addListener(object : android.animation.AnimatorListenerAdapter() { override fun onAnimationEnd(a: android.animation.Animator) { dismiss() } }) }.start()
            card.animate().scaleX(1.05f).scaleY(1.05f).setDuration(150).withEndAction { card.animate().scaleX(1f).scaleY(1f).setDuration(150).start() }.start() } }
        reply = { if (!gone && !loved) { dismiss(); h.postDelayed({ Stickers.show(this, overlay = true) }, 320) } }
        runCatching { wm.addView(scrim, lp) }.onFailure { return }
        Sfx.play(this, R.raw.note_pop, 800)
        scrim.requestFocus()
        scrim.animate().alpha(1f).setDuration(250).start()
        card.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(480).setInterpolator(android.view.animation.OvershootInterpolator(2.2f)).start()
        runCatching { RingtoneManager.getRingtone(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))?.play() }
        h.postAtTime({ dismiss() }, "pop", SystemClock.uptimeMillis() + 20_000)
    }
}
class BootReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) { c.startForegroundService(Intent(c, NotesService::class.java)) }
}
