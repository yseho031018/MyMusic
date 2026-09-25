package com.example.mymusic.model;

import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class SongSearchTest {
    private final Song imported = new Song(-2, "More Than Words", "羊文学");
    private final Song server = new Song(42, "밤편지", "아이유");
    private final List<Song> library = Arrays.asList(imported, server);

    @Test public void combinesTitleAndArtistTermsWithoutChangingSongIdentity() {
        List<Song> result = SongSearch.filter(library, "  WORDS  羊文学 ");
        assertEquals(1, result.size());
        assertSame(imported, result.get(0));
        assertEquals(-2, result.get(0).getId());
    }

    @Test public void matchesKoreanAndFullWidthLatin() {
        assertSame(server, SongSearch.filter(library, "아이유 밤").get(0));
        assertSame(imported, SongSearch.filter(library, "ＭＯＲＥ").get(0));
    }

    @Test public void clearingQueryRestoresFullOrderAndDoesNotMutateSource() {
        List<Song> displayed = SongSearch.filter(library, " ");
        displayed.remove(0);
        assertEquals(2, library.size());
        List<Song> restored = SongSearch.filter(library, "");
        assertSame(imported, restored.get(0));
        assertSame(server, restored.get(1));
    }

    @Test public void requiresEveryTermAndReturnsEmptyForNoMatch() {
        assertTrue(SongSearch.filter(library, "words 아이유").isEmpty());
    }
}
