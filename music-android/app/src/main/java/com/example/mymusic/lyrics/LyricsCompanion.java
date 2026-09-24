package com.example.mymusic.lyrics;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.google.mlkit.nl.languageid.LanguageIdentification;
import com.google.mlkit.nl.languageid.LanguageIdentifier;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Adds approximate Hangul pronunciation to English and Japanese lyric lines. */
public final class LyricsCompanion {
    public interface Listener {
        void onLanguage(String language);
        void onPronunciation(List<String> lines);
        void onStatus(String status);
    }

    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final LanguageIdentifier identifier = LanguageIdentification.getClient();
    private volatile int generation;

    public LyricsCompanion(Context context) {
        this.context = context.getApplicationContext();
    }

    public void load(Lyrics lyrics, Listener listener) {
        int request = ++generation;
        if (!lyrics.hasLyrics()) return;
        List<String> lines = lyricLines(lyrics);
        String sample = lyrics.plainText.length() > 1000 ? lyrics.plainText.substring(0, 1000) : lyrics.plainText;
        if (containsKana(sample)) {
            begin(lines, "ja", request, listener);
            return;
        }
        identifier.identifyLanguage(sample)
                .addOnSuccessListener(language -> {
                    if (request != generation) return;
                    String code = language == null ? "" : language.split("-")[0];
                    if ((code.isEmpty() || "und".equals(code)) && isMostlyLatin(sample)) code = "en";
                    if (!"en".equals(code) && !"ja".equals(code)) {
                        listener.onStatus("한글 발음은 영어·일본어 가사에서 지원합니다");
                        return;
                    }
                    begin(lines, code, request, listener);
                })
                .addOnFailureListener(error -> {
                    if (request == generation) listener.onStatus("가사 언어를 판별하지 못했습니다");
                });
    }

    private void begin(List<String> lines, String language, int request, Listener listener) {
        if (request != generation) return;
        listener.onLanguage(language);
        worker.execute(() -> {
            if (request != generation) return;
            try {
                List<String> result;
                if ("ja".equals(language)) {
                    result = JapanesePronunciation.transcribe(lines);
                } else {
                    try (InputStream dictionary = context.getAssets().open("cmudict.dict")) {
                        result = EnglishPronunciation.transcribe(dictionary, lines);
                    }
                }
                main.post(() -> {
                    if (request == generation) listener.onPronunciation(result);
                });
            } catch (Exception error) {
                main.post(() -> {
                    if (request == generation) listener.onStatus("한글 발음을 생성하지 못했습니다");
                });
            }
        });
    }

    private static boolean containsKana(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c >= 'ぁ' && c <= 'ゖ') || (c >= 'ァ' && c <= 'ヿ')) return true;
        }
        return false;
    }

    private static boolean isMostlyLatin(String text) {
        int latin = 0;
        int otherLetters = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!Character.isLetter(c)) continue;
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')) latin++;
            else otherLetters++;
        }
        return latin >= 8 && latin > otherLetters * 3;
    }

    public static List<String> lyricLines(Lyrics lyrics) {
        List<String> lines = new ArrayList<>();
        if (lyrics.isSynced()) {
            for (Lyrics.Line line : lyrics.timedLines) lines.add(line.text);
        } else {
            lines.addAll(Arrays.asList(lyrics.plainText.split("\\n", -1)));
        }
        return lines;
    }

    public void cancel() {
        generation++;
    }

    public void close() {
        cancel();
        identifier.close();
        worker.shutdownNow();
    }
}
