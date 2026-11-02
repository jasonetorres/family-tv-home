package app.familytv.ticker
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.net.URL

/** Runs the ticker's parse + order + label logic against the live ESPN feeds and writes the bar contents to build/ticker-live.tsv. */
class LiveEspnTest {
    @Test fun liveFeedsOrderAndLabels() {
        val all = League.values().flatMap { l -> Espn.fetch(l) }
        val now = System.currentTimeMillis()
        val ordered = Ticker.order(all) { false }
        val rank = ordered.map { when (it.state) { "in" -> 0; "pre" -> 1; else -> 2 } }
        assertEquals(rank.sorted(), rank)
        val out = ordered.joinToString("\n") { "${it.isLive}\t${it.label(now)}" }
        File("build/ticker-live.tsv").writeText(out + "\n"); println(out)
    }
    @Test fun formatting() {
        val t = Ticker.parseIso("2026-10-27T00:15Z")
        assertEquals("8:15 PM ET", Ticker.startText(t, Ticker.parseIso("2026-10-26T20:00Z"), "x"))
        assertEquals("Sun 11/1 1:00 PM ET", Ticker.startText(Ticker.parseIso("2026-11-01T18:00Z"), t, "x"))
        val g = Game(League.NFL, "DAL", "7", "PHI", "3", "in", "12:01 - 2nd", period = 2, clock = "12:01")
        assertEquals("[NFL] DAL 7  PHI 3  ·  Q2 12:01", g.label())
        assertEquals("Halftime", Ticker.liveStatus(g.copy(detail = "Halftime", clock = "0:00")))
    }
}
