package app.familytv.weather

import org.json.JSONObject
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

enum class Sky { CLEAR, PARTLY, CLOUDY, FOG, DRIZZLE, RAIN, SNOW, THUNDER }

data class Condition(val label: String, val sky: Sky)

object Wmo {
    fun map(code: Int): Condition = when (code) {
        0 -> Condition("Clear", Sky.CLEAR)
        1 -> Condition("Mostly clear", Sky.PARTLY)
        2 -> Condition("Partly cloudy", Sky.PARTLY)
        3 -> Condition("Overcast", Sky.CLOUDY)
        45, 48 -> Condition("Fog", Sky.FOG)
        51, 53, 55 -> Condition("Drizzle", Sky.DRIZZLE)
        56, 57 -> Condition("Freezing drizzle", Sky.DRIZZLE)
        61 -> Condition("Light rain", Sky.RAIN)
        63 -> Condition("Rain", Sky.RAIN)
        65 -> Condition("Heavy rain", Sky.RAIN)
        66, 67 -> Condition("Freezing rain", Sky.RAIN)
        71 -> Condition("Light snow", Sky.SNOW)
        73 -> Condition("Snow", Sky.SNOW)
        75 -> Condition("Heavy snow", Sky.SNOW)
        77 -> Condition("Snow grains", Sky.SNOW)
        80, 81 -> Condition("Showers", Sky.RAIN)
        82 -> Condition("Heavy showers", Sky.RAIN)
        85, 86 -> Condition("Snow showers", Sky.SNOW)
        95 -> Condition("Thunderstorm", Sky.THUNDER)
        96, 99 -> Condition("Thunderstorm, hail", Sky.THUNDER)
        else -> Condition("Unknown", Sky.CLOUDY)
    }
}

data class Hour(val time: LocalDateTime, val temp: Int, val precip: Int, val code: Int, val isDay: Boolean)
data class Day(val date: LocalDateTime, val code: Int, val hi: Int, val lo: Int, val precip: Int,
               val sunrise: LocalDateTime, val sunset: LocalDateTime)

data class Forecast(
    val time: LocalDateTime, val temp: Int, val feels: Int, val humidity: Int, val wind: Int,
    val windDir: Int, val code: Int, val isDay: Boolean, val hours: List<Hour>, val days: List<Day>
) {
    val condition get() = Wmo.map(code)
    val today get() = days.first()
    val precipNow get() = hours.firstOrNull()?.precip ?: today.precip
}

object Parser {
    private fun t(s: String): LocalDateTime =
        if (s.length == 10) LocalDateTime.parse(s + "T00:00") else LocalDateTime.parse(s)
    private fun r(d: Double) = Math.round(d).toInt()

    fun compass(deg: Int): String =
        arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")[Math.floorMod(Math.round(deg / 45.0).toInt(), 8)]

    fun parse(json: String, hoursAhead: Int = 12): Forecast {
        val o = JSONObject(json)
        val c = o.getJSONObject("current")
        val now = t(c.getString("time"))
        val h = o.getJSONObject("hourly")
        val ht = h.getJSONArray("time"); val htemp = h.getJSONArray("temperature_2m")
        val hp = h.getJSONArray("precipitation_probability"); val hc = h.getJSONArray("weather_code")
        val hd = h.getJSONArray("is_day")
        val start = now.truncatedTo(ChronoUnit.HOURS)
        val hours = (0 until ht.length()).map { i ->
            Hour(t(ht.getString(i)), r(htemp.getDouble(i)), hp.optInt(i, 0), hc.getInt(i), hd.getInt(i) == 1)
        }.filter { !it.time.isBefore(start) }.take(hoursAhead)
        val d = o.getJSONObject("daily")
        val dt = d.getJSONArray("time")
        val days = (0 until dt.length()).map { i ->
            Day(t(dt.getString(i)), d.getJSONArray("weather_code").getInt(i),
                r(d.getJSONArray("temperature_2m_max").getDouble(i)), r(d.getJSONArray("temperature_2m_min").getDouble(i)),
                d.getJSONArray("precipitation_probability_max").optInt(i, 0),
                t(d.getJSONArray("sunrise").getString(i)), t(d.getJSONArray("sunset").getString(i)))
        }
        return Forecast(now, r(c.getDouble("temperature_2m")), r(c.getDouble("apparent_temperature")),
            c.getInt("relative_humidity_2m"), r(c.getDouble("wind_speed_10m")), c.optInt("wind_direction_10m", 0),
            c.getInt("weather_code"), c.getInt("is_day") == 1, hours, days)
    }
}
