package app.familytv.where
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.*

/** Itineraries sent with "/trip <title>" + one leg per line. Times are local to the airport (flights) / stay city. */
object Trips {
    /** IATA -> (city, timezone). */
    val AIRPORTS: Map<String, Pair<String, String>> = mapOf(
        "ROC" to ("Rochester" to "America/New_York"), "DTW" to ("Detroit" to "America/New_York"), "AMS" to ("Amsterdam" to "Europe/Amsterdam"),
        "PHX" to ("Phoenix" to "America/Phoenix"), "TUS" to ("Tucson" to "America/Phoenix"), "AZA" to ("Mesa" to "America/Phoenix"), "FLG" to ("Flagstaff" to "America/Phoenix"),
        "ATL" to ("Atlanta" to "America/New_York"), "JFK" to ("New York" to "America/New_York"), "LGA" to ("New York" to "America/New_York"), "EWR" to ("Newark" to "America/New_York"),
        "BOS" to ("Boston" to "America/New_York"), "PHL" to ("Philadelphia" to "America/New_York"), "DCA" to ("Washington" to "America/New_York"), "IAD" to ("Washington" to "America/New_York"),
        "BWI" to ("Baltimore" to "America/New_York"), "CLT" to ("Charlotte" to "America/New_York"), "MCO" to ("Orlando" to "America/New_York"), "MIA" to ("Miami" to "America/New_York"),
        "FLL" to ("Fort Lauderdale" to "America/New_York"), "TPA" to ("Tampa" to "America/New_York"), "BUF" to ("Buffalo" to "America/New_York"), "SYR" to ("Syracuse" to "America/New_York"),
        "CLE" to ("Cleveland" to "America/New_York"), "PIT" to ("Pittsburgh" to "America/New_York"), "RDU" to ("Raleigh" to "America/New_York"),
        "ORD" to ("Chicago" to "America/Chicago"), "MDW" to ("Chicago" to "America/Chicago"), "MSP" to ("Minneapolis" to "America/Chicago"), "DFW" to ("Dallas" to "America/Chicago"),
        "IAH" to ("Houston" to "America/Chicago"), "AUS" to ("Austin" to "America/Chicago"), "MSY" to ("New Orleans" to "America/Chicago"), "BNA" to ("Nashville" to "America/Chicago"),
        "DEN" to ("Denver" to "America/Denver"), "SLC" to ("Salt Lake City" to "America/Denver"), "LAS" to ("Las Vegas" to "America/Los_Angeles"),
        "LAX" to ("Los Angeles" to "America/Los_Angeles"), "SFO" to ("San Francisco" to "America/Los_Angeles"), "SAN" to ("San Diego" to "America/Los_Angeles"),
        "SEA" to ("Seattle" to "America/Los_Angeles"), "PDX" to ("Portland" to "America/Los_Angeles"), "HNL" to ("Honolulu" to "Pacific/Honolulu"),
        "YYZ" to ("Toronto" to "America/Toronto"), "LHR" to ("London" to "Europe/London"), "LGW" to ("London" to "Europe/London"), "DUB" to ("Dublin" to "Europe/Dublin"),
        "CDG" to ("Paris" to "Europe/Paris"), "FRA" to ("Frankfurt" to "Europe/Berlin"), "MUC" to ("Munich" to "Europe/Berlin"), "BRU" to ("Brussels" to "Europe/Brussels"),
        "ZRH" to ("Zurich" to "Europe/Zurich"), "MAD" to ("Madrid" to "Europe/Madrid"), "BCN" to ("Barcelona" to "Europe/Madrid"), "FCO" to ("Rome" to "Europe/Rome"),
        "CPH" to ("Copenhagen" to "Europe/Copenhagen"), "LIS" to ("Lisbon" to "Europe/Lisbon"), "KEF" to ("Reykjavik" to "Atlantic/Reykjavik"))
    fun city(iata: String) = AIRPORTS[iata]?.first ?: iata
    fun tz(iata: String): ZoneId = runCatching { ZoneId.of(AIRPORTS[iata]!!.second) }.getOrDefault(ZoneId.systemDefault())

    data class Leg(val stay: Boolean, val ident: String, val from: String, val to: String, val city: String, val tz: String, val start: Long, val end: Long)

    private fun sp(c: Context) = c.getSharedPreferences("where", Context.MODE_PRIVATE)

    fun command(ctx: Context, text: String) {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val title = lines.first().removePrefix("/trip").removePrefix("/TRIP").trim()
        if (title.equals("clear", true) && lines.size == 1) { sp(ctx).edit().putString("trips", "[]").remove("where_live").apply(); return }
        val legs = parse(lines.drop(1)); if (legs.isEmpty()) return
        val o = JSONObject().put("title", title).put("legs", JSONArray(legs.map { JSONObject().put("stay", it.stay).put("ident", it.ident).put("from", it.from)
            .put("to", it.to).put("city", it.city).put("tz", it.tz).put("start", it.start).put("end", it.end) }))
        val all = load(ctx).filterNot { it.optString("title").equals(title, true) } + o
        sp(ctx).edit().putString("trips", JSONArray(all).toString()).apply()
    }
    private fun load(ctx: Context): List<JSONObject> = runCatching { val a = JSONArray(sp(ctx).getString("trips", "[]")); (0 until a.length()).map { a.getJSONObject(it) } }.getOrDefault(emptyList())

