package com.example.mymusic.lyrics;

import android.content.Context;
import android.net.Uri;

import androidx.media3.common.MediaItem;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

/** Embedded lyrics take precedence; online lyrics are a cached fallback. Call off the UI thread. */
public final class LyricsRepository {
    private static final String USER_AGENT = "MyMusic/1.0 (https://github.com/yseho031018/MyMusic)";
    private static final long MAX_RESPONSE_BYTES = 1_000_000;
    private final Context context;
    private static final String RATE_LIMIT_PREFS = "lyrics_rate_limit";

    public LyricsRepository(Context context) {
        this.context = context.getApplicationContext();
    }

    public void clearOnlineCache(MediaItem item) {
        if (item == null) return;
        String title = item.mediaMetadata.title == null ? "" : item.mediaMetadata.title.toString().trim();
        String artist = item.mediaMetadata.artist == null ? "" : item.mediaMetadata.artist.toString().trim();
        if (!title.isEmpty()) cacheFile(title, artist).delete();
    }

    public Lyrics load(MediaItem item, long durationMs, boolean embeddedOnly) throws IOException {
        Lyrics embedded;
        try {
            embedded = readEmbedded(item);
        } catch (IOException ignored) {
            // The server may be offline while a local queue is still available.
            embedded = empty();
        }
        if (embedded.hasLyrics() || embeddedOnly) return embedded;

        String title = item.mediaMetadata.title == null ? "" : item.mediaMetadata.title.toString().trim();
        String artist = item.mediaMetadata.artist == null ? "" : item.mediaMetadata.artist.toString().trim();
        if (title.isEmpty()) return empty();
        File cache = cacheFile(title, artist);
        if (cache.isFile()) {
            try (InputStream stream = new FileInputStream(cache)) {
                JSONObject cached = new JSONObject(new String(readUpTo(stream, MAX_RESPONSE_BYTES), StandardCharsets.UTF_8));
                long cachedDuration = cached.optLong("duration", 0);
                if (durationMs > 0 && cachedDuration > 0
                        && Math.abs(Math.round(durationMs / 1000d) - cachedDuration) > 10) throw new IOException("다른 길이의 곡");
                Lyrics saved = parseOnline(cached);
                if (saved.hasLyrics()) return saved;
            } catch (Exception ignored) {
                // Broken cache is replaced by the next successful lookup.
            }
        }
        long retryAfterEpochMs = context.getSharedPreferences(RATE_LIMIT_PREFS, Context.MODE_PRIVATE)
                .getLong("retry_after", 0);
        if (System.currentTimeMillis() < retryAfterEpochMs) throw new IOException("온라인 가사 서비스 요청 제한 중");

        JSONObject response = fetch(title, artist, durationMs);
        if (response == null) return empty();
        Lyrics result = parseOnline(response);
        if (result.hasLyrics()) {
            File parent = cache.getParentFile();
            if (parent != null && (parent.isDirectory() || parent.mkdirs())) {
                try (FileOutputStream output = new FileOutputStream(cache)) {
                    output.write(response.toString().getBytes(StandardCharsets.UTF_8));
                } catch (IOException ignored) {}
            }
        }
        return result;
    }

