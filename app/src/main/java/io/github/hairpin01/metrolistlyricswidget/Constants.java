package io.github.hairpin01.metrolistlyricswidget;

public final class Constants {
    private Constants() {}

    public static final String MODULE_PACKAGE = "io.github.hairpin01.metrolistlyricswidget";
    public static final String TARGET_PACKAGE = "com.metrolist.music";
    public static final String TARGET_SERVICE = "com.metrolist.music.playback.MusicService";

    public static final String ACTION_UPDATE = MODULE_PACKAGE + ".UPDATE";
    public static final String ACTION_CLEAR = MODULE_PACKAGE + ".CLEAR";

    public static final String EXTRA_TRACK_ID = "track_id";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_ARTIST = "artist";
    public static final String EXTRA_PREVIOUS = "previous";
    public static final String EXTRA_CURRENT = "current";
    public static final String EXTRA_NEXT = "next";
    public static final String EXTRA_STATUS = "status";
    public static final String EXTRA_PROVIDER = "provider";
    public static final String EXTRA_ARTWORK = "artwork";
    public static final String EXTRA_PLAYING = "playing";
    public static final String EXTRA_POSITION = "position";
    public static final String EXTRA_DURATION = "duration";
}