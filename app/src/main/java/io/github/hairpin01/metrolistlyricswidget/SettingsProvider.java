package io.github.hairpin01.metrolistlyricswidget;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

/** Exposes only the global timing correction to the hook running inside MetroList. */
public final class SettingsProvider extends ContentProvider {
    static final String AUTHORITY = Constants.MODULE_PACKAGE + ".settings";
    static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/karaoke");
    static final String COLUMN_LYRICS_OFFSET_MS = "lyrics_offset_ms";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        MatrixCursor result = new MatrixCursor(new String[]{COLUMN_LYRICS_OFFSET_MS}, 1);
        int offset = getContext() == null ? 0 : WidgetSettings.lyricsTimingOffsetMs(getContext());
        result.addRow(new Object[]{offset});
        return result;
    }

    @Override
    public String getType(Uri uri) {
        return "vnd.android.cursor.item/vnd." + AUTHORITY + ".karaoke";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
                      String[] selectionArgs) {
        return 0;
    }
}
