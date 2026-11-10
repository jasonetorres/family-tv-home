package app.familytv.notes
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.net.HttpURLConnection
import java.net.URL

/**
 * Optional Google Photos shared-album lookup. Off unless photos.enabled is true.
 * The page format is undocumented. See FamilyConfig.SCRAPE_TERMS before turning it on.
 */
object Album {
    private val RX = Regex("\\[\"(https://lh3\\.googleusercontent\\.com/pw/[^\"]+)\",(\\d+),(\\d+)")
    private fun sp(c: Context) = c.getSharedPreferences("dad", Context.MODE_PRIVATE)
    fun enabled(c: Context) = app.familytv.config.FamilyConfig.photosEnabled(c)
    fun link(c: Context) = app.familytv.config.FamilyConfig.albumUrl(c)
    fun setLink(c: Context, l: String) {
        app.familytv.config.FamilyConfig.setAlbumUrl(c, l)
        sp(c).edit().remove("album_urls").apply()
    }
    fun cached(c: Context): List<String> = sp(c).getString("album_urls", "")!!.split('\n').filter { it.isNotBlank() }

    /** Fetch + parse; on success caches and returns list, on failure returns cached. */
    fun refresh(c: Context): List<String> = runCatching {
        if (!enabled(c)) return emptyList()
        val link = link(c); if (link.isBlank()) return emptyList()
        var url = link; var html = ""
        repeat(5) {   // follow redirects manually (goo.gl → photos.google.com)
            val conn = (URL(url).openConnection() as HttpURLConnection).apply { instanceFollowRedirects = false; connectTimeout = 15000; readTimeout = 20000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/120 Safari/537.36") }
            val code = conn.responseCode
            if (code in 300..399) { url = URL(URL(url), conn.getHeaderField("Location")).toString(); conn.disconnect() }
            else { html = conn.inputStream.bufferedReader().readText(); conn.disconnect(); return@repeat }
        }
        // Each item: ["url",w,h,...],ts,id,...,{meta}. Video items carry the "76647426" (video info) key in meta — skip them.
        val ms = RX.findAll(html).toList()
        val urls = ms.mapIndexedNotNull { i, m ->
            val end = minOf(ms.getOrNull(i + 1)?.range?.first ?: html.length, m.range.last + 600)
            val meta = html.substring(m.range.last + 1, end).substringBefore("\"525000002\"")
            if (meta.contains("\"76647426\"")) null else m.groupValues[1]
        }.distinct()
        if (urls.isNotEmpty()) sp(c).edit().putString("album_urls", urls.joinToString("\n")).apply()
        urls.ifEmpty { cached(c) }
    }.getOrElse { cached(c) }

    fun sized(u: String, px: Int = 600) = "$u=w$px-h$px"

    /** Download and downscale to <= maxPx on the long side. */
    fun bitmap(url: String, maxPx: Int = 600): Bitmap? = runCatching {
        val bytes = Net.open(url).inputStream.use { it.readBytes() }
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        var s = 1; while (maxOf(o.outWidth, o.outHeight) / (s * 2) >= maxPx) s *= 2
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = s; inPreferredConfig = Bitmap.Config.RGB_565 })
    }.getOrNull()
}
