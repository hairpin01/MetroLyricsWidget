package io.github.hairpin01.metrolistlyricswidget;

final class LyricToken {
    final String text;
    final long startMs;
    final long endMs;
    final int startChar;
    final int endChar;

    LyricToken(String text, long startMs, long endMs, int startChar, int endChar) {
        this.text = text == null ? "" : text;
        this.startMs = Math.max(0L, startMs);
        this.endMs = Math.max(this.startMs, endMs);
        this.startChar = startChar;
        this.endChar = endChar;
    }

    boolean hasTextRange() {
        return startChar >= 0 && endChar > startChar;
    }
}
