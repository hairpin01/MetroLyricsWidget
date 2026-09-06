package io.github.hairpin01.metrolistlyricswidget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

public final class LyricsParserTest {
    @Test
    public void parsesWordAndSyllableTimings() {
        String raw = "[00:35.22]Trash the ploy, turn myself to a poster boy\n"
                + "<Trash:35.225:35.535|the:35.535:35.705|ploy,:35.705:35.935|"
                + "turn:36.275:36.525|myself:36.525:36.855|to:37.075:37.175|"
                + "a:37.305:37.365|poster:37.365:37.725|boy:37.935:38.225>\n"
                + "[00:39.02]{agent:v1}L-l-live by the sword, make myself turn two to four\n"
                + "<L-:39.02:39.29|l-:39.29:39.35|live:39.35:39.67|by:39.77:39.86|"
                + "the:39.97:40.17|sword,:40.17:40.45|make:40.75:40.95|"
                + "myself:41.1:41.55|turn:41.55:41.9|two:41.9:42.2|"
                + "to:42.2:42.31|four:42.41:42.671>";

        List<LyricLine> lines = LyricsParser.parse(raw);

        assertEquals(2, lines.size());
        LyricLine first = lines.get(0);
        assertEquals(35_220L, first.timeMs);
        assertEquals(9, first.tokens.size());
        assertToken(first.tokens.get(0), "Trash", 35_225L, 35_535L, 0, 5);
        assertToken(first.tokens.get(1), "the", 35_535L, 35_705L, 6, 9);
        assertToken(first.tokens.get(8), "boy", 37_935L, 38_225L, 40, 43);

        LyricLine second = lines.get(1);
        assertEquals("L-l-live by the sword, make myself turn two to four", second.text);
        assertEquals(12, second.tokens.size());
        assertToken(second.tokens.get(0), "L-", 39_020L, 39_290L, 0, 2);
        assertToken(second.tokens.get(1), "l-", 39_290L, 39_350L, 2, 4);
        assertToken(second.tokens.get(2), "live", 39_350L, 39_670L, 4, 8);
    }

    @Test
    public void parsesEscapedLyricsAndColonInsideTokenText() {
        String raw = "\"[00:01.20]Speed: Underground\\n"
                + "<Speed::1.2:1.4|Underground:1.4:2.0>\"";

        List<LyricLine> lines = LyricsParser.parse(raw);

        assertEquals(1, lines.size());
        assertEquals("Speed: Underground", lines.get(0).text);
        assertEquals(2, lines.get(0).tokens.size());
        assertToken(lines.get(0).tokens.get(0), "Speed:", 1_200L, 1_400L, 0, 6);
        assertToken(lines.get(0).tokens.get(1), "Underground", 1_400L, 2_000L, 7, 18);
    }

    @Test
    public void attachesTimingRowToClosestRepeatedTimestamp() {
        String raw = "[00:01.00][00:05.00]Hey\n<Hey:5.0:5.4>";

        List<LyricLine> lines = LyricsParser.parse(raw);

        assertEquals(2, lines.size());
        assertTrue(lines.get(0).tokens.isEmpty());
        assertFalse(lines.get(1).tokens.isEmpty());
        assertEquals(5_000L, lines.get(1).timeMs);
    }

    @Test
    public void alignsDecodedEntitiesAndNormalizesInvalidEndTime() {
        String raw = "[00:02.00]A & B\n<A:2.0:2.1|&amp;:2.1:2.2|B:2.2:2.0>";

        List<LyricLine> lines = LyricsParser.parse(raw);

        assertEquals(1, lines.size());
        assertEquals(3, lines.get(0).tokens.size());
        assertToken(lines.get(0).tokens.get(1), "&", 2_100L, 2_200L, 2, 3);
        LyricToken last = lines.get(0).tokens.get(2);
        assertEquals(last.startMs, last.endMs);
        assertEquals(4, last.startChar);
        assertEquals(5, last.endChar);
    }

    private static void assertToken(LyricToken token, String text, long startMs, long endMs,
                                    int startChar, int endChar) {
        assertEquals(text, token.text);
        assertEquals(startMs, token.startMs);
        assertEquals(endMs, token.endMs);
        assertEquals(startChar, token.startChar);
        assertEquals(endChar, token.endChar);
        assertTrue(token.hasTextRange());
    }
}
