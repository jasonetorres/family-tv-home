package app.familytv.notes
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.*
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private lateinit var list: LinearLayout
    private lateinit var header: TextView
    private val d by lazy { resources.displayMetrics.density }
    private fun px(v: Int) = (v * d).toInt()

    private lateinit var homeChip: TextView
    private lateinit var grid: FrameLayout
    private lateinit var pager: TextView
    private val polaroids = mutableListOf<Polaroid>()
    private var page = 0
    private var notes: List<Note> = emptyList()
    private val pages get() = maxOf(1, (notes.size + 3) / 4)

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        startForegroundService(Intent(this, NotesService::class.java))
        val root = FrameLayout(this).apply { clipChildren = false
            background = android.graphics.drawable.BitmapDrawable(resources, android.graphics.BitmapFactory.decodeResource(resources, R.drawable.paper)).apply {
                setTileModeXY(android.graphics.Shader.TileMode.REPEAT, android.graphics.Shader.TileMode.REPEAT) } }
        fun lp(w: Int, h: Int, g: Int, l: Int = 0, t: Int = 0, r: Int = 0, bt: Int = 0) =
            FrameLayout.LayoutParams(if (w < 0) w else px(w), if (h < 0) h else px(h), g).apply { setMargins(px(l), px(t), px(r), px(bt)) }
        // Polaroids: left top/bottom, right top/bottom
        listOf(Triple(android.view.Gravity.START, 92, -6f), Triple(android.view.Gravity.START, 300, 5f),
               Triple(android.view.Gravity.END, 120, 5f), Triple(android.view.Gravity.END, 340, -6f)).forEachIndexed { i, (g, top, rot) ->
            val pv = Polaroid(this).apply { rotation = rot }
            polaroids += pv; root.addView(pv, lp(116, 136, g or android.view.Gravity.TOP, 14, top, 14))
        }
        root.addView(Accents(this), lp(-1, -1, 0))
        root.addView(TitleView(this), lp(400, 84, android.view.Gravity.TOP or android.view.Gravity.START, 40, 12))
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false; gravity = android.view.Gravity.CENTER_VERTICAL }
        homeChip = Pill.make(this, "", 0xFFB9D7F7.toInt(), 0xFF5A9BE6.toInt(), "home") {}
        val love = Pill.make(this, "Send love back", 0xFFF9B9CB.toInt(), 0xFFE0483E.toInt(), "plane") {
            Stickers.show(this) }
        val set = Pill.make(this, "Settings", 0xFFFFF1DA.toInt(), 0xFFF5A33B.toInt(), "gear") { settings() }
        listOf(homeChip, love, set).forEach { it.id = View.generateViewId(); bar.addView(it, LinearLayout.LayoutParams(-2, px(44)).apply { marginStart = px(14) }) }
        root.addView(bar, lp(-2, -2, android.view.Gravity.TOP or android.view.Gravity.END, 0, 26, 40))
        grid = FrameLayout(this).apply { clipChildren = false; clipToPadding = false }
        root.addView(grid, lp(-1, -1, 0, 150, 104, 150, 34))
        pager = TextView(this).apply { textSize = 18f; setTextColor(NAVY); typeface = resources.getFont(R.font.patrick); gravity = android.view.Gravity.CENTER }
        header = TextView(this).apply { textSize = 13f; setTextColor(0xFF9A5B3C.toInt()) }
        root.addView(pager, lp(-2, -2, android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL, 0, 0, 0, 6))
        root.addView(header, lp(-2, -2, android.view.Gravity.BOTTOM or android.view.Gravity.START, 40, 0, 0, 8))
        setContentView(root)
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 0)
        render(true)
    }
    override fun onResume() { super.onResume(); NotesService.listener = { page = 0; render(true) }; render(false) }
    override fun onPause() { NotesService.listener = null; super.onPause() }

    private fun render(focus: Boolean, focusIdx: Int = 0) {
        val days = runCatching { ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(Store.homeDate(this))) }.getOrNull()
        homeChip.text = when { days == null || days < 0 -> ""; days == 0L -> "Home today!"; days == 1L -> "Home tomorrow"; else -> "Home in $days days" }
        homeChip.visibility = if (homeChip.text.isBlank()) View.GONE else View.VISIBLE
        header.text = if (!Settings.canDrawOverlays(this)) "pop-ups off: grant overlay permission (see README)" else ""
        notes = Store.notes(this); page = page.coerceIn(0, pages - 1)
        pager.text = if (notes.size > 4) "‹  ${page + 1} / $pages  ›" else ""
        // Polaroids: most recent image attachments
        rotatePhotos()
        grid.removeAllViews()
        val who = app.familytv.config.FamilyConfig.parentName(this)
        val shown = notes.drop(page * 4).take(4).ifEmpty { listOf(Note("empty", System.currentTimeMillis() / 1000, "", "No notes yet — notes from $who show up here", "")) }
        grid.post {
            val gw = grid.width; val gh = grid.height; val cw = (gw - px(28)) / 2; val ch = (gh - px(28)) / 2
            // loose 2x2: top-left wider, offsets like the reference
            val spots = listOf(intArrayOf(0, px(8), cw + px(24), ch), intArrayOf(cw + px(56), 0, cw - px(40), ch - px(10)),
                               intArrayOf(px(22), ch + px(30), cw - px(30), ch - px(14)), intArrayOf(cw - px(10), ch + px(16), cw + px(20), ch - px(6)))
            val views = shown.mapIndexed { i, n ->
                NoteCard(this, n, i).also { v -> v.id = View.generateViewId()
                    val s = spots[i]; grid.addView(v, FrameLayout.LayoutParams(s[2], s[3]).apply { leftMargin = s[0]; topMargin = s[1] }) } }
            views.forEachIndexed { i, v ->
                v.setOnKeyListener { _, k, e ->
                    val ok = k == android.view.KeyEvent.KEYCODE_DPAD_CENTER || k == android.view.KeyEvent.KEYCODE_ENTER || k == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER
                    if (ok && shown[i].id != "empty") {
                        if (e.action == android.view.KeyEvent.ACTION_DOWN && e.repeatCount > 0 && e.isLongPress) confirmDelete(shown[i], i)
                        return@setOnKeyListener true
                    }
                    if (e.action != android.view.KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                    val col = i % 2
                    when {
                        k == android.view.KeyEvent.KEYCODE_DPAD_RIGHT && (col == 1 || i + 1 >= views.size) -> { flip(+1); true }
                        k == android.view.KeyEvent.KEYCODE_DPAD_LEFT && col == 0 -> { flip(-1, true); true }
                        k == android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> { views[i + 1].requestFocus(); true }
                        k == android.view.KeyEvent.KEYCODE_DPAD_LEFT -> { views[i - 1].requestFocus(); true }
                        k == android.view.KeyEvent.KEYCODE_DPAD_DOWN && i + 2 < views.size -> { views[i + 2].requestFocus(); true }
                        k == android.view.KeyEvent.KEYCODE_DPAD_UP && i >= 2 -> { views[i - 2].requestFocus(); true }
                        k == android.view.KeyEvent.KEYCODE_DPAD_UP -> { (if (homeChip.visibility == View.VISIBLE) homeChip else love()).requestFocus(); true }
                        else -> false
                    }
                }
            }
            listOf(homeChip, love(), settingsBtn()).forEach { it.nextFocusDownId = views.first().id }
            if (focus) views.getOrNull(focusIdx.coerceAtMost(views.size - 1))?.requestFocus()
        }
    }
    private val ui = android.os.Handler(android.os.Looper.getMainLooper())
    private var albumUrls: List<String> = emptyList()
    /** Photos change every 2h; picks are seeded by the 2h window so restarts keep the same photos. */
    private val WINDOW = 2 * 60 * 60_000L
    private val rotator = object : Runnable { override fun run() { rotatePhotos()
        ui.postDelayed(this, WINDOW - System.currentTimeMillis() % WINDOW + 1000) } }
    private val albumRefresher = object : Runnable { override fun run() {
        thread { val u = Album.refresh(this@MainActivity); runOnUiThread { val first = albumUrls.isEmpty(); albumUrls = u; if (first) rotatePhotos() } }
        ui.postDelayed(this, 30 * 60_000L) } }
    override fun onStart() { super.onStart(); albumUrls = Album.cached(this); ui.post(albumRefresher); ui.postDelayed(rotator, WINDOW - System.currentTimeMillis() % WINDOW + 1000) }
    override fun onStop() { ui.removeCallbacks(rotator); ui.removeCallbacks(albumRefresher); super.onStop() }
    /** Note photos take priority (fixed); remaining frames get random album photos (crossfaded). */
    private fun rotatePhotos() {
        val noteImgs = notes.filter { it.attachUrl.isNotEmpty() }.map { it.attachUrl }.take(polaroids.size)
        val pool = albumUrls.shuffled(java.util.Random(System.currentTimeMillis() / WINDOW))
        polaroids.forEachIndexed { i, pv ->
            val url = noteImgs.getOrNull(i) ?: pool.getOrNull(i - noteImgs.size)?.let { Album.sized(it) }
            if (url == null) { pv.tag = null; pv.bmp = null; return@forEachIndexed }
            if (pv.tag == url) return@forEachIndexed
            pv.tag = url
            thread { Album.bitmap(url, 480)?.let { b -> runOnUiThread { if (pv.tag == url) pv.bmp = b } } }
        }
    }
    /** Small sticky-note style confirm; Delete removes the note from the board. */
    private fun confirmDelete(n: Note, idx: Int) {
        val dlg = android.app.Dialog(this)
        val f = resources.getFont(R.font.patrick)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = android.view.Gravity.CENTER_HORIZONTAL; clipChildren = false
            setPadding(px(28), px(22), px(28), px(22))
            background = android.graphics.drawable.GradientDrawable().apply { setColor(0xFFFFF8E7.toInt()); cornerRadius = px(18).toFloat(); setStroke(px(3), 0xFFFFE27A.toInt()) } }
        box.addView(TextView(this).apply { text = "Delete this note?"; typeface = f; textSize = 28f; setTextColor(NAVY); gravity = android.view.Gravity.CENTER })
        val snip = n.title.ifBlank { n.body }.replace("\n", " ").let { if (it.length > 50) it.take(50).trimEnd() + "…" else it }
        box.addView(TextView(this).apply { text = snip; typeface = f; textSize = 16f; setTextColor(0xAA1B2A4A.toInt()); gravity = android.view.Gravity.CENTER; setPadding(0, px(4), 0, px(16)) })
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false; gravity = android.view.Gravity.CENTER }
        val del = Pill.make(this, "Delete", 0xFFF9B9CB.toInt(), RED, "heart") {
            Store.remove(this, n.id); dlg.dismiss(); render(true, idx) }
        val cancel = Pill.make(this, "Cancel", 0xFFB9D7F7.toInt(), 0xFF5A9BE6.toInt(), "home") { dlg.dismiss() }
        listOf(cancel, del).forEach { row.addView(it, LinearLayout.LayoutParams(-2, px(44)).apply { marginStart = px(10); marginEnd = px(10) }) }
        box.addView(row)
        dlg.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dlg.setContentView(box)
        dlg.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(0))
        dlg.show(); cancel.requestFocus()   // safe default
    }
    private fun love() = ((homeChip.parent as LinearLayout).getChildAt(1))
    private fun settingsBtn() = ((homeChip.parent as LinearLayout).getChildAt(2))
    private fun flip(dir: Int, toLast: Boolean = false) {
        val next = page + dir
        if (next !in 0 until pages) { grid.animate().translationX(px(-14 * dir).toFloat()).setDuration(80).withEndAction { grid.animate().translationX(0f).setDuration(120).start() }.start(); return }
        grid.animate().translationX(px(-500 * dir).toFloat()).alpha(0f).setDuration(220).withEndAction {
            page = next; render(true, if (toLast) 1 else 0)
            grid.translationX = px(500 * dir).toFloat(); grid.animate().translationX(0f).alpha(1f).setDuration(240).start()
        }.start()
    }
    private fun settings() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(px(24), px(8), px(24), 0) }
        val topic = EditText(this).apply { setText(Store.topic(this@MainActivity)); textSize = 18f; hint = "ntfy topic" }
        val photos = android.widget.Switch(this).apply { text = "Photo frames"; textSize = 18f; isChecked = Album.enabled(this@MainActivity) }
        val album = EditText(this).apply { setText(Album.link(this@MainActivity)); textSize = 16f; hint = "shared album link, optional" }
        box.addView(TextView(this).apply { text = "ntfy topic (also ${Store.topic(this@MainActivity)}-replies)" }); box.addView(topic)
        box.addView(photos)
        box.addView(TextView(this).apply { text = "Google Photos shared album (optional, off by default)" }); box.addView(album)
        box.addView(TextView(this).apply { text = app.familytv.config.FamilyConfig.SCRAPE_TERMS; textSize = 13f })
        val sounds = android.widget.Switch(this).apply { text = "Sounds"; textSize = 18f; isChecked = Sfx.enabled(this@MainActivity)
            setOnCheckedChangeListener { _, on -> Sfx.setEnabled(this@MainActivity, on) } }
        box.addView(sounds)
        AlertDialog.Builder(this).setTitle("Settings").setView(box)
            .setPositiveButton("Save") { _, _ ->
                Store.setTopic(this, topic.text.toString())
                app.familytv.config.FamilyConfig.setPhotosEnabled(this, photos.isChecked)
                if (album.text.toString().trim() != Album.link(this)) { Album.setLink(this, album.text.toString()); albumUrls = emptyList() }
                ui.removeCallbacks(albumRefresher); ui.post(albumRefresher)
                startForegroundService(Intent(this, NotesService::class.java).putExtra("reconnect", true)) }
            .setNeutralButton("Overlay permission") { _, _ -> runCatching {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) }
                .onFailure { Toast.makeText(this, "Use adb appops (see README)", Toast.LENGTH_LONG).show() } }
            .setNegativeButton("Cancel", null).show()
    }
}
