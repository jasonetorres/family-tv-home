package app.familytv.notes
import android.app.Dialog
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.KeyEvent
import android.view.Window
import android.view.WindowManager
import android.widget.*

/** Kids reply picker: big D-pad sticker grid; OK sends to <topic>-replies titled "From the kids". */
object Stickers {
    val ITEMS = listOf("❤️" to "Love you!", "🤗" to "Big hug!", "😂" to "Haha!", "🏈" to "Football!", "🏀" to "Basketball!",
                       "🌙" to "Sweet dreams", "💌" to "Miss you", "🏠" to "Come home soon", "😴" to "Good night", "📞" to "Call me!")
    private val COLORS = intArrayOf(0xFFF9B9CB.toInt(), 0xFFFFE27A.toInt(), 0xFFB8D4FF.toInt(), 0xFFB9EFA0.toInt(), 0xFFFFF1DA.toInt())
    fun show(c: Context, overlay: Boolean = false) {
        val d = c.resources.displayMetrics.density; fun px(v: Int) = (v * d).toInt()
        val dlg = Dialog(c); dlg.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val f = c.resources.getFont(R.font.patrick)
        val box = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; clipChildren = false; clipToPadding = false
            setPadding(px(30), px(18), px(30), px(24))
            background = GradientDrawable().apply { setColor(0xFFFFF8E7.toInt()); cornerRadius = px(22).toFloat(); setStroke(px(3), 0xFFFFE27A.toInt()) } }
        val who = app.familytv.config.FamilyConfig.parentName(c)
        box.addView(TextView(c).apply { text = "Send $who a sticker!"; typeface = c.resources.getFont(R.font.marker); textSize = 28f; setTextColor(NAVY); gravity = Gravity.CENTER })
        val status = TextView(c).apply { text = "Pick one and press OK"; typeface = f; textSize = 16f; setTextColor(0xAA1B2A4A.toInt()); gravity = Gravity.CENTER; setPadding(0, 0, 0, px(10)) }
        box.addView(status)
        val grid = GridLayout(c).apply { columnCount = 5; clipChildren = false; clipToPadding = false }
        var sent = false
        val tiles = ITEMS.mapIndexed { i, (emoji, label) ->
            LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; isFocusable = true; isClickable = true
                val bg = { foc: Boolean -> GradientDrawable().apply { setColor(COLORS[i % COLORS.size]); cornerRadius = px(18).toFloat(); if (foc) setStroke(px(4), RED) } }
                background = bg(false); elevation = px(3).toFloat(); rotation = if (i % 2 == 0) -2f else 2f
                addView(TextView(c).apply { text = emoji; textSize = 40f; gravity = Gravity.CENTER })
                addView(TextView(c).apply { text = label; typeface = f; textSize = 17f; setTextColor(NAVY); gravity = Gravity.CENTER; maxLines = 1 })
                setOnFocusChangeListener { _, h -> background = bg(h); animate().scaleX(if (h) 1.12f else 1f).scaleY(if (h) 1.12f else 1f)
                    .rotation(if (h) 0f else if (i % 2 == 0) -2f else 2f).translationZ(if (h) px(14).toFloat() else 0f).setDuration(140).start() }
                setOnClickListener {
                    if (sent) return@setOnClickListener; sent = true
                    Net.publish(c, app.familytv.config.FamilyConfig.repliesTopic(c), "$emoji $label", "From home")
                    Sfx.play(c, R.raw.whoosh, 500)
                    status.text = "Sent to $who! $emoji"; status.setTextColor(RED)
                    animate().scaleX(1.35f).scaleY(1.35f).setDuration(180).withEndAction { animate().scaleX(1.12f).scaleY(1.12f).setDuration(180).start() }.start()
                    postDelayed({ runCatching { dlg.dismiss() } }, 1300)
                }
            }.also { grid.addView(it, GridLayout.LayoutParams().apply { width = px(132); height = px(112); setMargins(px(9), px(9), px(9), px(9)) }) }
        }
        box.addView(grid)
        box.addView(TextView(c).apply { text = "BACK to close"; typeface = f; textSize = 13f; setTextColor(0x881B2A4A.toInt()); setPadding(0, px(8), 0, 0) })
        dlg.setContentView(box)
        dlg.window?.apply { setBackgroundDrawable(ColorDrawable(0)); if (overlay) setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY) }
        runCatching { dlg.show(); tiles[0].requestFocus() }
    }
}
