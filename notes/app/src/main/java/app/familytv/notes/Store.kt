package app.familytv.notes
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
data class Note(val id: String, val time: Long, val title: String, val body: String, val attachUrl: String) {
    fun json() = JSONObject().put("id", id).put("time", time).put("title", title).put("body", body).put("att", attachUrl)
    companion object {
        fun from(o: JSONObject) = Note(o.getString("id"), o.getLong("time"), o.optString("title"), o.optString("body"), o.optString("att"))
        /** Parse one ntfy JSON line; null for non-message events. */
        fun fromNtfy(line: String): Note? = runCatching {
            val o = JSONObject(line); if (o.optString("event") != "message") return null
            val att = o.optJSONObject("attachment")?.optString("url") ?: ""
            Note(o.getString("id"), o.getLong("time"), o.optString("title"), o.optString("message"), att)
        }.getOrNull()
    }
}
object Store {
    const val MAX = 50
    private fun sp(c: Context) = c.getSharedPreferences("dad", Context.MODE_PRIVATE)
    fun topic(c: Context): String {
        val saved = sp(c).getString("topic", "")!!.trim()
        return saved.ifBlank { app.familytv.config.FamilyConfig.topic(c) }
    }
    fun setTopic(c: Context, t: String) {
        val value = t.trim()
        sp(c).edit().putString("topic", value).apply()
        app.familytv.config.FamilyConfig.setTopic(c, value)
    }
    fun homeDate(c: Context) = sp(c).getString("home", "") ?: ""
    fun setHomeDate(c: Context, d: String) = sp(c).edit().putString("home", d).apply()
    @Synchronized fun notes(c: Context): List<Note> {
        val a = JSONArray(sp(c).getString("notes", "[]")); return (0 until a.length()).map { Note.from(a.getJSONObject(it)) }
    }
    /** Returns true if new. Keeps newest MAX, sorted newest first. */
    @Synchronized fun add(c: Context, n: Note): Boolean {
        val cur = notes(c); if (cur.any { it.id == n.id }) return false
        val all = (cur + n).sortedByDescending { it.time }.take(MAX)
        sp(c).edit().putString("notes", JSONArray(all.map { it.json() }).toString()).apply(); return true
    }
    @Synchronized private fun save(c: Context, l: List<Note>) = sp(c).edit().putString("notes", JSONArray(l.map { it.json() }).toString()).apply()
    @Synchronized fun clear(c: Context) = save(c, emptyList())
    /** Removes the most recent note (list is newest first). */
    @Synchronized fun undo(c: Context) = save(c, notes(c).drop(1))
    @Synchronized fun remove(c: Context, id: String) = save(c, notes(c).filterNot { it.id == id })
    /** Last ntfy message id seen per topic, so restarts only fetch newer messages. */
    fun lastId(c: Context, topic: String) = sp(c).getString("last_$topic", "") ?: ""
    fun setLastId(c: Context, topic: String, id: String) = sp(c).edit().putString("last_$topic", id).apply()
}
