package com.example.mymusic.player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AlbumAccentTest {
    @Test public void missingCoverKeepsOriginalLavender() {
        assertEquals(AlbumAccent.DEFAULT, AlbumAccent.fromCover(null));
    }

    @Test public void warmAndCoolCoversKeepTheirHueAtReadableBrightness() {
        int warm = AlbumAccent.normalize(new float[]{25f, .8f, .2f});
        int cool = AlbumAccent.normalize(new float[]{215f, .8f, .2f});
        assertTrue(red(warm) > blue(warm));
        assertTrue(blue(cool) > red(cool));
        assertTrue(Math.min(red(warm), Math.min(green(warm), blue(warm))) > 80);
        assertTrue(Math.min(red(cool), Math.min(green(cool), blue(cool))) > 80);
    }

    @Test public void neutralCoverStaysNeutral() {
        int neutral = AlbumAccent.normalize(new float[]{0f, .02f, .5f});
        assertTrue(Math.abs(red(neutral) - blue(neutral)) < 15);
    }

    private static int red(int color) { return color >> 16 & 0xFF; }
    private static int green(int color) { return color >> 8 & 0xFF; }
    private static int blue(int color) { return color & 0xFF; }
}
