package app.familytv.notes
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.SoundPool
import android.os.Handler
import android.os.Looper

/** Short synthesized sound effects; mixes over video, ducks others transiently, then releases focus. */
object Sfx {
    private const val VOL = 0.6f
    private val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
    private var pool: SoundPool? = null
    private val ids = HashMap<Int, Int>(); private val loaded = HashSet<Int>(); private val pending = HashMap<Int, Long>()
    private val h = Handler(Looper.getMainLooper())
    private var focus: AudioFocusRequest? = null
    private fun sp(c: Context) = c.getSharedPreferences("dad", Context.MODE_PRIVATE)
    fun enabled(c: Context) = sp(c).getBoolean("sounds", true)
    fun setEnabled(c: Context, on: Boolean) = sp(c).edit().putBoolean("sounds", on).apply()

    @Synchronized fun preload(c: Context, vararg res: Int) { val p = pool(c); res.forEach { r -> if (r !in ids) ids[r] = p.load(c.applicationContext, r, 1) } }
    @Synchronized private fun pool(c: Context): SoundPool = pool ?: c.applicationContext.let { app -> SoundPool.Builder().setMaxStreams(3).setAudioAttributes(attrs).build().also { p ->
        p.setOnLoadCompleteListener { sp, id, st -> synchronized(this) { if (st == 0) { loaded += id; pending.remove(id)?.let { ms -> start(app, sp, id, ms) } } } }
        pool = p } }

    /** Play raw resource [res] lasting about [ms] milliseconds, if Sounds is on. */
    @Synchronized fun play(c: Context, res: Int, ms: Long) {
        if (!enabled(c)) return
        val p = pool(c); val id = ids[res] ?: p.load(c.applicationContext, res, 1).also { ids[res] = it }
        if (id in loaded) start(c, p, id, ms) else pending[id] = ms
    }
    private fun start(c: Context, p: SoundPool, id: Int, ms: Long) {
        val am = c.getSystemService(AudioManager::class.java)
        focus?.let { am.abandonAudioFocusRequest(it) }
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener { }.build()
        focus = req; runCatching { am.requestAudioFocus(req) }
        p.play(id, VOL, VOL, 1, 0, 1f)
        h.postDelayed({ synchronized(this) { if (focus === req) { am.abandonAudioFocusRequest(req); focus = null } } }, ms + 150)
    }
}
