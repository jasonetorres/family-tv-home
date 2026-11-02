package app.familytv.where
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

const val NAVY = 0xFF1B2A4A.toInt()

class MainActivity : Activity() {
    private val d by lazy { resources.displayMetrics.density }
    private fun px(v: Int) = (v * d).toInt()
    private lateinit var map: MapView
    private lateinit var headline: TextView
    private lateinit var cells: List<Pair<TextView, TextView>>
    private lateinit var upcoming: LinearLayout
    private val hand by lazy { resources.getFont(R.font.patrick) }
    private val marker by lazy { resources.getFont(R.font.marker) }

    private fun paper(color: Int = 0xFFFFF8E7.toInt(), stroke: Int = 0) = GradientDrawable().apply { setColor(color); cornerRadius = px(14).toFloat(); if (stroke != 0) setStroke(px(3), stroke) }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        startForegroundService(Intent(this, WhereService::class.java))
        val root = FrameLayout(this)
        map = MapView(this).also { root.addView(it, FrameLayout.LayoutParams(-1, -1)) }
        // --- top info bar (Santa Tracker style)
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; background = paper(); elevation = px(6).toFloat(); setPadding(px(10), px(6), px(10), px(6)) }
        val titles = listOf("📍 Where", "➡️ Next stop", "⏱ Arriving in", "🧭 Distance traveled", "🕘 Local time")
        cells = titles.map { t -> val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(px(14), 0, px(14), 0) }
            val a = TextView(this).apply { text = t; textSize = 13f; setTextColor(0xFF9A5B3C.toInt()); typeface = hand; gravity = Gravity.CENTER }
            val v = TextView(this).apply { textSize = 22f; setTextColor(NAVY); typeface = hand; gravity = Gravity.CENTER; maxLines = 1 }
            col.addView(a); col.addView(v); bar.addView(col, LinearLayout.LayoutParams(0, -2, 1f)); a to v }
        root.addView(bar, FrameLayout.LayoutParams(-1, -2, Gravity.TOP).apply { setMargins(px(40), px(18), px(40), 0) })
        // --- headline banner
        headline = TextView(this).apply { textSize = 30f; setTextColor(NAVY); typeface = marker; gravity = Gravity.CENTER
            background = paper(0xFFFFE27A.toInt()); setPadding(px(26), px(8), px(26), px(8)); elevation = px(6).toFloat(); rotation = -1f }
        root.addView(headline, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = px(96) })
        // --- upcoming legs card (bottom-left)
        upcoming = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = paper(); elevation = px(6).toFloat(); setPadding(px(18), px(10), px(18), px(12)); rotation = 0.8f }
        root.addView(upcoming, FrameLayout.LayoutParams(px(470), -2, Gravity.BOTTOM or Gravity.START).apply { setMargins(px(40), 0, 0, px(24)) })
        // --- tiles (bottom-right): Santa placeholder + settings
        val tiles = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val santa = tile("🎅  Santa mode\ncoming Dec", 0xFFF9B9CB.toInt(), 0xFFE0483E.toInt()) {
            if (!Travelers.santaAvailable()) Toast.makeText(this, "Santa mode arrives in December! 🎄", Toast.LENGTH_LONG).show()
            else { Travelers.set(this, if (Travelers.current(this).id == "santa") "dad" else "santa"); render() } }
        val set = tile("⚙️  Settings", 0xFFB9D7F7.toInt(), 0xFF5A9BE6.toInt()) { settings() }
        tiles.addView(santa, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = px(16) }); tiles.addView(set)
        root.addView(tiles, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END).apply { setMargins(0, 0, px(40), px(28)) })
        setContentView(root)
        santa.requestFocus()
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 0)
    }
    private fun tile(label: String, bg: Int, border: Int, onClick: () -> Unit) = TextView(this).apply {
        text = label; textSize = 20f; typeface = hand; setTextColor(NAVY); gravity = Gravity.CENTER; isFocusable = true; isClickable = true
        setPadding(px(22), px(12), px(22), px(12)); background = paper(bg); elevation = px(6).toFloat()
        setOnFocusChangeListener { _, f -> background = paper(bg, if (f) border else 0); animate().scaleX(if (f) 1.1f else 1f).scaleY(if (f) 1.1f else 1f).setDuration(150).start() }
        setOnClickListener { onClick() } }

    private val tick = object : Runnable { override fun run() { render(); map.postDelayed(this, 20_000) } }
    override fun onResume() { super.onResume(); WhereService.listener = { render() }; map.post(tick) }
    override fun onPause() { WhereService.listener = null; map.removeCallbacks(tick); super.onPause() }

    private fun render() {
        val tr = Travelers.current(this); val now = System.currentTimeMillis() / 1000
        val s = tr.snapshot(this, now); map.snap = s; map.avatar = tr.avatar
        headline.text = s.headline
        cells[0].first.text = "📍 ${tr.name(this)} is in"; cells[4].first.text = "🕘 ${tr.name(this)}'s time"
        fun dur(x: Long): String { val m = (x + 59) / 60; return if (m >= 60 * 24) "${m / 1440}d ${m % 1440 / 60}h" else if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m" }
        val tz = (s.here ?: s.hop?.let { if (s.progress > 0.5f) it.to else it.from })?.tz
        val local = tz?.takeIf { it.isNotBlank() }?.let { runCatching { Instant.ofEpochSecond(now).atZone(ZoneId.of(it)).format(DateTimeFormatter.ofPattern(app.familytv.config.FamilyConfig.timePattern(this), Locale.US)) }.getOrNull() }
        val vals = listOf(
            when { s.isHome -> "Home ❤️"; s.here != null -> s.here.name; s.hop != null -> "In the air ✈️"; else -> "—" },
            s.next?.let { if (it.lat == s.home.lat && it.lon == s.home.lon) "Home" else it.name } ?: "—",
            if (s.arriveAt > now) dur(s.arriveAt - now) else "—",
            if (app.familytv.config.FamilyConfig.fahrenheit(this)) "%,d mi".format((s.distanceKm * 0.621371).toLong()) else "%,d km".format(s.distanceKm.toLong()),
            local ?: "—")
        cells.forEachIndexed { i, (_, v) -> v.text = vals[i] }
        s.counter?.let { (k, v) -> cells[4].first.text = k; cells[4].second.text = v }   // Santa: e.g. presents delivered
        upcoming.removeAllViews()
        upcoming.addView(TextView(this).apply { text = "Upcoming"; textSize = 22f; typeface = marker; setTextColor(0xFFE0483E.toInt()) })
        (s.upcoming.ifEmpty { listOf(if (s.isHome) "${tr.name(this)} is home — no more legs!" else "Nothing planned yet") }).forEach {
            upcoming.addView(TextView(this).apply { text = it; textSize = 17f; typeface = hand; setTextColor(NAVY); maxLines = 1 }) }
    }

    private fun settings() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(px(24), px(8), px(24), 0) }
        val topic = EditText(this).apply { setText(Prefs.topic(this@MainActivity)); textSize = 18f; hint = "ntfy topic" }
        val who = app.familytv.config.FamilyConfig.parentName(this)
        val flights = android.widget.Switch(this).apply { text = "Live flight lookup (optional)"; isChecked = app.familytv.config.FamilyConfig.flightsEnabled(this@MainActivity); textSize = 16f }
        val chip = Switch(this).apply { text = "Show a corner chip on flight days"; isChecked = Prefs.chip(this@MainActivity); textSize = 16f }
        val note = TextView(this).apply { textSize = 13f
            text = if (Settings.canDrawOverlays(this@MainActivity)) "Overlay permission: granted" else "Overlay permission: NOT granted — chip stays hidden until granted" }
        box.addView(TextView(this).apply { text = "ntfy topic for $who (base topic plus -where)" }); box.addView(topic); box.addView(flights); box.addView(chip); box.addView(note)
        box.addView(TextView(this).apply { text = app.familytv.config.FamilyConfig.SCRAPE_TERMS; textSize = 13f })
        AlertDialog.Builder(this).setTitle("Where's $who").setView(box)
            .setPositiveButton("Save") { _, _ ->
                Prefs.setTopic(this, topic.text.toString()); Prefs.setChip(this, chip.isChecked)
                app.familytv.config.FamilyConfig.setFlightsEnabled(this, flights.isChecked)
                startForegroundService(Intent(this, WhereService::class.java).putExtra("reconnect", true)) }
            .setNeutralButton("Overlay permission") { _, _ -> runCatching {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) }
                .onFailure { Toast.makeText(this, "adb shell appops set app.familytv.where SYSTEM_ALERT_WINDOW allow", Toast.LENGTH_LONG).show() } }
            .setNegativeButton("Cancel", null).show()
    }
}
