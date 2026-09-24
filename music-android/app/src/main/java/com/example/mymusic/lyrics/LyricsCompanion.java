package com.example.mymusic.lyrics;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.google.mlkit.nl.languageid.LanguageIdentification;
import com.google.mlkit.nl.languageid.LanguageIdentifier;
import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;

import org.json.JSONArray;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Adds Korean translation and approximate Hangul pronunciation to lyric lines. */
public final class LyricsCompanion {
    public interface Listener {
        void onLanguage(String language);
        void onPronunciation(List<String> lines);
        void onTranslation(int index, String translation);
        void onStatus(String status);
    }

    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final LanguageIdentifier identifier = LanguageIdentification.getClient();
    private Translator translator;
    private volatile int generation;

    public LyricsCompanion(Context context) {
        this.context = context.getApplicationContext();
    }

    public void load(Lyrics lyrics, Listener listener) {
        int request = ++generation;
        if (translator != null) {
            translator.close();
            translator = null;
        }
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
                        listener.onStatus("한국어 보조 가사는 영어·일본어 곡에서 지원합니다");
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
        createPronunciation(lines, language, request, listener);
        loadTranslation(lines, language, request, listener);
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

    private void createPronunciation(List<String> lines, String language, int request, Listener listener) {
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

    private void loadTranslation(List<String> lines, String language, int request, Listener listener) {
        worker.execute(() -> {
            if (request != generation) return;
            List<String> saved = readCache(lines, language);
            if (saved != null) {
                main.post(() -> {
                    if (request != generation) return;
                    for (int i = 0; i < saved.size(); i++) {
                        listener.onTranslation(i, saved.get(i));
                    }
                    listener.onStatus("저장된 한국어 번역");
                });
            } else {
                main.post(() -> startTranslation(lines, language, request, listener));
            }
        });
    }

    private void startTranslation(List<String> lines, String language, int request, Listener listener) {
        if (request != generation) return;
        String source = TranslateLanguage.fromLanguageTag(language);
        if (source == null) {
            listener.onStatus("이 언어는 번역할 수 없습니다");
            return;
        }
        translator = Translation.getClient(new TranslatorOptions.Builder()
                .setSourceLanguage(source)
                .setTargetLanguage(TranslateLanguage.KOREAN)
                .build());
        listener.onStatus("한국어 번역 모델 준비 중 · 처음에는 Wi-Fi가 필요합니다");
        translator.downloadModelIfNeeded(new DownloadConditions.Builder().requireWifi().build())
                .addOnSuccessListener(ignored -> {
                    if (request != generation) return;
                    listener.onStatus("한국어 번역 중…");
                    String[] result = new String[lines.size()];
                    translateNext(lines, result, new HashMap<>(), 0, false, language, request, listener);
                })
                .addOnFailureListener(error -> {
                    if (request == generation) listener.onStatus("번역 모델을 받을 수 없습니다 · Wi-Fi 연결을 확인해 주세요");
                });
    }

    private void translateNext(List<String> lines, String[] result, Map<String, String> repeated,
                               int index, boolean hadError, String language, int request, Listener listener) {
        if (request != generation) return;
        if (index >= lines.size()) {
            if (!hadError) worker.execute(() -> saveCache(lines, language, Arrays.asList(result)));
            listener.onStatus(hadError ? "일부 가사를 번역하지 못했습니다" : "Google Translate로 자동 번역 · 발음은 참고용");
            return;
        }
        String original = lines.get(index).trim();
        if (original.isEmpty()) {
            result[index] = "";
            translateNext(lines, result, repeated, index + 1, hadError, language, request, listener);
            return;
        }
        if (repeated.containsKey(original)) {
            result[index] = repeated.get(original);
            listener.onTranslation(index, result[index]);
            translateNext(lines, result, repeated, index + 1, hadError, language, request, listener);
            return;
        }
        translator.translate(original)
                .addOnSuccessListener(translated -> {
                    if (request != generation) return;
                    result[index] = translated;
                    repeated.put(original, translated);
                    listener.onTranslation(index, translated);
                    translateNext(lines, result, repeated, index + 1, hadError, language, request, listener);
                })
                .addOnFailureListener(error -> {
                    if (request != generation) return;
                    result[index] = "";
                    translateNext(lines, result, repeated, index + 1, true, language, request, listener);
                });
    }

    private List<String> readCache(List<String> lines, String language) {
        File file = cacheFile(lines, language);
        if (!file.isFile()) return null;
        try (InputStream stream = new FileInputStream(file)) {
            byte[] data = new byte[(int) Math.min(file.length(), 1_000_000)];
            int size = 0;
            while (size < data.length) {
                int read = stream.read(data, size, data.length - size);
                if (read < 0) break;
                size += read;
            }
            JSONArray array = new JSONArray(new String(data, 0, size, StandardCharsets.UTF_8));
            if (array.length() != lines.size()) return null;
            List<String> result = new ArrayList<>(array.length());
            for (int i = 0; i < array.length(); i++) result.add(array.getString(i));
            return result;
        } catch (Exception ignored) {
            return null;
        }
    }

    private void saveCache(List<String> lines, String language, List<String> translated) {
        try {
            File file = cacheFile(lines, language);
            File folder = file.getParentFile();
            if (folder == null || (!folder.isDirectory() && !folder.mkdirs())) return;
            JSONArray array = new JSONArray(translated);
            try (FileOutputStream output = new FileOutputStream(file)) {
                output.write(array.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {}
    }

    private File cacheFile(List<String> lines, String language) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest((language + "\n" + String.join("\n", lines)).getBytes(StandardCharsets.UTF_8));
            StringBuilder name = new StringBuilder();
            for (int i = 0; i < 16; i++) name.append(String.format(java.util.Locale.ROOT, "%02x", hash[i] & 255));
            return new File(new File(context.getFilesDir(), "translated_lyrics"), name + ".json");
        } catch (Exception impossible) {
            return new File(new File(context.getFilesDir(), "translated_lyrics"), "fallback.json");
        }
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
        if (translator != null) {
            translator.close();
            translator = null;
        }
    }

    public void close() {
        cancel();
        identifier.close();
        worker.shutdownNow();
    }
}
