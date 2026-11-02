package app.familytv.weather

import android.graphics.Color
import java.time.Duration
import java.time.LocalDateTime

/** Picks sky colours from the current local time relative to that day's sunrise/sunset. Continuous, so it crossfades live. */
object SkyTime {
    data class Result(val top: Int, val bottom: Int, val daylight: Float, val isDay: Boolean, val phase: String)

    private val NIGHT = 0xFF070B1F.toInt() to 0xFF243660.toInt()
    private val PREDAWN = 0xFF1C2452.toInt() to 0xFF6A4E80.toInt()
    private val DAWN = 0xFF5A90D8.toInt() to 0xFFF6C49A.toInt()
    private val MORNING = 0xFF4A9BE0.toInt() to 0xFFA8D4F5.toInt()
    private val MIDDAY = 0xFF4FA2E6.toInt() to 0xFFB0DAF8.toInt()
    private val GOLDEN = 0xFF5A8FCC.toInt() to 0xFFF6CC8A.toInt()
    private val DUSK = 0xFF333E7A.toInt() to 0xFFE07E5A.toInt()
    private val TWILIGHT = 0xFF141B3E.toInt() to 0xFF3D3C6E.toInt()

    fun mix(a: Int, b: Int, k: Float): Int {
        val t = k.coerceIn(0f, 1f)
        fun ch(sh: Int) = (((a shr sh) and 0xFF) + (((b shr sh) and 0xFF) - ((a shr sh) and 0xFF)) * t).toInt()
        return Color.argb(255, ch(16), ch(8), ch(0))
    }

    fun at(now: LocalDateTime, f: Forecast?): Result {
        val d = f?.days?.firstOrNull { it.date.toLocalDate() == now.toLocalDate() } ?: f?.days?.firstOrNull()
        val date = now.toLocalDate()
        val sr = d?.sunrise?.toLocalTime()?.atDate(date) ?: date.atTime(7, 0)
        val ss = d?.sunset?.toLocalTime()?.atDate(date) ?: date.atTime(18, 0)
        val m = Duration.between(sr, now).toMinutes().toFloat()          // minutes since sunrise
        val len = Duration.between(sr, ss).toMinutes().toFloat()         // day length
        // keyframes: minutes relative to sunrise -> palette, daylight
        val keys = listOf(
            -90f to Triple(NIGHT, 0f, "night"), -40f to Triple(PREDAWN, 0.15f, "pre-dawn"),
            0f to Triple(DAWN, 0.8f, "dawn"), 40f to Triple(MORNING, 1f, "morning"),
            len / 2 to Triple(MIDDAY, 1f, "midday"), len - 75f to Triple(MORNING, 1f, "afternoon"),
            len - 30f to Triple(GOLDEN, 0.8f, "golden hour"), len to Triple(DUSK, 0.45f, "dusk"),
            len + 40f to Triple(TWILIGHT, 0.12f, "twilight"), len + 90f to Triple(NIGHT, 0f, "night"))
        val isDay = m >= 0f && m < len
        if (m <= keys.first().first || m >= keys.last().first) return Result(NIGHT.first, NIGHT.second, 0f, false, "night")
        val i = keys.indexOfLast { it.first <= m }
        val (a, b) = keys[i] to keys[i + 1]
        val k = (m - a.first) / (b.first - a.first)
        val e = k * k * (3 - 2 * k)
        return Result(mix(a.second.first.first, b.second.first.first, e), mix(a.second.first.second, b.second.first.second, e),
            a.second.second + (b.second.second - a.second.second) * e, isDay, if (k < 0.5f) a.second.third else b.second.third)
    }
}
