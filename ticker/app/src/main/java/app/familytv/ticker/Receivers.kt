package app.familytv.ticker
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        Watchdog.schedule(c)
        if (Prefs.shouldRun(c) && !TickerService.running) { EventLog.add(c, "watchdog/boot (${i.action}): restarting service"); Prefs.apply(c) }
    }
}
/** adb shell am broadcast -a app.familytv.ticker.TOGGLE -n app.familytv.ticker/.ToggleReceiver
 *  adb shell am broadcast -a app.familytv.ticker.SET_TEAMS --es teams "BUF,NYK" -n app.familytv.ticker/.ToggleReceiver
 *  adb shell am broadcast -a app.familytv.ticker.SET_MODE --es mode ALWAYS|LIVE_ONLY|OFF -n app.familytv.ticker/.ToggleReceiver */
class ToggleReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action?.endsWith("SET_TEAMS") == true) {
            val (set, bad) = Prefs.parseTeams(i.getStringExtra("teams") ?: "")
            Prefs.setFavs(c, set); if (Prefs.shouldRun(c)) Prefs.apply(c)
            Toast.makeText(c, "Game Day teams: " + (set.sorted().joinToString().ifEmpty { "none" }) +
                (if (bad.isNotEmpty()) "  (unknown: ${bad.joinToString()})" else ""), Toast.LENGTH_LONG).show(); return
        }
        if (i.action?.endsWith("SET_ALERTS") == true) {   // --ez on true|false
            Prefs.setAlerts(c, i.getBooleanExtra("on", true), "broadcast"); Prefs.apply(c)
            Toast.makeText(c, "Score alerts " + (if (Prefs.alerts(c)) "ON" else "OFF"), Toast.LENGTH_SHORT).show(); return
        }
        if (i.action?.endsWith("INJECT_PREV") == true) {   // test: --es team PIT --ei delta 7 -> fakes an older baseline; next real poll detects it
            if (TickerService.running) c.startForegroundService(Intent(c, TickerService::class.java).setAction("INJECT").putExtras(i)); return
        }
        if (i.action?.endsWith("CELEBRATE") == true) {   // test hook: --es team BUF --es league NFL --ei delta 6
            c.startForegroundService(Intent(c, TickerService::class.java).setAction("CELEBRATE").putExtras(i)); return
        }
        val m = if (i.action?.endsWith("SET_MODE") == true)
            runCatching { Mode.valueOf(i.getStringExtra("mode")!!.uppercase()) }.getOrNull()?.also { Prefs.setMode(c, it, "broadcast"); Prefs.apply(c) }
        else Prefs.toggle(c)
        Toast.makeText(c, if (m == null) "Score Ticker: bad mode" else if (m == Mode.OFF) "Ticker OFF" else "Ticker ON", Toast.LENGTH_SHORT).show()
    }
}
