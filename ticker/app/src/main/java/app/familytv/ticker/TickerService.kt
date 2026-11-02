package app.familytv.ticker
import android.app.*
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.widget.FrameLayout
import android.graphics.Typeface
import android.os.*
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import kotlin.concurrent.thread

class TickerService : Service() {
    private val h = Handler(Looper.getMainLooper())
    private var wm: WindowManager? = null
    private var view: MarqueeView? = null
    private var lastItems: List<String> = emptyList()
    private var lastGameDay: Int? = -1
    private val games = HashMap<League, List<Game>>()
    private val backoff = HashMap<League, Long>()
    private val polls = HashMap<League, Runnable>()
    /** "league:gameId:team" -> last seen score, for Game Day score-change detection. */
    private val lastScores = HashMap<String, Int>()
    private var celebrating: View? = null

    override fun onBind(i: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate()
        Sfx.preload(this, R.raw.td_cheer)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("t", "Score Ticker", NotificationManager.IMPORTANCE_MIN))
        val n = Notification.Builder(this, "t").setContentTitle("Score Ticker running")
            .setSmallIcon(android.R.drawable.star_on).build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1, n)
        wm = getSystemService(WindowManager::class.java)
        running = true; Watchdog.schedule(this); EventLog.add(this, "service started")
    }
    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        if (i?.action == "INJECT") {
            val team = i.getStringExtra("team")?.uppercase() ?: "PIT"; val d = i.getIntExtra("delta", 7)
            val g = games[League.NFL]?.firstOrNull { it.has(team) && it.state != "pre" }
            if (g != null) { val sc = (if (team == g.home) g.homeScore else g.awayScore).toIntOrNull() ?: 0
                val key = "NFL:${g.id}:$team"; lastScores[key] = sc - d; EventLog.add(this, "INJECT baseline $key = ${sc - d} (real $sc)")
                polls[League.NFL]?.let { h.removeCallbacks(it) }; poll(League.NFL) }
            else EventLog.add(this, "INJECT: no current $team game")
            return START_STICKY
        }
        if (i?.action == "CELEBRATE") {
            val team = i.getStringExtra("team")?.uppercase() ?: "BUF"
            val delta = i.getIntExtra("delta", 6); val ctype = i.getStringExtra("type")
            // Prefer the real live game for this team; demo data (clearly labeled TEST) only if none is live.
            val live = games[League.NFL]?.firstOrNull { it.state == "in" && it.has(team) }
            if (live != null) {
                android.util.Log.i("NflTicker", "CELEBRATE test using live game ${live.label()}")
                celebrate(live, team, delta, ctype ?: scoreType(delta, false))
            } else {
                val g = Game(League.NFL, "BUF", "21", "MIA", "14", "in", "0:00 - TEST", "demo", "Bills", "Dolphins",
                    "00338d", "008e97", LogoCache.espn("BUF"), LogoCache.espn("MIA"), "d50a0a", "fc4c02")
                val demo = if (team == g.away || team == g.home) g else g.copy(away = team, awayName = teamNames[team] ?: team,
                    awayColor = "101820", awayAlt = "ffb612", awayLogo = LogoCache.espn(team))
                android.util.Log.i("NflTicker", "CELEBRATE test: no live $team game, using TEST demo")
                celebrate(demo, if (team == "MIA") "MIA" else demo.away, delta, "TEST " + (ctype ?: scoreType(delta, false)))
            }
            if (!Prefs.shouldRun(this)) h.postDelayed({ stopSelf() }, CELEBRATE_MS + 3000)
            return START_NOT_STICKY
        }
        if (!Prefs.shouldRun(this)) { stopSelf(); return START_NOT_STICKY }
        h.removeCallbacksAndMessages(null); polls.clear(); games.clear()
        Prefs.leagues(this).set.forEach { backoff[it] = IDLE_MIN; poll(it) }
        render(); return START_STICKY
    }
    /** Independent schedule per league: 30s while that league has a live game, else 2→15 min backoff. */
    private fun poll(l: League) {
        thread {
            val g = runCatching { Espn.fetch(l) }.onFailure { android.util.Log.w("NflTicker", "poll $l failed: $it") }.getOrNull()
            android.util.Log.i("NflTicker", "poll $l: " + (g?.filter { it.state == "in" }?.joinToString { it.label() } ?: "error"))
            h.post {
                if (g != null) { detectScores(l, g); games[l] = g } else if (games[l] == null) games[l] = emptyList()
                val live = games[l]?.any { it.state == "in" } == true
                val favLive = games[l]?.any { it.state == "in" && favTeams(it).isNotEmpty() } == true
                val b = backoff[l] ?: IDLE_MIN
                val delay = if (favLive) FAV_LIVE_MS else if (live) LIVE_MS else b
                backoff[l] = if (live) IDLE_MIN else (b * 2).coerceAtMost(IDLE_MAX)
                val r = Runnable { poll(l) }; polls[l] = r; h.postDelayed(r, delay)
                render()
            }
        }
    }
    private fun favTeams(g: Game) = listOf(g.away, g.home).filter { Prefs.isFav(this, g.league, it) }
    private fun detectScores(l: League, list: List<Game>) {
        val sp = getSharedPreferences("scores", MODE_PRIVATE); val ed = sp.edit()
        for (g in list) for ((team, sc) in listOf(g.away to g.awayScore, g.home to g.homeScore)) {
            val key = "${l.name}:${g.id}:$team"; val now = sc.toIntOrNull() ?: continue
            // Baseline survives restarts (persisted), so a score while the service was down still alerts on the next poll.
            val prev = lastScores[key] ?: sp.getInt(key, -1).takeIf { it >= 0 }
            lastScores[key] = now; ed.putInt(key, now)
            if (prev == null || now <= prev || g.state == "pre" || !Prefs.isFav(this, l, team)) continue
            val d = now - prev
            EventLog.add(this, "score $team +$d ($prev->$now) ${g.label()}")
            if (!Prefs.alerts(this)) continue
            if (l == League.NFL) {
                val recentTd = (lastTdAt[key] ?: 0L) > SystemClock.uptimeMillis() - 10 * 60_000L
                if (d >= 6) lastTdAt[key] = SystemClock.uptimeMillis()
                celebrate(g, team, d, scoreType(d, recentTd))
            } else celebrate(g, team, d, "SCORE")
        }
        ed.apply()
    }
    private val lastTdAt = HashMap<String, Long>()
    /** NFL score type from the score delta (ESPN scoreboard has no per-play type here). */
    private fun scoreType(d: Int, recentTd: Boolean) = when (d) {
        1 -> "EXTRA POINT"; 2 -> if (recentTd) "2-PT CONVERSION" else "SAFETY"; 3 -> "FIELD GOAL"
        in 6..8 -> "TOUCHDOWN"; else -> "SCORE" }   // e.g. +10 = two scores between polls; still alerts
    private val teamNames = mapOf("PIT" to "Steelers", "BUF" to "Bills", "MIA" to "Dolphins")
    private fun parseColor(hex: String, fallback: Int) = runCatching { Color.parseColor("#" + hex.removePrefix("#")) }.getOrDefault(fallback)

    /** Full-screen, non-interactive touchdown banner (logo, brush text, +delta, mini scoreboard) for ~4s. Never focusable/touchable. */
    private fun celebrate(g: Game, team: String, delta: Int, kind: String = if (delta in 6..8) "TOUCHDOWN" else "SCORE") {
        if (!Settings.canDrawOverlays(this)) { EventLog.add(this, "banner blocked: no overlay permission"); return }
        Sfx.play(this, R.raw.td_cheer, if (kind == "TOUCHDOWN") 2600 else 1200)   // sound immediately; logos may take a moment on first download
        thread {
            val isHome = team == g.home
            val awayLogo = LogoCache.get(this, g.awayLogo.ifBlank { LogoCache.espn(g.away) })
            val homeLogo = LogoCache.get(this, g.homeLogo.ifBlank { LogoCache.espn(g.home) })
            val (period, clock) = g.detail.split(" - ").let { if (it.size == 2) it[1] to it[0] else g.detail to "" }
            val ac = parseColor(g.awayColor, 0xFF1565C0.toInt()); val hc = parseColor(g.homeColor, 0xFF2E7D32.toInt())
            val d = TdData(team, if (isHome) g.homeName else g.awayName, if (isHome) hc else ac,
                parseColor(if (isHome) g.homeAlt else g.awayAlt, 0xFFD50A0A.toInt()), if (isHome) homeLogo else awayLogo, delta,
                g.away, g.awayScore, ac, awayLogo, g.home, g.homeScore, hc, homeLogo, period, clock,
                kind)
            h.post { showBanner(d) }
        }
    }
    private fun showBanner(d: TdData) {
        h.removeCallbacksAndMessages(BANNER_TOKEN)
        celebrating?.let { runCatching { wm?.removeViewImmediate(it) } }; celebrating = null
        val root = FrameLayout(this)
        root.addView(ConfettiView(this, d.teamColor, d.teamAlt).also { it.start() }, FrameLayout.LayoutParams(-1, -1))
        val bw = (resources.displayMetrics.widthPixels * 0.78).toInt(); val bh = (bw * TdBannerView.H / TdBannerView.W).toInt()
        val banner = TdBannerView(this, d).apply { scaleX = 0.2f; scaleY = 0.2f; alpha = 0f; rotation = -12f }
        root.addView(banner, FrameLayout.LayoutParams(bw, bh, Gravity.CENTER))
        val lp = WindowManager.LayoutParams(-1, -1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT)
        runCatching { wm?.addView(root, lp) }.onFailure { EventLog.add(this, "banner addView failed: $it"); return }
        EventLog.add(this, "banner shown: ${d.headline} ${d.team} +${d.delta}")
        celebrating = root
        banner.animate().scaleX(1f).scaleY(1f).alpha(1f).rotation(-2f).setDuration(550)
            .setInterpolator(android.view.animation.OvershootInterpolator(2.2f)).start()
        h.postAtTime({ banner.animate().translationX(resources.displayMetrics.widthPixels.toFloat()).alpha(0f).rotation(6f)
            .setInterpolator(android.view.animation.AccelerateInterpolator()).setDuration(450).start() }, BANNER_TOKEN, android.os.SystemClock.uptimeMillis() + CELEBRATE_MS)
        h.postAtTime({ runCatching { wm?.removeViewImmediate(root) }; if (celebrating === root) celebrating = null }, BANNER_TOKEN, android.os.SystemClock.uptimeMillis() + CELEBRATE_MS + 500)
    }

    private fun render() {
        val mode = Prefs.mode(this)
        if (Prefs.leagues(this).set.any { games[it] == null }) return   // wait for every league's first poll
        val all = Prefs.leagues(this).set.flatMap { games[it] ?: emptyList() }
        val live = all.any { it.state == "in" }
        val isFav = { g: Game -> favTeams(g).isNotEmpty() }
        val shown = Ticker.order(all.filter { mode == Mode.ALWAYS || it.state != "pre" }, isFav)
        val visible = when (mode) { Mode.ALWAYS -> shown.isNotEmpty(); Mode.LIVE_ONLY -> live; Mode.OFF -> false }
        val pinned = shown.firstOrNull()?.takeIf { it.state == "in" && isFav(it) }
        val gameDayColor = pinned?.let { g -> favTeams(g).first().let { t -> parseColor(if (t == g.home) g.homeColor else g.awayColor, 0xFF1565C0.toInt()) } }
        val now = System.currentTimeMillis()
        val items = shown.map { TickItem("${it.league}:${it.id}", (if (it === pinned) "🔥 GAME DAY  " else if (isFav(it)) "★ " else "") + it.label(now, app.familytv.config.FamilyConfig.clock12(this)), it.isLive) }
        if (visible) show(gameDayColor, items) else hide()
    }
    private fun show(gameDay: Int?, items: List<TickItem>) {
        if (!Settings.canDrawOverlays(this)) return
        val v = view ?: MarqueeView(this).also { tv ->
            val lp = WindowManager.LayoutParams(
                (resources.displayMetrics.widthPixels * 0.8).toInt(), WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT)
            lp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; lp.y = 24
            wm?.addView(tv, lp); view = tv; lastGameDay = -1
        }
        v.visibility = View.VISIBLE
        if (gameDay != lastGameDay) {   // restyle only when Game Day state/color changes
            lastGameDay = gameDay
            val tf = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            if (gameDay != null) {
                v.setStyle(24f, Color.WHITE, tf); v.setPadding(48, 20, 48, 20)
                v.background = GradientDrawable().apply { cornerRadius = 999f; setColor((gameDay and 0x00FFFFFF) or 0xEE000000.toInt()); setStroke(4, Color.WHITE) }
            } else { v.setStyle(15f, 0xFFE6E1E5.toInt(), tf); v.setPadding(36, 12, 36, 12); v.setBackgroundResource(R.drawable.pill) }
            (v.layoutParams as? WindowManager.LayoutParams)?.let { lp ->
                val w = (resources.displayMetrics.widthPixels * (if (gameDay != null) 0.92 else 0.8)).toInt()
                if (lp.width != w) { lp.width = w; runCatching { wm?.updateViewLayout(v, lp) } } }
        }
        v.update(items)
        android.util.Log.d("NflTicker", "bar update items=${items.size} live=${items.count { it.live }} first=${items.firstOrNull()?.text} loops=${v.loops}")
    }
    private fun hide() { view?.visibility = View.GONE }
    override fun onDestroy() {
        running = false; EventLog.add(this, "service stopped")
        h.removeCallbacksAndMessages(null); view?.let { runCatching { wm?.removeView(it) } }; view = null
        celebrating?.let { runCatching { wm?.removeView(it) } }; celebrating = null; super.onDestroy()
    }
    companion object { @Volatile var running = false; const val LIVE_MS = 30_000L; const val FAV_LIVE_MS = 15_000L; const val CELEBRATE_MS = 4_000L; private val BANNER_TOKEN = Any(); const val IDLE_MIN = 120_000L; const val IDLE_MAX = 900_000L }
}

