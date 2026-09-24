package com.example.mymusic.lyrics;

import com.atilika.kuromoji.ipadic.Token;
import com.atilika.kuromoji.ipadic.Tokenizer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** IPADIC reading to approximate Hangul. Kanji readings are resolved by Kuromoji. */
public final class JapanesePronunciation {
    private static final Map<String, String> KANA = new HashMap<>();

    static {
        add("アイウエオ", "아 이 우 에 오");
        add("カキクケコ", "카 키 쿠 케 코");
        add("サシスセソ", "사 시 스 세 소");
        add("タチツテト", "타 치 츠 테 토");
        add("ナニヌネノ", "나 니 누 네 노");
        add("ハヒフヘホ", "하 히 후 헤 호");
        add("マミムメモ", "마 미 무 메 모");
        add("ヤユヨ", "야 유 요");
        add("ラリルレロ", "라 리 루 레 로");
        add("ワヲ", "와 오");
        add("ガギグゲゴ", "가 기 구 게 고");
        add("ザジズゼゾ", "자 지 즈 제 조");
        add("ダヂヅデド", "다 지 즈 데 도");
        add("バビブベボ", "바 비 부 베 보");
        add("パピプペポ", "파 피 푸 페 포");
        add("ァィゥェォ", "아 이 우 에 오");
        add("キャキュキョ", "캬 큐 쿄", 2);
        add("ギャギュギョ", "갸 규 교", 2);
        add("シャシュショ", "샤 슈 쇼", 2);
        add("ジャジュジョ", "자 주 조", 2);
        add("チャチュチョ", "차 추 초", 2);
        add("ニャニュニョ", "냐 뉴 뇨", 2);
        add("ヒャヒュヒョ", "햐 휴 효", 2);
        add("ビャビュビョ", "뱌 뷰 뵤", 2);
        add("ピャピュピョ", "퍄 퓨 표", 2);
        add("ミャミュミョ", "먀 뮤 묘", 2);
        add("リャリュリョ", "랴 류 료", 2);
        add("ティディファフィフェフォウィウェウォ", "티 디 파 피 페 포 위 웨 워", 2);
    }

    private JapanesePronunciation() {}

    public static List<String> transcribe(List<String> lines) {
        Tokenizer tokenizer = new Tokenizer();
        List<String> output = new ArrayList<>(lines.size());
        for (String line : lines) {
            StringBuilder rendered = new StringBuilder();
            for (Token token : tokenizer.tokenize(line)) {
                String surface = token.getSurface();
                String reading = token.getPronunciation();
                if (reading == null || reading.equals("*")) reading = token.getReading();
                if (reading == null || reading.equals("*")) reading = surface;
                String converted = kanaToHangul(reading);
                if (converted.isEmpty()) continue;
                String part = token.getPartOfSpeechLevel1();
                boolean attach = "助詞".equals(part) || "助動詞".equals(part) || isPunctuation(surface);
                if (rendered.length() > 0 && !attach && !Character.isWhitespace(rendered.charAt(rendered.length() - 1))) {
                    rendered.append(' ');
                }
                rendered.append(converted);
            }
            output.add(rendered.toString().trim());
        }
        return output;
    }

    static String kanaToHangul(String text) {
        StringBuilder output = new StringBuilder();
        for (int i = 0; i < text.length();) {
            char current = text.charAt(i);
            if (current >= 'ぁ' && current <= 'ゖ') current = (char) (current + 0x60);
            if (current == 'ン') {
                if (!attachCoda(output, 4)) output.append("응");
                i++;
                continue;
            }
            if (current == 'ッ') {
                attachCoda(output, 19);
                i++;
                continue;
            }
            if (current == 'ー') { i++; continue; }
            if (i + 1 < text.length()) {
                char next = text.charAt(i + 1);
                if (next >= 'ぁ' && next <= 'ゖ') next = (char) (next + 0x60);
                String pair = "" + current + next;
                String mapped = KANA.get(pair);
                if (mapped != null) {
                    output.append(mapped);
                    i += 2;
                    continue;
                }
            }
            String mapped = KANA.get(String.valueOf(current));
            output.append(mapped != null ? mapped : current);
            i++;
        }
        return output.toString();
    }

    private static boolean attachCoda(StringBuilder output, int coda) {
        if (output.length() == 0) return false;
        char previous = output.charAt(output.length() - 1);
        if (previous < 0xAC00 || previous > 0xD7A3 || (previous - 0xAC00) % 28 != 0) return false;
        output.setCharAt(output.length() - 1, (char) (previous + coda));
        return true;
    }

    private static boolean isPunctuation(String text) {
        return text.length() == 1 && "、。！？,.!?".contains(text);
    }

    private static void add(String kana, String hangul) {
        add(kana, hangul, 1);
    }

    private static void add(String kana, String hangul, int width) {
        String[] values = hangul.split(" ");
        for (int i = 0; i < values.length; i++) {
            KANA.put(kana.substring(i * width, (i + 1) * width), values[i]);
        }
    }
}
