package app.familytv.ticker
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.*
/** No UI: flips the mode, toasts, finishes immediately. */
class ToggleActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val m = Prefs.toggle(this)
        Toast.makeText(applicationContext, if (m == Mode.OFF) "Ticker OFF" else "Ticker ON", Toast.LENGTH_SHORT).show()
        finish(); overridePendingTransition(0, 0)
    }
}
class SettingsActivity : Activity() {
    private var refresher: (() -> Unit)? = null
    override fun onResume() { super.onResume(); refresher?.invoke() }
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(96, 64, 96, 64); gravity = Gravity.CENTER_HORIZONTAL }
        fun btn(t: String, f: () -> Unit) = Button(this).apply { text = t; textSize = 20f; setOnClickListener { f() } }.also { root.addView(it) }
        root.addView(TextView(this).apply { text = "Score Ticker"; textSize = 32f })
        val status = TextView(this).apply { textSize = 18f }; root.addView(status)
        fun refresh() { status.text = "Mode: ${Prefs.mode(this)}   Leagues: ${Prefs.leagues(this)}\n" +
            "Score alerts: " + (if (Prefs.alerts(this)) "ON" else "OFF") + "\nGame Day teams: " + Prefs.favs(this).sorted().joinToString().ifEmpty { "none" } +
            if (!Settings.canDrawOverlays(this)) "\n⚠ Overlay permission missing (see README: adb appops)" else "" }
        mapOf(Mode.ALWAYS to "Always on", Mode.LIVE_ONLY to "Only during live games", Mode.OFF to "Off").forEach { (m, t) ->
            btn("Mode: $t") {
                val go = { Prefs.setMode(this, m, "settings"); if (m != Mode.OFF) Prefs.setLastOn(this, m); Prefs.apply(this); refresh() }
                if (m == Mode.OFF && Prefs.mode(this) != Mode.OFF) android.app.AlertDialog.Builder(this)
                    .setTitle("Turn the ticker off?").setMessage("This hides the scrolling bar. Score pop-ups stay on (see Score alerts).")
                    .setNegativeButton("Keep it on", null).setPositiveButton("Turn off") { _, _ -> go() }.show().also {
                        it.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).requestFocus() }
                else go() } }
        mapOf(Leagues.BOTH to "Both", Leagues.NFL to "NFL only", Leagues.NBA to "NBA only").forEach { (l, t) ->
            btn("Leagues: $t") { Prefs.setLeagues(this, l); Prefs.apply(this); refresh() } }
        btn("Score alerts (pop-ups): on/off") { Prefs.setAlerts(this, !Prefs.alerts(this), "settings"); Prefs.apply(this); refresh() }
        btn("🏈🏀 Game Day: pick favorite teams") { startActivity(Intent(this, TeamsActivity::class.java)) }
        root.addView(Switch(this).apply { text = "Sounds"; textSize = 20f; isChecked = Sfx.enabled(this@SettingsActivity)
            setOnCheckedChangeListener { _, on -> Sfx.setEnabled(this@SettingsActivity, on) } })
        btn("Open overlay permission settings") {
            runCatching { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) }
                .onFailure { Toast.makeText(this, "Not available on this TV; use adb appops", Toast.LENGTH_LONG).show() } }
        this.refresher = ::refresh
        refresh(); setContentView(ScrollView(this).apply { addView(root) })
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 0)
    }
}

/** D-pad friendly multi-select list of every NFL + NBA team; saves on each toggle. */
class TeamsActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val items = League.values().flatMap { l -> l.teams.map { "${l.name}:$it" } }
        val list = ListView(this).apply {
            choiceMode = ListView.CHOICE_MODE_MULTIPLE; itemsCanFocus = false
            adapter = ArrayAdapter(this@TeamsActivity, android.R.layout.simple_list_item_multiple_choice,
                items.map { it.replace(":", "  ·  ") })
            val favs = Prefs.favs(this@TeamsActivity)
            items.forEachIndexed { i, k -> setItemChecked(i, k in favs) }
            setOnItemClickListener { _, _, _, _ ->
                val sel = items.filterIndexed { i, _ -> checkedItemPositions.get(i) }.toSet()
                Prefs.setFavs(this@TeamsActivity, sel)
                if (Prefs.shouldRun(this@TeamsActivity)) Prefs.apply(this@TeamsActivity)
            }
        }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(96, 48, 96, 24) }
        root.addView(TextView(this).apply { text = "Game Day teams — OK to check/uncheck, Back when done"; textSize = 24f; setPadding(0, 0, 0, 16) })
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root); list.requestFocus()
    }
}
