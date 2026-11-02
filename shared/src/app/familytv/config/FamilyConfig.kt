package app.familytv.config

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.random.Random

/**
 * One household file, [family.json], bundled into every app.
 * Blank ntfy topic: a topic is generated on first launch and kept on that device.
 * Run scripts/configure.sh before building so every app embeds the same topic.
 */
object FamilyConfig {
    private const val PREFS = "familytv"
    const val SCRAPE_TERMS =
        "This lookup reads an undocumented public page. It stays off until you enable it. Read that service's terms first. Family Home is not affiliated with the service."

    private var file: JSONObject? = null

    fun json(ctx: Context): JSONObject {
        file?.let { return it }
        val text = runCatching { ctx.assets.open("family.json").bufferedReader().use { it.readText() } }.getOrNull()
            ?: raw(ctx)
        val parsed = if (text.isNullOrBlank()) JSONObject() else runCatching { JSONObject(text) }.getOrDefault(JSONObject())
        file = parsed
        return parsed
    }

    private fun raw(ctx: Context): String? {
        val id = ctx.resources.getIdentifier("family", "raw", ctx.packageName)
        if (id == 0) return null
        return runCatching { ctx.resources.openRawResource(id).bufferedReader().use { it.readText() } }.getOrNull()
    }

    private fun obj(ctx: Context, key: String) = json(ctx).optJSONObject(key) ?: JSONObject()
    private fun sp(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun parentName(ctx: Context) = obj(ctx, "parent").optString("name", "Alex").ifBlank { "Alex" }
    fun familyName(ctx: Context) = obj(ctx, "family").optString("name", "Example family").ifBlank { "Example family" }
    fun homeName(ctx: Context) = obj(ctx, "home").optString("name", "Example City").ifBlank { "Example City" }
    fun latitude(ctx: Context) = obj(ctx, "home").optDouble("latitude", 39.9526)
    fun longitude(ctx: Context) = obj(ctx, "home").optDouble("longitude", -75.1652)
    fun timezone(ctx: Context) = obj(ctx, "home").optString("timezone", "America/New_York").ifBlank { "America/New_York" }
    fun airport(ctx: Context) = obj(ctx, "home").optString("airport", "PHL").ifBlank { "PHL" }

    fun units(ctx: Context): String {
        val saved = sp(ctx).getString("units", null)
        if (saved == "f" || saved == "c") return saved
        return if (obj(ctx, "home").optString("units", "fahrenheit").startsWith("c")) "c" else "f"
    }

    fun setUnits(ctx: Context, fahrenheit: Boolean) = sp(ctx).edit().putString("units", if (fahrenheit) "f" else "c").apply()
    fun fahrenheit(ctx: Context) = units(ctx) != "c"

    fun clock(ctx: Context): String {
        val saved = sp(ctx).getString("clock", null)
        if (saved == "12" || saved == "24") return saved
        return if (obj(ctx, "home").optString("clock", "12") == "24") "24" else "12"
    }

    fun setClock(ctx: Context, twelveHour: Boolean) = sp(ctx).edit().putString("clock", if (twelveHour) "12" else "24").apply()
    fun clock12(ctx: Context) = clock(ctx) != "24"
    fun timePattern(ctx: Context) = if (clock12(ctx)) "h:mm a" else "HH:mm"
    fun hourPattern(ctx: Context) = if (clock12(ctx)) "h a" else "HH:mm"

    fun server(ctx: Context) = obj(ctx, "ntfy").optString("server", "https://ntfy.sh").trimEnd('/').ifBlank { "https://ntfy.sh" }
    fun token(ctx: Context) = obj(ctx, "ntfy").optString("token", "")

    /** Base topic shared by notes, the home-screen card, and the travel suffix. */
    fun topic(ctx: Context): String {
        val override = sp(ctx).getString("topic", null)?.trim().orEmpty()
        if (override.isNotEmpty()) return override
        val bundled = obj(ctx, "ntfy").optString("topic", "").trim()
        if (bundled.isNotEmpty()) return bundled
        val existing = sp(ctx).getString("generated_topic", null)?.trim().orEmpty()
        if (existing.isNotEmpty()) return existing
        val alphabet = "0123456789abcdef"
        val gen = "family-" + buildString(24) { repeat(24) { append(alphabet[Random.nextInt(alphabet.length)]) } }
        sp(ctx).edit().putString("generated_topic", gen).apply()
        return gen
    }

    fun setTopic(ctx: Context, topic: String) = sp(ctx).edit().putString("topic", topic.trim()).apply()
    fun repliesTopic(ctx: Context) = topic(ctx) + "-replies"
    fun whereTopic(ctx: Context) = topic(ctx) + "-where"

    fun photosEnabled(ctx: Context): Boolean {
        val p = sp(ctx)
        return if (p.contains("photos")) p.getBoolean("photos", false) else obj(ctx, "photos").optBoolean("enabled", false)
    }

    fun setPhotosEnabled(ctx: Context, on: Boolean) = sp(ctx).edit().putBoolean("photos", on).apply()

    fun albumUrl(ctx: Context): String {
        val saved = sp(ctx).getString("album", null)
        if (saved != null) return saved
        return obj(ctx, "photos").optString("albumUrl", "")
    }

    fun setAlbumUrl(ctx: Context, url: String) = sp(ctx).edit().putString("album", url.trim()).apply()

    fun flightsEnabled(ctx: Context): Boolean {
        val p = sp(ctx)
        return if (p.contains("flights")) p.getBoolean("flights", false) else obj(ctx, "flights").optBoolean("enabled", false)
    }

    fun setFlightsEnabled(ctx: Context, on: Boolean) = sp(ctx).edit().putBoolean("flights", on).apply()

    fun leagues(ctx: Context) = obj(ctx, "ticker").optString("leagues", "BOTH").ifBlank { "BOTH" }

    fun favorites(ctx: Context): Set<String> {
        val array = obj(ctx, "ticker").optJSONArray("favorites") ?: return emptySet()
        return (0 until array.length()).map { array.optString(it).uppercase(Locale.US) }.filter { it.isNotBlank() }.toSet()
    }

    fun topRow(ctx: Context): List<String> = strings(obj(ctx, "launcher").optJSONArray("topRowApps"))
    fun hidden(ctx: Context): Set<String> = strings(obj(ctx, "launcher").optJSONArray("hiddenApps")).toSet()
    fun factPack(ctx: Context) = obj(ctx, "launcher").optString("factPack", "general").ifBlank { "general" }
    fun seasonal(ctx: Context) = obj(ctx, "launcher").optBoolean("seasonal", true)
    fun tvAddress(ctx: Context) = obj(ctx, "tv").optString("address", "")

    private fun strings(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return (0 until array.length()).map { array.optString(it) }.filter { it.isNotBlank() }
    }

    fun open(ctx: Context, url: String, readTimeout: Int = 15000): HttpURLConnection {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            this.readTimeout = readTimeout
            setRequestProperty("User-Agent", "FamilyHome")
        }
        val token = token(ctx)
        if (token.isNotBlank()) connection.setRequestProperty("Authorization", "Bearer $token")
        return connection
    }
}
