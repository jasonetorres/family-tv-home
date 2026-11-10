package app.familytv.weather

import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset

/** Local moon-phase calc (synodic month from a known new moon, 2000-01-06 18:14 UTC). */
data class MoonPhase(val age: Double, val fraction: Double, val illumination: Int, val name: String) {
    val waxing get() = fraction < 0.5
}

object Moon {
    const val SYNODIC = 29.530588853
    private val EPOCH = LocalDateTime.of(2000, 1, 6, 18, 14)

    fun phase(t: LocalDateTime, zoneOffsetHours: Int = -4): MoonPhase {
        val utc = t.minusHours(zoneOffsetHours.toLong())
        val days = Duration.between(EPOCH.toInstant(ZoneOffset.UTC), utc.toInstant(ZoneOffset.UTC)).toMinutes() / 1440.0
        val age = ((days % SYNODIC) + SYNODIC) % SYNODIC
        val frac = age / SYNODIC
        val illum = Math.round((1 - Math.cos(2 * Math.PI * frac)) / 2 * 100).toInt()
        val name = when {
            age < 1.0 || age >= SYNODIC - 1.0 -> "New Moon"
            age < 6.38 -> "Waxing Crescent"
            age < 8.38 -> "First Quarter"
            age < 13.77 -> "Waxing Gibbous"
            age < 15.77 -> "Full Moon"
            age < 21.15 -> "Waning Gibbous"
            age < 23.15 -> "Last Quarter"
            else -> "Waning Crescent"
        }
        return MoonPhase(age, frac, illum, name)
    }
}
