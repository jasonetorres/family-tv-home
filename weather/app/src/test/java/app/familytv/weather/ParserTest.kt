package app.familytv.weather

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ParserTest {
    private val json = javaClass.getResource("/sample.json")!!.readText()

    @Test fun parsesSample() {
        val f = Parser.parse(json)
        assertEquals(7, f.days.size)
        assertEquals(12, f.hours.size)
        assertFalse(f.hours.first().time.isAfter(f.time))
        assertTrue(f.today.hi >= f.today.lo)
        assertTrue(f.humidity in 0..100)
        assertTrue(f.today.sunrise.isBefore(f.today.sunset))
    }

    @Test fun roundsAndHourWindow() {
        val j = """{"current":{"time":"2026-01-05T07:45","temperature_2m":21.5,"apparent_temperature":10.4,
          "relative_humidity_2m":80,"is_day":0,"weather_code":73,"wind_speed_10m":12.6,"wind_direction_10m":270},
          "hourly":{"time":${(0..23).map { "\"2026-01-05T%02d:00\"".format(it) }},
            "temperature_2m":${(0..23).map { it.toDouble() }},"precipitation_probability":${(0..23).map { 50 }},
            "weather_code":${(0..23).map { 73 }},"is_day":${(0..23).map { if (it in 8..16) 1 else 0 }}},
          "daily":{"time":["2026-01-05"],"weather_code":[73],"temperature_2m_max":[25.4],"temperature_2m_min":[12.6],
            "sunrise":["2026-01-05T07:39"],"sunset":["2026-01-05T16:53"],"precipitation_probability_max":[90]}}"""
        val f = Parser.parse(j)
        assertEquals(22, f.temp); assertEquals(10, f.feels); assertEquals(13, f.wind)
        assertEquals("W", Parser.compass(f.windDir))
        assertEquals(7, f.hours.first().time.hour)   // current hour included
        assertEquals(12, f.hours.size)
        assertEquals(Sky.SNOW, f.condition.sky); assertFalse(f.isDay)
        assertEquals(25, f.today.hi); assertEquals(13, f.today.lo)
    }

    @Test fun nullPrecipTolerated() {
        val j = json.replaceFirst(Regex("\"precipitation_probability\":\\[\\d+"), "\"precipitation_probability\":[null")
        Parser.parse(j)
    }

    @Test fun wmoMapping() {
        assertEquals(Sky.CLEAR, Wmo.map(0).sky)
        assertEquals(Sky.PARTLY, Wmo.map(2).sky)
        assertEquals(Sky.CLOUDY, Wmo.map(3).sky)
        assertEquals(Sky.FOG, Wmo.map(45).sky)
        assertEquals(Sky.DRIZZLE, Wmo.map(53).sky)
        assertEquals(Sky.RAIN, Wmo.map(63).sky); assertEquals(Sky.RAIN, Wmo.map(81).sky)
        assertEquals(Sky.SNOW, Wmo.map(75).sky); assertEquals(Sky.SNOW, Wmo.map(86).sky)
        assertEquals(Sky.THUNDER, Wmo.map(95).sky); assertEquals(Sky.THUNDER, Wmo.map(99).sky)
        assertEquals("Unknown", Wmo.map(42).label)
        assertEquals("N", Parser.compass(359)); assertEquals("SE", Parser.compass(135))
    }
}
