package io.github.hairpin01.metrolistlyricswidget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Internal LRC parser with support for LyricsPlus per-word/per-syllable rows. */
final class LyricsParser {
    private static final Pattern LRC_TIME = Pattern.compile(
            "\\[(\\d{1,3}):(\\d{2})(?:[\\.:](\\d{1,3}))?\\]");
    private static final Pattern RICH_TIME = Pattern.compile(
            "<(\\d{1,3}):(\\d{2})\\.(\\d{1,3})>");
    private static final Pattern RICH_TAG = Pattern.compile(
            "<\\d{1,3}:\\d{2}\\.\\d{1,3}>");
    private static final Pattern AGENT_TAG = Pattern.compile(
            "\\{(?:agent:[^}]+|bg)\\}");
    // Greedy text group intentionally binds the final two colon-separated fields as
    // numeric start/end seconds, so punctuation containing ':' remains valid text.
    private static final Pattern TOKEN_PART = Pattern.compile(
            "^(.*):(\\d+(?:\\.\\d+)?):(\\d+(?:\\.\\d+)?)$");

    private LyricsParser() {}

    static List<LyricLine> parse(String rawLyrics) {
        List<LyricLine> result = new ArrayList<LyricLine>();
        if (rawLyrics == null) return result;
        String raw = prepareRaw(rawLyrics);
        String[] rows = raw.split("\\r?\\n");
        List<Integer> attachCandidates = new ArrayList<Integer>();

        for (String row : rows) {
            List<RawToken> rawTokens = parseTokenRow(row);
            if (!rawTokens.isEmpty()) {
                attachTokens(result, attachCandidates, rawTokens);
                attachCandidates.clear();
                continue;
            }

            attachCandidates.clear();
            Matcher matcher = LRC_TIME.matcher(row);
            List<Long> times = new ArrayList<Long>();
            int contentStart = 0;
            while (matcher.find()) {
                times.add(parseTime(matcher.group(1), matcher.group(2), matcher.group(3)));
                contentStart = matcher.end();
            }
            boolean background = row.trim().startsWith("[bg:") || row.contains("{bg}");
            if (times.isEmpty() && background) {
                Matcher rich = RICH_TIME.matcher(row);
                if (rich.find()) {
                    times.add(parseTime(rich.group(1), rich.group(2), rich.group(3)));
                    contentStart = rich.start();
                }
            }
            if (times.isEmpty()) continue;
            String text = contentStart < row.length() ? row.substring(contentStart) : "";
            text = normalizeText(text);
            if (text.length() == 0) continue;
            for (Long time : times) {
                attachCandidates.add(result.size());
                result.add(new LyricLine(time.longValue(), text, background));
            }
        }
        Collections.sort(result);
        return result;
    }

    static String normalizeText(String value) {
        if (value == null) return "";
        String text = RICH_TAG.matcher(value).replaceAll("");
        text = AGENT_TAG.matcher(text).replaceAll("");
        text = text.replaceFirst("^\\s*(?:v\\d+|bg)\\s*:\\s*", "");
        text = decodeEntities(text);
        return text.replaceAll("\\s+", " ").trim();
    }

    private static String prepareRaw(String rawLyrics) {
        String raw = rawLyrics.trim();
        if (raw.startsWith("\"") && raw.endsWith("\"") && raw.length() > 1) {
            raw = raw.substring(1, raw.length() - 1);
        }
        return raw.replace("\\r", "").replace("\\n", "\n").replace("\\t", " ");
    }

