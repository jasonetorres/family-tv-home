package app.familytv.ticker
import android.content.Context
enum class Mode { ALWAYS, LIVE_ONLY, OFF }
enum class League(val tag: String, val url: String, val teams: List<String>) {
    NFL("NFL", "https://site.api.espn.com/apis/site/v2/sports/football/nfl/scoreboard", listOf("ARI","ATL","BAL","BUF","CAR","CHI","CIN","CLE","DAL","DEN","DET","GB","HOU","IND","JAX","KC","LAC","LAR","LV","MIA","MIN","NE","NO","NYG","NYJ","PHI","PIT","SEA","SF","TB","TEN","WSH")),
    NBA("NBA", "https://site.api.espn.com/apis/site/v2/sports/basketball/nba/scoreboard", listOf("ATL","BKN","BOS","CHA","CHI","CLE","DAL","DEN","DET","GS","HOU","IND","LAC","LAL","MEM","MIA","MIL","MIN","NO","NY","OKC","ORL","PHI","PHX","POR","SA","SAC","TOR","UTAH","WSH"))
}
enum class Leagues(val set: List<League>) { BOTH(listOf(League.NFL, League.NBA)), NFL(listOf(League.NFL)), NBA(listOf(League.NBA)) }
object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("ticker", Context.MODE_PRIVATE)
    fun mode(c: Context) = runCatching { Mode.valueOf(sp(c).getString("mode", "LIVE_ONLY")!!) }.getOrDefault(Mode.LIVE_ONLY)
    fun setMode(c: Context, m: Mode, why: String = "?") { EventLog.add(c, "mode ${mode(c)} -> $m via $why"); sp(c).edit().putString("mode", m.name).apply() }
    fun lastOn(c: Context) = runCatching { Mode.valueOf(sp(c).getString("lastOn", "ALWAYS")!!) }.getOrDefault(Mode.ALWAYS)
    fun setLastOn(c: Context, m: Mode) = sp(c).edit().putString("lastOn", m.name).apply()
    fun leagues(c: Context) = runCatching { Leagues.valueOf(sp(c).getString("leagues", null) ?: app.familytv.config.FamilyConfig.leagues(c)) }.getOrDefault(Leagues.BOTH)
    fun setLeagues(c: Context, l: Leagues) = sp(c).edit().putString("leagues", l.name).apply()
    /** Per-league favorite; "" = none. NFL key keeps the v1 "fav" key so upgrades retain it. */
    private fun favKey(l: League) = if (l == League.NFL) "fav" else "fav_${l.name}"
    fun favorite(c: Context, l: League) = sp(c).getString(favKey(l), "") ?: ""
    fun setFavorite(c: Context, l: League, t: String) = sp(c).edit().putString(favKey(l), t.uppercase()).apply()
    /** Game Day favorites: set of "LEAGUE:TEAM", for example "NFL:GB". Legacy single favorites are migrated in. */
    fun favs(c: Context): Set<String> {
        val sp = sp(c)
        if (!sp.contains("favs")) {
            val legacy = League.values().mapNotNull { l -> sp.getString(favKey(l), "")!!.takeIf { it.isNotEmpty() }?.let { "${l.name}:$it" } }.toSet()
                .ifEmpty { app.familytv.config.FamilyConfig.favorites(c) }
            sp.edit().putStringSet("favs", legacy).apply(); return legacy
        }
        return sp.getStringSet("favs", emptySet())!!.toSet()
    }
    fun setFavs(c: Context, s: Set<String>) = sp(c).edit().putStringSet("favs", HashSet(s)).apply()
    fun isFav(c: Context, l: League, team: String) = team.isNotEmpty() && "${l.name}:$team" in favs(c)
    private val alias = mapOf("NYK" to "NY", "GSW" to "GS", "SAS" to "SA", "NOP" to "NO", "UTA" to "UTAH", "WAS" to "WSH", "PHO" to "PHX", "BRK" to "BKN", "JAC" to "JAX", "LA" to "LAR", "OAK" to "LV", "GNB" to "GB", "KAN" to "KC", "NWE" to "NE", "SFO" to "SF", "TAM" to "TB")
    /** Parses "BUF,NYK" or "NFL:CHI,NBA:CHI". Unprefixed codes apply to every league that has that team. Returns parsed set + unknown codes. */
    fun parseTeams(raw: String): Pair<Set<String>, List<String>> {
        val out = HashSet<String>(); val bad = ArrayList<String>()
        raw.split(',', ' ', ';').map { it.trim().uppercase() }.filter { it.isNotEmpty() }.forEach { tok ->
            val (lp, code) = if (':' in tok) tok.substringBefore(':') to tok.substringAfter(':') else null to tok
            val leagues = if (lp != null) League.values().filter { it.name == lp } else League.values().toList()
            val hits = leagues.mapNotNull { l -> listOf(code, alias[code]).firstOrNull { it != null && it in l.teams }?.let { "${l.name}:$it" } }
            if (hits.isEmpty()) bad += tok else out += hits
        }
        return out to bad
    }
    /** Score pop-ups, independent of the ticker bar mode. Default on. */
    fun alerts(c: Context) = sp(c).getBoolean("alerts", true)
    fun setAlerts(c: Context, on: Boolean, why: String = "?") { EventLog.add(c, "alerts -> $on via $why"); sp(c).edit().putBoolean("alerts", on).apply() }
    /** Service runs whenever the bar is on OR alerts are on. */
    fun shouldRun(c: Context) = mode(c) != Mode.OFF || alerts(c)
    fun toggle(c: Context): Mode {
        val next = if (mode(c) == Mode.OFF) lastOn(c) else { setLastOn(c, mode(c)); Mode.OFF }
        setMode(c, next, "toggle"); apply(c); return next
    }
    fun apply(c: Context) {
        val i = android.content.Intent(c, TickerService::class.java)
        if (!shouldRun(c)) c.stopService(i) else c.startForegroundService(i)
    }
}

/** Persistent event log (filesDir/events.log, ~200KB cap) so mode changes and scores survive logcat rotation. */
object EventLog {
    fun add(c: Context, msg: String) {
        android.util.Log.i("NflTicker", msg)
        runCatching {
            val f = java.io.File(c.filesDir, "events.log")
            if (f.length() > 200_000) f.writeText(f.readLines().takeLast(500).joinToString("\n") + "\n")
            f.appendText(java.text.SimpleDateFormat("MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date()) + " " + msg + "\n")
        }
    }
}
/** Every 15 min: restart the ticker if it should be running but isn't (killed by the TV's process manager, etc.). */
object Watchdog {
    fun schedule(c: Context) {
        val am = c.getSystemService(android.app.AlarmManager::class.java)
        val pi = android.app.PendingIntent.getBroadcast(c, 7, android.content.Intent(c, BootReceiver::class.java).setAction("app.familytv.ticker.WATCHDOG"),
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT)
        am.setInexactRepeating(android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP, android.os.SystemClock.elapsedRealtime() + 15 * 60_000L, 15 * 60_000L, pi)
    }
}
