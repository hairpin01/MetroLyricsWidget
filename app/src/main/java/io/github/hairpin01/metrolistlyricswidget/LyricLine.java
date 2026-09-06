package io.github.hairpin01.metrolistlyricswidget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class LyricLine implements Comparable<LyricLine> {
    final long timeMs;
    final String text;
    final boolean background;
    final List<LyricToken> tokens;

    LyricLine(long timeMs, String text, boolean background) {
        this(timeMs, text, background, Collections.<LyricToken>emptyList());
    }

    LyricLine(long timeMs, String text, boolean background, List<LyricToken> tokens) {
        this.timeMs = timeMs;
        this.text = text == null ? "" : text.trim();
        this.background = background;
        if (tokens == null || tokens.isEmpty()) {
            this.tokens = Collections.emptyList();
        } else {
            this.tokens = Collections.unmodifiableList(new ArrayList<LyricToken>(tokens));
        }
    }

    LyricLine withTokens(List<LyricToken> tokens) {
        return new LyricLine(timeMs, text, background, tokens);
    }

    @Override
    public int compareTo(LyricLine other) {
        if (timeMs < other.timeMs) return -1;
        if (timeMs > other.timeMs) return 1;
        if (background == other.background) return 0;
        return background ? 1 : -1;
    }
}
