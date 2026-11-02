package app.familytv.where
import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.*

/** Santa-Tracker-style illustrated world map: Mercator landmasses, great-circle arc, avatar, pins, follow-cam, celebration. */
class MapView(c: Context) : View(c) {
    private val d = c.resources.displayMetrics.density
    private fun u(v: Float) = v * d
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hand = c.resources.getFont(R.font.patrick)
    private val S = 1000f   // world units: 0..S
    private val land = Path().apply {
        c.assets.open("land.txt").bufferedReader().forEachLine { line ->
            line.split(' ').forEachIndexed { i, pt -> val (lo, la) = pt.split(',').map { it.toDouble() }
                val x = (Geo.wx(lo) * S).toFloat(); val y = (Geo.wy(la) * S).toFloat(); if (i == 0) moveTo(x, y) else lineTo(x, y) }
            close() } }
    var snap: Snapshot? = null
    var avatar = Avatar.PLANE
    // camera (world units) + zoom (px per world unit), eased toward targets every frame
    private var cx = S / 2; private var cy = S * 0.42f; private var z = 0f
    private val confetti = List(70) { floatArrayOf(Math.random().toFloat(), Math.random().toFloat(), (Math.random() * 360).toFloat(), (0.5 + Math.random()).toFloat(), (Math.random() * 6).toFloat()) }
    private val COLORS = intArrayOf(0xFFE0483E.toInt(), 0xFFFFC94A.toInt(), 0xFF5A9BE6.toInt(), 0xFF7CCB91.toInt(), 0xFFF9B9CB.toInt(), 0xFF8E7CE6.toInt())

    private fun w(pl: Place) = PointF((Geo.wx(pl.lon) * S).toFloat(), (Geo.wy(pl.lat) * S).toFloat())
    private fun w(ll: Pair<Double, Double>) = PointF((Geo.wx(ll.second) * S).toFloat(), (Geo.wy(ll.first) * S).toFloat())

    /** Where the avatar is, in world units, plus heading in degrees. */
    private fun avatarPos(s: Snapshot): Pair<PointF, Float>? {
        s.hop?.let { h -> if (s.here == null) { val t = s.progress.toDouble().coerceIn(0.0, 1.0)
            val a = w(Geo.along(h.from, h.to, t)); val b = w(Geo.along(h.from, h.to, (t + 0.01).coerceAtMost(1.0))); val b0 = w(Geo.along(h.from, h.to, (t - 0.01).coerceAtLeast(0.0)))
            return a to Math.toDegrees(atan2((b.y - b0.y).toDouble(), (b.x - b0.x).toDouble())).toFloat() } }
        return s.here?.let { w(it) to 0f }
    }

