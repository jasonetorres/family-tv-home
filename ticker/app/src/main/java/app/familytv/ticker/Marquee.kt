package app.familytv.ticker
import android.content.Context
import android.graphics.*
import android.view.Choreographer
import android.view.View

/** One ticker entry. [live] draws a red "LIVE" pill before the text. */
data class TickItem(val key: String, val text: String, val live: Boolean = false)

/**
 * Continuous ticker. Every update is applied immediately (v4.2 deferred list changes until a full
 * scroll loop ended, which with ~23 items left the bar stale for minutes). To avoid a jarring jump,
 * the item currently at the left edge is kept at the same screen position across the swap.
 */
class MarqueeView(c: Context) : View(c), Choreographer.FrameCallback {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tagText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
    private val tagBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE53935.toInt() }
    private var items: List<TickItem> = emptyList()
    private var starts = FloatArray(0); private var total = 0f
    private var offset = 0f; private var lastNs = 0L; private var running = false
    var speedDp = 60f
    var loops = 0; private set
    init { isFocusable = false }
    private val sep = "     ●     "
    private val tagPadX get() = p.textSize * 0.35f
    private val tagW get() = tagText.measureText("LIVE") + tagPadX * 2
    private fun itemW(it: TickItem) = (if (it.live) tagW + p.textSize * 0.4f else 0f) + p.measureText(it.text)
    private fun relayout() {
        starts = FloatArray(items.size); var x = 0f; val sw = p.measureText(sep)
        items.forEachIndexed { i, it -> starts[i] = x; x += itemW(it) + (if (i < items.size - 1) sw else 0f) }
        total = x
    }
    fun setStyle(sizeSp: Float, color: Int, tf: Typeface) {
        val px = sizeSp * resources.displayMetrics.scaledDensity
        if (p.textSize != px || p.color != color || p.typeface != tf) {
            p.textSize = px; p.color = color; p.typeface = tf; tagText.textSize = px * 0.78f
            relayout(); requestLayout(); invalidate() }
    }
    fun update(newItems: List<TickItem>) {
        val inner = (width - paddingLeft - paddingRight).toFloat()
        val left = offset - inner   // content x at the left edge of the bar
        val anchorIdx = starts.indices.firstOrNull { i -> starts[i] + itemW(items[i]) > left }
        val anchorKey = anchorIdx?.let { items[it].key }; val anchorOld = anchorIdx?.let { starts[it] } ?: 0f
        items = newItems; relayout()
        val ni = newItems.indexOfFirst { it.key == anchorKey }
        if (ni >= 0 && left > 0) offset += starts[ni] - anchorOld   // same item stays where the viewer is looking
        offset = offset.coerceIn(0f, inner + total + gap)
        invalidate()
    }
    private val gap get() = width * 0.35f
    override fun onMeasure(w: Int, h: Int) {
        val fm = p.fontMetrics
        setMeasuredDimension(MeasureSpec.getSize(w), (fm.descent - fm.ascent + paddingTop + paddingBottom).toInt())
    }
    override fun onDraw(cv: Canvas) {
        val inner = width - paddingLeft - paddingRight
        val base = paddingTop - p.fontMetrics.ascent
        val x0 = if (total <= inner) paddingLeft + (inner - total) / 2f else paddingLeft + inner - offset
        cv.save(); cv.clipRect(paddingLeft, 0, width - paddingRight, height)
        val sw = p.measureText(sep); val fm = p.fontMetrics
        items.forEachIndexed { i, it ->
            var x = x0 + starts[i]
            val w = itemW(it)
            if (x + w + sw < 0 || x > width) return@forEachIndexed
            if (it.live) {
                val th = (fm.descent - fm.ascent) * 0.82f; val top = base + (fm.ascent + fm.descent) / 2f - th / 2f
                cv.drawRoundRect(x, top, x + tagW, top + th, th / 4f, th / 4f, tagBg)
                val tf = tagText.fontMetrics
                cv.drawText("LIVE", x + tagPadX, top + th / 2f - (tf.ascent + tf.descent) / 2f, tagText)
                x += tagW + p.textSize * 0.4f
            }
            cv.drawText(it.text, x, base, p)
            if (i < items.size - 1) cv.drawText(sep, x0 + starts[i] + w, base, p)
        }
        cv.restore()
    }
    override fun doFrame(ns: Long) {
        if (!running) return
        val dt = if (lastNs == 0L) 0f else (ns - lastNs) / 1e9f; lastNs = ns
        val inner = width - paddingLeft - paddingRight
        if (total > inner) {
            offset += speedDp * resources.displayMetrics.density * dt
            if (offset > inner + total + gap) { offset = 0f; loops++ }
            invalidate()
        } else offset = 0f
        Choreographer.getInstance().postFrameCallback(this)
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); running = true; lastNs = 0; Choreographer.getInstance().postFrameCallback(this) }
    override fun onDetachedFromWindow() { running = false; Choreographer.getInstance().removeFrameCallback(this); super.onDetachedFromWindow() }
}
