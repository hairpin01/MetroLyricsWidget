package io.github.hairpin01.metrolistlyricswidget;

import android.app.WallpaperManager;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Build;

import java.lang.reflect.Method;

final class ThemePalette {
    final int surface;
    final int foreground;
    final int accent;
    final int muted;
    final int secondary;

    private ThemePalette(int surface, int foreground, int accent) {
        this.surface = opaque(surface);
        this.foreground = opaque(foreground);
        this.accent = opaque(accent);
        this.muted = alpha(this.foreground, 145);
        this.secondary = alpha(this.foreground, 190);
    }

    static ThemePalette resolve(Context context) {
        int source = WidgetSettings.colorSource(context);
        if (source == WidgetSettings.COLOR_CUSTOM) {
            return new ThemePalette(
                    WidgetSettings.customSurface(context),
                    WidgetSettings.customForeground(context),
                    WidgetSettings.customAccent(context)
            );
        }
        if (source == WidgetSettings.COLOR_WALLPAPER) {
            Integer wallpaper = wallpaperPrimary(context);
            if (wallpaper != null) return fromWallpaper(context, wallpaper.intValue());
        }
        return fromSystem(context);
    }

    private static ThemePalette fromSystem(Context context) {
        boolean night = isNight(context);
        int surface = systemColor(context,
                night ? "system_neutral1_900" : "system_neutral1_100",
                night ? Color.rgb(30, 28, 33) : Color.rgb(241, 237, 244));
        int foreground = systemColor(context,
                night ? "system_neutral1_50" : "system_neutral1_900",
                night ? Color.rgb(245, 239, 247) : Color.rgb(35, 31, 36));
        int accent = systemColor(context,
                night ? "system_accent1_200" : "system_accent1_700",
                night ? Color.rgb(208, 188, 255) : Color.rgb(82, 55, 139));
        return new ThemePalette(surface, foreground, accent);
    }

    private static ThemePalette fromWallpaper(Context context, int base) {
        boolean night = isNight(context);
        float[] hsv = new float[3];
        Color.colorToHSV(base, hsv);
        hsv[1] = Math.max(0.28f, Math.min(0.82f, hsv[1]));
        hsv[2] = night ? Math.max(0.72f, hsv[2]) : Math.max(0.48f, Math.min(0.72f, hsv[2]));
        int accent = Color.HSVToColor(hsv);
        int surface = night ? blend(base, Color.rgb(14, 14, 16), 0.78f)
                : blend(base, Color.WHITE, 0.84f);
        int foreground = night ? Color.rgb(247, 242, 247) : Color.rgb(28, 27, 31);
        return new ThemePalette(surface, foreground, accent);
    }

    private static Integer wallpaperPrimary(Context context) {
        if (Build.VERSION.SDK_INT < 27) return null;
        try {
            WallpaperManager manager = WallpaperManager.getInstance(context);
            Method getColors = WallpaperManager.class.getMethod("getWallpaperColors", int.class);
            Object colors = getColors.invoke(manager, 1); // FLAG_SYSTEM
            if (colors == null) return null;
            Method getPrimary = colors.getClass().getMethod("getPrimaryColor");
            Object color = getPrimary.invoke(colors);
            if (color == null) return null;
            Method toArgb = color.getClass().getMethod("toArgb");
            Object value = toArgb.invoke(color);
            return value instanceof Integer ? (Integer) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean isNight(Context context) {
        return (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    private static int systemColor(Context context, String name, int fallback) {
        try {
            Resources resources = context.getResources();
            int id = resources.getIdentifier(name, "color", "android");
            return id == 0 ? fallback : resources.getColor(id);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    static int blend(int from, int to, float amount) {
        float a = Math.max(0f, Math.min(1f, amount));
        int r = Math.round(Color.red(from) * (1f - a) + Color.red(to) * a);
        int g = Math.round(Color.green(from) * (1f - a) + Color.green(to) * a);
        int b = Math.round(Color.blue(from) * (1f - a) + Color.blue(to) * a);
        return Color.rgb(r, g, b);
    }

    static int alpha(int color, int alpha) {
        return Color.argb(Math.max(0, Math.min(255, alpha)), Color.red(color), Color.green(color), Color.blue(color));
    }

    private static int opaque(int color) {
        return Color.rgb(Color.red(color), Color.green(color), Color.blue(color));
    }
}