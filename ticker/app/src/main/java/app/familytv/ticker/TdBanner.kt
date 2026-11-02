package app.familytv.ticker
import android.content.Context
import android.graphics.*
import android.view.View
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Everything the touchdown banner needs, built from real poll data (or demo data). */
class TdData(val team: String, val teamName: String, val teamColor: Int, val teamAlt: Int, val logo: Bitmap?, val delta: Int,
             val away: String, val awayScore: String, val awayColor: Int, val awayLogo: Bitmap?,
             val home: String, val homeScore: String, val homeColor: Int, val homeLogo: Bitmap?,
             val period: String, val clock: String, val headline: String = "TOUCHDOWN")

/** Downloads ESPN logos once and caches them on disk (cacheDir/logos). Call off the main thread. */
object LogoCache {
    fun get(c: Context, url: String): Bitmap? {
        if (url.isBlank()) return null
        val dir = File(c.cacheDir, "logos").apply { mkdirs() }
        val f = File(dir, url.hashCode().toUInt().toString(16) + ".png")
        if (!f.exists() || f.length() == 0L) runCatching {
            val con = URL(url).openConnection() as HttpURLConnection
            con.connectTimeout = 8000; con.readTimeout = 8000
            try { val bytes = con.inputStream.readBytes(); f.writeBytes(bytes) } finally { con.disconnect() }
        }
        return runCatching { BitmapFactory.decodeFile(f.absolutePath) }.getOrNull()
    }
    fun espn(abbr: String) = "https://a.espncdn.com/i/teamlogos/nfl/500/${abbr.lowercase()}.png"
}

/** Draws the banner in a 1000x340 design space, scaled to the view. Mirrored by tools/td_preview.py. */
class TdBannerView(c: Context, private val d: TdData) : View(c) {
    private val font = runCatching { resources.getFont(R.font.bangers) }.getOrDefault(Typeface.DEFAULT_BOLD)
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var shadowMask: Bitmap? = null
    init { setLayerType(LAYER_TYPE_SOFTWARE, null); isFocusable = false; isClickable = false }

    override fun onDraw(cv: Canvas) {
        val s = minOf(width / W, height / H)
        cv.save(); cv.translate((width - W * s) / 2f, (height - H * s) / 2f); cv.scale(s, s)
        drawBanner(cv); cv.restore()
    }

    private fun darker(c: Int, f: Float) = Color.rgb((Color.red(c) * f).toInt(), (Color.green(c) * f).toInt(), (Color.blue(c) * f).toInt())