    private static List<RawToken> parseTokenRow(String row) {
        List<RawToken> result = new ArrayList<RawToken>();
        if (row == null) return result;
        String trimmed = row.trim();
        if (trimmed.length() < 3 || trimmed.charAt(0) != '<'
                || trimmed.charAt(trimmed.length() - 1) != '>') return result;
        String body = trimmed.substring(1, trimmed.length() - 1);
        String[] parts = body.split("\\|", -1);
        for (String part : parts) {
            Matcher matcher = TOKEN_PART.matcher(part.trim());
            if (!matcher.matches()) return Collections.emptyList();
            String text = decodeEntities(matcher.group(1)).trim();
            if (text.length() == 0) return Collections.emptyList();
            long start = parseSeconds(matcher.group(2));
            long end = parseSeconds(matcher.group(3));
            if (end < start) end = start;
            result.add(new RawToken(text, start, end));
        }
        return result;
    }

    private static void attachTokens(List<LyricLine> lines, List<Integer> candidates,
                                     List<RawToken> rawTokens) {
        if (lines.isEmpty() || candidates.isEmpty() || rawTokens.isEmpty()) return;
        long firstStart = rawTokens.get(0).startMs;
        int bestIndex = candidates.get(candidates.size() - 1).intValue();
        long bestDistance = Long.MAX_VALUE;
        for (Integer candidate : candidates) {
            int index = candidate.intValue();
            long distance = Math.abs(lines.get(index).timeMs - firstStart);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestIndex = index;
            }
        }
        LyricLine line = lines.get(bestIndex);
        lines.set(bestIndex, line.withTokens(alignTokens(line.text, rawTokens)));
    }

    private static List<LyricToken> alignTokens(String line, List<RawToken> rawTokens) {
        List<LyricToken> result = new ArrayList<LyricToken>(rawTokens.size());
        int cursor = 0;
        for (RawToken raw : rawTokens) {
            int start = findFlexible(line, raw.text, cursor);
            int end = start < 0 ? -1 : start + raw.text.length();
            if (start >= 0) cursor = end;
            result.add(new LyricToken(raw.text, raw.startMs, raw.endMs, start, end));
        }
        return result;
    }

    private static int findFlexible(String text, String token, int fromIndex) {
        if (token.length() == 0 || token.length() > text.length()) return -1;
        int from = Math.max(0, Math.min(fromIndex, text.length()));
        for (int start = from; start + token.length() <= text.length(); start++) {
            boolean matches = true;
            for (int offset = 0; offset < token.length(); offset++) {
                char left = fold(text.charAt(start + offset));
                char right = fold(token.charAt(offset));
                if (Character.toLowerCase(left) != Character.toLowerCase(right)) {
                    matches = false;
                    break;
                }
            }
            if (matches) return start;
        }
        return -1;
    }

    private static char fold(char value) {
        if (value == '\u2018' || value == '\u2019' || value == '\u02bc' || value == '`') return '\'';
        if (value == '\u2010' || value == '\u2011' || value == '\u2012'
                || value == '\u2013' || value == '\u2014' || value == '\u2212') return '-';
        if (value == '\u00a0') return ' ';
        return value;
    }

    private static String decodeEntities(String text) {
        return text.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&" + "quot;", "\"").replace("&#" + "39;", "'")
                .replace("&" + "nbsp;", " ");
    }

    private static long parseSeconds(String value) {
        try {
            return Math.max(0L, Math.round(Double.parseDouble(value) * 1000.0d));
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    private static long parseTime(String minute, String second, String fraction) {
        long min = parseLong(minute);
        long sec = parseLong(second);
        long part = parseLong(fraction);
        if (fraction == null) part = 0L;
        else if (fraction.length() == 1) part *= 100L;
        else if (fraction.length() == 2) part *= 10L;
        else if (fraction.length() > 3) part /= (long) Math.pow(10, fraction.length() - 3);
        return min * 60_000L + sec * 1_000L + part;
    }

    private static long parseLong(String value) {
        try {
            return value == null ? 0L : Long.parseLong(value);
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    private static final class RawToken {
        final String text;
        final long startMs;
        final long endMs;

        RawToken(String text, long startMs, long endMs) {
            this.text = text;
            this.startMs = startMs;
            this.endMs = endMs;
        }
    }
}
