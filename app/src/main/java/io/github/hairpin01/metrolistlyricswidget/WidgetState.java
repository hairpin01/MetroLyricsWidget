package io.github.hairpin01.metrolistlyricswidget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;

import java.util.Locale;

final class WidgetState {
    private static final String PREFS = "widget_state";

    private WidgetState() {}

    static void save(Context context, Intent intent) {
        SharedPreferences.Editor editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        copyString(intent, editor, Constants.EXTRA_TRACK_ID);
        copyString(intent, editor, Constants.EXTRA_TITLE);
        copyString(intent, editor, Constants.EXTRA_ARTIST);
        copyString(intent, editor, Constants.EXTRA_ARTWORK);
        copyString(intent, editor, Constants.EXTRA_PREVIOUS);
        copyString(intent, editor, Constants.EXTRA_CURRENT);
        copyString(intent, editor, Constants.EXTRA_NEXT);
        copyString(intent, editor, Constants.EXTRA_STATUS);
        copyString(intent, editor, Constants.EXTRA_PROVIDER);
        if (intent.hasExtra(Constants.EXTRA_PLAYING)) {
            editor.putBoolean(Constants.EXTRA_PLAYING, intent.getBooleanExtra(Constants.EXTRA_PLAYING, false));
        }
        if (intent.hasExtra(Constants.EXTRA_POSITION)) {
            editor.putLong(Constants.EXTRA_POSITION, intent.getLongExtra(Constants.EXTRA_POSITION, 0L));
        }
        editor.putLong("updated_at", System.currentTimeMillis());
        editor.apply();
    }

    private static void copyString(Intent intent, SharedPreferences.Editor editor, String key) {
        if (intent.hasExtra(key)) editor.putString(key, intent.getStringExtra(key));
    }

    static void clear(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply();
    }

    static void updateAll(Context context) {
        Context app = context.getApplicationContext();
        AppWidgetManager manager = AppWidgetManager.getInstance(app);
        int[] ids = manager.getAppWidgetIds(new ComponentName(app, LyricsWidgetProvider.class));
        update(app, manager, ids);
    }

    static void update(Context context, AppWidgetManager manager, int[] ids) {
        if (ids == null) return;
        for (int id : ids) updateOne(context, manager, id);
    }

    private static void updateOne(final Context context, AppWidgetManager manager, int widgetId) {
        SharedPreferences state = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String trackId = clean(state.getString(Constants.EXTRA_TRACK_ID, ""));
        String title = clean(state.getString(Constants.EXTRA_TITLE, ""));
        String artist = clean(state.getString(Constants.EXTRA_ARTIST, ""));
        String artwork = clean(state.getString(Constants.EXTRA_ARTWORK, ""));
        String previous = clean(state.getString(Constants.EXTRA_PREVIOUS, ""));
        String current = clean(state.getString(Constants.EXTRA_CURRENT, ""));
        String next = clean(state.getString(Constants.EXTRA_NEXT, ""));
        String status = clean(state.getString(Constants.EXTRA_STATUS, ""));
        String provider = clean(state.getString(Constants.EXTRA_PROVIDER, ""));
        boolean playing = state.getBoolean(Constants.EXTRA_PLAYING, false);
        long updatedAt = state.getLong("updated_at", 0L);

        if (title.length() == 0) title = "MetroList Lyrics";
        if (artist.length() != 0) title = title + "  ·  " + artist;
        if (current.length() == 0) current = "Запусти MetroList";
        if (next.length() == 0 && updatedAt == 0L) {
            next = "Здесь появится синхронный текст";
        }
        status = displayStatus(trackId, status, provider, playing, updatedAt);

        ThemePalette palette = ThemePalette.resolve(context);
        int backgroundMode = WidgetSettings.backgroundMode(context);
        int opacity = WidgetSettings.opacity(context);
        int dimming = WidgetSettings.dimming(context);
        Bitmap background = ArtworkLoader.background(context, backgroundMode, trackId, artwork);
        boolean imageBackground = background != null && backgroundMode != WidgetSettings.BACKGROUND_COLOR;

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_lyrics);
        views.setTextViewText(R.id.widget_title, title);
        views.setTextViewText(R.id.widget_previous, previous);
        views.setTextViewText(R.id.widget_current, current);
        views.setTextViewText(R.id.widget_next, next);
        views.setTextViewText(R.id.widget_status, status);
        views.setViewVisibility(R.id.widget_previous, previous.length() == 0 ? View.GONE : View.VISIBLE);
        views.setViewVisibility(R.id.widget_next, next.length() == 0 ? View.GONE : View.VISIBLE);

