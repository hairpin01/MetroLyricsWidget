package io.github.hairpin01.metrolistlyricswidget;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

final class WidgetSettings {
    static final String PREFS = "widget_settings";

    static final int COLOR_SYSTEM = 0;
    static final int COLOR_WALLPAPER = 1;
    // Keep 2 for custom so preferences written by previous builds remain valid.
    static final int COLOR_CUSTOM = 2;
    static final int COLOR_TRACK = 3;
    static final int BACKGROUND_COLOR = 0;
    static final int BACKGROUND_ARTWORK = 1;
    static final int BACKGROUND_IMAGE = 2;
    static final int PROGRESS_COLOR_ACCENT = 0;
    static final int PROGRESS_COLOR_CUSTOM = 1;
    static final int PROGRESS_COLOR_TRACK = 2;
    static final int PROGRESS_HEIGHT_MIN_DP = 2;
    static final int PROGRESS_HEIGHT_MAX_DP = 8;
    static final int VERTICAL_PADDING_MIN_DP = 0;
    static final int VERTICAL_PADDING_MAX_DP = 32;
    static final int TEXT_ALIGN_START = 0;
    static final int TEXT_ALIGN_CENTER = 1;
    static final int TEXT_ALIGN_END = 2;
    static final int KARAOKE_COLOR_ACCENT = 0;
    // Keep 1 for custom so preferences written by previous builds remain valid.
    static final int KARAOKE_COLOR_CUSTOM = 1;
    static final int KARAOKE_COLOR_TRACK = 2;
    static final int KARAOKE_MODE_TRAIL = 0;
    static final int KARAOKE_MODE_ACTIVE_TOKEN = 1;
    static final int KARAOKE_MODE_ACTIVE_WORD = 2;
    static final int LYRICS_OFFSET_MIN_MS = -2000;
    static final int LYRICS_OFFSET_MAX_MS = 2000;

    private static final String KEY_COLOR_SOURCE = "color_source";
    private static final String KEY_BACKGROUND = "background_mode";
    private static final String KEY_OPACITY = "background_opacity";
    private static final String KEY_DIM = "image_dimming";
    private static final String KEY_TEXT_SIZE = "current_text_size";
    private static final String KEY_AUTO_VERTICAL_PADDING = "auto_vertical_padding";
    private static final String KEY_TOP_PADDING_DP = "top_padding_dp";
    private static final String KEY_BOTTOM_PADDING_DP = "bottom_padding_dp";
    private static final String KEY_TEXT_ALIGNMENT = "text_alignment";
    private static final String KEY_CUSTOM_SURFACE = "custom_surface";
    private static final String KEY_CUSTOM_FOREGROUND = "custom_foreground";
    private static final String KEY_CUSTOM_ACCENT = "custom_accent";
    private static final String KEY_IMAGE_URI = "custom_image_uri";
    private static final String KEY_SHOW_COVER = "show_cover";
    private static final String KEY_SHOW_PROGRESS = "show_progress";
    private static final String KEY_PROGRESS_COLOR_MODE = "progress_color_mode";
    private static final String KEY_PROGRESS_CUSTOM_COLOR = "progress_custom_color";
    private static final String KEY_PROGRESS_TRACK_OPACITY = "progress_track_opacity";
    private static final String KEY_PROGRESS_HEIGHT_DP = "progress_height_dp";
    private static final String KEY_ANIMATE_LINES = "animate_lines";
    private static final String KEY_KARAOKE_ENABLED = "karaoke_enabled";
    // Kept to migrate settings from builds where the trail was a switch.
    private static final String KEY_KARAOKE_TRAIL = "karaoke_trail";
    private static final String KEY_KARAOKE_MODE = "karaoke_highlight_mode";
    private static final String KEY_KARAOKE_BOLD = "karaoke_bold";
    private static final String KEY_KARAOKE_POP = "karaoke_pop";
    private static final String KEY_KARAOKE_POP_STRENGTH = "karaoke_pop_strength";
    private static final String KEY_KARAOKE_UNSUNG_OPACITY = "karaoke_unsung_opacity";
    private static final String KEY_KARAOKE_COLOR_MODE = "karaoke_color_mode";
    private static final String KEY_KARAOKE_CUSTOM_COLOR = "karaoke_custom_color";
    private static final String KEY_KARAOKE_SEPARATE_ACTIVE_COLOR = "karaoke_separate_active_color";
    private static final String KEY_KARAOKE_ACTIVE_COLOR_MODE = "karaoke_active_color_mode";
    private static final String KEY_KARAOKE_ACTIVE_COLOR = "karaoke_active_color";
    private static final String KEY_LYRICS_TIMING_OFFSET_MS = "lyrics_timing_offset_ms";

