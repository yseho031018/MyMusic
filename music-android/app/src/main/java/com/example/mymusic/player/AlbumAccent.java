package com.example.mymusic.player;

import android.graphics.Bitmap;

import androidx.palette.graphics.Palette;

/** Selects a representative cover color and raises it to a legible dark-theme accent. */
public final class AlbumAccent {
    public static final int DEFAULT = 0xFFC9A9FF;

    private AlbumAccent() {}

    /** Run on a worker thread; palette quantization must not block the player UI. */
    public static int fromCover(Bitmap cover) {
        if (cover == null || cover.isRecycled()) return DEFAULT;
        Palette palette = Palette.from(cover).maximumColorCount(16)
                .resizeBitmapArea(12_000).generate();
        Palette.Swatch best = null;
        float bestScore = -1f;
        for (Palette.Swatch swatch : palette.getSwatches()) {
            float[] hsl = swatch.getHsl();
            if (hsl[2] < .08f || hsl[2] > .94f) continue;
            float score = swatch.getPopulation() * (.75f + hsl[1] * .55f);
            if (score > bestScore) {
                best = swatch;
                bestScore = score;
            }
        }
        if (best == null) best = palette.getDominantSwatch();
        return best == null ? DEFAULT : normalize(best.getHsl());
    }

    static int normalize(float[] hsl) {
        if (hsl[1] < .08f) return 0xFFD1CFD5;
        float saturation = Math.min(.67f, .22f + hsl[1] * .5f);
        return hslColor(hsl[0], saturation, .74f);
    }

    private static int hslColor(float hue, float saturation, float lightness) {
        float chroma = (1f - Math.abs(2f * lightness - 1f)) * saturation;
        float sector = ((hue % 360f) + 360f) % 360f / 60f;
        float secondary = chroma * (1f - Math.abs(sector % 2f - 1f));
        float red = 0f, green = 0f, blue = 0f;
        if (sector < 1f) { red = chroma; green = secondary; }
        else if (sector < 2f) { red = secondary; green = chroma; }
        else if (sector < 3f) { green = chroma; blue = secondary; }
        else if (sector < 4f) { green = secondary; blue = chroma; }
        else if (sector < 5f) { red = secondary; blue = chroma; }
        else { red = chroma; blue = secondary; }
        float match = lightness - chroma / 2f;
        return 0xFF000000 | channel(red + match) << 16
                | channel(green + match) << 8 | channel(blue + match);
    }

    private static int channel(float value) {
        return Math.max(0, Math.min(255, Math.round(value * 255f)));
    }
}
