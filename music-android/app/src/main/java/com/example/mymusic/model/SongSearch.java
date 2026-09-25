package com.example.mymusic.model;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Filters display rows without changing the source library or the playback queue. */
public final class SongSearch {
    private SongSearch() {}

    public static List<Song> filter(List<Song> source, String query) {
        String normalizedQuery = normalize(query).trim();
        if (normalizedQuery.isEmpty()) return new ArrayList<>(source);
        String[] terms = normalizedQuery.split("\\s+");
        List<Song> result = new ArrayList<>();
        for (Song song : source) {
            String searchable = normalize(song.getTitle() + " " + song.getArtist());
            boolean matches = true;
            for (String term : terms) {
                if (!searchable.contains(term)) {
                    matches = false;
                    break;
                }
            }
            if (matches) result.add(song);
        }
        return result;
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
    }
}