    private WidgetSettings() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static int colorSource(Context context) {
        return clamp(prefs(context).getInt(KEY_COLOR_SOURCE, COLOR_SYSTEM), COLOR_SYSTEM, COLOR_TRACK);
    }

    static void setColorSource(Context context, int value) {
        prefs(context).edit().putInt(KEY_COLOR_SOURCE, clamp(value, COLOR_SYSTEM, COLOR_TRACK)).apply();
    }

    static int backgroundMode(Context context) {
        return clamp(prefs(context).getInt(KEY_BACKGROUND, BACKGROUND_COLOR), BACKGROUND_COLOR, BACKGROUND_IMAGE);
    }

    static void setBackgroundMode(Context context, int value) {
        prefs(context).edit().putInt(KEY_BACKGROUND, clamp(value, BACKGROUND_COLOR, BACKGROUND_IMAGE)).apply();
    }

    static int opacity(Context context) {
        return clamp(prefs(context).getInt(KEY_OPACITY, 88), 0, 100);
    }

    static void setOpacity(Context context, int value) {
        prefs(context).edit().putInt(KEY_OPACITY, clamp(value, 0, 100)).apply();
    }

    static int dimming(Context context) {
        return clamp(prefs(context).getInt(KEY_DIM, 42), 0, 90);
    }

    static void setDimming(Context context, int value) {
        prefs(context).edit().putInt(KEY_DIM, clamp(value, 0, 90)).apply();
    }

    static int textSize(Context context) {
        return clamp(prefs(context).getInt(KEY_TEXT_SIZE, 19), 15, 27);
    }

    static void setTextSize(Context context, int value) {
        prefs(context).edit().putInt(KEY_TEXT_SIZE, clamp(value, 15, 27)).apply();
    }

    static boolean autoVerticalPadding(Context context) {
        return prefs(context).getBoolean(KEY_AUTO_VERTICAL_PADDING, true);
    }

    static void setAutoVerticalPadding(Context context, boolean value) {
        prefs(context).edit().putBoolean(KEY_AUTO_VERTICAL_PADDING, value).apply();
    }

    static int topPaddingDp(Context context) {
        return clamp(prefs(context).getInt(KEY_TOP_PADDING_DP, 8),
                VERTICAL_PADDING_MIN_DP, VERTICAL_PADDING_MAX_DP);
    }

    static void setTopPaddingDp(Context context, int value) {
        prefs(context).edit().putInt(KEY_TOP_PADDING_DP,
                clamp(value, VERTICAL_PADDING_MIN_DP, VERTICAL_PADDING_MAX_DP)).apply();
    }

    static int bottomPaddingDp(Context context) {
        return clamp(prefs(context).getInt(KEY_BOTTOM_PADDING_DP, 8),
                VERTICAL_PADDING_MIN_DP, VERTICAL_PADDING_MAX_DP);
    }

    static void setBottomPaddingDp(Context context, int value) {
        prefs(context).edit().putInt(KEY_BOTTOM_PADDING_DP,
                clamp(value, VERTICAL_PADDING_MIN_DP, VERTICAL_PADDING_MAX_DP)).apply();
    }

