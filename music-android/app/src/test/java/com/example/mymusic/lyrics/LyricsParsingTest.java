package com.example.mymusic.lyrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

public class LyricsParsingTest {
    @Test public void parsesMultipleLrcTimestampsAndTracksPlaybackPosition() {
        Lyrics lyrics = Lyrics.fromText("[ar:Example]\n[00:01.20][00:03.50]first line\n[00:05.000]second line", "test");
        assertEquals("first line\nsecond line", lyrics.plainText);
        assertEquals(3, lyrics.timedLines.size());
        assertEquals(1200, lyrics.timedLines.get(0).timeMs);
        assertEquals(-1, lyrics.lineAt(500));
        assertEquals(1, lyrics.lineAt(4000));
        assertEquals(2, lyrics.lineAt(6000));
    }

    @Test public void appliesLrcOffsetEvenWhenTagFollowsTheTimedLines() {
        Lyrics early = Lyrics.fromText("[00:05.00]first\n[00:08.00]second\n[offset:+500]", "test");
        assertEquals(4500, early.timedLines.get(0).timeMs);
        assertEquals(-1, early.lineAt(4499));
        assertEquals(0, early.lineAt(4500));

        Lyrics late = Lyrics.fromText("[offset:-750]\n[00:05.00]first", "test");
        assertEquals(5750, late.timedLines.get(0).timeMs);
        assertEquals(0, late.lineAt(5750));
        assertEquals("first", late.plainText);
    }

    @Test public void manualAdvanceAndDelayMoveTheActiveLine() {
        Lyrics lyrics = Lyrics.fromText("[00:05.00]first\n[00:08.00]second", "test");
        assertEquals(-1, lyrics.lineAt(4700));
        assertEquals(0, lyrics.lineAt(4700, 500));
        assertEquals(-1, lyrics.lineAt(5200, -500));
    }

    @Test public void readsEmbeddedUsltAsPlainLyrics() throws Exception {
        byte[] body = concat(new byte[]{3, 'e', 'n', 'g', 0}, "a line\nanother line".getBytes(StandardCharsets.UTF_8));
        Lyrics lyrics = Id3LyricsReader.read(new ByteArrayInputStream(id3("USLT", body)));
        assertTrue(lyrics.hasLyrics());
        assertFalse(lyrics.isSynced());
        assertEquals("a line\nanother line", lyrics.plainText);
    }

    @Test public void readsEmbeddedSyltAndPrefersItOverPlain() throws Exception {
        byte[] plain = concat(new byte[]{3, 'e', 'n', 'g', 0}, "whole song".getBytes(StandardCharsets.UTF_8));
        byte[] timed = concat(new byte[]{3, 'e', 'n', 'g', 2, 1, 0},
                "first".getBytes(StandardCharsets.UTF_8), new byte[]{0, 0, 0, 3, (byte) 232},
                "second".getBytes(StandardCharsets.UTF_8), new byte[]{0, 0, 0, 7, (byte) 208});
        Lyrics lyrics = Id3LyricsReader.read(new ByteArrayInputStream(id3Two("USLT", plain, "SYLT", timed)));
        assertTrue(lyrics.isSynced());
        assertEquals("whole song", lyrics.plainText);
        assertEquals(1000, lyrics.timedLines.get(0).timeMs);
        assertEquals(2000, lyrics.timedLines.get(1).timeMs);
    }

    @Test public void embeddedLrcInUsltBecomesSynced() throws Exception {
        byte[] body = concat(new byte[]{3, 'e', 'n', 'g', 0}, "[00:02.00]hello".getBytes(StandardCharsets.UTF_8));
        Lyrics lyrics = Id3LyricsReader.read(new ByteArrayInputStream(id3("USLT", body)));
        assertTrue(lyrics.isSynced());
        assertEquals(2000, lyrics.timedLines.get(0).timeMs);
    }

    @Test public void unsynchronisedEarlierFrameDoesNotShiftLaterLyricsFrame() throws Exception {
        byte[] lyricsBody = concat(new byte[]{3, 'e', 'n', 'g', 0}, "later lyric".getBytes(StandardCharsets.UTF_8));
        byte[] tag = id3Two("TIT2", new byte[]{0, (byte) 0xFF, 0}, "USLT", lyricsBody);
        tag[5] = (byte) 0x80;
        Lyrics lyrics = Id3LyricsReader.read(new ByteArrayInputStream(tag));
        assertEquals("later lyric", lyrics.plainText);
    }

    private static byte[] id3(String name, byte[] body) throws Exception { return id3Two(name, body, null, null); }

    private static byte[] id3Two(String firstName, byte[] first, String secondName, byte[] second) throws Exception {
        byte[] frames = secondName == null ? frame(firstName, first) : concat(frame(firstName, first), frame(secondName, second));
        int size = frames.length;
        byte[] header = new byte[]{'I', 'D', '3', 3, 0, 0,
                (byte) ((size >> 21) & 127), (byte) ((size >> 14) & 127),
                (byte) ((size >> 7) & 127), (byte) (size & 127)};
        return concat(header, frames);
    }

    private static byte[] frame(String name, byte[] body) throws Exception {
        int size = body.length;
        return concat(name.getBytes(StandardCharsets.US_ASCII),
                new byte[]{(byte) (size >> 24), (byte) (size >> 16), (byte) (size >> 8), (byte) size, 0, 0}, body);
    }

    private static byte[] concat(byte[]... chunks) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (byte[] chunk : chunks) output.write(chunk);
        return output.toByteArray();
    }
}
