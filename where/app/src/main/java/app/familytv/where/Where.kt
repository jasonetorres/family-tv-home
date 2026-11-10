package app.familytv.where
import android.content.Context
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

/** Travel state, stored as JSON in prefs. mode: "" (hidden) | "city" | "home" | "flight". Times are epoch seconds (0 = unknown). */
data class Where(val mode: String = "", val city: String = "", val ident: String = "", val date: String = "",
                 val from: String = "", val to: String = "", val toIata: String = "",
                 val dep: Long = 0, val arr: Long = 0, val depActual: Boolean = false, val arrActual: Boolean = false,
                 val progress: Float = -1f, val cancelled: Boolean = false, val updated: Long = 0,
                 val tz: String = "", val next: Long = 0, val homeAt: Long = 0, val fromIata: String = "") {
    fun json() = JSONObject().put("mode", mode).put("city", city).put("ident", ident).put("date", date).put("from", from).put("to", to).put("toIata", toIata)
        .put("dep", dep).put("arr", arr).put("depA", depActual).put("arrA", arrActual).put("prog", progress.toDouble()).put("canc", cancelled).put("upd", updated)
        .put("tz", tz).put("next", next).put("homeAt", homeAt).put("fromIata", fromIata)
    companion object {
        fun from(s: String?): Where = runCatching { val o = JSONObject(s!!)
            Where(o.optString("mode"), o.optString("city"), o.optString("ident"), o.optString("date"), o.optString("from"), o.optString("to"), o.optString("toIata"),
                o.optLong("dep"), o.optLong("arr"), o.optBoolean("depA"), o.optBoolean("arrA"), o.optDouble("prog", -1.0).toFloat(), o.optBoolean("canc"), o.optLong("upd"),
                o.optString("tz"), o.optLong("next"), o.optLong("homeAt"), o.optString("fromIata")) }.getOrDefault(Where())
        private fun c(c: Context) = c.getSharedPreferences("where", Context.MODE_PRIVATE)
        /** Manual state (set by /flight or /where). */
        fun manual(ctx: Context) = from(c(ctx).getString("where", null))
        fun set(ctx: Context, w: Where) = c(ctx).edit().putString("where", w.json().toString()).putBoolean("auto", false).apply()
        /** Effective state: manual override if set (and not /where auto), else derived from stored itineraries. */
        fun get(ctx: Context): Where {
            val m = manual(ctx)
            if (!c(ctx).getBoolean("auto", false) && m.active) return m
            val w = Trips.current(ctx, System.currentTimeMillis() / 1000) ?: return Where()
            if (w.mode != "flight") return w
            val live = from(c(ctx).getString("where_live", null))
            return if (live.ident == w.ident && live.date == w.date && live.dep > 0) live else w
        }

        /** Parses /flight, /where and /trip commands. Returns true if the note was a where-command. */
        fun command(ctx: Context, body: String): Boolean {
            val t = body.trim()
            Regex("^/flight\\s+([A-Za-z0-9]{2,3}\\s?\\d{1,4})(?:\\s+(\\d{4}-\\d{2}-\\d{2}))?\\s*$", RegexOption.IGNORE_CASE).find(t)?.let { m ->
                set(ctx, Where("flight", ident = m.groupValues[1].replace(" ", "").uppercase(), date = m.groupValues[2])); refresh(ctx); return true }
            Regex("^/where\\s+(.+)$", RegexOption.IGNORE_CASE).find(t)?.let { m ->
                val a = m.groupValues[1].trim()
                if (a.lowercase() == "auto") { c(ctx).edit().putBoolean("auto", true).apply(); refresh(ctx); return true }
                set(ctx, when (a.lowercase()) { "clear", "off", "none" -> Where(); "home" -> Where("home"); else -> Where("city", city = a) }); return true }
            if (Regex("^/trip\\b", RegexOption.IGNORE_CASE).containsMatchIn(t)) {
                Trips.command(ctx, t); c(ctx).edit().putBoolean("auto", true).apply(); refresh(ctx); return true }
            return false
        }

        private val ICAO = mapOf("UA" to "UAL", "AA" to "AAL", "DL" to "DAL", "WN" to "SWA", "B6" to "JBU", "AS" to "ASA", "NK" to "NKS",
            "F9" to "FFT", "G4" to "AAY", "HA" to "HAL", "SY" to "SCX", "AC" to "ACA", "WS" to "WJA", "MX" to "MXY", "XP" to "CXP",
            "YX" to "RPA", "OO" to "SKW", "9E" to "EDV", "MQ" to "ENY", "OH" to "JIA", "YV" to "ASH", "QX" to "QXE", "BA" to "BAW", "LH" to "DLH", "AF" to "AFR")
        /** "UA123" -> "UAL123" (FlightAware treats 2-letter idents as other things). */
        fun icao(ident: String): String { val m = Regex("^([A-Z0-9]{2})(\\d+)$").find(ident) ?: return ident
            return (ICAO[m.groupValues[1]] ?: return ident) + m.groupValues[2] }
        /**
         * Optional FlightAware public-page lookup. Off unless flights.enabled is true.
         * The page is undocumented. See FamilyConfig.SCRAPE_TERMS. Network: call off the main thread.
         */
        fun refresh(ctx: Context) {
            if (!app.familytv.config.FamilyConfig.flightsEnabled(ctx)) return
            val w = get(ctx); if (w.mode != "flight") return
            val isAuto = c(ctx).getBoolean("auto", false) || !manual(ctx).active
            fun save(n: Where) { if (isAuto) c(ctx).edit().putString("where_live", n.json().toString()).apply() else set(ctx, n) }
            val html = runCatching { Net.open("https://www.flightaware.com/live/flight/${icao(w.ident)}").apply {
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/120 Safari/537.36") }
                .inputStream.bufferedReader().readText() }.getOrNull() ?: return
            val i = html.indexOf("trackpollBootstrap = "); if (i < 0) return
            val j = html.indexOf("};", i); if (j < 0) return
            val f = runCatching { JSONObject(html.substring(i + 21, j + 1)).getJSONObject("flights").let { it.getJSONObject(it.keys().next()) } }.getOrNull() ?: return
            if (!f.has("origin")) return   // unknown flight: keep the schedule-based line
            // Pick the leg: requested date (by scheduled departure, device-local date), else the current/top-level one.
            val legs = f.optJSONObject("activityLog")?.optJSONArray("flights")
            val df = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            fun sched(o: JSONObject) = o.optJSONObject("gateDepartureTimes")?.optLong("scheduled") ?: 0L
            var leg = f
            if (w.date.isNotBlank() && legs != null) (0 until legs.length()).map { legs.getJSONObject(it) }
                .firstOrNull { df.format(Date(sched(it) * 1000)) == w.date }?.let { leg = if (sched(it) == sched(f)) f else it }
            fun t(k: String): Pair<Long, Boolean> { val o = leg.optJSONObject(k) ?: return 0L to false
                val a = o.optLong("actual", 0); if (!o.isNull("actual") && a > 0) return a to true
                val e = o.optLong("estimated", 0); return (if (!o.isNull("estimated") && e > 0) e else o.optLong("scheduled", 0)) to false }
            val (dep, depA) = t("gateDepartureTimes").let { if (it.first == 0L) t("takeoffTimes") else it }
            val (arr, arrA) = t("gateArrivalTimes").let { g -> val l = t("landingTimes"); if (l.second || g.first == 0L) l else g }
            val dist = leg.optJSONObject("distance")
            val el = dist?.optDouble("elapsed", Double.NaN) ?: Double.NaN; val rem = dist?.optDouble("remaining", Double.NaN) ?: Double.NaN
            val prog = if (leg === f && !el.isNaN() && !rem.isNaN() && el + rem > 0) (el / (el + rem)).toFloat() else -1f
            fun city(o: JSONObject?) = o?.optString("friendlyLocation")?.substringBefore(",")?.ifBlank { null } ?: o?.optString("iata") ?: ""
            save(w.copy(from = city(leg.optJSONObject("origin")), to = city(leg.optJSONObject("destination")), toIata = leg.optJSONObject("destination")?.optString("iata") ?: "", fromIata = leg.optJSONObject("origin")?.optString("iata") ?: "",
                dep = dep, arr = arr, depActual = depA, arrActual = arrA, progress = prog, cancelled = leg.optBoolean("cancelled"), updated = System.currentTimeMillis() / 1000))
        }
    }
    val active get() = mode.isNotBlank()
    /** Plane position 0..1 (live distance if airborne, else from times). */
    fun planeAt(now: Long): Float = when {
        mode == "home" || arrActual -> 1f
        mode == "city" || mode == "stay" || mode == "layover" || mode == "pre" -> 0f
        progress >= 0 && depActual -> progress
        dep > 0 && arr > dep -> ((now - dep).toFloat() / (arr - dep)).coerceIn(0f, 1f)
        else -> 0f
    }
    private fun hm(t: Long) = SimpleDateFormat("h:mm a", Locale.US).format(Date(t * 1000))
    fun headline(now: Long, who: String, clock12: Boolean = true): String {
        fun dur(s: Long): String { val m = (s + 59) / 60; return if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m" }
        fun hm(t: Long) = SimpleDateFormat(if (clock12) "h:mm a" else "HH:mm", Locale.US).format(Date(t * 1000))
        val fmt = SimpleDateFormat(if (clock12) "EEE h:mm a" else "EEE HH:mm", Locale.getDefault())
        return when (mode) {
            "home" -> "$who is home!"
            "city" -> "$who is in $city"
            "pre" -> { val d = java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.now(), java.time.Instant.ofEpochSecond(dep).atZone(java.time.ZoneId.systemDefault()).toLocalDate())
                when { d <= 0 -> "$who leaves for $city today, ${hm(dep)}"; d == 1L -> "$who leaves for $city tomorrow"; else -> "$who leaves for $city in $d days" } }
            "layover" -> "$who is in $city, next flight ${hm(next)}"
            "stay" -> { val local = runCatching { java.time.Instant.ofEpochSecond(now).atZone(java.time.ZoneId.of(tz)).format(java.time.format.DateTimeFormatter.ofPattern(if (clock12) "h:mm a" else "HH:mm", Locale.US)) }.getOrNull()
                val d = if (homeAt > 0) java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.now(), java.time.Instant.ofEpochSecond(homeAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate()) else -1
                "$who is in $city" + (if (local != null) " · it's $local there" else "") +
                    when { d < 0 -> ""; d == 0L -> " · home today!"; d == 1L -> " · home tomorrow"; else -> " · home in $d days" } }
            "flight" -> when {
                cancelled -> "$who's flight $ident was cancelled"
                arrActual -> "$who landed in ${to.ifBlank { "home" }}!"
                arr == 0L -> "$who is flying on $ident"
                depActual || now >= dep -> if (arr > now) "$who lands in ${dur(arr - now)}" else "$who is landing now!"
                dep - now < 24 * 3600 -> "$who takes off in ${dur(dep - now)}"
                else -> "$who flies ${fmt.format(Date(dep * 1000))}"
            }
            else -> ""
        }
    }
}