    static int textAlignment(Context context) {
        return clamp(prefs(context).getInt(KEY_TEXT_ALIGNMENT, TEXT_ALIGN_START),
                TEXT_ALIGN_START, TEXT_ALIGN_END);
    }

    static void setTextAlignment(Context context, int value) {
        prefs(context).edit().putInt(KEY_TEXT_ALIGNMENT,
                clamp(value, TEXT_ALIGN_START, TEXT_ALIGN_END)).apply();
    }

    static int customSurface(Context context) {
        return prefs(context).getInt(KEY_CUSTOM_SURFACE, Color.rgb(35, 31, 39));
    }

    static int customForeground(Context context) {
        return prefs(context).getInt(KEY_CUSTOM_FOREGROUND, Color.WHITE);
    }

    static int customAccent(Context context) {
        return prefs(context).getInt(KEY_CUSTOM_ACCENT, Color.rgb(208, 188, 255));
    }

    static void setCustomColors(Context context, int surface, int foreground, int accent) {
        prefs(context).edit()
                .putInt(KEY_CUSTOM_SURFACE, opaque(surface))
                .putInt(KEY_CUSTOM_FOREGROUND, opaque(foreground))
                .putInt(KEY_CUSTOM_ACCENT, opaque(accent))
                .apply();
    }

    static String imageUri(Context context) {
        String value = prefs(context).getString(KEY_IMAGE_URI, "");
        return value == null ? "" : value;
    }

    static void setImageUri(Context context, String value) {
        prefs(context).edit().putString(KEY_IMAGE_URI, value == null ? "" : value).apply();
    }

    static boolean showCover(Context context) {
        return prefs(context).getBoolean(KEY_SHOW_COVER, true);
    }

    static void setShowCover(Context context, boolean value) {
        prefs(context).edit().putBoolean(KEY_SHOW_COVER, value).apply();
    }

    static boolean showProgress(Context context) {
        return prefs(context).getBoolean(KEY_SHOW_PROGRESS, true);
    }

    static void setShowProgress(Context context, boolean value) {
        prefs(context).edit().putBoolean(KEY_SHOW_PROGRESS, value).apply();
    }

    static int progressColorMode(Context context) {
        return clamp(prefs(context).getInt(KEY_PROGRESS_COLOR_MODE, PROGRESS_COLOR_ACCENT),
                PROGRESS_COLOR_ACCENT, PROGRESS_COLOR_TRACK);
    }

    static void setProgressColorMode(Context context, int value) {
        prefs(context).edit().putInt(KEY_PROGRESS_COLOR_MODE,
                clamp(value, PROGRESS_COLOR_ACCENT, PROGRESS_COLOR_TRACK)).apply();
    }

    static int customProgressColor(Context context) {
        return prefs(context).getInt(KEY_PROGRESS_CUSTOM_COLOR, Color.rgb(208, 188, 255));
    }

    static void setCustomProgressColor(Context context, int color) {
        prefs(context).edit().putInt(KEY_PROGRESS_CUSTOM_COLOR, opaque(color)).apply();
    }

    static int progressColor(Context context, int paletteAccent, int trackAccent) {
        int mode = progressColorMode(context);
        if (mode == PROGRESS_COLOR_CUSTOM) return customProgressColor(context);
        if (mode == PROGRESS_COLOR_TRACK) return trackAccent;
        return paletteAccent;
    }

    static int progressTrackOpacity(Context context) {
        return clamp(prefs(context).getInt(KEY_PROGRESS_TRACK_OPACITY, 30), 0, 100);
    }

    static void setProgressTrackOpacity(Context context, int value) {
        prefs(context).edit().putInt(KEY_PROGRESS_TRACK_OPACITY, clamp(value, 0, 100)).apply();
    }

    static int progressHeightDp(Context context) {
        return clamp(prefs(context).getInt(KEY_PROGRESS_HEIGHT_DP, 4),
                PROGRESS_HEIGHT_MIN_DP, PROGRESS_HEIGHT_MAX_DP);
    }

