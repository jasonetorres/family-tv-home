package app.familytv.notes
import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.*
import kotlin.math.*

const val NAVY = 0xFF1B2A4A.toInt()
const val RED = 0xFFE0483E.toInt()

object Doodle {
    fun pick(n: Note): String {
        val t = (n.title + " " + n.body).lowercase()
        return when {
            Regex("mountain|denver|hike|ski|peak|colorado").containsMatchIn(t) -> "mountains"
            Regex("moon|night|sleep|dream|star").containsMatchIn(t) -> "moon"
            Regex("pancake|breakfast|waffle").containsMatchIn(t) -> "pancakes"
            Regex("\\?|test|smile|happy|great|fun").containsMatchIn(t) -> "smiley"
            else -> "heart"
        }
    }
    /** Simple navy line icons inside box (x,y,s). */
    fun draw(cv: Canvas, kind: String, x: Float, y: Float, s: Float, color: Int = NAVY) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = s * 0.05f; this.color = color; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
        when (kind) {
            "mountains" -> { cv.drawPath(Path().apply { moveTo(x, y + s * .8f); lineTo(x + s * .35f, y + s * .2f); lineTo(x + s * .55f, y + s * .5f)
                lineTo(x + s * .72f, y + s * .3f); lineTo(x + s, y + s * .8f) }, p)
                cv.drawPath(Path().apply { moveTo(x + s * .25f, y + s * .37f); lineTo(x + s * .35f, y + s * .45f); lineTo(x + s * .45f, y + s * .36f) }, p) }
            "moon" -> { cv.drawPath(Path().apply { addArc(x, y, x + s * .7f, y + s * .7f, 60f, 240f)
                arcTo(x + s * .15f, y - s * .02f, x + s * .65f, y + s * .62f, 290f, -220f, false) }, p)
                star(cv, x + s * .85f, y + s * .2f, s * .1f, p); star(cv, x + s * .75f, y + s * .75f, s * .07f, p) }
            "pancakes" -> { for (i in 0..2) cv.drawOval(x + s * .1f, y + s * (.45f + i * .14f), x + s * .9f, y + s * (.62f + i * .14f), p)
                cv.drawRect(x + s * .42f, y + s * .36f, x + s * .58f, y + s * .46f, p)
                for (a in listOf(-50, -20, 20, 50)) { val r = Math.toRadians(a - 90.0); cv.drawLine(x + s / 2 + (s * .3f * cos(r)).toFloat(), y + s * .32f + (s * .3f * sin(r)).toFloat(),
                    x + s / 2 + (s * .42f * cos(r)).toFloat(), y + s * .32f + (s * .42f * sin(r)).toFloat(), p) } }
            "smiley" -> { cv.drawCircle(x + s / 2, y + s / 2, s * .4f, p); cv.drawPoint(x + s * .38f, y + s * .42f, p.apply { strokeWidth = s * .08f })
                cv.drawPoint(x + s * .62f, y + s * .42f, p); p.strokeWidth = s * .05f; cv.drawArc(x + s * .3f, y + s * .35f, x + s * .7f, y + s * .7f, 20f, 140f, false, p) }
            else -> heart(cv, x, y, s, p.apply { this.color = RED })
        }
    }
    fun heart(cv: Canvas, x: Float, y: Float, s: Float, p: Paint) = cv.drawPath(Path().apply {
        moveTo(x + s / 2, y + s * .85f); cubicTo(x - s * .1f, y + s * .45f, x + s * .15f, y + s * .05f, x + s / 2, y + s * .3f)
        cubicTo(x + s * .85f, y + s * .05f, x + s * 1.1f, y + s * .45f, x + s / 2, y + s * .85f) }, p)
    private fun star(cv: Canvas, cx: Float, cy: Float, r: Float, p: Paint) = cv.drawPath(Path().apply {
        for (i in 0 until 10) { val rr = if (i % 2 == 0) r else r * .45f; val a = Math.toRadians(i * 36.0 - 90)
            val px = cx + (rr * cos(a)).toFloat(); val py = cy + (rr * sin(a)).toFloat(); if (i == 0) moveTo(px, py) else lineTo(px, py) }; close() }, p)
}

