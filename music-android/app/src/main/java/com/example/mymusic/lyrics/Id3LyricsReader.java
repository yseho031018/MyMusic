package com.example.mymusic.lyrics;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Reads only the leading ID3v2 tag; audio data is never loaded into memory. */
public final class Id3LyricsReader {
    public static final int MAX_TAG_BYTES = 4 * 1024 * 1024;

    private Id3LyricsReader() {}

    public static Lyrics read(InputStream stream) throws IOException {
        byte[] header = readExact(stream, 10);
        if (header.length != 10 || header[0] != 'I' || header[1] != 'D' || header[2] != '3') return empty();
        int version = header[3] & 255;
        if (version < 2 || version > 4) return empty();
        int tagSize = syncSafe(header, 6);
        if (tagSize <= 0 || tagSize > MAX_TAG_BYTES) return empty();
        byte[] data = readExact(stream, tagSize);
        if (data.length != tagSize) return empty();
        boolean tagUnsynchronised = (header[5] & 0x80) != 0;

        int offset = 0;
        if ((header[5] & 0x40) != 0 && version > 2) {
            if (data.length < 4) return empty();
            int extended = version == 4 ? syncSafe(data, 0) : bigEndian(data, 0, 4) + 4;
            if (extended < 4 || extended > data.length) return empty();
            offset = extended;
        }

        Lyrics plain = empty();
        Lyrics synced = empty();
        int frameHeader = version == 2 ? 6 : 10;
        while (offset + frameHeader <= data.length) {
            int idSize = version == 2 ? 3 : 4;
            String id = new String(data, offset, idSize, StandardCharsets.ISO_8859_1);
            if (id.charAt(0) == 0) break;
            int length = version == 2 ? bigEndian(data, offset + 3, 3)
                    : version == 4 ? syncSafe(data, offset + 4) : bigEndian(data, offset + 4, 4);
            int flags = version == 2 ? 0 : data[offset + 9] & 255;
            int bodyStart = offset + frameHeader;
            if (length <= 0 || length > data.length - bodyStart) break;
            offset = bodyStart + length;
            // Compressed or encrypted frames cannot be interpreted as text.
            if ((version == 3 && (flags & 0xC0) != 0) || (version == 4 && (flags & 0x0C) != 0)) continue;
            byte[] frame = new byte[length];
            System.arraycopy(data, bodyStart, frame, 0, length);
            if (tagUnsynchronised || (version == 4 && (flags & 0x02) != 0)) {
                frame = removeUnsynchronization(frame);
            }
            if (version == 4 && (flags & 0x01) != 0 && frame.length >= 4) {
                byte[] withoutLength = new byte[frame.length - 4];
                System.arraycopy(frame, 4, withoutLength, 0, withoutLength.length);
                frame = withoutLength;
            }

            if (id.equals("USLT") || id.equals("ULT")) {
                Lyrics candidate = readUslt(frame);
                if (candidate.hasLyrics() && !plain.hasLyrics()) plain = candidate;
            } else if (id.equals("SYLT") || id.equals("SLT")) {
                Lyrics candidate = readSylt(frame);
                if (candidate.isSynced() && !synced.isSynced()) synced = candidate;
            } else if (id.equals("TXXX") || id.equals("TXX")) {
                Lyrics candidate = readUserText(frame);
                if (candidate.hasLyrics() && !plain.hasLyrics()) plain = candidate;
            }
        }
        return synced.isSynced() ? new Lyrics(plain.hasLyrics() ? plain.plainText : synced.plainText,
                synced.timedLines, "MP3 내장 가사") : plain;
    }

    private static Lyrics readUslt(byte[] frame) {
        if (frame.length < 5) return empty();
        int start = afterTerminator(frame, 4, frame[0] & 255);
        return start <= frame.length ? Lyrics.fromText(decode(frame, start, frame.length - start, frame[0] & 255), "MP3 내장 가사") : empty();
    }

    private static Lyrics readUserText(byte[] frame) {
        if (frame.length < 2) return empty();
        int start = afterTerminator(frame, 1, frame[0] & 255);
        if (start > frame.length) return empty();
        String description = decode(frame, 1, start - 1, frame[0] & 255).toLowerCase(Locale.ROOT);
        if (!description.contains("lyric") && !description.contains("가사")) return empty();
        return Lyrics.fromText(decode(frame, start, frame.length - start, frame[0] & 255), "MP3 내장 가사");
    }

    private static Lyrics readSylt(byte[] frame) {
        if (frame.length < 7 || frame[4] != 2) return empty(); // timestamp format 2 = milliseconds
        int encoding = frame[0] & 255;
        int position = afterTerminator(frame, 6, encoding);
        List<Lyrics.Line> lines = new ArrayList<>();
        while (position < frame.length) {
            int end = terminator(frame, position, encoding);
            int terminatorSize = encoding == 1 || encoding == 2 ? 2 : 1;
            if (end < 0 || end + terminatorSize + 4 > frame.length) break;
            String words = decode(frame, position, end - position, encoding).trim();
            long time = bigEndian(frame, end + terminatorSize, 4) & 0xFFFFFFFFL;
            if (!words.isEmpty()) lines.add(new Lyrics.Line(time, words));
            position = end + terminatorSize + 4;
        }
        lines.sort((a, b) -> Long.compare(a.timeMs, b.timeMs));
        List<String> words = new ArrayList<>();
        for (Lyrics.Line line : lines) words.add(line.text);
        return new Lyrics(String.join("\n", words), lines, "MP3 내장 가사");
    }

    private static int afterTerminator(byte[] data, int start, int encoding) {
        int end = terminator(data, start, encoding);
        return end < 0 ? data.length + 1 : end + ((encoding == 1 || encoding == 2) ? 2 : 1);
    }

    private static int terminator(byte[] data, int start, int encoding) {
        if (encoding == 1 || encoding == 2) {
            for (int i = start; i + 1 < data.length; i += 2) {
                if (data[i] == 0 && data[i + 1] == 0) return i;
            }
        } else {
            for (int i = start; i < data.length; i++) if (data[i] == 0) return i;
        }
        return -1;
    }

    private static String decode(byte[] data, int start, int length, int encoding) {
        if (length <= 0) return "";
        Charset charset = encoding == 1 ? StandardCharsets.UTF_16 : encoding == 2 ? StandardCharsets.UTF_16BE
                : encoding == 3 ? StandardCharsets.UTF_8 : StandardCharsets.ISO_8859_1;
        return new String(data, start, length, charset).replace("\u0000", "").trim();
    }

    private static int syncSafe(byte[] data, int offset) {
        return ((data[offset] & 127) << 21) | ((data[offset + 1] & 127) << 14)
                | ((data[offset + 2] & 127) << 7) | (data[offset + 3] & 127);
    }

    private static int bigEndian(byte[] data, int offset, int length) {
        int value = 0;
        for (int i = 0; i < length; i++) value = (value << 8) | (data[offset + i] & 255);
        return value;
    }

    private static byte[] readExact(InputStream stream, int count) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(count);
        byte[] buffer = new byte[Math.min(8192, count)];
        while (out.size() < count) {
            int read = stream.read(buffer, 0, Math.min(buffer.length, count - out.size()));
            if (read < 0) break;
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static byte[] removeUnsynchronization(byte[] data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(data.length);
        for (int i = 0; i < data.length; i++) {
            out.write(data[i]);
            if ((data[i] & 255) == 255 && i + 1 < data.length && data[i + 1] == 0) i++;
        }
        return out.toByteArray();
    }

    private static Lyrics empty() {
        return new Lyrics("", Collections.emptyList(), "MP3 내장 가사");
    }
}