    static void setProgressHeightDp(Context context, int value) {
        prefs(context).edit().putInt(KEY_PROGRESS_HEIGHT_DP,
                clamp(value, PROGRESS_HEIGHT_MIN_DP, PROGRESS_HEIGHT_MAX_DP)).apply();
    }

    static boolean animateLines(Context context) {
        return prefs(context).getBoolean(KEY_ANIMATE_LINES, true);
    }

    static void setAnimateLines(Context context, boolean value) {
        prefs(context).edit().putBoolean(KEY_ANIMATE_LINES, value).apply();
    }

    static boolean karaokeEnabled(Context context) {
        return prefs(context).getBoolean(KEY_KARAOKE_ENABLED, true);
    }

    static void setKaraokeEnabled(Context context, boolean value) {
        prefs(context).edit().putBoolean(KEY_KARAOKE_ENABLED, value).apply();
    }

    static int karaokeMode(Context context) {
        SharedPreferences settings = prefs(context);
        if (settings.contains(KEY_KARAOKE_MODE)) {
            return clamp(settings.getInt(KEY_KARAOKE_MODE, KARAOKE_MODE_TRAIL),
                    KARAOKE_MODE_TRAIL, KARAOKE_MODE_ACTIVE_WORD);
        }
        return settings.getBoolean(KEY_KARAOKE_TRAIL, true)
                ? KARAOKE_MODE_TRAIL : KARAOKE_MODE_ACTIVE_TOKEN;
    }

    static void setKaraokeMode(Context context, int value) {
        int mode = clamp(value, KARAOKE_MODE_TRAIL, KARAOKE_MODE_ACTIVE_WORD);
        prefs(context).edit()
                .putInt(KEY_KARAOKE_MODE, mode)
                .putBoolean(KEY_KARAOKE_TRAIL, mode == KARAOKE_MODE_TRAIL)
                .apply();
    }

    static boolean karaokeBold(Context context) {
        return prefs(context).getBoolean(KEY_KARAOKE_BOLD, true);
    }

    static void setKaraokeBold(Context context, boolean value) {
        prefs(context).edit().putBoolean(KEY_KARAOKE_BOLD, value).apply();
    }

    static boolean karaokePop(Context context) {
        return prefs(context).getBoolean(KEY_KARAOKE_POP, false);
    }

    static void setKaraokePop(Context context, boolean value) {
        prefs(context).edit().putBoolean(KEY_KARAOKE_POP, value).apply();
    }

    static int karaokePopStrength(Context context) {
        return clamp(prefs(context).getInt(KEY_KARAOKE_POP_STRENGTH, 12), 4, 24);
    }

    static void setKaraokePopStrength(Context context, int value) {
        prefs(context).edit().putInt(KEY_KARAOKE_POP_STRENGTH, clamp(value, 4, 24)).apply();
    }

    static int karaokeUnsungOpacity(Context context) {
        return clamp(prefs(context).getInt(KEY_KARAOKE_UNSUNG_OPACITY, 62), 35, 100);
    }

    static void setKaraokeUnsungOpacity(Context context, int value) {
        prefs(context).edit().putInt(KEY_KARAOKE_UNSUNG_OPACITY, clamp(value, 35, 100)).apply();
    }

    static int karaokeColorMode(Context context) {
        return clamp(prefs(context).getInt(KEY_KARAOKE_COLOR_MODE, KARAOKE_COLOR_ACCENT),
                KARAOKE_COLOR_ACCENT, KARAOKE_COLOR_TRACK);
    }
    static void setKaraokeColorMode(Context context, int value) {
        prefs(context).edit().putInt(KEY_KARAOKE_COLOR_MODE,
                clamp(value, KARAOKE_COLOR_ACCENT, KARAOKE_COLOR_TRACK)).apply();
    }

    static int customKaraokeColor(Context context) {
        return prefs(context).getInt(KEY_KARAOKE_CUSTOM_COLOR, Color.rgb(255, 214, 10));
    }

