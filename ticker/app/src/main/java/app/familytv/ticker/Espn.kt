package app.familytv.ticker
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
data class Game(val league: League, val away: String, val awayScore: String, val home: String, val homeScore: String,
                val state: String, val detail: String, val id: String = "$away@$home",
                val awayName: String = away, val homeName: String = home,
                val awayColor: String = "", val homeColor: String = "",
                val awayLogo: String = "", val homeLogo: String = "",
                val awayAlt: String = "", val homeAlt: String = "",
                val startMs: Long = 0L, val period: Int = 0, val clock: String = "") {
    fun has(t: String) = t.isNotEmpty() && (away == t || home == t)
    val isLive get() = state == "in"
    /** Log/ticker text (without the LIVE tag, which the bar draws as a red pill). */
    fun label(now: Long = System.currentTimeMillis(), clock12: Boolean = true): String = "[${league.tag}] " + when (state) {
        "pre" -> "$away @ $home  ${Ticker.startText(startMs, now, detail, clock12)}"
        "in" -> "$away $awayScore  $home $homeScore  ·  ${Ticker.liveStatus(this)}"
        else -> "$away $awayScore  $home $homeScore  ·  ${detail.ifBlank { "Final" }}"
    }
}

/** Pure (Android-free) ordering + formatting, unit-tested against live ESPN JSON. */
object Ticker {
    val ET: java.time.ZoneId = java.time.ZoneId.of("America/New_York")
    private val DAY = java.time.format.DateTimeFormatter.ofPattern("EEE M/d", java.util.Locale.US)
    /** Live games first, then upcoming (soonest first), then finals. Favorites first inside each group. */
    fun order(games: List<Game>, isFav: (Game) -> Boolean): List<Game> = games.sortedWith(
        compareBy<Game>({ when (it.state) { "in" -> 0; "pre" -> 1; else -> 2 } }, { !isFav(it) },
            { if (it.state == "in") it.league.ordinal else 0 },   // live: NFL ahead of NBA
            { if (it.state == "post") -it.startMs else it.startMs }, { it.league.ordinal }))
    /** "8:15 PM ET" today, else "Sun 10/26 1:00 PM ET". 12-hour by default, Eastern time. */
    fun startText(startMs: Long, now: Long, fallback: String, clock12: Boolean = true): String {
        if (startMs <= 0L) return fallback
        val t = java.time.Instant.ofEpochMilli(startMs).atZone(ET)
        val today = java.time.Instant.ofEpochMilli(now).atZone(ET).toLocalDate()
        val pattern = if (clock12) "h:mm a" else "HH:mm"
        val time = java.time.format.DateTimeFormatter.ofPattern(pattern, java.util.Locale.US).format(t) + " ET"
        return if (t.toLocalDate() == today) time else DAY.format(t) + " " + time
    }
    /** "Q1 14:55" (NFL) / "Q2 5:32" (NBA), "OT 3:10", "Halftime", "End Q3". */
    fun liveStatus(g: Game): String {
        val d = g.detail
        if (d.contains("half", true) || d.startsWith("End", true) || d.contains("delay", true)) return d
        val p = when { g.period <= 0 -> null; g.period <= 4 -> "Q${g.period}"; g.period == 5 -> "OT"; else -> "${g.period - 4}OT" }
        return if (p != null && g.clock.isNotBlank()) "$p ${g.clock}" else d
    }
    fun parseIso(s: String): Long = runCatching { java.time.OffsetDateTime.parse(s).toInstant().toEpochMilli() }.getOrDefault(0L)
}
object Espn {
    fun fetch(l: League): List<Game> {
        val c = URL(l.url).openConnection() as HttpURLConnection
        c.connectTimeout = 10000; c.readTimeout = 10000; c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android TV) ScoreTicker/4.3"); c.useCaches = false
        return try { parse(l, c.inputStream.bufferedReader().readText()) } finally { c.disconnect() }
    }
    fun parse(l: League, json: String): List<Game> {
        val ev = JSONObject(json).optJSONArray("events") ?: return emptyList()
        return (0 until ev.length()).mapNotNull { i ->
            val e = ev.getJSONObject(i)
            val cs = e.getJSONArray("competitions").getJSONObject(0).getJSONArray("competitors")
            var a: JSONObject? = null; var h: JSONObject? = null
            for (j in 0 until cs.length()) { val o = cs.getJSONObject(j); if (o.optString("homeAway") == "home") h = o else a = o }
            if (a == null || h == null) return@mapNotNull null
            val st = e.getJSONObject("status"); val type = st.getJSONObject("type")
            val at = a.getJSONObject("team"); val ht = h.getJSONObject("team")
            Game(l, at.optString("abbreviation"), a.optString("score", "0"),
                 ht.optString("abbreviation"), h.optString("score", "0"),
                 type.optString("state"), type.optString("shortDetail"), e.optString("id", ""),
                 at.optString("name", at.optString("abbreviation")), ht.optString("name", ht.optString("abbreviation")),
                 at.optString("color"), ht.optString("color"), at.optString("logo"), ht.optString("logo"),
                 at.optString("alternateColor"), ht.optString("alternateColor"),
                 Ticker.parseIso(e.optString("date")), st.optInt("period", 0), st.optString("displayClock", ""))
        }
    }
}