    override fun onDraw(cv: Canvas) {
        val W = width.toFloat(); val H = height.toFloat(); val s = snap
        val t = System.currentTimeMillis() / 1000.0
        // --- camera target
        val worldZ = W / S
        var tx = S / 2; var ty = S * 0.42f; var tz = worldZ
        val ap = s?.let { avatarPos(it) }
        if (s?.hop != null && s.here == null) {
            val a = w(s.hop.from); val b = w(s.hop.to); val span = max(abs(a.x - b.x), abs(a.y - b.y) * W / H) + 25f
            tz = (W * 0.62f / span).coerceIn(worldZ, worldZ * 14f); tx = (a.x + b.x) / 2 * 0.5f + ap!!.first.x * 0.5f; ty = (a.y + b.y) / 2 * 0.5f + ap.first.y * 0.5f
        } else if (ap != null) { tz = worldZ * 6f; tx = ap.first.x; ty = ap.first.y }
        if (z == 0f) { z = tz; cx = tx; cy = ty }
        z += (tz - z) * 0.04f; cx += (tx - cx) * 0.05f; cy += (ty - cy) * 0.05f
        // --- ocean
        p.shader = LinearGradient(0f, 0f, 0f, H, 0xFF8FD3F0.toInt(), 0xFF6CB8E6.toInt(), Shader.TileMode.CLAMP); cv.drawRect(0f, 0f, W, H, p); p.shader = null
        cv.save(); cv.translate(W / 2, H * 0.55f); cv.scale(z, z); cv.translate(-cx, -cy)
        // grid every 15°
        p.style = Paint.Style.STROKE; p.strokeWidth = u(1f) / z; p.color = 0x33FFFFFF
        for (lo in -180..180 step 15) { val x = (Geo.wx(lo.toDouble()) * S).toFloat(); cv.drawLine(x, 0f, x, S, p) }
        for (la in -75..75 step 15) { val y = (Geo.wy(la.toDouble()) * S).toFloat(); cv.drawLine(0f, y, S, y, p) }
        // land: soft shadow, mint fill, darker coast
        p.style = Paint.Style.FILL; p.color = 0x22003355; cv.save(); cv.translate(u(3f) / z, u(4f) / z); cv.drawPath(land, p); cv.restore()
        p.color = 0xFFB6EBC0.toInt(); cv.drawPath(land, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = u(2f) / z; p.color = 0xFF7CCB91.toInt(); p.strokeJoin = Paint.Join.ROUND; cv.drawPath(land, p)
        if (s != null) {
            // route arcs: every hop of the trip as a faint dashed line, current hop travelled part solid red
            s.hop?.let { h ->
                val arc = Path(); val done = Path()
                for (i in 0..80) { val f = i / 80.0; val q = w(Geo.along(h.from, h.to, f)); if (i == 0) arc.moveTo(q.x, q.y) else arc.lineTo(q.x, q.y)
                    if (f <= s.progress) { if (i == 0) done.moveTo(q.x, q.y) else done.lineTo(q.x, q.y) } }
                p.strokeCap = Paint.Cap.ROUND; p.strokeWidth = u(3.5f) / z; p.color = 0xCCFFFFFF.toInt()
                p.pathEffect = DashPathEffect(floatArrayOf(u(10f) / z, u(9f) / z), (-t * 12 % 1000).toFloat() / z); cv.drawPath(arc, p); p.pathEffect = null
                p.color = 0xFFE0483E.toInt(); p.strokeWidth = u(4f) / z; if (s.progress > 0f) cv.drawPath(done, p)
            }
            // pins with little paper cards
            p.style = Paint.Style.FILL
            val labelled = (s.stops + listOfNotNull(s.home)).distinctBy { "%.1f,%.1f".format(it.lat, it.lon) }
            for (pl in labelled) { val q = w(pl); val isHome = abs(pl.lat - s.home.lat) < 0.2 && abs(pl.lon - s.home.lon) < 0.2
                cv.save(); cv.translate(q.x, q.y); cv.scale(1 / z, 1 / z)
                if (isHome) house(cv, 0f, 0f, u(30f)) else pin(cv, if (pl == s.next) 0xFFFFC94A.toInt() else 0xFFE0483E.toInt())
                card(cv, if (isHome) "Home" else pl.name, 0f, u(if (isHome) 16f else 12f)); cv.restore() }
            // avatar
            if (ap != null) { cv.save(); cv.translate(ap.first.x, ap.first.y); cv.scale(1 / z, 1 / z)
                val bob = (sin(t * 2.4) * u(3f)).toFloat()
                p.color = 0x33000000; cv.drawOval(-u(16f), u(10f), u(16f), u(18f), p)
                if (s.here == null) avatarDraw(cv, 0f, -u(8f) + bob, u(56f), ap.second)
                else { p.color = 0xFFE0483E.toInt(); heart(cv, 0f, -u(52f) + bob, u(36f)); face(cv, 0f, -u(30f) + bob, u(16f)) }
                cv.restore() }
        }
        cv.restore()
        if (s?.isHome == true) celebrate(cv, W, H, t)
        postInvalidateDelayed(33)
    }

    private fun pin(cv: Canvas, color: Int) {
        p.style = Paint.Style.FILL; p.color = color
        cv.drawPath(Path().apply { moveTo(0f, 0f); cubicTo(-u(14f), -u(16f), -u(12f), -u(34f), 0f, -u(34f)); cubicTo(u(12f), -u(34f), u(14f), -u(16f), 0f, 0f) }, p)
        p.color = 0xFFFFFFFF.toInt(); cv.drawCircle(0f, -u(23f), u(5f), p)
    }
    private fun card(cv: Canvas, text: String, x: Float, y: Float) {
        p.typeface = hand; p.textSize = u(17f); p.textAlign = Paint.Align.CENTER
        val tw = p.measureText(text) + u(18f); val r = RectF(x - tw / 2, y, x + tw / 2, y + u(26f))
        p.color = 0x33000000; cv.drawRoundRect(r.left + u(1f), r.top + u(3f), r.right + u(1f), r.bottom + u(3f), u(6f), u(6f), p)
        p.color = 0xFFFFF8E7.toInt(); cv.drawRoundRect(r, u(6f), u(6f), p)
        p.color = 0xFF1B2A4A.toInt(); cv.drawText(text, x, r.bottom - u(7f), p); p.textAlign = Paint.Align.LEFT
    }
    private fun house(cv: Canvas, x: Float, y: Float, s: Float) {
        p.style = Paint.Style.FILL; p.color = 0xFFFFF8E7.toInt(); cv.drawRect(x - s * .35f, y - s * .55f, x + s * .35f, y, p)
        p.color = 0xFFE0483E.toInt(); cv.drawPath(Path().apply { moveTo(x - s / 2, y - s * .5f); lineTo(x, y - s); lineTo(x + s / 2, y - s * .5f); close() }, p)
        p.color = 0xFF1B2A4A.toInt(); cv.drawRect(x - s * .08f, y - s * .3f, x + s * .08f, y, p)
    }
    private fun heart(cv: Canvas, cx: Float, cy: Float, s: Float) {
        cv.drawPath(Path().apply { moveTo(cx, cy + s * .35f); cubicTo(cx - s * .6f, cy - s * .05f, cx - s * .3f, cy - s * .55f, cx, cy - s * .2f)
            cubicTo(cx + s * .3f, cy - s * .55f, cx + s * .6f, cy - s * .05f, cx, cy + s * .35f) }, p)
    }
    private fun face(cv: Canvas, cx: Float, cy: Float, r: Float) {
        p.color = 0xFFFFD9B8.toInt(); cv.drawCircle(cx, cy, r, p); p.color = 0xFF5A3A2A.toInt(); cv.drawArc(cx - r, cy - r, cx + r, cy + r * .2f, 180f, 180f, true, p)
        p.color = 0xFF1B2A4A.toInt(); cv.drawCircle(cx - r * .35f, cy + r * .1f, r * .1f, p); cv.drawCircle(cx + r * .35f, cy + r * .1f, r * .1f, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = r * .1f; cv.drawArc(cx - r * .4f, cy, cx + r * .4f, cy + r * .6f, 20f, 140f, false, p); p.style = Paint.Style.FILL
    }
    private fun avatarDraw(cv: Canvas, cx: Float, cy: Float, s: Float, deg: Float) {
        // SLEIGH: Santa mode hook — draw the sleigh here when SantaTraveler is added. Plane for now.
        cv.save(); cv.translate(cx, cy); cv.rotate(deg)
        p.style = Paint.Style.FILL; p.color = 0xFFFFFFFF.toInt()
        cv.drawRoundRect(-s * .5f, -s * .1f, s * .5f, s * .1f, s * .1f, s * .1f, p)
        val wing = { sgn: Float -> Path().apply { moveTo(s * .1f, 0f); lineTo(-s * .12f, sgn * s * .48f); lineTo(-s * .24f, sgn * s * .48f); lineTo(-s * .08f, 0f); close() } }
        p.color = 0xFFE0483E.toInt(); cv.drawPath(wing(-1f), p); cv.drawPath(wing(1f), p)
        val tail = { sgn: Float -> Path().apply { moveTo(-s * .36f, 0f); lineTo(-s * .48f, sgn * s * .22f); lineTo(-s * .54f, sgn * s * .22f); lineTo(-s * .48f, 0f); close() } }
        cv.drawPath(tail(-1f), p); cv.drawPath(tail(1f), p)
        p.color = 0xFF5A9BE6.toInt(); for (i in -2..2) cv.drawCircle(i * s * .1f, 0f, s * .03f, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = s * .03f; p.color = 0xFF1B2A4A.toInt(); cv.drawRoundRect(-s * .5f, -s * .1f, s * .5f, s * .1f, s * .1f, s * .1f, p); p.style = Paint.Style.FILL
        cv.restore()
        face(cv, cx + s * .05f, cy - s * .32f, s * .15f)   // traveler waving from the window
    }
    private fun celebrate(cv: Canvas, W: Float, H: Float, t: Double) {
        p.style = Paint.Style.FILL
        confetti.forEachIndexed { i, c -> val y = ((c[1] + t * 0.08 * c[3]) % 1.0).toFloat() * H; val x = c[0] * W + sin(t * 1.5 + c[4]).toFloat() * u(20f)
            cv.save(); cv.translate(x, y); cv.rotate(c[2] + (t * 90 * c[3]).toFloat()); p.color = COLORS[i % COLORS.size]
            if (i % 5 == 0) heart(cv, 0f, 0f, u(22f)) else cv.drawRect(-u(5f), -u(3f), u(5f), u(3f), p); cv.restore() }
    }
}