/** Sticky note: pastel, washi tape (color/pattern), torn bottom edge, shadow, doodle. */
class NoteCard(c: Context, val n: Note, val idx: Int) : LinearLayout(c) {
    private val d = c.resources.displayMetrics.density
    private val color = PASTEL[idx % 4]
    private val tape = TAPES[idx % TAPES.size]
    private val doodle = Doodle.pick(n)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rnd = java.util.Random(n.id.hashCode().toLong())
    val baseRot = ROTS[idx % 4]
    init {
        orientation = VERTICAL; setWillNotDraw(false); isFocusable = true; clipChildren = false
        setLayerType(LAYER_TYPE_SOFTWARE, null); rotation = baseRot
        setPadding((26 * d).toInt(), (30 * d).toInt(), (80 * d).toInt(), (20 * d).toInt())
        val f = c.resources.getFont(R.font.patrick)
        addView(TextView(c).apply {
            text = (if (n.title.isNotBlank()) n.title + "\n" else "") + n.body; typeface = f; setTextColor(NAVY); setLineSpacing(0f, 1.05f)
            setAutoSizeTextTypeUniformWithConfiguration(12, 26, 1, android.util.TypedValue.COMPLEX_UNIT_SP) }, LayoutParams(-1, 0, 1f))
        addView(TextView(c).apply { text = Net.fmt(n.time); typeface = f; textSize = 12f; setTextColor(NAVY); gravity = Gravity.END
            setPadding(0, 0, (-54 * d).toInt(), 0) }, LayoutParams(-1, -2))
        setOnFocusChangeListener { _, has -> animate().scaleX(if (has) 1.08f else 1f).scaleY(if (has) 1.08f else 1f)
            .rotation(if (has) 0f else baseRot).translationZ(if (has) 20 * d else 0f).setDuration(160).start(); invalidate() }
    }
    private fun body(w: Float, h: Float, s: Float): Path {
        val p = Path(); p.moveTo(s, s * 1.4f); p.lineTo(w - s, s * 1.4f); p.lineTo(w - s, h - s * 1.6f)
        var x = w - s; val r = java.util.Random(n.id.hashCode().toLong())
        while (x > s) { x -= (4 + r.nextInt(6)) * d; p.lineTo(max(x, s), h - s * 1.6f + (r.nextFloat() * 5 - 1.5f) * d) }
        p.close(); return p
    }
    override fun onDraw(cv: Canvas) {
        val w = width.toFloat(); val h = height.toFloat(); val s = 8 * d
        val b = body(w, h, s)
        paint.color = if (isFocused) 0x77000000 else 0x50000000; paint.maskFilter = BlurMaskFilter((if (isFocused) 14 else 8) * d, BlurMaskFilter.Blur.NORMAL)
        cv.save(); cv.translate(2 * d, 5 * d); cv.drawPath(b, paint); cv.restore(); paint.maskFilter = null
        paint.color = color; cv.drawPath(b, paint)
        // doodle top-right
        val ds = min(56 * d, h * 0.3f); Doodle.draw(cv, doodle, w - s - ds - 10 * d, s * 2.2f, ds)
        // washi tape
        cv.save(); cv.rotate(if (idx % 2 == 0) -3f else 2f, w / 2, s); val tw = 92 * d; val th = 24 * d
        val tr = RectF(w / 2 - tw / 2, 0f, w / 2 + tw / 2, th)
        paint.color = tape.first; paint.alpha = 215; cv.drawRect(tr, paint); paint.alpha = 255
        when (tape.second) {
            1 -> { paint.color = 0x66FFFFFF; paint.strokeWidth = 3 * d; var x = tr.left; while (x < tr.right) { cv.drawLine(x, tr.top, x + 6 * d, tr.bottom, paint); x += 12 * d } }
            2 -> { paint.color = 0x66FFFFFF; var y = tr.top + 4 * d; while (y < tr.bottom) { var x = tr.left + 4 * d; while (x < tr.right) { cv.drawRect(x, y, x + 4 * d, y + 4 * d, paint); x += 8 * d }; y += 8 * d } }
            3 -> { paint.color = 0x77FFFFFF; var x = tr.left + 8 * d; while (x < tr.right) { cv.drawCircle(x, tr.centerY(), 2.5f * d, paint); x += 12 * d } }
        }
        cv.restore()
        super.onDraw(cv)
    }
    companion object {
        val PASTEL = intArrayOf(0xFFFFE27A.toInt(), 0xFFFFB3C7.toInt(), 0xFFB8D4FF.toInt(), 0xFFB9EFA0.toInt())
        val TAPES = listOf(0xFFF06A5B.toInt() to 0, 0xFF8E7CE6.toInt() to 1, 0xFF7BC67B.toInt() to 2, 0xFFF4C542.toInt() to 3)
        val ROTS = floatArrayOf(-1.5f, 1.8f, 1.2f, -1.4f)
    }
}

