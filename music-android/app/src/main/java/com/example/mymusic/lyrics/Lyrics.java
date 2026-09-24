package com.example.mymusic.lyrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Lyrics {
    private static final Pattern TIMESTAMP = Pattern.compile("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?\\]");
    private static final Pattern OFFSET = Pattern.compile("(?i)^\\[offset:\\s*([+-]?\\d+)\\s*]$");
    private static final Pattern LRC_METADATA = Pattern.compile("(?i)^\\[(ar|al|ti|by|offset|length):.*]$");

    public static final class Line {
        public final long timeMs;
        public final String text;

        public Line(long timeMs, String text) {
            this.timeMs = timeMs;
            this.text = text;
        }
    }

    public final String plainText;
    public final List<Line> timedLines;
    public final String source;

    public Lyrics(String plainText, List<Line> timedLines, String source) {
        this.plainText = plainText == null ? "" : plainText.trim();
        this.timedLines = Collections.unmodifiableList(new ArrayList<>(timedLines));
        this.source = source;
    }

    public boolean hasLyrics() {
        return !plainText.isEmpty() || !timedLines.isEmpty();
    }

    public boolean isSynced() {
        return !timedLines.isEmpty();
    }

    public int lineAt(long positionMs) {
        int index = -1;
        for (int i = 0; i < timedLines.size(); i++) {
            if (timedLines.get(i).timeMs > positionMs) break;
            index = i;
        }
        return index;
    }

    public int lineAt(long positionMs, long advanceMs) {
        return lineAt(positionMs + advanceMs);
    }

    public static Lyrics fromText(String text, String source) {
        if (text == null || text.trim().isEmpty()) return new Lyrics("", Collections.emptyList(), source);
        List<Line> lines = new ArrayList<>();
        List<String> plain = new ArrayList<>();
        long offsetMs = 0;
        for (String raw : text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)) {
            Matcher offset = OFFSET.matcher(raw.trim());
            if (offset.matches()) {
                try { offsetMs = Math.max(-600_000, Math.min(600_000, Long.parseLong(offset.group(1)))); }
                catch (NumberFormatException ignored) { offsetMs = 0; }
                continue;
            }
            Matcher matcher = TIMESTAMP.matcher(raw);
            int end = 0;
            List<Long> times = new ArrayList<>();
            while (matcher.find(end) && matcher.start() == end) {
                long minutes = Long.parseLong(matcher.group(1));
                long seconds = Long.parseLong(matcher.group(2));
                String fraction = matcher.group(3);
                long millis = fraction == null ? 0 : Long.parseLong((fraction + "000").substring(0, 3));
                times.add((minutes * 60 + seconds) * 1000 + millis);
                end = matcher.end();
            }
            String words = raw.substring(end).trim();
            if (!times.isEmpty()) {
                for (long time : times) lines.add(new Line(time, words));
                if (!words.isEmpty()) plain.add(words);
            } else if (!LRC_METADATA.matcher(raw.trim()).matches()) {
                plain.add(raw);
            }
        }
        List<Line> adjusted = new ArrayList<>(lines.size());
        for (Line line : lines) {
            // In LRC, a positive offset advances lyrics; a negative value delays them.
            adjusted.add(new Line(Math.max(0, line.timeMs - offsetMs), line.text));
        }
        adjusted.sort((a, b) -> Long.compare(a.timeMs, b.timeMs));
        return new Lyrics(String.join("\n", plain).trim(), adjusted, source);
    }
}
