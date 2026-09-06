package io.github.hairpin01.metrolistlyricswidget;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

final class WidgetSettings {
    static final String PREFS = "widget_settings";

    static final int COLOR_SYSTEM = 0;
    static final int COLOR_WALLPAPER = 1;
    static final int COLOR_CUSTOM = 2;

    static final int BACKGROUND_COLOR = 0;
    static final int BACKGROUND_ARTWORK = 1;
    static final int BACKGROUND_IMAGE = 2;
    static final int KARAOKE_COLOR_ACCENT = 0;
    static final int KARAOKE_COLOR_CUSTOM = 1;

    private static final String KEY_COLOR_SOURCE = "color_source";
    private static final String KEY_BACKGROUND = "background_mode";
    private static final String KEY_OPACITY = "background_opacity";
    private static final String KEY_DIM = "image_dimming";
    private static final String KEY_TEXT_SIZE = "current_text_size";
    private static final String KEY_CUSTOM_SURFACE = "custom_surface";
    private static final String KEY_CUSTOM_FOREGROUND = "custom_foreground";
    private static final String KEY_CUSTOM_ACCENT = "custom_accent";
    private static final String KEY_IMAGE_URI = "custom_image_uri";
    private static final String KEY_SHOW_COVER = "show_cover";
    private static final String KEY_SHOW_PROGRESS = "show_progress";
    private static final String KEY_ANIMATE_LINES = "animate_lines";
    private static final String KEY_KARAOKE_ENABLED = "karaoke_enabled";
    private static final String KEY_KARAOKE_TRAIL = "karaoke_trail";
    private static final String KEY_KARAOKE_BOLD = "karaoke_bold";
    private static final String KEY_KARAOKE_POP = "karaoke_pop";
    private static final String KEY_KARAOKE_UNSUNG_OPACITY = "karaoke_unsung_opacity";
    private static final String KEY_KARAOKE_COLOR_MODE = "karaoke_color_mode";
    private static final String KEY_KARAOKE_CUSTOM_COLOR = "karaoke_custom_color";

    private WidgetSettings() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static int colorSource(Context context) {
        return clamp(prefs(context).getInt(KEY_COLOR_SOURCE, COLOR_SYSTEM), COLOR_SYSTEM, COLOR_CUSTOM);
    }

    static void setColorSource(Context context, int value) {
        prefs(context).edit().putInt(KEY_COLOR_SOURCE, clamp(value, COLOR_SYSTEM, COLOR_CUSTOM)).apply();
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
    static boolean karaokeTrail(Context context) {
        return prefs(context).getBoolean(KEY_KARAOKE_TRAIL, true);
    }
    static void setKaraokeTrail(Context context, boolean value) {
        prefs(context).edit().putBoolean(KEY_KARAOKE_TRAIL, value).apply();
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
    static int karaokeUnsungOpacity(Context context) {
        return clamp(prefs(context).getInt(KEY_KARAOKE_UNSUNG_OPACITY, 62), 35, 100);
    }
    static void setKaraokeUnsungOpacity(Context context, int value) {
        prefs(context).edit().putInt(KEY_KARAOKE_UNSUNG_OPACITY,
                clamp(value, 35, 100)).apply();
    }
    static int karaokeColorMode(Context context) {
        return clamp(prefs(context).getInt(KEY_KARAOKE_COLOR_MODE, KARAOKE_COLOR_ACCENT),
                KARAOKE_COLOR_ACCENT, KARAOKE_COLOR_CUSTOM);
    }
    static void setKaraokeColorMode(Context context, int value) {
        prefs(context).edit().putInt(KEY_KARAOKE_COLOR_MODE,
                clamp(value, KARAOKE_COLOR_ACCENT, KARAOKE_COLOR_CUSTOM)).apply();
    }
    static int customKaraokeColor(Context context) {
        return prefs(context).getInt(KEY_KARAOKE_CUSTOM_COLOR, Color.rgb(255, 214, 10));
    }
    static void setCustomKaraokeColor(Context context, int color) {
        prefs(context).edit().putInt(KEY_KARAOKE_CUSTOM_COLOR, opaque(color)).apply();
    }
    static int karaokeColor(Context context, int paletteAccent) {
        return karaokeColorMode(context) == KARAOKE_COLOR_CUSTOM
                ? customKaraokeColor(context) : paletteAccent;
    }
    static void reset(Context context) {
        prefs(context).edit().clear().apply();
    }

    private static int opaque(int color) {
        return Color.rgb(Color.red(color), Color.green(color), Color.blue(color));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
