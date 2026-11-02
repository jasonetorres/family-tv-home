package app.familytv.notes
import android.content.Context
import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.View
import kotlin.math.*

/** Incoming-note pop-up, drawn to match popup_reference.png. Size: 620x440 dp. */
class PopupCard(c: Context, val n: Note) : View(c) {
    private val d = c.resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hand = c.resources.getFont(R.font.patrick)
    private val marker = c.resources.getFont(R.font.marker)
    private val doodle = Doodle.pick(n)
    var btnFocus = true
    var burst = -1f; set(v) { field = v; invalidate() }
    init { setLayerType(LAYER_TYPE_SOFTWARE, null) }
    private fun u(v: Float) = v * d

    private fun strokes(cv: Canvas, cx: Float, cy: Float, startDeg: Float, n: Int, spread: Float, r0: Float, len: Float, col: Int, w: Float) {
        p.style = Paint.Style.STROKE; p.color = col; p.strokeWidth = u(w); p.strokeCap = Paint.Cap.ROUND
        for (i in 0 until n) { val a = Math.toRadians((startDeg + (i - (n - 1) / 2f) * spread).toDouble())
            cv.drawLine(cx + u(r0) * cos(a).toFloat(), cy + u(r0) * sin(a).toFloat(), cx + u(r0 + len) * cos(a).toFloat(), cy + u(r0 + len) * sin(a).toFloat(), p) }
        p.style = Paint.Style.FILL
    }
    override fun onDraw(cv: Canvas) {
        val Y = 0xFFF7C531.toInt()
        // yellow burst strokes around note
        strokes(cv, u(150f), u(40f), 235f, 3, 22f, u(0f) / d + 8f, 16f, Y, 4f)
        strokes(cv, u(470f), u(40f), 305f, 3, 22f, 8f, 16f, Y, 4f)
        strokes(cv, u(40f), u(140f), 180f, 3, 22f, 6f, 18f, Y, 4f)
        strokes(cv, u(585f), u(200f), 0f, 3, 22f, 6f, 18f, Y, 4f)
        // note body (tilted)
        cv.save(); cv.rotate(-1.8f, u(310f), u(200f))
        val l = u(52f); val t = u(56f); val r = u(572f); val b = u(352f); val curl = u(34f)
        val body = Path().apply { moveTo(l, t); lineTo(r, t); lineTo(r, b - curl); quadTo(r - u(4f), b - u(6f), r - curl, b); lineTo(l, b); close() }
        p.color = 0x66000000; p.maskFilter = BlurMaskFilter(u(14f), BlurMaskFilter.Blur.NORMAL); cv.save(); cv.translate(u(4f), u(10f)); cv.drawPath(body, p); cv.restore(); p.maskFilter = null
        p.color = 0xFFFFFFFF.toInt() // reset alpha: Paint alpha (was 0x66 from shadow) modulates the shader
        p.shader = LinearGradient(0f, t, 0f, b, 0xFFFFEA7A.toInt(), 0xFFFBDC5E.toInt(), Shader.TileMode.CLAMP); cv.drawPath(body, p); p.shader = null
        p.color = 0xFFE9BE34.toInt(); cv.drawPath(Path().apply { moveTo(r, b - curl); quadTo(r - curl * .9f, b - curl * .7f, r - curl, b); quadTo(r - u(6f), b - u(8f), r, b - curl); close() }, p)
        // body text + doodle
        val ds = u(74f); Doodle.draw(cv, doodle, r - ds - u(26f), t + u(26f), ds)
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = hand; color = NAVY }
        val txt = (if (n.title.isNotBlank()) n.title + "\n" else "") + n.body
        val tw = (r - l - u(64f) - ds * 0.6f).toInt(); val maxH = b - t - u(96f)
        var sz = 44f; var lay: StaticLayout
        while (true) { tp.textSize = u(sz); lay = StaticLayout.Builder.obtain(txt, 0, txt.length, tp, tw).setLineSpacing(0f, 1.02f).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
            if (lay.height <= maxH || sz <= 18f) break; sz -= 2f }
        cv.save(); cv.translate(l + u(32f), t + u(34f)); lay.draw(cv); cv.restore()
        // heart + timestamp
        p.style = Paint.Style.STROKE; p.strokeWidth = u(4f); p.color = RED; Doodle.heart(cv, u(272f), b - u(66f), u(48f), p); p.style = Paint.Style.FILL
        p.color = NAVY; p.typeface = hand; p.textSize = u(16f); p.textAlign = Paint.Align.RIGHT; cv.drawText(Net.fmt(n.time), r - u(30f), b - u(22f), p); p.textAlign = Paint.Align.LEFT
        cv.restore()
        // tape label "NEW NOTE!"
        cv.save(); cv.rotate(-3f, u(318f), u(48f))
        val tr = RectF(u(222f), u(22f), u(414f), u(76f))
        p.color = 0x33000000; p.maskFilter = BlurMaskFilter(u(4f), BlurMaskFilter.Blur.NORMAL); cv.drawRect(tr.left + u(2f), tr.top + u(3f), tr.right + u(2f), tr.bottom + u(3f), p); p.maskFilter = null
        p.color = 0xF2F6EAD2.toInt(); cv.drawPath(Path().apply { moveTo(tr.left, tr.top); var y = tr.top
            while (y < tr.bottom) { y += u(6f); lineTo(tr.left + (if ((y / u(6f)).toInt() % 2 == 0) u(4f) else 0f), min(y, tr.bottom)) }
            lineTo(tr.right, tr.bottom); y = tr.bottom; while (y > tr.top) { y -= u(6f); lineTo(tr.right - (if ((y / u(6f)).toInt() % 2 == 0) u(4f) else 0f), max(y, tr.top)) }; close() }, p)
        p.color = NAVY; p.typeface = marker; p.textSize = u(26f); p.textAlign = Paint.Align.CENTER; cv.drawText("NEW NOTE!", tr.centerX(), tr.centerY() + u(6f), p)
        p.style = Paint.Style.STROKE; p.color = RED; p.strokeWidth = u(3.5f); p.strokeCap = Paint.Cap.ROUND
        cv.drawPath(Path().apply { moveTo(tr.left + u(30f), tr.bottom - u(9f)); quadTo(tr.centerX(), tr.bottom - u(13f), tr.right - u(36f), tr.bottom - u(11f)) }, p)
        p.style = Paint.Style.FILL; p.textAlign = Paint.Align.LEFT
        cv.restore()
        // "Love it!" pill (left) + "Reply" pill (right); btnFocus = Love it! focused
        val br = RectF(u(150f), u(372f), u(326f), u(424f))
        val rr = RectF(u(350f), u(372f), u(486f), u(424f))
        strokes(cv, br.left - u(6f), br.centerY(), 180f, 3, 28f, 6f, 16f, Y, 4f); strokes(cv, rr.right + u(6f), rr.centerY(), 0f, 3, 28f, 6f, 16f, Y, 4f)
        for ((rect, foc) in listOf(br to btnFocus, rr to !btnFocus)) {
            p.color = if (foc) 0x99FF8A65.toInt() else 0x44000000; p.maskFilter = BlurMaskFilter(u(if (foc) 14f else 6f), BlurMaskFilter.Blur.NORMAL)
            cv.drawRoundRect(rect.left, rect.top + u(4f), rect.right, rect.bottom + u(4f), u(26f), u(26f), p); p.maskFilter = null
            p.color = if (rect === br) 0xFFFFF6E5.toInt() else 0xFFE4F0FF.toInt(); cv.drawRoundRect(rect, u(26f), u(26f), p)
            if (foc) { p.style = Paint.Style.STROKE; p.strokeWidth = u(3f); p.color = RED; cv.drawRoundRect(rect, u(26f), u(26f), p); p.style = Paint.Style.FILL }
        }
        p.color = RED; Doodle.heart(cv, br.left + u(16f), br.top + u(9f), u(36f), p)
        p.color = NAVY; p.typeface = hand; p.textSize = u(28f); cv.drawText("Love it!", br.left + u(62f), br.centerY() + u(10f), p)
        // speech bubble icon + Reply
        p.style = Paint.Style.STROKE; p.strokeWidth = u(3f); p.color = 0xFF5A9BE6.toInt()
        cv.drawRoundRect(rr.left + u(16f), rr.top + u(13f), rr.left + u(46f), rr.top + u(35f), u(8f), u(8f), p)
        cv.drawPath(Path().apply { moveTo(rr.left + u(22f), rr.top + u(35f)); lineTo(rr.left + u(20f), rr.top + u(43f)); lineTo(rr.left + u(30f), rr.top + u(35f)) }, p); p.style = Paint.Style.FILL
        p.color = NAVY; cv.drawText("Reply", rr.left + u(56f), rr.centerY() + u(10f), p)
        // heart burst
        if (burst >= 0f) { val cx = br.centerX(); val cy = br.centerY()
            for (i in 0 until 12) { val a = Math.toRadians(i * 30.0 - 90); val dist = u(30f + 220f * burst)
                p.color = RED; p.alpha = ((1f - burst) * 255).toInt().coerceIn(0, 255)
                val s = u(26f) * (1f - burst * 0.4f); Doodle.heart(cv, cx + dist * cos(a).toFloat() - s / 2, cy + dist * sin(a).toFloat() - s / 2, s, p) }
            p.alpha = 255 }
    }
}
