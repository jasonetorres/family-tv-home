package app.familytv.where
import android.content.Context
import java.text.SimpleDateFormat
import java.util.*

/** How the traveler is drawn on the map. SLEIGH is reserved for Santa mode (December). */
enum class Avatar { PLANE, SLEIGH }

/** One travel segment (a flight, or a stop-to-stop hop for Santa). Times are epoch seconds. */
data class Hop(val from: Place, val to: Place, val dep: Long, val arr: Long, val label: String)

/** Everything the screen needs at one instant. */
data class Snapshot(
    val headline: String,
    val here: Place?,            // where the traveler is on the ground (null while in the air)
    val hop: Hop?,               // segment being travelled (or about to be)
    val progress: Float,         // 0..1 along hop
    val next: Place?,            // next stop
    val arriveAt: Long,          // arrival at next stop (0 = unknown)
    val distanceKm: Double,      // travelled so far on this trip
    val stops: List<Place>,      // pins to draw
    val home: Place,
    val isHome: Boolean,         // celebrate!
    val counter: Pair<String, String>? = null,   // extra info-bar cell, e.g. "Presents delivered" to "1,234,567" for Santa
    val upcoming: List<String> = emptyList(),
    val active: Boolean = true,
)

/**
 * A pluggable traveler. The parent is built from /trip, /flight and /where. Santa (Dec 24) is the other.
 */
interface Traveler {
    val id: String
    val avatar: Avatar
    fun name(ctx: Context): String
    fun snapshot(ctx: Context, now: Long): Snapshot
}

object Travelers {
    /** Register new travelers here (e.g. SantaTraveler in December). */
    val all: List<Traveler> = listOf(ParentTraveler, SantaTraveler)
    /** Santa mode is only available in December; otherwise the parent traveler is used. */
    fun santaAvailable() = java.time.LocalDate.now().monthValue == 12
    fun current(ctx: Context): Traveler { val id = ctx.getSharedPreferences("where", Context.MODE_PRIVATE).getString("traveler", "parent")
        return if (id == "santa" && !santaAvailable()) ParentTraveler else all.firstOrNull { it.id == id } ?: ParentTraveler }
    fun set(ctx: Context, id: String) = ctx.getSharedPreferences("where", Context.MODE_PRIVATE).edit().putString("traveler", id).apply()
}

object ParentTraveler : Traveler {
    override val id = "parent"; override val avatar = Avatar.PLANE
    override fun name(ctx: Context) = app.familytv.config.FamilyConfig.parentName(ctx)
    private fun place(iata: String) = Geo.airport(iata)
    private fun home(ctx: Context): Place {
        val known = Geo.airport(app.familytv.config.FamilyConfig.airport(ctx))
        return if (known != null) known.copy(name = "Home")
        else Place("Home", app.familytv.config.FamilyConfig.latitude(ctx), app.familytv.config.FamilyConfig.longitude(ctx), app.familytv.config.FamilyConfig.timezone(ctx))
    }

    override fun snapshot(ctx: Context, now: Long): Snapshot {
        val w = Where.get(ctx)
        val trip = Trips.active(ctx, now)
        val legs = trip?.second ?: emptyList()
        val flights = legs.filter { !it.stay }.mapNotNull { l -> val a = place(l.from) ?: return@mapNotNull null; val b = place(l.to) ?: return@mapNotNull null
            Hop(a, b, l.start, l.end, l.ident) }
        val stops = (flights.flatMap { listOf(it.from, it.to) } + legs.filter { it.stay }.mapNotNull { Geo.city(it.city) })
            .distinctBy { "%.1f,%.1f".format(it.lat, it.lon) }
        val upcoming = legs.filter { it.end > now }.take(6).map { l ->
            val z = TimeZone.getDefault()
            if (l.stay) "${SimpleDateFormat("MMM d", Locale.US).apply { timeZone = z }.format(Date(l.start * 1000))}–${SimpleDateFormat("d", Locale.US).format(Date(l.end * 1000))}  🏨 Stay in ${l.city}"
            else "${SimpleDateFormat("EEE MMM d, h:mm a", Locale.US).format(Date(l.start * 1000))}  ✈ ${l.ident}  ${Trips.city(l.from)} → ${Trips.city(l.to)}" }

        var hop: Hop? = null; var here: Place? = null; var prog = 0f
        when (w.mode) {
            "flight" -> { val a = place(w.fromIata); val b = place(w.toIata)
                if (a != null && b != null) hop = Hop(a, b, w.dep, w.arr, w.ident)
                else flights.firstOrNull { it.label == w.ident }?.let { hop = it }
                prog = w.planeAt(now); if (prog <= 0f && hop != null && now < (hop!!.dep)) here = hop!!.from }
            "home", "pre" -> here = home(ctx)
            "stay", "layover", "city" -> here = Geo.city(w.city) ?: flights.lastOrNull { it.arr <= now }?.to
        }
        val doneKm = flights.filter { it.arr <= now && it != hop }.sumOf { Geo.km(it.from, it.to) } + (hop?.let { Geo.km(it.from, it.to) * prog } ?: 0.0)
        val nextFlight = flights.firstOrNull { it.dep > now }
        val next = hop?.to ?: nextFlight?.to
        val arriveAt = hop?.arr ?: nextFlight?.arr ?: 0L
        val who = name(ctx)
        val head = w.headline(now, who, app.familytv.config.FamilyConfig.clock12(ctx)).ifBlank { if (trip != null) "Next trip: ${trip.first}" else "No trips yet — send /trip from your phone" }
        return Snapshot(head, here, hop, prog, next, arriveAt, if (w.mode == "pre") 0.0 else doneKm, stops, home(ctx), w.mode == "home", null, upcoming, w.active)
    }
}
