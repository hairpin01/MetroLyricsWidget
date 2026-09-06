package io.github.hairpin01.metrolistlyricswidget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
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
        if (intent.hasExtra(Constants.EXTRA_DURATION)) {
            editor.putLong(Constants.EXTRA_DURATION, intent.getLongExtra(Constants.EXTRA_DURATION, 0L));
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

    static void clearLineState(Context context, int widgetId) {
        SharedPreferences.Editor editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        editor.remove("line_previous_" + widgetId);
        editor.remove("line_current_" + widgetId);
        editor.remove("line_next_" + widgetId);
        for (int slot = 0; slot < 3; slot++) {
            editor.remove("line_child_" + slot + "_" + widgetId);
        }
        editor.apply();
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
        long position = state.getLong(Constants.EXTRA_POSITION, 0L);
        long duration = state.getLong(Constants.EXTRA_DURATION, 0L);
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

        // Adaptive layout: one-row widgets (3x1, 4x1…) have little vertical room, so
        // prev/next lines are dropped and the current line takes all remaining height.
        // Narrow widgets lose the status label (it steals title width) and get a
        // smaller cover so the current line keeps as much room as possible.
        // Per AppWidgetManager docs: MIN_WIDTH is the portrait width and MAX_HEIGHT
        // is the portrait height, which is the orientation the widget lives in.
        float density = context.getResources().getDisplayMetrics().density;
        Bundle options = manager.getAppWidgetOptions(widgetId);
        int portraitWidthDp = Math.round(options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) / density);
        int portraitHeightDp = Math.round(options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0) / density);
        // One launcher row is roughly 100-160dp, two rows start around 240dp;
        // a 3x1 cell is about 200-230dp wide, 4x1 around 280dp and wider.
        boolean compact = portraitHeightDp > 0 && portraitHeightDp < 180;
        boolean narrow = portraitWidthDp > 0 && portraitWidthDp < 260;

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_lyrics);
        views.setTextViewText(R.id.widget_title, title);
        views.setTextViewText(R.id.widget_status, status);
        views.setViewVisibility(R.id.widget_status, narrow ? View.GONE : View.VISIBLE);

        // Lyric lines: each slot is a ViewFlipper with two children. On a line change
        // the fresh text is written into the hidden slot and the flipper switches —
        // the launcher then plays in/out animations, producing a smooth transition.
        LineState lineState = LineState.load(context, widgetId);
        boolean changed = !lineState.matches(previous, current, next);
        boolean animate = changed && WidgetSettings.animateLines(context);
        setLine(views, lineState, LineState.SLOT_PREVIOUS,
                R.id.widget_flipper_previous, R.id.widget_previous_a, R.id.widget_previous_b,
                previous.length() == 0 ? " " : previous, changed, animate);
        setLine(views, lineState, LineState.SLOT_CURRENT,
                R.id.widget_flipper_current, R.id.widget_current_a, R.id.widget_current_b,
                current, changed, animate);
        setLine(views, lineState, LineState.SLOT_NEXT,
                R.id.widget_flipper_next, R.id.widget_next_a, R.id.widget_next_b,
                next.length() == 0 ? " " : next, changed, animate);
        lineState.apply(previous, current, next);
        lineState.save(context, widgetId);
        // Prev/next lines need roughly 3+ lyric rows of height; in the compact
        // (Nx1) layout only the current line and progress are worth the space.
        views.setViewVisibility(R.id.widget_flipper_previous,
                !compact && previous.length() != 0 ? View.VISIBLE : View.GONE);
        views.setViewVisibility(R.id.widget_flipper_next,
                !compact && next.length() != 0 ? View.VISIBLE : View.GONE);

        int currentSize = WidgetSettings.textSize(context);
        int sideSize = Math.max(11, currentSize - 6);
        views.setTextViewTextSize(R.id.widget_current_a, TypedValue.COMPLEX_UNIT_SP, currentSize);
        views.setTextViewTextSize(R.id.widget_current_b, TypedValue.COMPLEX_UNIT_SP, currentSize);
        views.setTextViewTextSize(R.id.widget_previous_a, TypedValue.COMPLEX_UNIT_SP, sideSize);
        views.setTextViewTextSize(R.id.widget_previous_b, TypedValue.COMPLEX_UNIT_SP, sideSize);
        views.setTextViewTextSize(R.id.widget_next_a, TypedValue.COMPLEX_UNIT_SP, sideSize);
        views.setTextViewTextSize(R.id.widget_next_b, TypedValue.COMPLEX_UNIT_SP, sideSize);
        // In the compact layout the current line owns all vertical space, so it may
        // wrap into more lines; wide layouts keep two lines like before.
        views.setInt(R.id.widget_current_a, "setMaxLines", compact ? 3 : 2);
        views.setInt(R.id.widget_current_b, "setMaxLines", compact ? 3 : 2);

        // Album cover next to the lyrics: placeholder while loading, bitmap when cached.
        boolean showCover = WidgetSettings.showCover(context) && trackId.length() != 0;
        Bitmap cover = showCover ? ArtworkLoader.cover(context, trackId, artwork) : null;
        views.setViewVisibility(R.id.widget_cover, showCover ? View.VISIBLE : View.GONE);
        if (cover != null) {
            views.setImageViewBitmap(R.id.widget_cover, cover);
        } else {
            views.setImageViewResource(R.id.widget_cover, R.drawable.widget_cover_placeholder);
        }
        // Narrow widgets cannot afford a 52dp cover next to the lyrics.
        if (showCover) {
            views.setViewLayoutHeightDimen(R.id.widget_cover,
                    narrow ? R.dimen.widget_cover_size_compact : R.dimen.widget_cover_size);
            views.setViewLayoutWidthDimen(R.id.widget_cover,
                    narrow ? R.dimen.widget_cover_size_compact : R.dimen.widget_cover_size);
        }

        // Progress bar: extrapolate position by wall clock since the last snapshot,
        // polls arrive every ~220 ms but identical payloads are deduplicated upstream.
        boolean showProgress = WidgetSettings.showProgress(context) && trackId.length() != 0 && duration > 0L;
        views.setViewVisibility(R.id.widget_progress_wrap, showProgress ? View.VISIBLE : View.GONE);
        if (showProgress) {
            long elapsed = position;
            if (playing) elapsed += Math.max(0L, System.currentTimeMillis() - updatedAt);
            long clamped = Math.max(0L, Math.min(duration, elapsed));
            // ClipDrawable levels span 0..10000, same scale as ProgressBar.
            int level = (int) (10000L * clamped / duration);
            views.setInt(R.id.widget_progress, "setImageLevel", level);
        }

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
            setLineColors(views, Color.argb(158, 255, 255, 255), Color.WHITE);
            if (showProgress) {
                views.setInt(R.id.widget_progress, "setColorFilter", readableOnDark(palette.accent));
                views.setInt(R.id.widget_progress_track, "setColorFilter", Color.WHITE);
                views.setInt(R.id.widget_progress_track, "setImageAlpha", 80);
            }
        } else {
            views.setImageViewResource(R.id.widget_bg, R.drawable.widget_panel);
            views.setInt(R.id.widget_bg, "setColorFilter", palette.surface);
            views.setInt(R.id.widget_bg, "setImageAlpha", imageAlpha);
            views.setImageViewResource(R.id.widget_scrim, R.drawable.widget_scrim);
            views.setInt(R.id.widget_scrim, "setImageAlpha", 0);

            views.setTextColor(R.id.widget_title, palette.accent);
            views.setTextColor(R.id.widget_status, palette.secondary);
            setLineColors(views, palette.muted, palette.foreground);
            if (showProgress) {
                views.setInt(R.id.widget_progress, "setColorFilter", palette.accent);
                views.setInt(R.id.widget_progress_track, "setColorFilter", palette.foreground);
                views.setInt(R.id.widget_progress_track, "setImageAlpha", 77);
            }
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

        if ((trackId.length() != 0 || artwork.length() != 0)
                && ((showCover && cover == null)
                || (backgroundMode == WidgetSettings.BACKGROUND_ARTWORK && background == null))) {
            final Context app = context.getApplicationContext();
            ArtworkLoader.ensureAsync(app, trackId, artwork, new Runnable() {
                @Override public void run() {
                    WidgetState.updateAll(app);
                }
            });
        }
    }

    // Writes the lyric text into the flipper slots. On an animated change the visible
    // slot keeps the old text (it animates out) and the hidden slot receives the new
    // line, then the flipper switches children so the host plays both animations.
    // RemoteViews actions replay in order, so texts must be set before the flip.
    private static void setLine(RemoteViews views, LineState state, int slot,
                                int flipperId, int slotA, int slotB,
                                String text, boolean changed, boolean animate) {
        int shown = state.currentChild(slot);
        int visible = shown == 0 ? slotA : slotB;
        int hidden = shown == 0 ? slotB : slotA;
        if (animate) {
            int flipTo = state.nextChild(slot);
            views.setTextViewText(visible, changed ? state.text(slot) : text);
            views.setTextViewText(hidden, text);
            views.setDisplayedChild(flipperId, flipTo);
            state.advance(slot, flipTo);
        } else {
            views.setTextViewText(visible, text);
            views.setTextViewText(hidden, text);
        }
    }

    private static void setLineColors(RemoteViews views, int sideColor, int currentColor) {
        views.setTextColor(R.id.widget_previous_a, sideColor);
        views.setTextColor(R.id.widget_previous_b, sideColor);
        views.setTextColor(R.id.widget_current_a, currentColor);
        views.setTextColor(R.id.widget_current_b, currentColor);
        views.setTextColor(R.id.widget_next_a, sideColor);
        views.setTextColor(R.id.widget_next_b, sideColor);
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

    // Tracks which flipper child is displayed for each line slot of a widget, so the
    // flip happens only when the lyric text actually changes. ViewFlipper.restart
    // semantics: setDisplayedChild always replays the in/out animation pair, so an
    // unconditional flip on every 220 ms refresh would keep the widget flickering.
    private static final class LineState {
        static final int SLOT_PREVIOUS = 0;
        static final int SLOT_CURRENT = 1;
        static final int SLOT_NEXT = 2;
        private static final int SLOTS = 3;

        private String previous;
        private String current;
        private String next;
        private final int[] children = new int[SLOTS];

        private LineState(SharedPreferences prefs, int widgetId) {
            previous = clean(prefs.getString("line_previous_" + widgetId, null));
            current = clean(prefs.getString("line_current_" + widgetId, null));
            next = clean(prefs.getString("line_next_" + widgetId, null));
            for (int slot = 0; slot < SLOTS; slot++) {
                children[slot] = prefs.getInt("line_child_" + slot + "_" + widgetId, 0);
            }
        }

        static LineState load(Context context, int widgetId) {
            return new LineState(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE), widgetId);
        }

        boolean matches(String previous, String current, String next) {
            return this.previous.equals(previous) && this.current.equals(current) && this.next.equals(next);
        }

        String text(int slot) {
            if (slot == SLOT_PREVIOUS) return previous;
            if (slot == SLOT_CURRENT) return current;
            return next;
        }

        int currentChild(int slot) {
            return children[slot];
        }

        int nextChild(int slot) {
            return (children[slot] + 1) % 2;
        }

        void advance(int slot, int child) {
            children[slot] = child;
        }

        void apply(String previous, String current, String next) {
            this.previous = previous == null ? "" : previous;
            this.current = current == null ? "" : current;
            this.next = next == null ? "" : next;
        }

        void save(Context context, int widgetId) {
            SharedPreferences.Editor editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
            editor.putString("line_previous_" + widgetId, previous);
            editor.putString("line_current_" + widgetId, current);
            editor.putString("line_next_" + widgetId, next);
            for (int slot = 0; slot < SLOTS; slot++) {
                editor.putInt("line_child_" + slot + "_" + widgetId, children[slot]);
            }
            editor.apply();
        }
    }
}