    static void setCustomKaraokeColor(Context context, int color) {
        prefs(context).edit().putInt(KEY_KARAOKE_CUSTOM_COLOR, opaque(color)).apply();
    }

    static int karaokeColor(Context context, int paletteAccent, int trackAccent) {
        int mode = karaokeColorMode(context);
        if (mode == KARAOKE_COLOR_CUSTOM) return customKaraokeColor(context);
        if (mode == KARAOKE_COLOR_TRACK) return trackAccent;
        return paletteAccent;
    }

    static boolean karaokeSeparateActiveColor(Context context) {
        return prefs(context).getBoolean(KEY_KARAOKE_SEPARATE_ACTIVE_COLOR, false);
    }

    static void setKaraokeSeparateActiveColor(Context context, boolean value) {
        prefs(context).edit().putBoolean(KEY_KARAOKE_SEPARATE_ACTIVE_COLOR, value).apply();
    }

    static int karaokeActiveColorMode(Context context) {
        // Custom is the default to preserve the behavior of the old separate-color switch.
        return clamp(prefs(context).getInt(KEY_KARAOKE_ACTIVE_COLOR_MODE,
                        KARAOKE_COLOR_CUSTOM),
                KARAOKE_COLOR_ACCENT, KARAOKE_COLOR_TRACK);
    }
    static void setKaraokeActiveColorMode(Context context, int value) {
        prefs(context).edit().putInt(KEY_KARAOKE_ACTIVE_COLOR_MODE,
                clamp(value, KARAOKE_COLOR_ACCENT, KARAOKE_COLOR_TRACK)).apply();
    }
    static int customKaraokeActiveColor(Context context) {
        return prefs(context).getInt(KEY_KARAOKE_ACTIVE_COLOR, Color.rgb(255, 107, 157));
    }
    static void setCustomKaraokeActiveColor(Context context, int color) {
        prefs(context).edit().putInt(KEY_KARAOKE_ACTIVE_COLOR, opaque(color)).apply();
    }
    static int karaokeActiveColor(Context context, int highlightColor,
                                  int paletteAccent, int trackAccent) {
        if (!karaokeSeparateActiveColor(context)) return highlightColor;
        int mode = karaokeActiveColorMode(context);
        if (mode == KARAOKE_COLOR_CUSTOM) return customKaraokeActiveColor(context);
        if (mode == KARAOKE_COLOR_TRACK) return trackAccent;
        return paletteAccent;
    }
    static boolean usesTrackAccent(Context context) {
        return colorSource(context) == COLOR_TRACK
                || (showProgress(context) && progressColorMode(context) == PROGRESS_COLOR_TRACK)
                || karaokeColorMode(context) == KARAOKE_COLOR_TRACK
                || (karaokeSeparateActiveColor(context)
                && karaokeActiveColorMode(context) == KARAOKE_COLOR_TRACK);
    }

    static int lyricsTimingOffsetMs(Context context) {
        return clamp(prefs(context).getInt(KEY_LYRICS_TIMING_OFFSET_MS, 0),
                LYRICS_OFFSET_MIN_MS, LYRICS_OFFSET_MAX_MS);
    }

    static void setLyricsTimingOffsetMs(Context context, int value) {
        prefs(context).edit().putInt(KEY_LYRICS_TIMING_OFFSET_MS,
                clamp(value, LYRICS_OFFSET_MIN_MS, LYRICS_OFFSET_MAX_MS)).apply();
        notifySettingsChanged(context);
    }

    static void reset(Context context) {
        prefs(context).edit().clear().apply();
        notifySettingsChanged(context);
    }

    private static void notifySettingsChanged(Context context) {
        try {
            context.getContentResolver().notifyChange(SettingsProvider.CONTENT_URI, null);
        } catch (Throwable ignored) {
        }
    }

    private static int opaque(int color) {
        return Color.rgb(Color.red(color), Color.green(color), Color.blue(color));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