    private fun drawBanner(cv: Canvas) {
        val rnd = java.util.Random(7)
        // burst streaks
        val streakCols = intArrayOf(d.teamColor, d.teamAlt, Color.WHITE)
        for ((i, st) in STREAKS.withIndex()) {
            p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND; p.strokeWidth = st[4]
            p.color = streakCols[i % 3]; p.setShadowLayer(3f, 0f, 2f, 0x66000000)
            cv.drawLine(st[0], st[1], st[2], st[3], p)
        }
        p.clearShadowLayer()
        // pill banner
        val pill = RectF(150f, 40f, 905f, 232f); val path = Path().apply { addRoundRect(pill, 96f, 96f, Path.Direction.CW) }
        p.style = Paint.Style.FILL; p.color = 0xFF0A0F1E.toInt(); p.setShadowLayer(14f, 0f, 8f, 0x99000000.toInt())
        cv.drawRoundRect(RectF(pill.left - 9, pill.top - 9, pill.right + 9, pill.bottom + 9), 105f, 105f, p); p.clearShadowLayer()
        p.color = d.teamColor; cv.drawPath(path, p)
        cv.save(); cv.clipPath(path)
        repeat(70) { // brushed paint texture
            val y = 40f + rnd.nextFloat() * 192f; val x = 120f + rnd.nextFloat() * 700f; val len = 120f + rnd.nextFloat() * 320f
            p.style = Paint.Style.STROKE; p.strokeWidth = 3f + rnd.nextFloat() * 10f; p.strokeCap = Paint.Cap.ROUND
            p.color = if (rnd.nextBoolean()) Color.argb(28 + rnd.nextInt(40), 255, 255, 255) else Color.argb(40 + rnd.nextInt(50), 0, 0, 0)
            cv.drawLine(x, y, x + len, y + rnd.nextFloat() * 8f - 4f, p)
        }
        p.style = Paint.Style.FILL; p.shader = LinearGradient(0f, 40f, 0f, 232f, 0x33FFFFFF, 0x55000000, Shader.TileMode.CLAMP)
        cv.drawRect(pill, p); p.shader = null; cv.restore()
        p.style = Paint.Style.STROKE; p.strokeWidth = 6f; p.color = Color.WHITE; cv.drawPath(path, p)

        // scoreboard bar
        drawScoreBar(cv)
        // red paint swipe badge
        val sw = Path().apply {
            moveTo(642f, 138f); lineTo(842f, 126f); lineTo(832f, 156f); lineTo(850f, 170f); lineTo(830f, 196f)
            lineTo(838f, 222f); lineTo(650f, 232f); lineTo(660f, 210f); lineTo(636f, 190f); lineTo(656f, 168f); close()
        }
        p.style = Paint.Style.FILL; p.color = when (d.headline) { "TOUCHDOWN" -> 0xFFD7192B.toInt(); "FIELD GOAL" -> 0xFF1E8E3E.toInt(); "SAFETY" -> 0xFFF29900.toInt(); "EXTRA POINT", "2-PT CONVERSION" -> 0xFF5E35B1.toInt(); else -> 0xFF455A64.toInt() }; p.setShadowLayer(8f, 0f, 4f, 0x88000000.toInt()); cv.drawPath(sw, p); p.clearShadowLayer()
        repeat(14) { p.style = Paint.Style.STROKE; p.strokeWidth = 2f + rnd.nextFloat() * 4; p.color = Color.argb(70, 255, 255, 255)
            val y = 140f + rnd.nextFloat() * 85; cv.drawLine(650f + rnd.nextFloat() * 30, y, 800f + rnd.nextFloat() * 40, y - 6, p) }
        // football
        drawFootball(cv, 900f, 168f)
        // text
        outlined(cv, d.headline, 548f, 128f, if (d.headline == "TOUCHDOWN") 104f else 92f, maxW = 560f)
        outlined(cv, d.teamName.uppercase() + "!", 470f, 222f, 112f, maxW = 330f)
        outlined(cv, "+${d.delta}", 745f, 214f, 92f)
        // logo, tilted, overlapping left edge
        d.logo?.let { bm ->
            cv.save(); cv.rotate(-8f, 175f, 150f)
            val dst = RectF(28f, 20f, 322f, 270f).fitTo(bm)
            // Soft dark silhouette shadow (alpha mask only, never the coloured bitmap), then the logo exactly once.
            val sh = shadowMask ?: bm.extractAlpha().also { shadowMask = it }
            val sp = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                color = 0x66000000; maskFilter = BlurMaskFilter(10f, BlurMaskFilter.Blur.NORMAL) }
            cv.save(); cv.translate(5f, 6f); cv.drawBitmap(sh, null, dst, sp); cv.restore()
            cv.drawBitmap(bm, null, dst, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)); cv.restore()
        }
    }

    private fun RectF.fitTo(bm: Bitmap): RectF {
        val sc = minOf(width() / bm.width, height() / bm.height); val w = bm.width * sc; val h = bm.height * sc
        return RectF(centerX() - w / 2, centerY() - h / 2, centerX() + w / 2, centerY() + h / 2)
    }

    private fun outlined(cv: Canvas, t: String, cx: Float, base: Float, size: Float, maxW: Float = 9999f) {
        p.typeface = font; p.textAlign = Paint.Align.CENTER; p.textSize = size; p.textSkewX = -0.18f; p.shader = null
        val w = p.measureText(t); if (w > maxW) p.textScaleX = maxW / w else p.textScaleX = 1f
        p.style = Paint.Style.FILL; p.color = 0xCC000000.toInt(); cv.drawText(t, cx + 5f, base + 7f, p)        // drop shadow
        p.style = Paint.Style.STROKE; p.strokeJoin = Paint.Join.ROUND; p.strokeWidth = 14f; p.color = 0xFF0B1230.toInt()
        cv.drawText(t, cx, base, p)
        p.style = Paint.Style.FILL; p.shader = LinearGradient(0f, base - size * 0.8f, 0f, base, Color.WHITE, 0xFFDDE3EE.toInt(), Shader.TileMode.CLAMP)
        cv.drawText(t, cx, base, p); p.shader = null; p.textSkewX = 0f; p.textScaleX = 1f
    }

    private fun drawScoreBar(cv: Canvas) {
        val bar = RectF(195f, 240f, 870f, 304f); val bp = Path().apply { addRoundRect(bar, 32f, 32f, Path.Direction.CW) }
        p.style = Paint.Style.FILL; p.color = 0xFF0A0F1E.toInt(); p.setShadowLayer(10f, 0f, 5f, 0x99000000.toInt())
        cv.drawRoundRect(RectF(bar.left - 6, bar.top - 6, bar.right + 6, bar.bottom + 6), 38f, 38f, p); p.clearShadowLayer()
        cv.save(); cv.clipPath(bp)
        p.shader = LinearGradient(0f, 240f, 0f, 304f, d.awayColor, darker(d.awayColor, 0.6f), Shader.TileMode.CLAMP); cv.drawRect(195f, 240f, 450f, 304f, p)
        p.shader = LinearGradient(0f, 240f, 0f, 304f, 0xFF2A2F3A.toInt(), 0xFF0E1118.toInt(), Shader.TileMode.CLAMP); cv.drawRect(450f, 240f, 615f, 304f, p)
        p.shader = LinearGradient(0f, 240f, 0f, 304f, d.homeColor, darker(d.homeColor, 0.6f), Shader.TileMode.CLAMP); cv.drawRect(615f, 240f, 870f, 304f, p)
        p.shader = null; p.color = 0x55000000; cv.drawRect(320f, 240f, 450f, 304f, p); cv.drawRect(615f, 240f, 720f, 304f, p)
        cv.restore()
        p.style = Paint.Style.STROKE; p.strokeWidth = 3f; p.color = 0xCCFFFFFF.toInt(); cv.drawPath(bp, p)
        p.style = Paint.Style.FILL; p.color = Color.WHITE; p.typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD); p.textSkewX = 0f
        p.textAlign = Paint.Align.CENTER
        p.textSize = 32f; cv.drawText(d.away, 262f, 284f, p); cv.drawText(d.home, 770f, 284f, p)
        p.textSize = 46f; cv.drawText(d.awayScore, 385f, 290f, p); cv.drawText(d.homeScore, 667f, 290f, p)
        p.textSize = 28f; p.color = 0xFFE0E4EC.toInt(); cv.drawText("${d.period}  ${d.clock}".trim(), 532f, 283f, p)
        d.homeLogo?.let { cv.drawBitmap(it, null, RectF(812f, 246f, 864f, 298f).fitTo(it), null) }
    }

    private fun drawFootball(cv: Canvas, cx: Float, cy: Float) {
        cv.save(); cv.rotate(-35f, cx, cy)
        val r = RectF(cx - 78, cy - 48, cx + 78, cy + 48)
        p.style = Paint.Style.FILL; p.setShadowLayer(10f, 4f, 6f, 0x99000000.toInt())
        p.shader = RadialGradient(cx - 20, cy - 20, 110f, 0xFFA0522D.toInt(), 0xFF4A1E0C.toInt(), Shader.TileMode.CLAMP)
        cv.drawOval(r, p); p.shader = null; p.clearShadowLayer()
        p.color = Color.WHITE; p.style = Paint.Style.STROKE; p.strokeWidth = 7f
        cv.drawLine(cx - 58, cy - 26, cx - 58, cy + 26, p); cv.drawLine(cx + 58, cy - 26, cx + 58, cy + 26, p)
        p.strokeWidth = 5f; cv.drawLine(cx - 30, cy, cx + 30, cy, p)
        p.strokeWidth = 4f; for (i in -2..2) cv.drawLine(cx + i * 12, cy - 9, cx + i * 12, cy + 9, p)
        p.style = Paint.Style.FILL; cv.restore()
    }

    companion object {
        const val W = 1000f; const val H = 340f
        /** x1,y1,x2,y2,width — burst streaks radiating out of the banner corners. */
        val STREAKS = arrayOf(
            floatArrayOf(115f, 70f, 70f, 40f, 12f), floatArrayOf(100f, 110f, 40f, 100f, 10f), floatArrayOf(130f, 40f, 110f, 10f, 9f),
            floatArrayOf(95f, 200f, 35f, 220f, 10f), floatArrayOf(110f, 250f, 60f, 300f, 12f), floatArrayOf(150f, 280f, 135f, 325f, 9f),
            floatArrayOf(880f, 45f, 925f, 12f, 11f), floatArrayOf(925f, 85f, 980f, 60f, 12f), floatArrayOf(945f, 130f, 995f, 125f, 9f),
            floatArrayOf(950f, 230f, 995f, 255f, 10f), floatArrayOf(915f, 270f, 950f, 315f, 12f), floatArrayOf(860f, 310f, 870f, 335f, 8f),
            floatArrayOf(845f, 30f, 855f, 5f, 7f), floatArrayOf(180f, 20f, 170f, 2f, 7f))
    }
}