/** Simple confetti burst drawn with Canvas; ~4s, no input handling. */
class ConfettiView(c: android.content.Context, teamColor: Int, alt: Int) : View(c) {
    private val colors = intArrayOf(teamColor, alt, Color.WHITE, teamColor, alt)
    private class P(var x: Float, var y: Float, var vx: Float, var vy: Float, var rot: Float, val vr: Float, val color: Int, val size: Float)
    private val parts = ArrayList<P>(); private val paint = Paint(Paint.ANTI_ALIAS_FLAG); private var last = 0L
    fun start() { post {
        val r = java.util.Random(); val cx = width / 2f; val cy = height / 2f
        repeat(220) { val a = r.nextDouble() * Math.PI * 2; val sp = 400 + r.nextFloat() * 1100
            parts += P(cx, cy, (Math.cos(a) * sp).toFloat(), (Math.sin(a) * sp).toFloat() - 500, r.nextFloat() * 360, r.nextFloat() * 720 - 360,
                colors[r.nextInt(colors.size)], 10 + r.nextFloat() * 14) }
        last = System.nanoTime(); invalidate() } }
    override fun onDraw(cv: Canvas) {
        val now = System.nanoTime(); val dt = ((now - last) / 1e9f).coerceAtMost(0.05f); last = now
        for (p in parts) {
            p.vy += 900 * dt; p.vx *= 0.99f; p.x += p.vx * dt; p.y += p.vy * dt; p.rot += p.vr * dt
            paint.color = p.color; cv.save(); cv.rotate(p.rot, p.x, p.y)
            cv.drawRoundRect(p.x - p.size * 1.6f, p.y - p.size / 5, p.x + p.size * 1.6f, p.y + p.size / 5, 6f, 6f, paint); cv.restore()
        }
        if (parts.any { it.y < height + 50 }) postInvalidateOnAnimation()
    }
}
