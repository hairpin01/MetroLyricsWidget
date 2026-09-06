package io.github.hairpin01.metrolistlyricswidget;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;

import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

final class MetroBridge implements Runnable {
    private static final Map<Object, MetroBridge> ACTIVE = new WeakHashMap<Object, MetroBridge>();

    static void attach(Object service, ClassLoader classLoader) {
        if (!(service instanceof Context)) return;
        synchronized (ACTIVE) {
            MetroBridge old = ACTIVE.get(service);
            if (old != null) return;
            MetroBridge bridge = new MetroBridge(service, (Context) service, classLoader);
            ACTIVE.put(service, bridge);
            bridge.start();
        }
    }

    static void detach(Object service) {
        synchronized (ACTIVE) {
            MetroBridge bridge = ACTIVE.remove(service);
            if (bridge != null) bridge.stop();
        }
    }

    private final Object service;
    private final Context context;
    private final ClassLoader targetClassLoader;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "MetroLyrics-loader");
            thread.setDaemon(true);
            return thread;
        }
    });

    private volatile boolean stopped;
    private volatile List<LyricLine> lyricLines = Collections.emptyList();
    private volatile int lyricsOffsetMs;
    private volatile String lyricProvider = "";
    private volatile String lyricsTrackId = "";
    private Future<?> loaderTask;

    private String currentTrackId = "";
    private String currentTitle = "MetroList";
    private String currentArtist = "";
    private String currentArtwork = "";
    private String currentPrevious = "";
    private String currentLineText = "";
    private String currentNextText = "";
    private String lastPayload = "";
    private boolean dbErrorLogged;

    private MetroBridge(Object service, Context context, ClassLoader targetClassLoader) {
        this.service = service;
        this.context = context;
        this.targetClassLoader = targetClassLoader;
    }

    private void start() {
        log("bridge attached to MusicService");
        handler.postDelayed(this, 350L);
    }

    private void stop() {
        stopped = true;
        handler.removeCallbacks(this);
        if (loaderTask != null) loaderTask.cancel(true);
        worker.shutdownNow();
        log("bridge detached");
    }

    @Override
    public void run() {
        if (stopped) return;
        long nextDelay = 900L;
        try {
            Object player = getField(service, "player");
            if (player == null) {
                sendIdle("плеер запускается");
                schedule(nextDelay);
                return;
            }

            Object mediaItem = call(player, "getCurrentMediaItem");
            String trackId = mediaItemId(mediaItem);
            boolean playing = bool(call(player, "isPlaying"), false);
            nextDelay = playing ? 220L : 700L;

            if (trackId.length() == 0) {
                if (currentTrackId.length() != 0) resetTrack();
                sendIdle("ожидание");
                schedule(nextDelay);
                return;
            }

            TrackInfo info = readTrackInfo(mediaItem, trackId);
            long position = number(call(player, "getCurrentPosition"), 0L);
            long duration = number(call(player, "getDuration"), 0L);
            if (!trackId.equals(currentTrackId)) {
                beginTrack(trackId, info, playing, position, duration);
            } else {
                if (info.title.length() != 0) currentTitle = info.title;
                if (info.artist.length() != 0) currentArtist = info.artist;
                if (info.artwork.length() != 0) currentArtwork = info.artwork;
            }

            if (trackId.equals(lyricsTrackId) && !lyricLines.isEmpty()) {
                nextDelay = Math.min(nextDelay, emitCurrentLine(position, duration, playing, false));
            } else {
                emitHeartbeat(position, duration, playing);
            }
        } catch (Throwable error) {
            log("poll failed: " + error);
        }
        schedule(nextDelay);
    }

    private void schedule(long delayMs) {
        if (!stopped) handler.postDelayed(this, delayMs);
    }

    private void resetTrack() {
        currentTrackId = "";
        currentTitle = "MetroList";
        currentArtist = "";
        currentArtwork = "";
        lyricLines = Collections.emptyList();
        lyricsTrackId = "";
        lyricProvider = "";
        lyricsOffsetMs = 0;
        if (loaderTask != null) loaderTask.cancel(true);
    }

    private void beginTrack(final String trackId, TrackInfo info, boolean playing, long position, long duration) {
        currentTrackId = trackId;
        currentTitle = info.title.length() == 0 ? "MetroList" : info.title;
        currentArtist = info.artist;
        currentArtwork = info.artwork;
        lyricLines = Collections.emptyList();
        lyricsTrackId = "";
        lyricProvider = "";
        lyricsOffsetMs = 0;
        lastPayload = "";

        if (loaderTask != null) loaderTask.cancel(true);
        sendSnapshot("", "Ищу синхронный текст…", "", "поиск текста", "", playing, position, duration, true);
        loaderTask = worker.submit(new Runnable() {
            @Override public void run() { loadLyrics(trackId); }
        });
    }

    private void loadLyrics(String trackId) {
        boolean helperStarted = false;
        for (int attempt = 0; attempt < 38 && !stopped && trackId.equals(currentTrackId); attempt++) {
            DbResult db = readDatabase(trackId);
            if (db.offsetKnown) lyricsOffsetMs = db.offsetMs;
            if (validLyrics(db.lyrics)) {
                if (acceptLyrics(trackId, db.lyrics, db.provider, db.offsetMs)) return;
            }

            if (!helperStarted && attempt >= 1) {
                helperStarted = true;
                requestLyricsFromMetroList(trackId);
            }

            try {
                Thread.sleep(750L);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        if (!stopped && trackId.equals(currentTrackId) && lyricLines.isEmpty()) {
            handler.post(new Runnable() {
                @Override public void run() {
                    if (!stopped && trackId.equals(currentTrackId) && lyricLines.isEmpty()) {
                        boolean playing = isPlayerPlaying();
                        long[] timeline = playerTimeline();
                        sendSnapshot("", "Нет синхронизированного текста", "", "нет текста", "",
                                playing, timeline[0], timeline[1], true);
                    }
                }
            });
        }
    }

    private long[] playerTimeline() {
        try {
            Object player = getField(service, "player");
            return new long[]{
                    number(call(player, "getCurrentPosition"), 0L),
                    number(call(player, "getDuration"), 0L)
            };
        } catch (Throwable ignored) {
            return new long[]{0L, 0L};
        }
    }

    private DbResult readDatabase(String trackId) {
        SQLiteDatabase db = null;
        Cursor cursor = null;
        try {
            File file = context.getDatabasePath("song.db");
            if (!file.exists()) return new DbResult(null, "", 0, false);
            db = SQLiteDatabase.openDatabase(
                    file.getAbsolutePath(),
                    null,
                    SQLiteDatabase.OPEN_READONLY | SQLiteDatabase.NO_LOCALIZED_COLLATORS
            );
            cursor = db.rawQuery(
                    "SELECT l.lyrics, l.provider, COALESCE(s.lyricsOffset, 0) " +
                            "FROM lyrics l LEFT JOIN song s ON s.id = l.id WHERE l.id = ? LIMIT 1",
                    new String[]{trackId}
            );
            if (cursor.moveToFirst()) {
                return new DbResult(cursor.getString(0), cursor.getString(1), cursor.getInt(2), true);
            }
            cursor.close();
            cursor = db.rawQuery("SELECT COALESCE(lyricsOffset, 0) FROM song WHERE id = ? LIMIT 1", new String[]{trackId});
            if (cursor.moveToFirst()) return new DbResult(null, "", cursor.getInt(0), true);
        } catch (Throwable error) {
            if (!dbErrorLogged) {
                dbErrorLogged = true;
                log("database read failed, reflection fallback remains active: " + error);
            }
        } finally {
            if (cursor != null) try { cursor.close(); } catch (Throwable ignored) {}
            if (db != null) try { db.close(); } catch (Throwable ignored) {}
        }
        return new DbResult(null, "", 0, false);
    }

    private void requestLyricsFromMetroList(final String expectedTrackId) {
        try {
            Object metadataFlow = getField(service, "currentMediaMetadata");
            Object metadata = metadataFlow == null ? null : call(metadataFlow, "getValue");
            if (metadata == null) return;
            String metadataId = string(read(metadata, "id", "getId"));
            if (metadataId.length() != 0 && !expectedTrackId.equals(metadataId)) return;

            final Object helper = getField(service, "lyricsHelper");
            if (helper == null) return;
            final Class<?> continuationClass = Class.forName("kotlin.coroutines.Continuation", false, targetClassLoader);
            Class<?> emptyContextClass = Class.forName("kotlin.coroutines.EmptyCoroutineContext", false, targetClassLoader);
            final Object emptyContext = XposedHelpers.getStaticObjectField(emptyContextClass, "INSTANCE");

            Object continuation = Proxy.newProxyInstance(
                    targetClassLoader,
                    new Class<?>[]{continuationClass},
                    new InvocationHandler() {
                        @Override
                        public Object invoke(Object proxy, Method method, Object[] args) {
                            String name = method.getName();
                            if ("getContext".equals(name)) return emptyContext;
                            if ("resumeWith".equals(name)) {
                                if (args != null && args.length != 0) handleFetchResult(expectedTrackId, args[0]);
                                return null;
                            }
                            if ("toString".equals(name)) return "MetroLyricsContinuation";
                            if ("hashCode".equals(name)) return System.identityHashCode(proxy);
                            if ("equals".equals(name)) return args != null && args.length == 1 && proxy == args[0];
                            return null;
                        }
                    }
            );

            Method target = null;
            for (Method method : helper.getClass().getMethods()) {
                if ("getLyrics".equals(method.getName()) && method.getParameterTypes().length == 2) {
                    target = method;
                    break;
                }
            }
            if (target == null) {
                log("LyricsHelper.getLyrics continuation method not found");
                return;
            }
            target.setAccessible(true);
            Object immediate = target.invoke(helper, metadata, continuation);
            Object suspended = coroutineSuspendedMarker();
            if (immediate != null && immediate != suspended) handleFetchResult(expectedTrackId, immediate);
            log("requested lyrics through MetroList LyricsHelper");
        } catch (Throwable error) {
            Throwable cause = error.getCause() == null ? error : error.getCause();
            log("LyricsHelper request failed: " + cause);
        }
    }

    private Object coroutineSuspendedMarker() {
        try {
            Class<?> intrinsics = Class.forName("kotlin.coroutines.intrinsics.IntrinsicsKt", false, targetClassLoader);
            return XposedHelpers.callStaticMethod(intrinsics, "getCOROUTINE_SUSPENDED");
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void handleFetchResult(String expectedTrackId, Object result) {
        if (result == null || stopped || !expectedTrackId.equals(currentTrackId)) return;
        try {
            if (result.getClass().getName().contains("Result$Failure")) {
                log("MetroList lyrics fetch returned failure: " + result);
                return;
            }
            String raw = string(read(result, "lyrics", "getLyrics"));
            String provider = string(read(result, "provider", "getProvider"));
            if (validLyrics(raw)) acceptLyrics(expectedTrackId, raw, provider, lyricsOffsetMs);
        } catch (Throwable error) {
            log("could not unwrap lyrics result: " + error);
        }
    }

    private boolean acceptLyrics(String trackId, String rawLyrics, String provider, int offsetMs) {
        if (!trackId.equals(currentTrackId) || !validLyrics(rawLyrics)) return false;
        List<LyricLine> fallback = parseFallback(rawLyrics);
        List<LyricLine> parsed = hasKaraokeTimings(fallback)
                ? fallback : parseUsingMetroList(rawLyrics);
        if (parsed.isEmpty()) parsed = fallback;
        if (parsed.isEmpty()) {
            lyricsTrackId = trackId;
            lyricProvider = provider == null ? "" : provider;
            lyricsOffsetMs = offsetMs;
            handler.post(new Runnable() {
                @Override public void run() {
                    if (trackId.equals(currentTrackId)) {
                        boolean playing = isPlayerPlaying();
                        long[] timeline = playerTimeline();
                        sendSnapshot("", "Текст найден, но без таймкодов", "", "нет синхронизации", lyricProvider,
                                playing, timeline[0], timeline[1], true);
                    }
                }
            });
            return true;
        }

        lyricLines = Collections.unmodifiableList(mergeSameTime(parsed));
        lyricsTrackId = trackId;
        lyricProvider = provider == null ? "" : provider.trim();
        lyricsOffsetMs = offsetMs;
        log("loaded " + lyricLines.size() + " timed lines for " + trackId + " from " + lyricProvider);
        handler.removeCallbacks(this);
        handler.post(this);
        return true;
    }

    private List<LyricLine> parseUsingMetroList(String rawLyrics) {
        List<LyricLine> result = new ArrayList<LyricLine>();
        try {
            Class<?> utilsClass = Class.forName("com.metrolist.music.lyrics.LyricsUtils", false, targetClassLoader);
            Object instance = XposedHelpers.getStaticObjectField(utilsClass, "INSTANCE");
            Object parsed = XposedHelpers.callMethod(instance, "parseLyrics", rawLyrics);
            if (parsed instanceof Iterable) {
                for (Object entry : (Iterable<?>) parsed) {
                    long time = number(read(entry, "time", "getTime"), -1L);
                    String text = LyricsParser.normalizeText(string(read(entry, "text", "getText")));
                    boolean background = bool(read(entry, "isBackground", "isBackground", "getBackground"), false);
                    if (time >= 0L && text.length() != 0) result.add(new LyricLine(time, text, background));
                }
            }
        } catch (Throwable error) {
            log("MetroList parser unavailable, using internal parser: " + error);
        }
        Collections.sort(result);
        return result;
    }

    private List<LyricLine> parseFallback(String rawLyrics) {
        return LyricsParser.parse(rawLyrics);
    }

    private static boolean hasKaraokeTimings(List<LyricLine> lines) {
        for (LyricLine line : lines) {
            if (!line.tokens.isEmpty()) return true;
        }
        return false;
    }

    private List<LyricLine> mergeSameTime(List<LyricLine> input) {
        Collections.sort(input, new Comparator<LyricLine>() {
            @Override public int compare(LyricLine a, LyricLine b) { return a.compareTo(b); }
        });
        List<LyricLine> merged = new ArrayList<LyricLine>();
        for (LyricLine line : input) {
            if (line.text.length() == 0) continue;
            if (!merged.isEmpty()) {
                LyricLine last = merged.get(merged.size() - 1);
                if (last.timeMs == line.timeMs) {
                    if (last.text.equals(line.text)) {
                        if (last.tokens.isEmpty() && !line.tokens.isEmpty()) {
                            merged.set(merged.size() - 1,
                                    new LyricLine(last.timeMs, last.text,
                                            last.background && line.background, line.tokens));
                        }
                    } else {
                        // Keep timings from the leading/main line. Timings belonging to
                        // an appended simultaneous line may overlap and need shifted
                        // ranges, so that appended part intentionally stays plain.
                        merged.set(merged.size() - 1,
                                new LyricLine(last.timeMs, last.text + "  ·  " + line.text,
                                        last.background && line.background, last.tokens));
                    }
                    continue;
                }
            }
            merged.add(line);
        }
        return merged;
    }

    private long emitCurrentLine(long positionMs, long durationMs, boolean playing, boolean force) {
        List<LyricLine> lines = lyricLines;
        if (lines.isEmpty()) return playing ? 220L : 700L;
        long effectivePosition = positionMs + lyricsOffsetMs + 100L;
        int low = 0;
        int high = lines.size() - 1;
        int index = -1;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            if (lines.get(middle).timeMs <= effectivePosition) {
                index = middle;
                low = middle + 1;
            } else {
                high = middle - 1;
            }
        }
        String previous = index > 0 ? lines.get(index - 1).text : "";
        LyricLine currentLine = index >= 0 ? lines.get(index) : null;
        String current = currentLine == null ? "♪" : currentLine.text;
        String next = index + 1 < lines.size() ? lines.get(index + 1).text : "";
        String status = playing ? "играет" : "пауза";
        if (lyricProvider.length() != 0) status += " · " + lyricProvider;

        KaraokeFrame frame = KaraokeFrame.at(currentLine, effectivePosition);
        sendSnapshot(previous, current, next, status, lyricProvider, playing,
                positionMs, durationMs, frame.highlightEnd, frame.activeStart, frame.activeEnd, force);

        if (!playing) return 700L;
        long nextBoundary = frame.nextBoundaryMs;
        if (index + 1 < lines.size()) {
            long nextLineTime = lines.get(index + 1).timeMs;
            if (nextLineTime > effectivePosition) nextBoundary = Math.min(nextBoundary, nextLineTime);
        }
        if (nextBoundary == Long.MAX_VALUE) return 220L;
        long untilBoundary = nextBoundary - effectivePosition;
        return Math.max(35L, Math.min(220L, untilBoundary));
    }

    // Periodic snapshot for tracks without synced lyrics: keeps the progress bar
    // and status fresh roughly once per second while text stays unchanged.
    private void emitHeartbeat(long positionMs, long durationMs, boolean playing) {
        String status = playing ? "играет" : "пауза";
        if (lyricProvider.length() != 0) status += " · " + lyricProvider;
        sendSnapshot(currentPrevious, currentLineText, currentNextText, status, lyricProvider,
                playing, positionMs, durationMs, false);
    }

    private void sendIdle(String status) {
        sendSnapshot("", "Музыка не играет", "", status, "", false, 0L, 0L, false);
    }

    private void sendSnapshot(
            String previous,
            String current,
            String next,
            String status,
            String provider,
            boolean playing,
            long position,
            long duration,
            boolean force
    ) {
        sendSnapshot(previous, current, next, status, provider, playing, position, duration,
                -1, -1, -1, force);
    }

    private void sendSnapshot(
            String previous,
            String current,
            String next,
            String status,
            String provider,
            boolean playing,
            long position,
            long duration,
            int highlightEnd,
            int activeStart,
            int activeEnd,
            boolean force
    ) {
        // Position must not be fully deduplicated: the widget progress bar is driven
        // by these snapshots, so the payload carries a one-second quantized position
        // to broadcast roughly once per second while everything else stays unchanged.
        String payload = currentTrackId + '\u0001' + currentTitle + '\u0001' + currentArtist + '\u0001' + currentArtwork + '\u0001' +
                previous + '\u0001' + current + '\u0001' + next + '\u0001' + status + '\u0001' + provider + '\u0001' + playing +
                '\u0001' + highlightEnd + '\u0001' + activeStart + '\u0001' + activeEnd +
                '\u0001' + (position / 1000L);
        if (!force && payload.equals(lastPayload)) return;
        lastPayload = payload;
        currentPrevious = previous;
        currentLineText = current;
        currentNextText = next;
        try {
            Intent intent = new Intent(Constants.ACTION_UPDATE);
            intent.setComponent(new ComponentName(
                    Constants.MODULE_PACKAGE,
                    Constants.MODULE_PACKAGE + ".LyricsWidgetProvider"
            ));
            intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            intent.putExtra(Constants.EXTRA_TRACK_ID, currentTrackId);
            intent.putExtra(Constants.EXTRA_TITLE, currentTitle);
            intent.putExtra(Constants.EXTRA_ARTIST, currentArtist);
            intent.putExtra(Constants.EXTRA_ARTWORK, currentArtwork);
            intent.putExtra(Constants.EXTRA_PREVIOUS, previous);
            intent.putExtra(Constants.EXTRA_CURRENT, current);
            intent.putExtra(Constants.EXTRA_NEXT, next);
            intent.putExtra(Constants.EXTRA_STATUS, status);
            intent.putExtra(Constants.EXTRA_PROVIDER, provider);
            intent.putExtra(Constants.EXTRA_PLAYING, playing);
            intent.putExtra(Constants.EXTRA_POSITION, position);
            intent.putExtra(Constants.EXTRA_DURATION, duration);
            intent.putExtra(Constants.EXTRA_HIGHLIGHT_END, highlightEnd);
            intent.putExtra(Constants.EXTRA_ACTIVE_START, activeStart);
            intent.putExtra(Constants.EXTRA_ACTIVE_END, activeEnd);
            context.sendBroadcast(intent);
        } catch (Throwable error) {
            log("widget broadcast failed: " + error);
        }
    }

    private TrackInfo readTrackInfo(Object mediaItem, String fallbackId) {
        String title = "";
        String artist = "";
        String artwork = "";
        try {
            Object flow = getField(service, "currentMediaMetadata");
            Object metadata = flow == null ? null : call(flow, "getValue");
            if (metadata != null) {
                String id = string(read(metadata, "id", "getId"));
                if (id.length() == 0 || fallbackId.equals(id)) {
                    title = string(read(metadata, "title", "getTitle"));
                    Object artists = read(metadata, "artists", "getArtists");
                    artist = artistNames(artists);
                    artwork = string(read(metadata, "thumbnailUrl", "getThumbnailUrl"));
                    if (artwork.length() == 0) artwork = string(read(metadata, "artworkUrl", "getArtworkUrl"));
                }
            }
        } catch (Throwable ignored) {}

        try {
            Object metadata = read(mediaItem, "mediaMetadata", "getMediaMetadata");
            if (title.length() == 0) title = string(read(metadata, "title", "getTitle"));
            if (artist.length() == 0) artist = string(read(metadata, "artist", "getArtist"));
            if (artwork.length() == 0) artwork = string(read(metadata, "artworkUri", "getArtworkUri"));
        } catch (Throwable ignored) {}

        title = oneLine(title);
        artist = oneLine(artist);
        artwork = oneLine(artwork);
        return new TrackInfo(title, artist, artwork);
    }

    private String artistNames(Object value) {
        if (!(value instanceof Iterable)) return string(value);
        StringBuilder out = new StringBuilder();
        for (Object artist : (Iterable<?>) value) {
            String name = string(read(artist, "name", "getName"));
            if (name.length() == 0) name = string(artist);
            if (name.length() == 0) continue;
            if (out.length() != 0) out.append(", ");
            out.append(name);
            if (out.length() > 80) break;
        }
        return out.toString();
    }

    private String mediaItemId(Object mediaItem) {
        if (mediaItem == null) return "";
        Object value = read(mediaItem, "mediaId", "getMediaId");
        return oneLine(string(value));
    }

    private boolean isPlayerPlaying() {
        try {
            Object player = getField(service, "player");
            return bool(call(player, "isPlaying"), false);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private Object getField(Object object, String fieldName) {
        if (object == null) return null;
        try {
            return XposedHelpers.getObjectField(object, fieldName);
        } catch (Throwable ignored) {
            return call(object, "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1));
        }
    }

    private Object read(Object object, String fieldName, String... methodNames) {
        if (object == null) return null;
        try {
            return XposedHelpers.getObjectField(object, fieldName);
        } catch (Throwable ignored) {}
        for (String methodName : methodNames) {
            Object value = call(object, methodName);
            if (value != null) return value;
        }
        return null;
    }

    private Object call(Object object, String methodName) {
        if (object == null) return null;
        try {
            return XposedHelpers.callMethod(object, methodName);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static long number(Object value, long fallback) {
        return value instanceof Number ? ((Number) value).longValue() : fallback;
    }

    private static boolean bool(Object value, boolean fallback) {
        return value instanceof Boolean ? ((Boolean) value).booleanValue() : fallback;
    }

    private static String string(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String oneLine(String value) {
        return value == null ? "" : value.replace('\n', ' ').replace('\r', ' ').trim();
    }

    private static boolean validLyrics(String value) {
        return value != null && value.trim().length() != 0 && !"LYRICS_NOT_FOUND".equals(value.trim());
    }

    private static void log(String message) {
        XposedBridge.log("[MetroLyrics] " + message);
    }

    private static final class KaraokeFrame {
        final int highlightEnd;
        final int activeStart;
        final int activeEnd;
        final long nextBoundaryMs;

        KaraokeFrame(int highlightEnd, int activeStart, int activeEnd, long nextBoundaryMs) {
            this.highlightEnd = highlightEnd;
            this.activeStart = activeStart;
            this.activeEnd = activeEnd;
            this.nextBoundaryMs = nextBoundaryMs;
        }

        static KaraokeFrame at(LyricLine line, long positionMs) {
            if (line == null || line.tokens.isEmpty()) {
                return new KaraokeFrame(-1, -1, -1, Long.MAX_VALUE);
            }
            int highlightEnd = -1;
            int activeStart = -1;
            int activeEnd = -1;
            long nextBoundary = Long.MAX_VALUE;
            for (LyricToken token : line.tokens) {
                if (token.startMs <= positionMs) {
                    if (token.hasTextRange()) highlightEnd = Math.max(highlightEnd, token.endChar);
                    if (positionMs < token.endMs && token.hasTextRange()) {
                        activeStart = token.startChar;
                        activeEnd = token.endChar;
                    }
                } else {
                    nextBoundary = Math.min(nextBoundary, token.startMs);
                }
                if (token.endMs > positionMs) nextBoundary = Math.min(nextBoundary, token.endMs);
            }
            return new KaraokeFrame(highlightEnd, activeStart, activeEnd, nextBoundary);
        }
    }

    private static final class TrackInfo {
        final String title;
        final String artist;
        final String artwork;
        TrackInfo(String title, String artist, String artwork) {
            this.title = title == null ? "" : title;
            this.artist = artist == null ? "" : artist;
            this.artwork = artwork == null ? "" : artwork;
        }
    }

    private static final class DbResult {
        final String lyrics;
        final String provider;
        final int offsetMs;
        final boolean offsetKnown;
        DbResult(String lyrics, String provider, int offsetMs, boolean offsetKnown) {
            this.lyrics = lyrics;
            this.provider = provider == null ? "" : provider;
            this.offsetMs = offsetMs;
            this.offsetKnown = offsetKnown;
        }
    }
}