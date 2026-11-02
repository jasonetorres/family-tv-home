package app.familytv.notes
import android.content.Context
import android.graphics.*
import android.graphics.drawable.*
import android.view.Gravity
import android.view.View
import android.widget.*
import java.net.URL

object Ui {
    val PASTELS = intArrayOf(0xFFFFF3A3.toInt(), 0xFFFFD1DC.toInt(), 0xFFCDE7FF.toInt(), 0xFFD4F5C9.toInt())
    const val INK = 0xFF3E2A1E.toInt()
    fun hand(c: Context): Typeface = c.resources.getFont(R.font.caveat)
    fun dp(c: Context, v: Float) = v * c.resources.displayMetrics.density

    /** Rounded glass pill: translucent white gradient, thin light border; grows + glows on focus. */
    fun glass(c: Context, label: String, onClick: () -> Unit) = TextView(c).apply {
        text = label; textSize = 20f; setTextColor(INK); typeface = Typeface.DEFAULT_BOLD; isFocusable = true; isClickable = true
        gravity = Gravity.CENTER; val p = dp(c, 14f).toInt(); setPadding(p * 2, p, p * 2, p)
        fun bg(f: Boolean) = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            if (f) intArrayOf(0xF2FFFFFF.toInt(), 0xCCFFF4E6.toInt()) else intArrayOf(0xB3FFFFFF.toInt(), 0x66FFFFFF)).apply {
            cornerRadius = dp(c, 999f); setStroke(dp(c, if (f) 2.5f else 1.2f).toInt(), if (f) 0xFFFFB86B.toInt() else 0xCCFFFFFF.toInt()) }
        background = bg(false); elevation = dp(c, 4f)
        if (android.os.Build.VERSION.SDK_INT >= 28) { outlineSpotShadowColor = 0xFFFF9E40.toInt(); outlineAmbientShadowColor = 0x55000000 }
        setOnFocusChangeListener { _, f -> background = bg(f)
            animate().scaleX(if (f) 1.1f else 1f).scaleY(if (f) 1.1f else 1f).translationZ(if (f) dp(c, 14f) else 0f).setDuration(150).start() }
        setOnClickListener { onClick() }
    }
}

/** Post-it: pastel square, soft shadow, folded corner, optional tape strip. Rotation set by caller. */
class StickyView(c: Context, val color: Int, val tape: Boolean) : LinearLayout(c) {
    private val d = c.resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    init {
        orientation = VERTICAL; setWillNotDraw(false); isFocusable = true
        setLayerType(LAYER_TYPE_SOFTWARE, null)   // for blurred shadow
        val p = (22 * d).toInt(); setPadding(p, p + (if (tape) (10 * d).toInt() else 0), p, p)
    }
    override fun onDraw(cv: Canvas) {
        val w = width.toFloat(); val h = height.toFloat(); val s = 10 * d; val fold = 26 * d
        val note = Path().apply { moveTo(s, s); lineTo(w - s, s); lineTo(w - s, h - s - fold); lineTo(w - s - fold, h - s); lineTo(s, h - s); close() }
        paint.color = 0x55000000; paint.maskFilter = BlurMaskFilter(8 * d, BlurMaskFilter.Blur.NORMAL)
        cv.save(); cv.translate(2 * d, 5 * d); cv.drawPath(note, paint); cv.restore(); paint.maskFilter = null
        paint.shader = LinearGradient(0f, s, 0f, h, color, blend(color, 0xFF000000.toInt(), 0.06f), Shader.TileMode.CLAMP)
        cv.drawPath(note, paint); paint.shader = null
        // curled corner
        paint.color = blend(color, 0xFF000000.toInt(), 0.18f)
        cv.drawPath(Path().apply { moveTo(w - s, h - s - fold); lineTo(w - s - fold, h - s - fold * 0.15f); lineTo(w - s - fold, h - s); close() }, paint)
        if (tape) {
            paint.color = 0xB3F5EBD7.toInt(); val tw = 90 * d
            cv.save(); cv.rotate(-4f, w / 2, s); cv.drawRect(w / 2 - tw / 2, 0f, w / 2 + tw / 2, 28 * d, paint)
            paint.color = 0xFFD2452F.toInt(); paint.textSize = 16 * d; paint.typeface = Typeface.DEFAULT_BOLD; paint.textAlign = Paint.Align.CENTER
            cv.drawText("NEW", w / 2, 20 * d, paint); cv.restore()
        }
        super.onDraw(cv)
    }
    private fun blend(a: Int, b: Int, t: Float) = Color.rgb(
        (Color.red(a) * (1 - t) + Color.red(b) * t).toInt(), (Color.green(a) * (1 - t) + Color.green(b) * t).toInt(), (Color.blue(a) * (1 - t) + Color.blue(b) * t).toInt())

    companion object {
        fun forNote(c: Context, n: Note, index: Int, big: Boolean, maxBodyLines: Int = 8, fill: Boolean = false): StickyView {
            val v = StickyView(c, Ui.PASTELS[(n.id.hashCode() and 0x7fffffff) % 4], tape = big)
            val f = Ui.hand(c)
            if (n.title.isNotBlank()) v.addView(TextView(c).apply { text = n.title; typeface = f; textSize = if (big) 34f else 28f; setTextColor(0xFF8A3B2E.toInt()) })
            v.addView(TextView(c).apply { text = n.body; typeface = f; textSize = if (big) 38f else 30f; setTextColor(Ui.INK); setLineSpacing(0f, 0.95f)
                if (fill) setAutoSizeTextTypeUniformWithConfiguration(20, 56, 2, android.util.TypedValue.COMPLEX_UNIT_SP)
                else { maxLines = maxBodyLines; ellipsize = android.text.TextUtils.TruncateAt.END } },
                if (fill) LayoutParams(-1, 0, 1f) else LayoutParams(-1, -2))
            if (n.attachUrl.isNotEmpty()) {
                val iv = ImageView(c).apply { adjustViewBounds = true; maxHeight = (Ui.dp(c, if (big) 240f else 140f)).toInt() }
                v.addView(iv); kotlin.concurrent.thread { Net.bitmap(n.attachUrl)?.let { b -> iv.post { iv.setImageBitmap(b) } } }
            }
            if (!fill) v.addView(Space(c), LayoutParams(1, 0, 1f))
            v.addView(TextView(c).apply { text = Net.fmt(n.time); textSize = 13f; setTextColor(0x993E2A1E.toInt()); gravity = Gravity.END })
            val rots = floatArrayOf(-2.5f, 1.8f, -1.2f, 2.6f, -2f, 1.2f)
            v.rotation = rots[index % rots.size]
            v.setOnFocusChangeListener { _, has ->
                v.animate().scaleX(if (has) 1.08f else 1f).scaleY(if (has) 1.08f else 1f)
                    .translationY(if (has) -Ui.dp(c, 10f) else 0f).translationZ(if (has) Ui.dp(c, 16f) else 0f)
                    .rotation(if (has) 0f else rots[index % rots.size]).setDuration(160).start() }
            return v
        }
    }
}