        int currentSize = WidgetSettings.textSize(context);
        int sideSize = Math.max(11, currentSize - 6);
        views.setTextViewTextSize(R.id.widget_current, TypedValue.COMPLEX_UNIT_SP, currentSize);
        views.setTextViewTextSize(R.id.widget_previous, TypedValue.COMPLEX_UNIT_SP, sideSize);
        views.setTextViewTextSize(R.id.widget_next, TypedValue.COMPLEX_UNIT_SP, sideSize);

        int imageAlpha = Math.round(255f * opacity / 100f);
        if (imageBackground) {
            views.setImageViewBitmap(R.id.widget_bg, background);
            views.setInt(R.id.widget_bg, "setImageAlpha", imageAlpha);
            views.setImageViewResource(R.id.widget_scrim, R.drawable.widget_scrim);
            views.setInt(R.id.widget_scrim, "setColorFilter", Color.BLACK);
            int scrimAlpha = Math.round(255f * dimming / 100f * opacity / 100f);
            views.setInt(R.id.widget_scrim, "setImageAlpha", scrimAlpha);

            int readableAccent = readableOnDark(palette.accent);
            views.setTextColor(R.id.widget_title, readableAccent);
            views.setTextColor(R.id.widget_status, Color.argb(210, 255, 255, 255));
            views.setTextColor(R.id.widget_previous, Color.argb(158, 255, 255, 255));
            views.setTextColor(R.id.widget_current, Color.WHITE);
            views.setTextColor(R.id.widget_next, Color.argb(158, 255, 255, 255));
        } else {
            views.setImageViewResource(R.id.widget_bg, R.drawable.widget_panel);
            views.setInt(R.id.widget_bg, "setColorFilter", palette.surface);
            views.setInt(R.id.widget_bg, "setImageAlpha", imageAlpha);
            views.setImageViewResource(R.id.widget_scrim, R.drawable.widget_scrim);
            views.setInt(R.id.widget_scrim, "setImageAlpha", 0);

            views.setTextColor(R.id.widget_title, palette.accent);
            views.setTextColor(R.id.widget_status, palette.secondary);
            views.setTextColor(R.id.widget_previous, palette.muted);
            views.setTextColor(R.id.widget_current, palette.foreground);
            views.setTextColor(R.id.widget_next, palette.muted);
        }

        Intent launch = context.getPackageManager().getLaunchIntentForPackage(Constants.TARGET_PACKAGE);
        if (launch == null) launch = new Intent(context, MainActivity.class);
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pending = PendingIntent.getActivity(
                context,
                widgetId,
                launch,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        views.setOnClickPendingIntent(R.id.widget_root, pending);
        manager.updateAppWidget(widgetId, views);

        if (backgroundMode == WidgetSettings.BACKGROUND_ARTWORK && background == null
                && (trackId.length() != 0 || artwork.length() != 0)) {
            final Context app = context.getApplicationContext();
            ArtworkLoader.ensureAsync(app, trackId, artwork, new Runnable() {
                @Override public void run() {
                    WidgetState.updateAll(app);
                }
            });
        }
    }

    private static String displayStatus(String trackId, String status, String provider,
                                        boolean playing, long updatedAt) {
        if (updatedAt == 0L) return provider.length() == 0 ? "ожидание" : provider;
        if (trackId.length() == 0) return status.length() == 0 ? "ожидание" : status;

        String lower = status.toLowerCase(Locale.ROOT);
        if (lower.startsWith("играет") || lower.startsWith("пауза")) return status;

        String activity = playing ? "играет" : "пауза";
        String detail = provider.length() == 0 ? status : provider;
        if (detail.length() == 0 || activity.equalsIgnoreCase(detail)) return activity;
        return activity + " · " + detail;
    }

    private static int readableOnDark(int color) {
        double luminance = 0.2126 * Color.red(color) + 0.7152 * Color.green(color) + 0.0722 * Color.blue(color);
        return luminance < 150.0 ? ThemePalette.blend(color, Color.WHITE, 0.48f) : color;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
