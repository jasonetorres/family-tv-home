package app.familytv.weather

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.WindowManager
import android.app.AlertDialog
import android.widget.LinearLayout
import android.widget.Switch
import app.familytv.config.FamilyConfig
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private lateinit var view: WeatherView
    private lateinit var repo: Repo
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val refresh = object : Runnable { override fun run() { load(); main.postDelayed(this, 15 * 60 * 1000L) } }
    private fun fmt() = DateTimeFormatter.ofPattern(FamilyConfig.timePattern(this), Locale.US)

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        repo = Repo(this)
        view = WeatherView(this).apply { isFocusable = true }
        setContentView(view)
        repo.cached()?.let { (f, at) -> view.forecast = f; view.status = "Cached · ${time(at)}" }
    }

    override fun onResume() { super.onResume(); main.post(refresh) }
    override fun onPause() { super.onPause(); main.removeCallbacks(refresh) }

    private fun time(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.of(FamilyConfig.timezone(this))).format(fmt())

    private fun load() {
        io.execute {
            val r = runCatching { repo.fetch() }
            main.post {
                r.onSuccess { view.forecast = it; view.offline = false; view.status = "Updated ${time(System.currentTimeMillis())} · Open-Meteo" }
                 .onFailure {
                     val c = repo.cached()
                     view.offline = true
                     view.status = if (c != null) { view.forecast = c.first; "Offline · showing data from ${time(c.second)}" }
                                   else "Offline · no weather data yet, retrying…"
                     if (c == null) main.postDelayed({ load() }, 60_000)
                 }
                view.invalidate()
            }
        }
    }

    override fun onKeyDown(code: Int, e: KeyEvent): Boolean {
        when (code) {
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> view.setPage(view.page + 1)
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> view.setPage(view.page - 1)
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> view.setPage((view.page + 1) % 3)
            KeyEvent.KEYCODE_DPAD_UP -> load()
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_INFO -> settings()
            else -> return super.onKeyDown(code, e)
        }
        return true
    }

    private fun settings() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 24, 48, 8) }
        val units = Switch(this).apply { text = "Fahrenheit"; textSize = 18f; isChecked = FamilyConfig.fahrenheit(this@MainActivity) }
        val clock = Switch(this).apply { text = "12-hour clock"; textSize = 18f; isChecked = FamilyConfig.clock12(this@MainActivity) }
        box.addView(units); box.addView(clock)
        AlertDialog.Builder(this).setTitle(FamilyConfig.homeName(this)).setView(box)
            .setPositiveButton("Save") { _, _ ->
                FamilyConfig.setUnits(this, units.isChecked)
                FamilyConfig.setClock(this, clock.isChecked)
                load()
            }
            .setNegativeButton("Cancel", null).show()
    }
}