/** Title on white tape label with red swoosh + heart. */
class TitleView(c: Context) : View(c) {
    private val d = c.resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val font = c.resources.getFont(R.font.marker)
    init { setLayerType(LAYER_TYPE_SOFTWARE, null) }
    override fun onDraw(cv: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        cv.save(); cv.rotate(-1f, w / 2, h / 2)
        p.color = 0x33000000; p.maskFilter = BlurMaskFilter(5 * d, BlurMaskFilter.Blur.NORMAL); cv.drawRect(8 * d, 8 * d, w - 56 * d, h - 14 * d, p); p.maskFilter = null
        p.color = 0xF2FFFDF7.toInt(); cv.drawPath(Path().apply { moveTo(4 * d, 6 * d); lineTo(w - 58 * d, 4 * d); lineTo(w - 62 * d, h * .45f); lineTo(w - 56 * d, h - 16 * d); lineTo(6 * d, h - 12 * d); lineTo(2 * d, h * .5f); close() }, p)
        p.color = NAVY; p.typeface = font; p.textSize = 34 * d; cv.drawText("Notes from ${app.familytv.config.FamilyConfig.parentName(context)}", 18 * d, h * 0.58f, p)
        p.style = Paint.Style.STROKE; p.color = RED; p.strokeWidth = 3.5f * d; p.strokeCap = Paint.Cap.ROUND
        cv.drawPath(Path().apply { moveTo(16 * d, h - 20 * d); quadTo(w * .45f, h - 26 * d, w - 70 * d, h - 22 * d) }, p)
        Doodle.heart(cv, w - 50 * d, 10 * d, 38 * d, p); p.style = Paint.Style.FILL
        cv.restore()
    }
}

/** Solid pastel pill with drawn icon; lifts + glows on focus. */
object Pill {
    fun make(c: Context, label: String, bg: Int, glow: Int, icon: String, onClick: () -> Unit) = TextView(c).apply {
        val d = c.resources.displayMetrics.density
        text = label; textSize = 16f; typeface = Typeface.create("sans-serif-medium", Typeface.BOLD); setTextColor(NAVY); isFocusable = true; isClickable = true
        gravity = Gravity.CENTER_VERTICAL; setPadding((16 * d).toInt(), (10 * d).toInt(), (20 * d).toInt(), (10 * d).toInt())
        compoundDrawablePadding = (8 * d).toInt()
        setCompoundDrawablesRelativeWithIntrinsicBounds(IconDrawable(icon, (22 * d).toInt()), null, null, null)
        fun bgd(f: Boolean) = GradientDrawable().apply { setColor(bg); cornerRadius = 999f; if (f) setStroke((3 * d).toInt(), 0xFFFFFFFF.toInt()) }
        background = bgd(false); elevation = 3 * d
        if (android.os.Build.VERSION.SDK_INT >= 28) { outlineSpotShadowColor = glow; outlineAmbientShadowColor = glow }
        setOnFocusChangeListener { _, f -> background = bgd(f)
            animate().scaleX(if (f) 1.1f else 1f).scaleY(if (f) 1.1f else 1f).translationY(if (f) -4 * d else 0f).translationZ(if (f) 18 * d else 0f).setDuration(150).start() }
        setOnClickListener { onClick() }
    }
}
class IconDrawable(private val kind: String, private val sz: Int) : android.graphics.drawable.Drawable() {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = NAVY; style = Paint.Style.FILL }
    override fun getIntrinsicWidth() = sz; override fun getIntrinsicHeight() = sz
    override fun draw(cv: Canvas) {
        val s = sz.toFloat()
        when (kind) {
            "home" -> { cv.drawPath(Path().apply { moveTo(s * .5f, s * .1f); lineTo(s * .95f, s * .5f); lineTo(s * .82f, s * .5f); lineTo(s * .82f, s * .9f)
                lineTo(s * .6f, s * .9f); lineTo(s * .6f, s * .62f); lineTo(s * .4f, s * .62f); lineTo(s * .4f, s * .9f); lineTo(s * .18f, s * .9f); lineTo(s * .18f, s * .5f); lineTo(s * .05f, s * .5f); close() }, p) }
            "plane" -> { p.color = 0xFFC2263A.toInt(); cv.drawPath(Path().apply { moveTo(s * .05f, s * .45f); lineTo(s * .95f, s * .1f); lineTo(s * .62f, s * .92f); lineTo(s * .45f, s * .58f); close() }, p)
                p.color = 0xFF8E1426.toInt(); cv.drawPath(Path().apply { moveTo(s * .45f, s * .58f); lineTo(s * .95f, s * .1f); lineTo(s * .4f, s * .85f); close() }, p) }
            else -> { p.color = 0xFF4A3A2E.toInt(); for (i in 0 until 8) { cv.save(); cv.rotate(i * 45f, s / 2, s / 2); cv.drawRect(s * .42f, s * .02f, s * .58f, s * .25f, p); cv.restore() }
                cv.drawCircle(s / 2, s / 2, s * .34f, p); p.color = 0xFFFFF4DC.toInt(); cv.drawCircle(s / 2, s / 2, s * .14f, p) }
        }
    }
    override fun setAlpha(a: Int) {}; override fun setColorFilter(cf: ColorFilter?) {}
    @Deprecated("") override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/** Polaroid frame with image (or placeholder) — rotated by caller. */
