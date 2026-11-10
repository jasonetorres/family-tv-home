package app.familytv.where
/*
 * Santa mode data source (NOT enabled yet — the "Santa mode coming Dec" tile is a placeholder until December).
 * Route format and fetch flow follow Google's Santa Tracker (github.com/google/santa-tracker-android,
 * tracker/api/RemoteSantaApi.kt + vo/Destination.kt, and santa-tracker-web), Apache License 2.0,
 * Copyright Google LLC. Re-implemented here without Firebase SDK; see SANTA_NOTES.md.
 *
 *  info:  GET https://santa-api.appspot.com/info?client=web
 *         -> {"status":"OK","now":ms,"takeoff":ms,"duration":ms,"route":["<route json url>"]}
 *  route: {"destinations":[{"id","arrival":ms,"departure":ms,"population","presentsDelivered","city","region",
 *          "location":{"lat","lng"},"details":{"timezone":secondsOffset,...}}]}
 *  Route files carry a past year's times; they are shifted so destinations[0].departure == this year's takeoff.
 */
import android.content.Context
import org.json.JSONObject
import java.time.*
import kotlin.concurrent.thread

data class Destination(val id: String, val arrival: Long, val departure: Long, val presents: Long, val city: String, val region: String,
                       val lat: Double, val lng: Double, val tzOffset: Long?)

object SantaRoute {
    const val INFO_URL = "https://santa-api.appspot.com/info?client=web"
    @Volatile var dests: List<Destination> = emptyList(); private set
    @Volatile private var takeoffMs = 0L

    fun parse(json: String): List<Destination> { val a = JSONObject(json).getJSONArray("destinations")
        return (0 until a.length()).map { val o = a.getJSONObject(it); val l = o.getJSONObject("location")
            Destination(o.getString("id"), o.optLong("arrival"), o.optLong("departure"), o.optLong("presentsDelivered"), o.optString("city"), o.optString("region"),
                l.getDouble("lat"), l.getDouble("lng"), o.optJSONObject("details")?.let { d -> if (d.isNull("timezone")) null else d.optLong("timezone") }) } }

    /** Default takeoff: Dec 24 10:00 UTC of the current year (Santa Tracker's usual takeoff). */
    private fun defaultTakeoff() = ZonedDateTime.of(Year.now().value, 12, 24, 10, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()

    /** Loads bundled fallback immediately, then tries the live info+route feed in the background. */
    fun load(ctx: Context) {
        if (dests.isEmpty()) runCatching { shift(parse(ctx.assets.open("santa_route_fallback.json").bufferedReader().readText()), defaultTakeoff()) }
        thread { runCatching {
            val info = JSONObject(Net.open(INFO_URL).inputStream.bufferedReader().readText())
            val take = info.optLong("takeoff").takeIf { it > 0 } ?: defaultTakeoff()
            val url = info.getJSONArray("route").getString(0)
            shift(parse(Net.open(url).inputStream.bufferedReader().readText()), take) } }
    }
    private fun shift(raw: List<Destination>, takeoff: Long) {
        if (raw.isEmpty()) return
        val off = takeoff - raw.first().departure; takeoffMs = takeoff
        dests = raw.map { it.copy(arrival = if (it.arrival == 0L) 0 else it.arrival + off, departure = it.departure + off) }
    }
}

/** Santa as a Traveler. Hook only: registered in Travelers.all but selectable only in December. */
object SantaTraveler : Traveler {
    override val id = "santa"; override val avatar = Avatar.SLEIGH
    override fun name(ctx: Context) = "Santa"
    private fun place(d: Destination) = Place(if (d.region.isNotBlank()) "${d.city}, ${d.region}" else d.city, d.lat, d.lng,
        d.tzOffset?.let { ZoneOffset.ofTotalSeconds(it.toInt()).id } ?: "UTC")

    override fun snapshot(ctx: Context, now: Long): Snapshot {
        if (SantaRoute.dests.isEmpty()) SantaRoute.load(ctx)
        val ds = SantaRoute.dests; val ms = now * 1000
        val pole = Place("North Pole", 84.6, 168.0, "UTC")
        if (ds.size < 2) return Snapshot("Santa is getting ready…", pole, null, 0f, null, 0, 0.0, listOf(pole), pole, false, "Presents delivered" to "0")
        val i = ds.indexOfLast { it.departure <= ms }   // last departed stop
        val atStop = ds.indexOfFirst { it.arrival in 1..ms && ms < it.departure }
        var hop: Hop? = null; var here: Place? = null; var prog = 0f; var presents = 0L; val head: String
        when {
            ms < ds.first().departure -> { here = pole; val d = Duration.ofMillis(ds.first().departure - ms)
                head = if (d.toDays() >= 1) "Santa takes off in ${d.toDays()} days" else "Santa takes off in ${d.toHours()}h ${d.toMinutesPart()}m" }
            atStop >= 0 -> { here = place(ds[atStop]); presents = ds[atStop].presents; head = "Santa is in ${ds[atStop].city}!" }
            i >= ds.size - 1 -> { here = pole; presents = ds.last().presents; head = "Santa is back at the North Pole!" }
            else -> { val a = ds[i]; val b = ds[i + 1]; hop = Hop(place(a), place(b), a.departure / 1000, b.arrival / 1000, "")
                prog = ((ms - a.departure).toFloat() / (b.arrival - a.departure).coerceAtLeast(1)).coerceIn(0f, 1f)
                presents = a.presents + ((b.presents - a.presents) * prog).toLong(); head = "Santa is flying to ${b.city}" }
        }
        val visited = ds.filter { it.arrival in 1..ms }
        var km = 0.0; visited.zipWithNext { a, b -> km += Geo.km(place(a), place(b)) }
        val nxt = ds.firstOrNull { it.arrival > ms }
        return Snapshot(head, here, hop, prog, nxt?.let { place(it) }, (nxt?.arrival ?: 0) / 1000, km,
            (visited.takeLast(8) + listOfNotNull(nxt)).map { place(it) }, pole, false, "Presents delivered" to "%,d".format(presents),
            ds.filter { it.arrival > ms }.take(5).map { "${it.city}, ${it.region}" })
    }
}
