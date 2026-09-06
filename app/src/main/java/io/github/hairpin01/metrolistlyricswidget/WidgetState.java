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
import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.RemoteViews;

import java.util.ArrayList;
import java.util.List;
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
        copyContextString(intent, editor, Constants.EXTRA_PREVIOUS_CONTEXT, Constants.EXTRA_PREVIOUS);
        copyString(intent, editor, Constants.EXTRA_CURRENT);
        copyString(intent, editor, Constants.EXTRA_NEXT);
        copyContextString(intent, editor, Constants.EXTRA_NEXT_CONTEXT, Constants.EXTRA_NEXT);
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
        // Always reset absent ranges for compatibility with an older embedded hook.
        editor.putInt(Constants.EXTRA_HIGHLIGHT_END,
                intent.getIntExtra(Constants.EXTRA_HIGHLIGHT_END, -1));
        editor.putInt(Constants.EXTRA_ACTIVE_START,
                intent.getIntExtra(Constants.EXTRA_ACTIVE_START, -1));
        editor.putInt(Constants.EXTRA_ACTIVE_END,
                intent.getIntExtra(Constants.EXTRA_ACTIVE_END, -1));
        editor.putBoolean(Constants.EXTRA_KARAOKE,
                intent.getBooleanExtra(Constants.EXTRA_KARAOKE, false));
        editor.putLong("updated_at", System.currentTimeMillis());
        editor.apply();
    }

    private static void copyString(Intent intent, SharedPreferences.Editor editor, String key) {
        if (intent.hasExtra(key)) editor.putString(key, intent.getStringExtra(key));
    }

    private static void copyContextString(Intent intent, SharedPreferences.Editor editor,
                                          String contextKey, String fallbackKey) {
        if (intent.hasExtra(contextKey)) {
            editor.putString(contextKey, intent.getStringExtra(contextKey));
        } else if (intent.hasExtra(fallbackKey)) {
            editor.putString(contextKey, intent.getStringExtra(fallbackKey));
        }
    }

    static void clear(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply();
    }

    static String currentTrackId(Context context) {
        return clean(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(Constants.EXTRA_TRACK_ID, ""));
    }

    static String currentArtwork(Context context) {
        return clean(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(Constants.EXTRA_ARTWORK, ""));
    }

    static void ensureCurrentArtwork(Context context, Runnable finished) {
        String trackId = currentTrackId(context);
        String artwork = currentArtwork(context);
        if (trackId.length() == 0 && artwork.length() == 0) {
            if (finished != null) finished.run();
            return;
        }
        boolean pending = ArtworkLoader.ensureAsync(
                context.getApplicationContext(), trackId, artwork, finished);
        if (!pending && finished != null) finished.run();
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
        String previousContext = clean(state.getString(
                Constants.EXTRA_PREVIOUS_CONTEXT, previous));
        String current = clean(state.getString(Constants.EXTRA_CURRENT, ""));
        String next = clean(state.getString(Constants.EXTRA_NEXT, ""));
        String nextContext = clean(state.getString(Constants.EXTRA_NEXT_CONTEXT, next));
        String status = clean(state.getString(Constants.EXTRA_STATUS, ""));
        String provider = clean(state.getString(Constants.EXTRA_PROVIDER, ""));
        boolean playing = state.getBoolean(Constants.EXTRA_PLAYING, false);
        long position = state.getLong(Constants.EXTRA_POSITION, 0L);
        long duration = state.getLong(Constants.EXTRA_DURATION, 0L);
        int highlightEnd = state.getInt(Constants.EXTRA_HIGHLIGHT_END, -1);
        int activeStart = state.getInt(Constants.EXTRA_ACTIVE_START, -1);
        int activeEnd = state.getInt(Constants.EXTRA_ACTIVE_END, -1);
        boolean karaoke = state.getBoolean(Constants.EXTRA_KARAOKE, false);
        long updatedAt = state.getLong("updated_at", 0L);

        if (title.length() == 0) title = "MetroList Lyrics";
        if (artist.length() != 0) title = title + "  ·  " + artist;
        if (current.length() == 0) current = "Запусти MetroList";
        if (next.length() == 0 && updatedAt == 0L) {
            next = "Здесь появится синхронный текст";
        }
        status = displayStatus(trackId, status, provider, playing, updatedAt);

        ThemePalette palette = ThemePalette.resolve(context, trackId, artwork);
        int backgroundMode = WidgetSettings.backgroundMode(context);
        int opacity = WidgetSettings.opacity(context);
        int dimming = WidgetSettings.dimming(context);
        // AppWidget option dimensions are already expressed in dp. In portrait the
        // host reports the active shape through MIN_WIDTH and MAX_HEIGHT.
        Bundle options = manager.getAppWidgetOptions(widgetId);
        int portraitWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0);
        int portraitHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0);
        // Render the artwork to the widget's own aspect ratio instead of preserving
        // one fixed 16:9 crop after the launcher resizes it.
        Bitmap background = ArtworkLoader.background(context, backgroundMode, trackId, artwork,
                portraitWidthDp, portraitHeightDp);
        boolean imageBackground = background != null && backgroundMode != WidgetSettings.BACKGROUND_COLOR;
        int currentLineColor = imageBackground ? Color.WHITE : palette.foreground;
        int sideLineColor = imageBackground
                ? Color.argb(158, 255, 255, 255) : palette.muted;
        int trackAccent = ThemePalette.trackAccent(
                context, trackId, artwork, palette.accent);
        int configuredKaraokeColor = WidgetSettings.karaokeColor(
                context, palette.accent, trackAccent);
        int configuredActiveColor = WidgetSettings.karaokeActiveColor(
                context, configuredKaraokeColor, palette.accent, trackAccent);
        int configuredProgressColor = WidgetSettings.progressColor(
                context, palette.accent, trackAccent);
        int karaokeColor = imageBackground
                ? readableOnDark(configuredKaraokeColor) : configuredKaraokeColor;
        int activeKaraokeColor = imageBackground
                ? readableOnDark(configuredActiveColor) : configuredActiveColor;
        CharSequence currentDisplay = karaokeText(context, current, karaoke,
                highlightEnd, activeStart, activeEnd, currentLineColor, karaokeColor,
                activeKaraokeColor);
        int automaticPadding = automaticVerticalPaddingDp(portraitHeightDp);
        boolean autoPadding = WidgetSettings.autoVerticalPadding(context);
        int topPadding = autoPadding ? automaticPadding : WidgetSettings.topPaddingDp(context);
        int bottomPadding = autoPadding ? automaticPadding : WidgetSettings.bottomPaddingDp(context);
        // The capacity formula is calibrated around the default 8dp + 8dp padding.
        // Manual larger offsets therefore reduce the number of surrounding lines too.
        int lyricsHeightDp = portraitHeightDp <= 0 ? portraitHeightDp : Math.max(0,
                portraitHeightDp - topPadding - bottomPadding + 16);
        // The current line stays centered in the free middle area. Each increase in
        // widget height reveals more context above and below it (up to six per side).
        int currentSize = WidgetSettings.textSize(context);
        int sideSize = Math.max(11, currentSize - 6);
        int contextLinesPerSide = contextLinesPerSide(lyricsHeightDp, currentSize);
        ContextWindow contextWindow = ContextWindow.create(
                previousContext, nextContext, previous, next, contextLinesPerSide);
        previous = contextWindow.previous;
        next = contextWindow.next;
        boolean compact = contextLinesPerSide == 0;
        // Narrow widgets lose the status label because it steals title width.
        boolean narrow = portraitWidthDp > 0 && portraitWidthDp < 260;

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_lyrics);
        views.setViewPadding(R.id.widget_content, dpToPx(context, 14),
                dpToPx(context, topPadding), dpToPx(context, 14),
                dpToPx(context, bottomPadding));

        // Apply both flipper children's colors and gravity before setDisplayedChild.
        // Otherwise the launcher can render the XML defaults for the first frame.
        setLineColors(views, sideLineColor, currentLineColor);
        setLyricGravity(views, lyricGravity(WidgetSettings.textAlignment(context)));
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
                currentDisplay, changed, animate);
        setLine(views, lineState, LineState.SLOT_NEXT,
                R.id.widget_flipper_next, R.id.widget_next_a, R.id.widget_next_b,
                next.length() == 0 ? " " : next, changed, animate);
        lineState.apply(previous, current, next);
        lineState.save(context, widgetId);
        views.setViewVisibility(R.id.widget_flipper_previous,
                previous.length() != 0 ? View.VISIBLE : View.GONE);
        views.setViewVisibility(R.id.widget_flipper_next,
                next.length() != 0 ? View.VISIBLE : View.GONE);
        int previousMaxLines = Math.max(1, contextWindow.previousLines);
        int nextMaxLines = Math.max(1, contextWindow.nextLines);
        views.setInt(R.id.widget_previous_a, "setMaxLines", previousMaxLines);
        views.setInt(R.id.widget_previous_b, "setMaxLines", previousMaxLines);
        views.setInt(R.id.widget_next_a, "setMaxLines", nextMaxLines);
        views.setInt(R.id.widget_next_b, "setMaxLines", nextMaxLines);

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
            int height = progressHeightDimen(WidgetSettings.progressHeightDp(context));
            views.setViewLayoutHeightDimen(R.id.widget_progress, height);
            views.setViewLayoutHeightDimen(R.id.widget_progress_track, height);
            int fillColor = imageBackground
                    ? readableOnDark(configuredProgressColor) : configuredProgressColor;
            views.setInt(R.id.widget_progress, "setColorFilter", fillColor);
            views.setInt(R.id.widget_progress_track, "setColorFilter",
                    imageBackground ? Color.WHITE : palette.foreground);
            views.setInt(R.id.widget_progress_track, "setImageAlpha",
                    Math.round(255f * WidgetSettings.progressTrackOpacity(context) / 100f));
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
            // Lyric colors were queued before the ViewFlipper switch above.
        } else {
            views.setImageViewResource(R.id.widget_bg, R.drawable.widget_panel);
            views.setInt(R.id.widget_bg, "setColorFilter", palette.surface);
            views.setInt(R.id.widget_bg, "setImageAlpha", imageAlpha);
            views.setImageViewResource(R.id.widget_scrim, R.drawable.widget_scrim);
            views.setInt(R.id.widget_scrim, "setImageAlpha", 0);

            views.setTextColor(R.id.widget_title, palette.accent);
            views.setTextColor(R.id.widget_status, palette.secondary);
            // Lyric colors were queued before the ViewFlipper switch above.
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

        // Always ask for the exact current-track artwork when an artwork surface is
        // enabled. ensureAsync is a cheap no-op for cached/running requests; this must
        // not depend on a non-null bitmap because that bitmap may be the transition
        // fallback from the previous track.
        if ((trackId.length() != 0 || artwork.length() != 0)
                && (showCover || backgroundMode == WidgetSettings.BACKGROUND_ARTWORK
                || WidgetSettings.usesTrackAccent(context))) {
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
                                CharSequence text, boolean changed, boolean animate) {
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

    static CharSequence karaokeText(Context context, String text, boolean karaoke,
                                      int highlightEnd, int activeStart, int activeEnd,
                                      int baseColor, int highlightColor, int activeColor) {
        if (text.length() == 0 || !karaoke || !WidgetSettings.karaokeEnabled(context)) {
            return text;
        }
        int length = text.length();
        int sungEnd = Math.max(0, Math.min(length, highlightEnd));
        int tokenStart = Math.max(0, Math.min(length, activeStart));
        int tokenEnd = Math.max(tokenStart, Math.min(length, activeEnd));
        boolean hasActive = activeStart >= 0 && tokenEnd > tokenStart;
        int effectStart = tokenStart;
        int effectEnd = tokenEnd;
        int mode = WidgetSettings.karaokeMode(context);
        if (hasActive && mode == WidgetSettings.KARAOKE_MODE_ACTIVE_WORD) {
            while (effectStart > 0 && !Character.isWhitespace(text.charAt(effectStart - 1))) {
                effectStart--;
            }
            while (effectEnd < length && !Character.isWhitespace(text.charAt(effectEnd))) {
                effectEnd++;
            }
        }

        int unsungAlpha = Math.round(255f
                * WidgetSettings.karaokeUnsungOpacity(context) / 100f);
        int unsungColor = ThemePalette.alpha(baseColor, unsungAlpha);
        SpannableString styled = new SpannableString(text);
        setTextColor(styled, unsungColor, 0, length);
        if (mode == WidgetSettings.KARAOKE_MODE_TRAIL) {
            setTextColor(styled, highlightColor, 0, sungEnd);
        }
        if (hasActive) {
            setTextColor(styled, activeColor, effectStart, effectEnd);
        }
        if (hasActive && WidgetSettings.karaokeBold(context)) {
            styled.setSpan(new StyleSpan(Typeface.BOLD), effectStart, effectEnd,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (hasActive && WidgetSettings.karaokePop(context)) {
            float scale = 1f + WidgetSettings.karaokePopStrength(context) / 100f;
            styled.setSpan(new RelativeSizeSpan(scale), effectStart, effectEnd,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return styled;
    }

    private static void setTextColor(SpannableString text, int color, int start, int end) {
        if (end <= start) return;
        text.setSpan(new ForegroundColorSpan(color), start, end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private static void setLineColors(RemoteViews views, int sideColor, int currentColor) {
        views.setTextColor(R.id.widget_previous_a, sideColor);
        views.setTextColor(R.id.widget_previous_b, sideColor);
        views.setTextColor(R.id.widget_current_a, currentColor);
        views.setTextColor(R.id.widget_current_b, currentColor);
        views.setTextColor(R.id.widget_next_a, sideColor);
        views.setTextColor(R.id.widget_next_b, sideColor);
    }

    private static int lyricGravity(int alignment) {
        if (alignment == WidgetSettings.TEXT_ALIGN_CENTER) return Gravity.CENTER_HORIZONTAL;
        if (alignment == WidgetSettings.TEXT_ALIGN_END) return Gravity.END;
        return Gravity.START;
    }

    private static void setLyricGravity(RemoteViews views, int gravity) {
        views.setInt(R.id.widget_previous_a, "setGravity", gravity);
        views.setInt(R.id.widget_previous_b, "setGravity", gravity);
        views.setInt(R.id.widget_current_a, "setGravity", gravity);
        views.setInt(R.id.widget_current_b, "setGravity", gravity);
        views.setInt(R.id.widget_next_a, "setGravity", gravity);
        views.setInt(R.id.widget_next_b, "setGravity", gravity);
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

    private static int contextLinesPerSide(int heightDp, int currentTextSize) {
        if (heightDp <= 0) return 1;
        int sideTextSize = Math.max(11, currentTextSize - 6);
        int fixedSpaceDp = 118 + Math.max(0, currentTextSize - 19) * 2;
        int pairHeightDp = Math.max(30, 2 * (sideTextSize + 3));
        if (heightDp <= fixedSpaceDp) return 0;
        return Math.max(0, Math.min(6, (heightDp - fixedSpaceDp) / pairHeightDp));
    }

    private static int automaticVerticalPaddingDp(int heightDp) {
        if (heightDp <= 0) return 8;
        return Math.max(4, Math.min(16, Math.round(heightDp * 0.04f)));
    }

    private static int dpToPx(Context context, int dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }

    private static int progressHeightDimen(int heightDp) {
        switch (heightDp) {
            case 2: return R.dimen.widget_progress_height_2;
            case 3: return R.dimen.widget_progress_height_3;
            case 5: return R.dimen.widget_progress_height_5;
            case 6: return R.dimen.widget_progress_height_6;
            case 7: return R.dimen.widget_progress_height_7;
            case 8: return R.dimen.widget_progress_height_8;
            default: return R.dimen.widget_progress_height_4;
        }
    }

    private static int readableOnDark(int color) {
        double luminance = 0.2126 * Color.red(color) + 0.7152 * Color.green(color) + 0.0722 * Color.blue(color);
        return luminance < 150.0 ? ThemePalette.blend(color, Color.WHITE, 0.48f) : color;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static final class ContextWindow {
        final String previous;
        final String next;
        final int previousLines;
        final int nextLines;

        private ContextWindow(String previous, String next, int previousLines, int nextLines) {
            this.previous = previous;
            this.next = next;
            this.previousLines = previousLines;
            this.nextLines = nextLines;
        }

        static ContextWindow create(String previousContext, String nextContext,
                                    String previousFallback, String nextFallback, int limit) {
            if (limit <= 0) return new ContextWindow("", "", 0, 0);
            List<String> before = splitContext(previousContext, previousFallback);
            List<String> after = splitContext(nextContext, nextFallback);
            int previousCount = Math.min(limit, before.size());
            int nextCount = Math.min(limit, after.size());
            String previous = joinLines(before, before.size() - previousCount, before.size());
            String next = joinLines(after, 0, nextCount);
            return new ContextWindow(previous, next, previousCount, nextCount);
        }

        private static List<String> splitContext(String context, String fallback) {
            String source = clean(context);
            if (source.length() == 0) source = clean(fallback);
            List<String> lines = new ArrayList<String>();
            if (source.length() == 0) return lines;
            for (String raw : source.split("\r?\n")) {
                String line = clean(raw);
                if (line.length() != 0) lines.add(line);
            }
            return lines;
        }

        private static String joinLines(List<String> lines, int from, int to) {
            StringBuilder joined = new StringBuilder();
            for (int index = from; index < to; index++) {
                if (joined.length() != 0) joined.append('\n');
                joined.append(lines.get(index));
            }
            return joined.toString();
        }
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
