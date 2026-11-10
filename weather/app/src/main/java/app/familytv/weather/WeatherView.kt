package app.familytv.weather

import android.content.Context
import android.graphics.*
import android.view.View
import app.familytv.config.FamilyConfig
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.*
import kotlin.random.Random

class WeatherView(ctx: Context) : View(ctx) {
    var forecast: Forecast? = null
        set(v) { field = v; invalidate() }
    var status: String = "Loading…"
    var offline = false
    var clock: () -> LocalDateTime = { LocalDateTime.now() }
    /** Fixed animation time for deterministic previews; null = live. */
    var frozenTimeMs: Long? = null

    var page = 0
        private set
    private var prevPage = 0
    private var pageChangedAt = 0L
    val pageNames = listOf("Now", "Hourly", "7-Day")

    fun setPage(p: Int) {
        val n = p.coerceIn(0, pageNames.size - 1)
        if (n == page) return
        prevPage = page; page = n; pageChangedAt = now(); invalidate()
    }

    private val start = System.currentTimeMillis()
    private fun now() = frozenTimeMs ?: (System.currentTimeMillis() - start)

    private val rnd = Random(7)
    private val stars = List(140) { floatArrayOf(rnd.nextFloat(), rnd.nextFloat() * 0.75f, rnd.nextFloat() * 1.6f + 0.6f, rnd.nextFloat() * 6.28f) }
    private val drops = List(220) { floatArrayOf(rnd.nextFloat(), rnd.nextFloat(), rnd.nextFloat() * 0.5f + 0.75f) }
    private val flakes = List(160) { floatArrayOf(rnd.nextFloat(), rnd.nextFloat(), rnd.nextFloat() * 0.8f + 0.5f, rnd.nextFloat() * 6.28f) }
    private val clouds = List(7) { floatArrayOf(rnd.nextFloat(), rnd.nextFloat() * 0.45f + 0.02f, rnd.nextFloat() * 0.6f + 0.7f, rnd.nextFloat() * 0.5f + 0.5f) }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; typeface = Typeface.create("sans-serif", Typeface.NORMAL) }
    private val light = Typeface.create("sans-serif-light", Typeface.NORMAL)
    private val medium = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val regular = Typeface.create("sans-serif", Typeface.NORMAL)
    private fun hm() = DateTimeFormatter.ofPattern(FamilyConfig.timePattern(context), Locale.US)
    private fun hh() = DateTimeFormatter.ofPattern(FamilyConfig.hourPattern(context), Locale.US)
    private val dow = DateTimeFormatter.ofPattern("EEE", Locale.US)
    private val dateFmt = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.US)

    private var daylight = 0f
    private var s = 1f  // scale relative to a 1920x1080 design

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        s = h / 1080f
        val t = now() / 1000f
        val f = forecast
        val sky = f?.condition?.sky ?: Sky.CLEAR
        val tod = SkyTime.at(clock(), f)
        daylight = tod.daylight
        val day = tod.isDay
        drawSky(c, w, h, t, sky, day, tod)
        // legibility scrim (stronger on bright daytime skies so white text stays readable)
        val sa = (0x33 * (1 - daylight)).toInt(); val ba = (0x66 - 0x4E * daylight).toInt()
        p.color = Color.BLACK; p.shader = LinearGradient(0f, 0f, 0f, h, intArrayOf(sa shl 24, 0, ba shl 24), floatArrayOf(0f, .4f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p); p.shader = null

        val mx = 96 * s; val my = 64 * s
        drawHeader(c, w, mx, my)
        if (f == null) {
            text(c, status, mx, h / 2, 56f, regular, Paint.Align.LEFT)
        } else {
            val k = ((now() - pageChangedAt) / 350f).coerceIn(0f, 1f)
            val e = 1 - (1 - k) * (1 - k) * (1 - k)
            if (k < 1f) {
                val dir = if (page > prevPage) 1 else -1
                c.save(); c.translate(-dir * 80 * s * e, 0f); drawPage(c, prevPage, f, w, h, mx, my, ((1 - e) * 255).toInt()); c.restore()
                c.save(); c.translate(dir * 80 * s * (1 - e), 0f); drawPage(c, page, f, w, h, mx, my, (e * 255).toInt()); c.restore()
            } else drawPage(c, page, f, w, h, mx, my, 255)
        }
        // footer
        tp.alpha = 200
        text(c, status, mx, h - my + 8 * s, 26f, regular, Paint.Align.LEFT, if (offline) 0xFFFFD27A.toInt() else 0xCCFFFFFF.toInt())
        text(c, "◀ ▶  switch view", w - mx, h - my + 8 * s, 26f, regular, Paint.Align.RIGHT, 0x99FFFFFF.toInt())
        if (frozenTimeMs == null) postInvalidateOnAnimation()
    }

    private fun drawPage(c: Canvas, pg: Int, f: Forecast, w: Float, h: Float, mx: Float, my: Float, a: Int) {
        alpha = a
        when (pg) { 0 -> drawNow(c, f, w, h, mx); 1 -> drawHourly(c, f, w, h, mx); else -> drawDaily(c, f, w, h, mx) }
        alpha = 255
    }
    private var alpha = 255
    private fun ac(color: Int): Int = (color and 0xFFFFFF) or ((((color ushr 24) * alpha) / 255) shl 24)

    // ---------- text helpers ----------
    private fun text(c: Canvas, str: String, x: Float, y: Float, size: Float, tf: Typeface, al: Paint.Align, color: Int = Color.WHITE) {
        tp.textSize = size * s; tp.typeface = tf; tp.textAlign = al; tp.color = ac(color)
        tp.setShadowLayer((8 + 4 * daylight) * s, 0f, 2 * s, ac(((0x55 + 0x40 * daylight).toInt()) shl 24))
        c.drawText(str, x, y, tp)
        tp.clearShadowLayer()
    }

    private fun drawHeader(c: Canvas, w: Float, mx: Float, my: Float) {
        val n = clock()
        text(c, FamilyConfig.homeName(context), mx, my + 40 * s, 44f, medium, Paint.Align.LEFT)
        text(c, n.format(dateFmt), mx, my + 86 * s, 28f, regular, Paint.Align.LEFT, 0xCCFFFFFF.toInt())
        text(c, n.format(hm()), w - mx, my + 40 * s, 44f, regular, Paint.Align.RIGHT)
        // tabs
        tp.textSize = 28 * s; tp.typeface = medium
        var x = w - mx
        val y = my + 70 * s
        for (i in pageNames.indices.reversed()) {
            val tw = tp.measureText(pageNames[i]); val pw = tw + 44 * s
            val r = RectF(x - pw, y, x, y + 50 * s)
            p.color = if (i == page) 0xF2FFFFFF.toInt() else 0x2EFFFFFF
            c.drawRoundRect(r, 25 * s, 25 * s, p)
            text(c, pageNames[i], r.centerX(), r.bottom - 15 * s, 28f, medium, Paint.Align.CENTER, if (i == page) 0xFF1B2A41.toInt() else Color.WHITE)
            x -= pw + 14 * s
        }
    }

    private fun card(c: Canvas, r: RectF) {
        p.color = ac(if (daylight > 0.5f) 0x2E0A2A55 else 0x2EFFFFFF); c.drawRoundRect(r, 32 * s, 32 * s, p)
    }

    private fun drawNow(c: Canvas, f: Forecast, w: Float, h: Float, mx: Float) {
        val base = h * 0.60f
        text(c, "${f.temp}°", mx - 12 * s, base, 300f, light, Paint.Align.LEFT)
        text(c, f.condition.label, mx, base + 80 * s, 64f, regular, Paint.Align.LEFT)
        text(c, "Feels like ${f.feels}°   ·   H ${f.today.hi}°  L ${f.today.lo}°", mx, base + 140 * s, 36f, regular, Paint.Align.LEFT, 0xE6FFFFFF.toInt())
        // detail grid 2x3
        val items = listOf(
            "Wind" to "${f.wind} mph ${Parser.compass(f.windDir)}",
            "Moon" to "",
            "Precip" to "${f.precipNow}%",
            "High / Low" to "${f.today.hi}° / ${f.today.lo}°",
            "Sunrise" to f.today.sunrise.format(hm()),
            "Sunset" to f.today.sunset.format(hm()))
        val cw = 300 * s; val ch = 150 * s; val gap = 24 * s
        val x0 = w - mx - 2 * cw - gap; val y0 = h * 0.30f
        items.forEachIndexed { i, (k, v) ->
            val x = x0 + (i % 2) * (cw + gap); val y = y0 + (i / 2) * (ch + gap)
            val r = RectF(x, y, x + cw, y + ch); card(c, r)
            if (k == "Moon") {
                val m = Moon.phase(f.time)
                text(c, "Moon · ${m.illumination}%", x + 30 * s, y + 52 * s, 28f, regular, Paint.Align.LEFT, 0xCCFFFFFF.toInt())
                text(c, m.name, x + 30 * s, y + 108 * s, if (m.name.length > 12) 30f else 40f, medium, Paint.Align.LEFT)
                moonIcon(c, m, x + cw - 50 * s, y + 48 * s, 26 * s)
            } else {
            text(c, k, x + 30 * s, y + 52 * s, 28f, regular, Paint.Align.LEFT, 0xCCFFFFFF.toInt())
            text(c, v, x + 30 * s, y + 112 * s, 44f, medium, Paint.Align.LEFT)
            }
        }
    }

    private fun drawHourly(c: Canvas, f: Forecast, w: Float, h: Float, mx: Float) {
        val hrs = f.hours; if (hrs.isEmpty()) return
        text(c, "Next 12 hours", mx, h * 0.27f, 48f, medium, Paint.Align.LEFT)
        val n = hrs.size; val gap = 16 * s
        val cw = (w - 2 * mx - gap * (n - 1)) / n
        val top = h * 0.33f; val bottom = h * 0.85f
        val lo = hrs.minOf { it.temp }; val hi = hrs.maxOf { it.temp }
        val gTop = top + 230 * s; val gBot = top + 330 * s
        val pts = hrs.mapIndexed { i, hr ->
            val x = mx + i * (cw + gap) + cw / 2
            val y = if (hi == lo) (gTop + gBot) / 2 else gBot - (hr.temp - lo).toFloat() / (hi - lo) * (gBot - gTop)
            PointF(x, y)
        }
        hrs.forEachIndexed { i, hr ->
            val x = mx + i * (cw + gap)
            card(c, RectF(x, top, x + cw, bottom))
            val cx = x + cw / 2
            text(c, if (i == 0) "Now" else hr.time.format(hh()), cx, top + 56 * s, 28f, medium, Paint.Align.CENTER, 0xE6FFFFFF.toInt())
            icon(c, Wmo.map(hr.code).sky, hr.isDay, cx, top + 140 * s, 64 * s)
            text(c, "${hr.temp}°", cx, pts[i].y - 26 * s, 40f, medium, Paint.Align.CENTER)
            text(c, "${hr.precip}%", cx, bottom - 30 * s, 28f, regular, Paint.Align.CENTER, if (hr.precip >= 30) 0xFF9FD3FF.toInt() else 0xB3FFFFFF.toInt())
        }
        p.style = Paint.Style.STROKE; p.strokeWidth = 4 * s; p.color = ac(0xE6FFFFFF.toInt()); p.strokeCap = Paint.Cap.ROUND
        val path = Path(); pts.forEachIndexed { i, pt -> if (i == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y) }
        c.drawPath(path, p); p.style = Paint.Style.FILL
        pts.forEach { c.drawCircle(it.x, it.y, 8 * s, p) }
    }

    private fun drawDaily(c: Canvas, f: Forecast, w: Float, h: Float, mx: Float) {
        val days = f.days.take(7)
        val lo = days.minOf { it.lo }; val hi = days.maxOf { it.hi }
        val top = h * 0.24f; val rowH = 92 * s; val gap = 8 * s
        val left = mx; val right = w - mx
        days.forEachIndexed { i, d ->
            val y = top + i * (rowH + gap)
            card(c, RectF(left, y, right, y + rowH))
            val cy = y + rowH / 2
            text(c, if (i == 0) "Today" else d.date.format(dow), left + 40 * s, cy + 15 * s, 40f, medium, Paint.Align.LEFT)
            icon(c, Wmo.map(d.code).sky, true, left + 300 * s, cy, 56 * s)
            text(c, Wmo.map(d.code).label, left + 370 * s, cy + 13 * s, 34f, regular, Paint.Align.LEFT, 0xE6FFFFFF.toInt())
            text(c, if (d.precip > 0) "${d.precip}% precip" else "", left + 760 * s, cy + 13 * s, 32f, regular, Paint.Align.LEFT, 0xFF9FD3FF.toInt())
            // range bar
            val bx0 = right - 640 * s; val bx1 = right - 160 * s
            text(c, "${d.lo}°", bx0 - 24 * s, cy + 14 * s, 38f, regular, Paint.Align.RIGHT, 0xB3FFFFFF.toInt())
            text(c, "${d.hi}°", bx1 + 24 * s, cy + 14 * s, 38f, medium, Paint.Align.LEFT)
            p.color = ac(0x33FFFFFF); c.drawRoundRect(RectF(bx0, cy - 6 * s, bx1, cy + 6 * s), 6 * s, 6 * s, p)
            val span = max(1, hi - lo).toFloat()
            val a = bx0 + (d.lo - lo) / span * (bx1 - bx0); val b = bx0 + (d.hi - lo) / span * (bx1 - bx0)
            p.color = Color.BLACK; p.shader = LinearGradient(bx0, 0f, bx1, 0f, ac(0xFF7FC8FF.toInt()), ac(0xFFFFB86B.toInt()), Shader.TileMode.CLAMP)
            c.drawRoundRect(RectF(a, cy - 6 * s, max(b, a + 12 * s), cy + 6 * s), 6 * s, 6 * s, p); p.shader = null
        }
    }

    /** Lit disc with phase terminator, same palette as the night moon icon. */
    private fun moonIcon(c: Canvas, m: MoonPhase, cx: Float, cy: Float, r: Float) {
        p.color = ac(0x55223044); c.drawCircle(cx, cy, r, p)
        val k = Math.cos(2 * Math.PI * m.fraction).toFloat() // 1 new .. -1 full
        val lit = Path()
        val oval = RectF(cx - r, cy - r, cx + r, cy + r)
        // lit half: right side when waxing, left when waning
        lit.arcTo(oval, -90f, if (m.waxing) 180f else -180f)
        val tw = Math.abs(k) * r
        val term = RectF(cx - tw, cy - r, cx + tw, cy + r)
        // terminator: bulges toward lit side when crescent (k>0), away when gibbous
        val sweep = if (m.waxing) (if (k > 0) -180f else 180f) else (if (k > 0) 180f else -180f)
        lit.arcTo(term, 90f, sweep)
        lit.close()
        p.color = ac(0xFFE8ECF5.toInt()); c.drawPath(lit, p)
    }

    // ---------- icons ----------
    private fun icon(c: Canvas, sky: Sky, day: Boolean, cx: Float, cy: Float, sz: Float) {
        val r = sz / 2
        fun sun(x: Float, y: Float, rr: Float) {
            p.color = ac(0xFFFFD54F.toInt()); c.drawCircle(x, y, rr, p)
        }
        fun moon(x: Float, y: Float, rr: Float) {
            p.color = ac(0xFFE8ECF5.toInt()); c.drawCircle(x, y, rr, p)
        }
        fun cloud(x: Float, y: Float, k: Float, col: Int) {
            p.color = ac(col)
            c.drawCircle(x - r * .35f * k, y + r * .1f * k, r * .38f * k, p)
            c.drawCircle(x + r * .1f * k, y - r * .12f * k, r * .5f * k, p)
            c.drawCircle(x + r * .5f * k, y + r * .12f * k, r * .34f * k, p)
            c.drawRoundRect(RectF(x - r * .72f * k, y + r * .05f * k, x + r * .84f * k, y + r * .46f * k), r * .2f * k, r * .2f * k, p)
        }
        when (sky) {
            Sky.CLEAR -> if (day) sun(cx, cy, r * .6f) else moon(cx, cy, r * .55f)
            Sky.PARTLY -> { if (day) sun(cx + r * .3f, cy - r * .3f, r * .42f) else moon(cx + r * .3f, cy - r * .3f, r * .38f); cloud(cx - r * .05f, cy + r * .1f, 1f, 0xFFF5F7FA.toInt()) }
            Sky.CLOUDY, Sky.FOG -> { cloud(cx + r * .2f, cy - r * .2f, .8f, 0xFFB8C2CF.toInt()); cloud(cx - r * .05f, cy + r * .05f, 1f, 0xFFECEFF3.toInt()) }
            Sky.RAIN, Sky.DRIZZLE, Sky.THUNDER, Sky.SNOW -> {
                cloud(cx, cy - r * .25f, 1f, 0xFFDDE3EA.toInt())
                when (sky) {
                    Sky.SNOW -> { p.color = ac(Color.WHITE); for (i in -1..1) c.drawCircle(cx + i * r * .4f, cy + r * .6f + (i and 1) * r * .15f, r * .1f, p) }
                    Sky.THUNDER -> { p.color = ac(0xFFFFD54F.toInt()); val pa = Path(); pa.moveTo(cx + r * .1f, cy + r * .2f); pa.lineTo(cx - r * .2f, cy + r * .62f); pa.lineTo(cx + r * .02f, cy + r * .62f); pa.lineTo(cx - r * .12f, cy + r * .98f); pa.lineTo(cx + r * .28f, cy + r * .5f); pa.lineTo(cx + r * .06f, cy + r * .5f); pa.close(); c.drawPath(pa, p) }
                    else -> { p.color = ac(0xFF8AC7FF.toInt()); p.strokeWidth = r * .1f; p.strokeCap = Paint.Cap.ROUND
                        for (i in -1..1) c.drawLine(cx + i * r * .4f, cy + r * .4f, cx + i * r * .4f - r * .12f, cy + r * .75f, p) }
                }
            }
        }
    }

    // ---------- sky ----------
    private fun drawSky(c: Canvas, w: Float, h: Float, t: Float, sky: Sky, day: Boolean, tod: SkyTime.Result) {
        val (wTop, wBot) = when {
            sky == Sky.THUNDER -> 0xFF1C2230.toInt() to 0xFF3A4152.toInt()
            sky == Sky.CLEAR -> 0xFF2F6FD6.toInt() to 0xFF8CC4F8.toInt()
            sky == Sky.PARTLY -> 0xFF3C74C9.toInt() to 0xFF9CC3EA.toInt()
            sky == Sky.SNOW -> 0xFF7D8EA6.toInt() to 0xFFC9D3DF.toInt()
            sky == Sky.FOG -> 0xFF8E99A6.toInt() to 0xFFC4CBD2.toInt()
            sky == Sky.RAIN || sky == Sky.DRIZZLE -> 0xFF3E4C5E.toInt() to 0xFF6F7F92.toInt()
            else -> 0xFF5D6F86.toInt() to 0xFF9DAEC2.toInt()
        }
        // weather palette dimmed toward night by daylight, then blended over the time-of-day clear sky
        val nightW = 0xFF141A28.toInt() to 0xFF2C3446.toInt()
        val dl = tod.daylight
        val wt = SkyTime.mix(nightW.first, wTop, dl); val wb = SkyTime.mix(nightW.second, wBot, dl)
        val amt = when (sky) { Sky.CLEAR -> 0f; Sky.PARTLY -> 0.2f; Sky.THUNDER -> 1f; else -> 0.75f }
        val top = SkyTime.mix(tod.top, wt, amt); val bot = SkyTime.mix(tod.bottom, wb, amt)
        p.color = Color.BLACK; p.shader = LinearGradient(0f, 0f, 0f, h, top, bot, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p); p.shader = null

        val clear = sky == Sky.CLEAR || sky == Sky.PARTLY
        if (!day && clear) {
            for (st in stars) {
                val tw = 0.55f + 0.45f * sin(t * 1.3f + st[3])
                p.color = Color.argb((tw * 230).toInt(), 255, 255, 255)
                c.drawCircle(st[0] * w, st[1] * h, st[2] * s, p)
            }
            val mx = w * 0.43f; val my = h * 0.17f; val mr = 64 * s
            p.color = Color.BLACK; p.shader = RadialGradient(mx, my, mr * 3.5f, 0x40C8D8FF, 0x00C8D8FF, Shader.TileMode.CLAMP)
            c.drawCircle(mx, my, mr * 3.5f, p); p.shader = null
            p.color = 0xFFF1F3F8.toInt(); c.drawCircle(mx, my, mr, p)
            p.color = 0x1A8090A8; c.drawCircle(mx - mr * .3f, my - mr * .2f, mr * .18f, p); c.drawCircle(mx + mr * .25f, my + mr * .3f, mr * .12f, p)
        }
        if (day && clear) {
            val sx = w * 0.43f; val sy = h * 0.17f; val sr = 80 * s
            p.color = Color.BLACK; p.shader = RadialGradient(sx, sy, sr * 5f, 0x66FFE9A8, 0x00FFE9A8, Shader.TileMode.CLAMP)
            c.drawCircle(sx, sy, sr * 5f, p); p.shader = null
            c.save(); c.rotate(t * 4f, sx, sy)
            p.color = 0x22FFF4C4
            for (i in 0 until 12) { c.rotate(30f, sx, sy); c.drawRect(sx - 6 * s, sy - sr * 2.1f, sx + 6 * s, sy - sr * 1.3f, p) }
            c.restore()
            val pulse = 1f + 0.03f * sin(t * 1.5f)
            p.color = 0xFFFFE07A.toInt(); c.drawCircle(sx, sy, sr * pulse, p)
            p.color = 0xFFFFF3C4.toInt(); c.drawCircle(sx, sy, sr * 0.78f * pulse, p)
        }
        // clouds
        val nClouds = when (sky) { Sky.CLEAR -> 0; Sky.PARTLY -> 3; else -> 7 }
        val cloudCol = when {
            sky == Sky.THUNDER -> 0xCC4A5163.toInt()
            sky == Sky.RAIN || sky == Sky.DRIZZLE -> 0xB37C8A9C.toInt()
            !day -> 0x8C5A6478.toInt()
            sky == Sky.PARTLY -> 0xE6FFFFFF.toInt()
            else -> 0xB3E6EBF1.toInt()
        }
        for (i in 0 until nClouds) {
            val cl = clouds[i]
            val span = w * 1.6f
            val x = ((cl[0] * span + t * 12f * s * cl[3]) % span) - w * 0.3f
            puff(c, x, cl[1] * h, 220 * s * cl[2], cloudCol)
        }
        if (sky == Sky.FOG) {
            for (i in 0 until 5) {
                val y = h * (0.35f + i * 0.13f); val off = sin(t * 0.2f + i) * 80 * s
                p.color = Color.BLACK; p.shader = LinearGradient(0f, y - 60 * s, 0f, y + 60 * s, intArrayOf(0x00FFFFFF, 0x55FFFFFF, 0x00FFFFFF), null, Shader.TileMode.CLAMP)
                c.drawRect(-200 * s + off, y - 60 * s, w + 200 * s + off, y + 60 * s, p); p.shader = null
            }
        }
        if (sky == Sky.RAIN || sky == Sky.DRIZZLE || sky == Sky.THUNDER) {
            val n = if (sky == Sky.DRIZZLE) 90 else drops.size
            p.strokeWidth = (if (sky == Sky.DRIZZLE) 2f else 3f) * s; p.strokeCap = Paint.Cap.ROUND
            p.color = 0x8CCFE3FF.toInt()
            val len = (if (sky == Sky.DRIZZLE) 18f else 38f) * s
            for (i in 0 until n) {
                val d = drops[i]
                val y = ((d[1] + t * 0.9f * d[2]) % 1.1f) * h - 0.05f * h
                val x = d[0] * w * 1.1f - (y / h) * 0.08f * w
                c.drawLine(x, y, x - len * 0.25f, y + len * d[2], p)
            }
        }
        if (sky == Sky.THUNDER) {
            val cyc = t % 7f
            val fl = when { cyc < 0.08f -> 0.7f; cyc in 0.16f..0.26f -> 0.45f; else -> 0f }
            if (fl > 0f) {
                p.color = Color.argb((fl * 255).toInt(), 230, 235, 255); c.drawRect(0f, 0f, w, h, p)
                val pa = Path(); var x = w * 0.62f; var y = 0f; pa.moveTo(x, y)
                val br = Random(((t / 7f).toInt()))
                while (y < h * 0.7f) { x += (br.nextFloat() - 0.5f) * 90 * s; y += 60 * s; pa.lineTo(x, y) }
                p.style = Paint.Style.STROKE; p.strokeWidth = 5 * s; p.color = Color.WHITE; c.drawPath(pa, p); p.style = Paint.Style.FILL
            }
        }
        if (sky == Sky.SNOW) {
            p.color = 0xE6FFFFFF.toInt()
            for (fk in flakes) {
                val y = ((fk[1] + t * 0.07f * fk[2]) % 1.05f) * h - 0.02f * h
                val x = fk[0] * w + sin(t * 0.8f + fk[3]) * 30 * s
                c.drawCircle(x, y, 5 * s * fk[2], p)
            }
        }
    }

    private fun puff(c: Canvas, x: Float, y: Float, sz: Float, col: Int) {
        p.color = col
        c.drawCircle(x, y, sz * .45f, p)
        c.drawCircle(x + sz * .5f, y - sz * .2f, sz * .6f, p)
        c.drawCircle(x + sz * 1.05f, y, sz * .42f, p)
        c.drawRoundRect(RectF(x - sz * .2f, y, x + sz * 1.35f, y + sz * .42f), sz * .21f, sz * .21f, p)
    }
}
