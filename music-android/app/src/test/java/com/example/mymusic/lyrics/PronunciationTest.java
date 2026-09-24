package com.example.mymusic.lyrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

public class PronunciationTest {
    @Test public void japaneseKanaKeepsNasalsAndCompoundSounds() {
        assertEquals("콘야모 키미오 오모우", JapanesePronunciation.kanaToHangul("コンヤモ キミヲ オモウ"));
        assertEquals("쇼", JapanesePronunciation.kanaToHangul("ショ"));
    }

    @Test public void japaneseKanjiUsesDictionaryReading() {
        List<String> result = JapanesePronunciation.transcribe(Arrays.asList("今夜も君を想う"));
        assertEquals(1, result.size());
        assertEquals("콘야모 키미오 오모우", result.get(0));
    }

    @Test public void englishUsesPronunciationNotSpelling() throws Exception {
        String entries = "love L AH1 V\nyou Y UW1\nnight N AY1 T\n";
        List<String> result = EnglishPronunciation.transcribe(
                new ByteArrayInputStream(entries.getBytes(StandardCharsets.UTF_8)),
                Arrays.asList("Love you tonight", "Love you night"));
        assertEquals("러브 유 나이트", result.get(1));
        assertTrue(result.get(0).startsWith("러브 유"));
    }

    @Test public void englishCurlyApostropheUsesDictionaryEntry() throws Exception {
        String entries = "i'm AY1 M\n";
        List<String> result = EnglishPronunciation.transcribe(
                new ByteArrayInputStream(entries.getBytes(StandardCharsets.UTF_8)),
                Arrays.asList("I’m"));
        assertEquals("아임", result.get(0));
    }
}
