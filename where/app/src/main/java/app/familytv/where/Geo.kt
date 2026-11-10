package app.familytv.where
import kotlin.math.*

/** A point on the globe a traveler can be at. */
data class Place(val name: String, val lat: Double, val lon: Double, val tz: String = "")

object Geo {
    /** IATA -> lat/lon (city + tz come from Trips.AIRPORTS). */
    private val LL = mapOf(
        "ROC" to (43.12 to -77.67), "DTW" to (42.21 to -83.35), "AMS" to (52.31 to 4.76), "PHX" to (33.43 to -112.01), "TUS" to (32.12 to -110.94),
        "AZA" to (33.31 to -111.66), "FLG" to (35.14 to -111.67), "ATL" to (33.64 to -84.43), "JFK" to (40.64 to -73.78), "LGA" to (40.78 to -73.87),
        "EWR" to (40.69 to -74.17), "BOS" to (42.36 to -71.01), "PHL" to (39.87 to -75.24), "DCA" to (38.85 to -77.04), "IAD" to (38.95 to -77.46),
        "BWI" to (39.18 to -76.67), "CLT" to (35.21 to -80.94), "MCO" to (28.43 to -81.31), "MIA" to (25.80 to -80.29), "FLL" to (26.07 to -80.15),
        "TPA" to (27.98 to -82.53), "BUF" to (42.94 to -78.73), "SYR" to (43.11 to -76.11), "CLE" to (41.41 to -81.85), "PIT" to (40.49 to -80.23),
        "RDU" to (35.88 to -78.79), "ORD" to (41.98 to -87.90), "MDW" to (41.79 to -87.75), "MSP" to (44.88 to -93.22), "DFW" to (32.90 to -97.04),
        "IAH" to (29.99 to -95.34), "AUS" to (30.19 to -97.67), "MSY" to (29.99 to -90.26), "BNA" to (36.12 to -86.68), "DEN" to (39.86 to -104.67),
        "SLC" to (40.79 to -111.98), "LAS" to (36.08 to -115.15), "LAX" to (33.94 to -118.41), "SFO" to (37.62 to -122.38), "SAN" to (32.73 to -117.19),
        "SEA" to (47.45 to -122.31), "PDX" to (45.59 to -122.60), "HNL" to (21.32 to -157.92), "YYZ" to (43.68 to -79.63), "LHR" to (51.47 to -0.45),
        "LGW" to (51.15 to -0.19), "DUB" to (53.42 to -6.27), "CDG" to (49.01 to 2.55), "FRA" to (50.03 to 8.57), "MUC" to (48.35 to 11.79),
        "BRU" to (50.90 to 4.48), "ZRH" to (47.46 to 8.55), "MAD" to (40.47 to -3.56), "BCN" to (41.30 to 2.08), "FCO" to (41.80 to 12.24),
        "CPH" to (55.62 to 12.65), "LIS" to (38.77 to -9.13), "KEF" to (63.99 to -22.62))
    /** Extra non-airport cities for "stay" lines and /where. */
    private val CITIES = listOf(
        Place("Chicago", 41.88, -87.63, "America/Chicago"), Place("Denver", 39.74, -104.99, "America/Denver"),
        Place("Austin", 30.27, -97.74, "America/Chicago"), Place("Seattle", 47.61, -122.33, "America/Los_Angeles"),
        Place("London", 51.51, -0.13, "Europe/London"), Place("Paris", 48.86, 2.35, "Europe/Paris"),
        Place("Rome", 41.90, 12.50, "Europe/Rome"), Place("Tokyo", 35.68, 139.69, "Asia/Tokyo"),
        Place("Toronto", 43.65, -79.38, "America/Toronto"), Place("Honolulu", 21.31, -157.86, "Pacific/Honolulu"))

    fun airport(iata: String): Place? { val ll = LL[iata.uppercase()] ?: return null
        return Place(Trips.city(iata.uppercase()), ll.first, ll.second, Trips.tz(iata.uppercase()).id) }
    /** Looks up a city by name (extra cities, then airport cities); null if unknown. */
    fun city(name: String): Place? { val n = name.trim().lowercase()
        CITIES.firstOrNull { it.name.lowercase() == n }?.let { return it }
        return LL.keys.firstOrNull { Trips.city(it).lowercase() == n }?.let { airport(it)!!.copy(name = name.trim()) } }

    private fun vec(p: Place): DoubleArray { val la = Math.toRadians(p.lat); val lo = Math.toRadians(p.lon); return doubleArrayOf(cos(la) * cos(lo), cos(la) * sin(lo), sin(la)) }
    /** Great-circle distance in km. */
    fun km(a: Place, b: Place): Double { val x = vec(a); val y = vec(b); return 6371.0 * acos((x[0] * y[0] + x[1] * y[1] + x[2] * y[2]).coerceIn(-1.0, 1.0)) }
    /** Point at fraction t along the great circle a->b, as (lat, lon). */
    fun along(a: Place, b: Place, t: Double): Pair<Double, Double> {
        val x = vec(a); val y = vec(b); val d = acos((x[0] * y[0] + x[1] * y[1] + x[2] * y[2]).coerceIn(-1.0, 1.0))
        if (d < 1e-9) return a.lat to a.lon
        val s1 = sin((1 - t) * d) / sin(d); val s2 = sin(t * d) / sin(d)
        val v = DoubleArray(3) { s1 * x[it] + s2 * y[it] }
        return Math.toDegrees(atan2(v[2], hypot(v[0], v[1]))) to Math.toDegrees(atan2(v[1], v[0]))
    }
    /** Web-Mercator world coords in 0..1 (x east, y south), lat clamped to ±80. */
    fun wx(lon: Double) = (lon + 180.0) / 360.0
    fun wy(lat: Double): Double { val l = Math.toRadians(lat.coerceIn(-80.0, 80.0)); return (1 - ln(tan(PI / 4 + l / 2)) / PI) / 2 }
}
