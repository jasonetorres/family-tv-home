package app.familytv.weather

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [31], qualifiers = "w1920dp-h1080dp-land-television-mdpi")
class PreviewTest {
    private val base = Parser.parse(javaClass.getResource("/sample.json")!!.readText())

    private fun render(name: String, f: Forecast, page: Int, t: Long = 4200, offline: Boolean = false, at: LocalDateTime = LocalDateTime.of(2026, 10, 24, 16, 42)) {
        val v = WeatherView(RuntimeEnvironment.getApplication())
        v.frozenTimeMs = 0; v.setPage(page); v.frozenTimeMs = t
        v.forecast = f; v.offline = offline
        v.status = if (offline) "Offline · showing data from 4:30 PM" else "Updated 4:30 PM · Open-Meteo"
        v.clock = { at }
        v.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, 1920, 1080)
        val bmp = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        v.draw(Canvas(bmp)); v.draw(Canvas(bmp))
        val dir = File(System.getProperty("previewDir") ?: "build/previews").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun renderAll() {
        render("now_clear_day", base, 0)
        render("hourly", base, 1)
        render("daily", base, 2)
        render("now_night_clear", base.copy(isDay = false, code = 0), 0)
        render("now_rain", base.copy(code = 63, temp = 48), 0)
        render("now_snow", base.copy(code = 73, temp = 27, feels = 18), 0)
        render("now_thunder_flash", base.copy(code = 95), 0, t = 7040)
        render("now_cloudy_offline", base.copy(code = 3), 0, offline = true)
        render("now_fog", base.copy(code = 45), 0)
        val live = Parser.parse(javaClass.getResource("/live.json")!!.readText())
        println("SKYTIME " + SkyTime.at(LocalDateTime.of(2026,10,25,8,44), live).let { "%08X %08X %.2f %s".format(it.top, it.bottom, it.daylight, it.phase) })
        render("live_0844", live, 0, at = LocalDateTime.of(2026, 10, 25, 8, 44))
        val d = base.today.date.toLocalDate()
        render("tod_morning_0834", base.copy(code = 0), 0, at = d.atTime(8, 34))
        render("tod_midday", base.copy(code = 0), 0, at = d.atTime(12, 30))
        render("tod_dusk", base.copy(code = 0), 0, at = base.today.sunset.plusMinutes(5))
        render("tod_night", base.copy(code = 0), 0, at = d.atTime(22, 0))
        render("tod_morning_cloudy", base.copy(code = 3), 0, at = d.atTime(8, 34))
        render("tod_dawn", base.copy(code = 1), 0, at = base.today.sunrise.plusMinutes(10))
    }
}