    private Lyrics readEmbedded(MediaItem item) throws IOException {
        if (item.localConfiguration == null) return empty();
        Uri uri = item.localConfiguration.uri;
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            try (InputStream stream = new FileInputStream(new File(uri.getPath()))) {
                return Id3LyricsReader.read(stream);
            }
        }
        if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) return empty();
        HttpURLConnection connection = (HttpURLConnection) new URL(uri.toString()).openConnection();
        connection.setRequestProperty("Range", "bytes=0-" + (Id3LyricsReader.MAX_TAG_BYTES + 9));
        connection.setConnectTimeout(3000);
        connection.setReadTimeout(4000);
        try {
            int status = connection.getResponseCode();
            if (status != 200 && status != 206) return empty();
            try (InputStream stream = connection.getInputStream()) {
                return Id3LyricsReader.read(stream);
            }
        } finally {
            connection.disconnect();
        }
    }

    private JSONObject fetch(String title, String artist, long durationMs) throws IOException {
        String parameters = "track_name=" + encode(title);
        if (!artist.isEmpty() && !artist.equalsIgnoreCase("Unknown Artist")) {
            parameters += "&artist_name=" + encode(artist);
            if (durationMs >= 1000 && durationMs <= 3_600_000) parameters += "&duration=" + Math.round(durationMs / 1000d);
            JSONObject exact = request("https://lrclib.net/api/get?" + parameters);
            if (exact != null) return exact;
        }
        // Search only by title when metadata lacks an artist or an exact duration match.
        JSONObject search = request("https://lrclib.net/api/search?track_name=" + encode(title)
                + (!artist.isEmpty() && !artist.equalsIgnoreCase("Unknown Artist") ? "&artist_name=" + encode(artist) : ""));
        if (search == null) return null;
        org.json.JSONArray results = search.optJSONArray("results");
        if (results == null) return null;
        JSONObject best = null;
        long bestDifference = Long.MAX_VALUE;
        int plausibleMatches = 0;
        for (int i = 0; i < results.length(); i++) {
            JSONObject candidate = results.optJSONObject(i);
            if (candidate == null || !title.equalsIgnoreCase(candidate.optString("trackName").trim())) continue;
            if (!artist.isEmpty() && !artist.equalsIgnoreCase("Unknown Artist")
                    && !artist.equalsIgnoreCase(candidate.optString("artistName").trim())) continue;
            long difference = durationMs > 0 ? Math.abs(Math.round(durationMs / 1000d) - candidate.optLong("duration")) : 0;
            if (durationMs > 0 && difference > 10) continue;
            plausibleMatches++;
            if (difference < bestDifference) {
                bestDifference = difference;
                best = candidate;
            }
        }
        if ((artist.isEmpty() || artist.equalsIgnoreCase("Unknown Artist")) && durationMs <= 0
                && plausibleMatches > 1) return null;
        return best;
    }

    private JSONObject request(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setConnectTimeout(4000);
        connection.setReadTimeout(4000);
        try {
            int status = connection.getResponseCode();
            if (status == 404) return null;
            if (status == 429) {
                long seconds = 60;
                try { seconds = Long.parseLong(connection.getHeaderField("Retry-After")); } catch (Exception ignored) {}
                long retryAfterEpochMs = System.currentTimeMillis() + Math.max(1, seconds) * 1000;
                context.getSharedPreferences(RATE_LIMIT_PREFS, Context.MODE_PRIVATE).edit()
                        .putLong("retry_after", retryAfterEpochMs).apply();
                throw new IOException("온라인 가사 서비스 요청 제한 중");
            }
            if (status != 200) throw new IOException("온라인 가사 조회 실패 (" + status + ")");
            try (InputStream stream = connection.getInputStream()) {
                byte[] body = readUpTo(stream, MAX_RESPONSE_BYTES);
                if (body.length >= MAX_RESPONSE_BYTES) throw new IOException("온라인 가사 응답이 너무 큽니다");
                if (url.contains("/search?")) {
                    JSONObject wrapped = new JSONObject();
                    wrapped.put("results", new org.json.JSONArray(new String(body, StandardCharsets.UTF_8)));
                    return wrapped;
                }
                return new JSONObject(new String(body, StandardCharsets.UTF_8));
            } catch (org.json.JSONException e) {
                throw new IOException("온라인 가사 응답을 읽을 수 없습니다", e);
            }
        } finally {
            connection.disconnect();
        }
    }

    private static Lyrics parseOnline(JSONObject response) {
        String synced = response.optString("syncedLyrics", "");
        String plain = response.optString("plainLyrics", "");
        Lyrics timed = Lyrics.fromText(synced, "LRCLIB 온라인 가사");
        if (timed.isSynced()) return new Lyrics(plain.isEmpty() ? timed.plainText : plain, timed.timedLines, timed.source);
        return Lyrics.fromText(plain, "LRCLIB 온라인 가사");
    }

    private File cacheFile(String title, String artist) {
        String key = (title + "\n" + artist).toLowerCase(java.util.Locale.ROOT);
        return new File(new File(context.getFilesDir(), "lyrics"), Integer.toHexString(key.hashCode()) + ".json");
    }

    private static byte[] readUpTo(InputStream stream, long maximum) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        while (output.size() < maximum) {
            int read = stream.read(buffer, 0, (int) Math.min(buffer.length, maximum - output.size()));
            if (read < 0) break;
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static String encode(String value) throws IOException {
        return URLEncoder.encode(value, "UTF-8");
    }

    private static Lyrics empty() {
        return new Lyrics("", Collections.emptyList(), "");
    }
}
