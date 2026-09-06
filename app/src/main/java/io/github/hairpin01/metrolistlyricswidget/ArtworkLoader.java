package io.github.hairpin01.metrolistlyricswidget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class ArtworkLoader {
    // Keeps RemoteViews safely below Android's Binder transaction limit.
    private static final int DEFAULT_BACKGROUND_WIDTH = 480;
    private static final int DEFAULT_BACKGROUND_HEIGHT = 270;
    private static final int MAX_BACKGROUND_PIXELS = DEFAULT_BACKGROUND_WIDTH * DEFAULT_BACKGROUND_HEIGHT;
    private static final int MAX_BACKGROUND_EDGE = 720;
    private static final float WIDGET_CORNER_RADIUS_DP = 26f;
    private static final int COVER_SIZE = 240;
    // Matches the 12dp/52dp ratio used by widget_cover_placeholder.xml so the
    // real artwork and the placeholder read as the same rounded shape.
    private static final float COVER_CORNER_RADIUS = 56f;
    private static final int MAX_DOWNLOAD = 6 * 1024 * 1024;
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2);
    private static final Set<String> RUNNING = Collections.synchronizedSet(new HashSet<String>());
    private static final Map<String, Long> RETRY_AFTER = Collections.synchronizedMap(new HashMap<String, Long>());

    private static String artworkMemoryKey = "";
    private static Bitmap artworkMemoryBitmap;
    private static String coverMemoryKey = "";
    private static Bitmap coverMemoryBitmap;
    private static String customMemoryKey = "";
    private static Bitmap customMemoryBitmap;

    private ArtworkLoader() {}

    static Bitmap background(Context context, int mode, String trackId, String artworkUrl,
                             int widgetWidthDp, int widgetHeightDp) {
        final int[] target = backgroundSize(widgetWidthDp, widgetHeightDp);
        final float[] radii = backgroundCornerRadii(
                widgetWidthDp, widgetHeightDp, target[0], target[1]);
        if (mode == WidgetSettings.BACKGROUND_IMAGE) {
            return customImage(context, WidgetSettings.imageUri(context),
                    target[0], target[1], radii[0], radii[1]);
        }
        if (mode == WidgetSettings.BACKGROUND_ARTWORK) {
            String key = cacheKey(trackId, artworkUrl);
            String renderedKey = key + "@" + target[0] + "x" + target[1]
                    + ":r" + Math.round(radii[0]) + "x" + Math.round(radii[1]);
            synchronized (ArtworkLoader.class) {
                if (renderedKey.equals(artworkMemoryKey) && artworkMemoryBitmap != null
                        && !artworkMemoryBitmap.isRecycled()) return artworkMemoryBitmap;
            }
            File file = artworkFile(context, key);
            if (file != null && file.isFile()) {
                Bitmap bitmap = decode(file, new Crop() {
                    @Override public Bitmap apply(Bitmap source) {
                        return crop(source, target[0], target[1], radii[0], radii[1]);
                    }
                });
                if (bitmap != null) {
                    synchronized (ArtworkLoader.class) {
                        artworkMemoryKey = renderedKey;
                        artworkMemoryBitmap = bitmap;
                    }
                }
                return bitmap;
            }
        }
        return null;
    }

    // Square cover art shown in the widget next to the lyrics. Uses the same
    // downloaded artwork cache as the background mode but renders a square crop.
    static Bitmap cover(Context context, String trackId, String artworkUrl) {
        String key = cacheKey(trackId, artworkUrl);
        if (key.length() == 0) return null;
        synchronized (ArtworkLoader.class) {
            if (key.equals(coverMemoryKey) && coverMemoryBitmap != null
                    && !coverMemoryBitmap.isRecycled()) return coverMemoryBitmap;
        }
        File file = artworkFile(context, key);
        if (file == null || !file.isFile()) return null;
        Bitmap square = decode(file, new Crop() {
            @Override public Bitmap apply(Bitmap source) { return cropSquare(source); }
        });
        if (square != null) {
            synchronized (ArtworkLoader.class) {
                coverMemoryKey = key;
                coverMemoryBitmap = square;
            }
        }
        return square;
    }

    private interface Crop {
        Bitmap apply(Bitmap source);
    }

    private static Bitmap decode(File file, Crop crop) {
        InputStream input = null;
        try {
            input = new FileInputStream(file);
            Bitmap source = BitmapFactory.decodeStream(input);
            Bitmap result = crop.apply(source);
            if (source != null && source != result) source.recycle();
            return result;
        } catch (Throwable ignored) {
            return null;
        } finally {
            close(input);
        }
    }

    private static Bitmap cropSquare(Bitmap source) {
        if (source == null || source.getWidth() <= 0 || source.getHeight() <= 0) return null;
        int side = Math.min(source.getWidth(), source.getHeight());
        int left = (source.getWidth() - side) / 2;
        int top = (source.getHeight() - side) / 2;
        Bitmap center = Bitmap.createBitmap(source, left, top, side, side);
        Bitmap scaled = center.getWidth() == COVER_SIZE && center.getHeight() == COVER_SIZE
                ? center
                : Bitmap.createScaledBitmap(center, COVER_SIZE, COVER_SIZE, true);
        if (scaled != center) center.recycle();
        Bitmap rounded = roundCorners(scaled, COVER_CORNER_RADIUS);
        if (rounded != scaled) scaled.recycle();
        return rounded;
    }

    // Clips a bitmap to rounded-rect corners. Needed because RemoteViews sets the
    // cover via setImageViewBitmap (plain ImageView), so the shape has to be baked
    // into the pixels themselves rather than relying on a view-level outline/clip.
    private static Bitmap roundCorners(Bitmap source, float radius) {
        if (source == null) return null;
        int width = source.getWidth();
        int height = source.getHeight();
        Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);
        Path path = new Path();
        path.addRoundRect(new RectF(0f, 0f, width, height), radius, radius, Path.Direction.CW);
        canvas.clipPath(path);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(source, 0f, 0f, paint);
        return result;
    }

    static void ensureAsync(Context context, String trackId, String artworkUrl, Runnable finished) {
        final Context app = context.getApplicationContext();
        final String key = cacheKey(trackId, artworkUrl);
        final String url = usableUrl(artworkUrl, trackId);
        final File target = artworkFile(app, key);
        if (key.length() == 0 || url.length() == 0 || target == null || target.isFile()) return;

        Long retryAt = RETRY_AFTER.get(key);
        if (retryAt != null && System.currentTimeMillis() < retryAt.longValue()) return;
        if (!RUNNING.add(key)) return;

        EXECUTOR.execute(new Runnable() {
            @Override public void run() {
                boolean success = false;
                try {
                    download(url, target);
                    success = target.isFile();
                } catch (Throwable ignored) {
                } finally {
                    RUNNING.remove(key);
                    if (success) {
                        RETRY_AFTER.remove(key);
                        if (finished != null) finished.run();
                    } else {
                        RETRY_AFTER.put(key, System.currentTimeMillis() + 5L * 60L * 1000L);
                    }
                }
            }
        });
    }

    private static Bitmap customImage(Context context, String uriString, int width, int height,
                                      float radiusX, float radiusY) {
        if (uriString == null || uriString.length() == 0) return null;
        String renderedKey = uriString + "@" + width + "x" + height
                + ":r" + Math.round(radiusX) + "x" + Math.round(radiusY);
        synchronized (ArtworkLoader.class) {
            if (renderedKey.equals(customMemoryKey) && customMemoryBitmap != null
                    && !customMemoryBitmap.isRecycled()) {
                return customMemoryBitmap;
            }
        }
        InputStream input = null;
        try {
            input = context.getContentResolver().openInputStream(Uri.parse(uriString));
            Bitmap source = BitmapFactory.decodeStream(input);
            Bitmap result = crop(source, width, height, radiusX, radiusY);
            if (source != null && source != result) source.recycle();
            synchronized (ArtworkLoader.class) {
                customMemoryKey = renderedKey;
                customMemoryBitmap = result;
            }
            return result;
        } catch (Throwable ignored) {
            return null;
        } finally {
            close(input);
        }
    }

    private static int[] backgroundSize(int widgetWidthDp, int widgetHeightDp) {
        if (widgetWidthDp <= 0 || widgetHeightDp <= 0) {
            return new int[]{DEFAULT_BACKGROUND_WIDTH, DEFAULT_BACKGROUND_HEIGHT};
        }
        float ratio = widgetWidthDp / (float) widgetHeightDp;
        ratio = Math.max(0.35f, Math.min(8f, ratio));
        int width = Math.max(1, Math.round((float) Math.sqrt(MAX_BACKGROUND_PIXELS * ratio)));
        int height = Math.max(1, Math.round((float) Math.sqrt(MAX_BACKGROUND_PIXELS / ratio)));
        if (width > MAX_BACKGROUND_EDGE || height > MAX_BACKGROUND_EDGE) {
            float scale = Math.min(MAX_BACKGROUND_EDGE / (float) width,
                    MAX_BACKGROUND_EDGE / (float) height);
            width = Math.max(1, Math.round(width * scale));
            height = Math.max(1, Math.round(height * scale));
        }
        return new int[]{width, height};
    }
    private static float[] backgroundCornerRadii(int widgetWidthDp, int widgetHeightDp,
                                                  int bitmapWidth, int bitmapHeight) {
        // The panel and scrim use 26dp. Bake that exact radius into the bitmap,
        // compensating for the bitmap-to-widget scale on each axis.
        int widthDp = widgetWidthDp > 0 ? widgetWidthDp : 250;
        int heightDp = widgetHeightDp > 0 ? widgetHeightDp : 110;
        return new float[]{
                WIDGET_CORNER_RADIUS_DP * bitmapWidth / widthDp,
                WIDGET_CORNER_RADIUS_DP * bitmapHeight / heightDp
        };
    }
    private static Bitmap crop(Bitmap source, int width, int height,
                               float radiusX, float radiusY) {
        if (source == null || source.getWidth() <= 0 || source.getHeight() <= 0) return null;
        Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);
        Path rounded = new Path();
        rounded.addRoundRect(new RectF(0f, 0f, width, height),
                radiusX, radiusY, Path.Direction.CW);
        canvas.clipPath(rounded);
        float scale = Math.max(width / (float) source.getWidth(), height / (float) source.getHeight());
        int drawWidth = Math.round(source.getWidth() * scale);
        int drawHeight = Math.round(source.getHeight() * scale);
        int left = (width - drawWidth) / 2;
        int top = (height - drawHeight) / 2;
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(source, null, new Rect(left, top, left + drawWidth, top + drawHeight), paint);
        return result;
    }

    private static void download(String address, File target) throws Exception {
        HttpURLConnection connection = null;
        InputStream input = null;
        ByteArrayOutputStream output = null;
        try {
            connection = (HttpURLConnection) new URL(address).openConnection();
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(10000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) MetroLyricsWidget/0.2");
            connection.connect();
            if (connection.getResponseCode() < 200 || connection.getResponseCode() >= 300) return;
            input = connection.getInputStream();
            output = new ByteArrayOutputStream();
            byte[] buffer = new byte[16384];
            int total = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > MAX_DOWNLOAD) return;
                output.write(buffer, 0, count);
            }
            byte[] bytes = output.toByteArray();
            Bitmap source = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (source == null) return;
            // Cap cached artwork so the disk cache stays compact; still plenty for
            // both the 480x270 background and the 240x240 cover crops.
            if (source.getWidth() > 960 || source.getHeight() > 960) {
                float scale = Math.min(960f / source.getWidth(), 960f / source.getHeight());
                Bitmap scaled = Bitmap.createScaledBitmap(
                        source,
                        Math.max(1, Math.round(source.getWidth() * scale)),
                        Math.max(1, Math.round(source.getHeight() * scale)),
                        true);
                source.recycle();
                source = scaled;
                if (source == null) return;
            }
            // Cache stores the untouched artwork so both the wide background crop
            // and the square cover crop can be produced from the same file.
            File dir = target.getParentFile();
            if (dir != null && !dir.exists()) dir.mkdirs();
            File temp = new File(target.getAbsolutePath() + ".tmp");
            FileOutputStream fileOutput = new FileOutputStream(temp);
            try {
                source.compress(Bitmap.CompressFormat.JPEG, 88, fileOutput);
            } finally {
                fileOutput.close();
                source.recycle();
            }
            if (!temp.renameTo(target)) {
                target.delete();
                temp.renameTo(target);
            }
            trimCache(dir, target);
        } finally {
            close(input);
            close(output);
            if (connection != null) connection.disconnect();
        }
    }

    private static void trimCache(File dir, File keep) {
        if (dir == null) return;
        File[] files = dir.listFiles();
        if (files == null || files.length <= 8) return;
        for (File file : files) {
            if (!file.equals(keep) && file.isFile()) file.delete();
        }
    }

    private static String usableUrl(String explicit, String trackId) {
        String value = explicit == null ? "" : explicit.trim();
        if (value.startsWith("https://") || value.startsWith("http://")) return value;
        String id = youtubeId(trackId);
        return id.length() == 0 ? "" : "https://i.ytimg.com/vi/" + id + "/hqdefault.jpg";
    }

    private static String youtubeId(String value) {
        if (value == null) return "";
        String id = value.trim();
        if (id.matches("[A-Za-z0-9_-]{11}")) return id;
        int query = id.indexOf("v=");
        if (query >= 0 && query + 13 <= id.length()) {
            String candidate = id.substring(query + 2, query + 13);
            if (candidate.matches("[A-Za-z0-9_-]{11}")) return candidate;
        }
        String[] pieces = id.split("[/?:=&]");
        for (String piece : pieces) {
            if (piece.matches("[A-Za-z0-9_-]{11}")) return piece;
        }
        return "";
    }

    private static String cacheKey(String trackId, String artworkUrl) {
        String url = artworkUrl == null ? "" : artworkUrl.trim();
        String id = trackId == null ? "" : trackId.trim();
        String basis = url.length() == 0 ? id : url;
        if (basis.length() == 0) return "";
        return Integer.toHexString(basis.hashCode()) + "_" + Integer.toHexString(basis.length());
    }

    private static File artworkFile(Context context, String key) {
        if (key == null || key.length() == 0) return null;
        return new File(new File(context.getCacheDir(), "artwork"), key + ".jpg");
    }

    private static void close(java.io.Closeable closeable) {
        if (closeable == null) return;
        try { closeable.close(); } catch (Throwable ignored) {}
    }
}