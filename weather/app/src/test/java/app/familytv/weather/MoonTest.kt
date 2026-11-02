package app.familytv.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class MoonTest {
    @Test fun knownPhases() {
        // Full moon 2026-10-26 ~00:12 EDT; new moon 2026-10-10 ~11:50 EDT
        val full = Moon.phase(LocalDateTime.of(2026, 10, 26, 0, 12))
        assertEquals("Full Moon", full.name); assertTrue(full.illumination >= 97)
        val new = Moon.phase(LocalDateTime.of(2026, 10, 10, 11, 50))
        assertEquals("New Moon", new.name); assertTrue(new.illumination <= 3)
        val g = Moon.phase(LocalDateTime.of(2026, 10, 22, 20, 0))
        assertEquals("Waxing Gibbous", g.name)
    }
}
