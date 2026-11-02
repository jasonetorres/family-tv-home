package app.familytv.weather

import android.content.Context
import app.familytv.config.FamilyConfig
import java.io.File
import java.net.URLEncoder

class Repo(private val ctx: Context) {
    private val cache = File(ctx.filesDir, "last.json")

    fun cached(): Pair<Forecast, Long>? = try {
        if (cache.exists()) Parser.parse(cache.readText()) to cache.lastModified() else null
    } catch (e: Exception) { null }

    /** Network fetch; on success writes cache. Throws on failure. */
    fun fetch(): Forecast {
        val unit = if (FamilyConfig.fahrenheit(ctx)) "fahrenheit" else "celsius"
        val zone = URLEncoder.encode(FamilyConfig.timezone(ctx), "UTF-8")
        val url = "https://api.open-meteo.com/v1/forecast?latitude=${FamilyConfig.latitude(ctx)}&longitude=${FamilyConfig.longitude(ctx)}" +
            "&current=temperature_2m,relative_humidity_2m,apparent_temperature,is_day,weather_code,wind_speed_10m,wind_direction_10m" +
            "&hourly=temperature_2m,precipitation_probability,weather_code,is_day" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,precipitation_probability_max" +
            "&temperature_unit=$unit&wind_speed_unit=mph&timezone=$zone&forecast_days=7"
        val c = FamilyConfig.open(ctx, url)
        try {
            val body = c.inputStream.bufferedReader().readText()
            val f = Parser.parse(body)
            cache.writeText(body)
            return f
        } finally { c.disconnect() }
    }
}
