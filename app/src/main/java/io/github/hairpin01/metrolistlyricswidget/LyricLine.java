package io.github.hairpin01.metrolistlyricswidget;

final class LyricLine implements Comparable<LyricLine> {
    final long timeMs;
    final String text;
    final boolean background;

    LyricLine(long timeMs, String text, boolean background) {
        this.timeMs = timeMs;
        this.text = text == null ? "" : text.trim();
        this.background = background;
    }

    @Override
    public int compareTo(LyricLine other) {
        if (timeMs < other.timeMs) return -1;
        if (timeMs > other.timeMs) return 1;
        if (background == other.background) return 0;
        return background ? 1 : -1;
    }
}