class Polaroid(c: Context) : View(c) {
    private val d = c.resources.displayMetrics.density
    private var prev: Bitmap? = null
    private var fade = 1f
    var bmp: Bitmap? = null; set(v) { if (v === field) return; prev = field; field = v; fade = 0f
        android.animation.ValueAnimator.ofFloat(0f, 1f).apply { duration = 900; addUpdateListener { fade = it.animatedValue as Float; invalidate() }
            addListener(object : android.animation.AnimatorListenerAdapter() { override fun onAnimationEnd(a: android.animation.Animator) { prev = null } }) }.start() }
    private fun drawFit(cv: Canvas, b: Bitmap, inner: RectF, alpha: Int) {
        val sc = max(inner.width() / b.width, inner.height() / b.height); val sw = inner.width() / sc; val sh = inner.height() / sc
        p.alpha = alpha; cv.drawBitmap(b, Rect(((b.width - sw) / 2).toInt(), ((b.height - sh) / 2).toInt(), ((b.width + sw) / 2).toInt(), ((b.height + sh) / 2).toInt()), inner, p); p.alpha = 255 }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    init { setLayerType(LAYER_TYPE_SOFTWARE, null) }
    override fun onDraw(cv: Canvas) {
        val w = width.toFloat(); val h = height.toFloat(); val m = 7 * d
        p.color = 0x55000000; p.maskFilter = BlurMaskFilter(6 * d, BlurMaskFilter.Blur.NORMAL); cv.drawRect(m, m + 3 * d, w - m + 2 * d, h - m + 4 * d, p); p.maskFilter = null
        p.color = 0xFFFDFBF5.toInt(); cv.drawRect(m, m, w - m, h - m, p)
        val inner = RectF(m + 7 * d, m + 7 * d, w - m - 7 * d, h - m - 26 * d)
        val b = bmp
        if (b != null) { prev?.let { drawFit(cv, it, inner, 255) } ?: run { p.color = 0xFFE9E1D3.toInt(); cv.drawRect(inner, p); p.color = 0xFFFFFFFF.toInt() }
            drawFit(cv, b, inner, (fade * 255).toInt()) }
        else { p.color = 0xFFE9E1D3.toInt(); cv.drawRect(inner, p)
            Doodle.draw(cv, if (rotation < 0) "mountains" else "heart", inner.centerX() - inner.width() * .3f, inner.centerY() - inner.width() * .3f, inner.width() * .6f, 0xFF9AA5B8.toInt()) }
        Doodle.heart(cv, w - m - 24 * d, h - m - 24 * d, 18 * d, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2 * d; color = RED })
    }
}

/** Full-screen marker accents: rainbow, sparkle lines, "missing this" + arrow. */
class Accents(c: Context) : View(c) {
    private val d = c.resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val f = c.resources.getFont(R.font.patrick)
    override fun onDraw(cv: Canvas) {
        val w = width.toFloat(); val u = d
        fun sparkle(x: Float, y: Float, flip: Float) { p.color = 0xFF2F6FD6.toInt(); p.strokeWidth = 2.5f * u
            for (i in 0..2) { val a = Math.toRadians((200 + i * 35.0) * flip); cv.drawLine(x, y + i * 7 * u, x + (12 * u * cos(a)).toFloat(), y + i * 7 * u + (5 * u * sin(a)).toFloat(), p) } }
        sparkle(30 * u, 22 * u, 1f); sparkle(w - 16 * u, 20 * u, -1f); sparkle(w - 120 * u, 410 * u, 1f)
        // rainbow (right side, between polaroids)
        val cx = w - 70 * u; val cy = 322 * u
        intArrayOf(0xFFE84B4B.toInt(), 0xFFF5A33B.toInt(), 0xFFF5D547.toInt(), 0xFF6CC56C.toInt(), 0xFF5A9BE6.toInt()).forEachIndexed { i, col ->
            p.color = col; p.strokeWidth = 4 * u; val r = (26 - i * 4) * u; cv.drawArc(cx - r, cy - r, cx + r, cy + r, 180f, 180f, false, p) }
        // "missing this" + arrow
        p.style = Paint.Style.FILL; p.color = 0xFF2F6FD6.toInt(); p.typeface = f; p.textSize = 17 * u; p.textAlign = Paint.Align.CENTER
        cv.drawText("missing this", 92 * u, 262 * u, p); p.style = Paint.Style.STROKE; p.strokeWidth = 2 * u
        cv.drawPath(Path().apply { moveTo(120 * u, 268 * u); quadTo(126 * u, 284 * u, 112 * u, 296 * u); lineTo(114 * u, 287 * u); moveTo(112 * u, 296 * u); lineTo(121 * u, 295 * u) }, p)
    }
}