    private val FLIGHT = Regex("^(\\d{4}-\\d{2}-\\d{2})\\s+(\\d{1,2}:\\d{2})\\s+([A-Za-z0-9]{2,3}\\s?\\d{1,4})\\s+([A-Za-z]{3})\\s+([A-Za-z]{3})\\s+(\\d{1,2}:\\d{2})(?:\\s*\\+(\\d))?$")
    private val STAY = Regex("^stay\\s+(.+?)\\s+(\\d{4}-\\d{2}-\\d{2})\\s+(\\d{1,2}:\\d{2})\\s+(\\d{4}-\\d{2}-\\d{2})\\s+(\\d{1,2}:\\d{2})$", RegexOption.IGNORE_CASE)
    private fun t(s: String) = LocalTime.parse(s.padStart(5, '0'))
    fun parse(lines: List<String>): List<Leg> {
        val out = ArrayList<Leg>(); var lastTz = ZoneId.systemDefault()
        for (l in lines) {
            FLIGHT.find(l)?.let { m -> val g = m.groupValues; val from = g[4].uppercase(); val to = g[5].uppercase()
                val d = LocalDate.parse(g[1]); val dep = ZonedDateTime.of(d, t(g[2]), tz(from)).toEpochSecond()
                val arr = ZonedDateTime.of(d.plusDays(g[7].toLongOrNull() ?: 0), t(g[6]), tz(to)).toEpochSecond()
                lastTz = tz(to)
                out += Leg(false, g[3].replace(" ", "").uppercase(), from, to, city(to), tz(to).id, dep, arr); return@let }
                ?: STAY.find(l)?.let { m -> val g = m.groupValues
                    out += Leg(true, "", "", "", g[1], lastTz.id, ZonedDateTime.of(LocalDate.parse(g[2]), t(g[3]), lastTz).toEpochSecond(),
                        ZonedDateTime.of(LocalDate.parse(g[4]), t(g[5]), lastTz).toEpochSecond()) }
        }
        return out.sortedBy { it.start }
    }
    private fun legs(o: JSONObject): List<Leg> { val a = o.getJSONArray("legs"); return (0 until a.length()).map { a.getJSONObject(it) }.map {
        Leg(it.optBoolean("stay"), it.optString("ident"), it.optString("from"), it.optString("to"), it.optString("city"), it.optString("tz"), it.optLong("start"), it.optLong("end")) } }

    /** All stored trips (title, legs), oldest first. */
    fun all(ctx: Context): List<Pair<String, List<Leg>>> = load(ctx).map { it.optString("title") to legs(it) }.filter { it.second.isNotEmpty() }.sortedBy { it.second.first().start }
    /** The trip shown right now (same window as [current]), else the next upcoming one. */
    fun active(ctx: Context, now: Long): Pair<String, List<Leg>>? = all(ctx).let { ts ->
        ts.firstOrNull { (_, l) -> now >= l.first().start - PRE && now < l.maxOf { it.end } + HOME_SHOW } ?: ts.firstOrNull { it.second.first().start > now } }
    const val PRE = 3 * 86400L; const val HOME_SHOW = 6 * 3600L; const val BOARD = 2 * 3600L
    /** Travel state for [now] from stored trips, or null. */
    fun current(ctx: Context, now: Long): Where? {
        val trips = load(ctx).map { it.optString("title") to legs(it) }.filter { it.second.isNotEmpty() }.sortedBy { it.second.first().start }
        val (title, ls) = trips.firstOrNull { (_, l) -> now >= l.first().start - PRE && now < l.maxOf { it.end } + HOME_SHOW } ?: return null
        val flights = ls.filter { !it.stay }
        val homeAt = flights.lastOrNull()?.end ?: ls.last().end
        val dest = title.ifBlank { ls.firstOrNull { it.stay }?.city ?: flights.first().city }
        if (now < ls.first().start - BOARD) return Where("pre", city = dest, dep = ls.first().start)
        if (now >= homeAt) return Where("home")
        fun flight(f: Leg) = Where("flight", ident = f.ident, date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(f.start * 1000)),
            from = city(f.from), to = city(f.to), toIata = f.to, fromIata = f.from, dep = f.start, arr = f.end)
        flights.firstOrNull { now >= it.start - BOARD && now < it.end }?.let { return flight(it) }
        ls.firstOrNull { it.stay && now >= it.start && now < it.end }?.let { return Where("stay", city = it.city, tz = it.tz, homeAt = homeAt) }
        val prev = flights.lastOrNull { it.end <= now }; val next = flights.firstOrNull { it.start > now }
        if (prev == null) return next?.let { flight(it) }
        return if (next != null && next.start - now < 24 * 3600 && next.from == prev.to) Where("layover", city = city(prev.to), next = next.start)
               else Where("stay", city = city(prev.to), tz = prev.tz, homeAt = homeAt)
    }
}
