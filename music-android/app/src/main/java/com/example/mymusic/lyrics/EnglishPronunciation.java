package com.example.mymusic.lyrics;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Approximate Hangul caption from CMUdict's American English phonemes. */
public final class EnglishPronunciation {
    private static final Pattern WORD = Pattern.compile("[A-Za-z]+(?:['’][A-Za-z]+)?");

    private EnglishPronunciation() {}

    public static List<String> transcribe(InputStream dictionary, List<String> lines) throws IOException {
        Set<String> needed = new HashSet<>();
        for (String line : lines) {
            Matcher words = WORD.matcher(line);
            while (words.find()) needed.add(normalizeWord(words.group()));
        }
        Map<String, String[]> pronunciations = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(dictionary, StandardCharsets.UTF_8))) {
            String entry;
            while ((entry = reader.readLine()) != null) {
                int separator = entry.indexOf(' ');
                if (separator < 1) continue;
                String word = entry.substring(0, separator);
                int variant = word.indexOf('(');
                if (variant > 0) word = word.substring(0, variant);
                if (needed.contains(word) && !pronunciations.containsKey(word)) {
                    pronunciations.put(word, entry.substring(separator + 1).trim().split(" +"));
                }
            }
        }
        List<String> result = new ArrayList<>(lines.size());
        for (String line : lines) {
            Matcher matcher = WORD.matcher(line);
            StringBuilder rendered = new StringBuilder();
            int position = 0;
            while (matcher.find()) {
                rendered.append(line, position, matcher.start());
                String word = normalizeWord(matcher.group());
                String[] phonemes = pronunciations.get(word);
                if (phonemes == null) phonemes = guessPhonemes(word);
                rendered.append(toHangul(phonemes));
                position = matcher.end();
            }
            rendered.append(line, position, line.length());
            result.add(rendered.toString().trim());
        }
        return result;
    }

    private static String normalizeWord(String word) {
        return word.toLowerCase(Locale.ROOT).replace('’', '\'');
    }

    static String toHangul(String[] phonemes) {
        StringBuilder output = new StringBuilder();
        List<String> consonants = new ArrayList<>();
        for (String phoneWithStress : phonemes) {
            String phone = phoneWithStress.replaceAll("[0-9]", "").toUpperCase(Locale.ROOT);
            String vowel = vowel(phone);
            if (vowel == null) {
                consonants.add(phone);
                continue;
            }
            for (int i = 0; i < consonants.size() - 1; i++) output.append(looseConsonant(consonants.get(i)));
            String onset = consonants.isEmpty() ? "" : consonants.get(consonants.size() - 1);
            output.append(withOnset(onset, phone, vowel));
            consonants.clear();
        }
        for (String phone : consonants) {
            if (!attachCoda(output, phone)) output.append(looseConsonant(phone));
        }
        return output.toString();
    }

    private static String withOnset(String onset, String phone, String vowel) {
        if (onset.equals("W")) {
            switch (phone) {
                case "AH": case "ER": return "워";
                case "IY": case "IH": return "위";
                case "EH": return "웨";
                case "AE": return "왜";
                case "UW": case "UH": return "우";
                default: break;
            }
        }
        if (onset.equals("Y") && (phone.equals("UW") || phone.equals("UH"))) return "유";
        int initial = initial(onset);
        char first = vowel.charAt(0);
        if (first < 0xAC00 || first > 0xD7A3) return vowel;
        int syllable = first - 0xAC00;
        char replaced = (char) (0xAC00 + initial * 588 + syllable % 588);
        return replaced + vowel.substring(1);
    }

    private static boolean attachCoda(StringBuilder text, String phone) {
        int coda;
        switch (phone) {
            case "L": case "R": coda = 8; break;
            case "M": coda = 16; break;
            case "N": coda = 4; break;
            case "NG": coda = 21; break;
            default: return false;
        }
        if (text.length() == 0) return false;
        char last = text.charAt(text.length() - 1);
        if (last < 0xAC00 || last > 0xD7A3 || (last - 0xAC00) % 28 != 0) return false;
        text.setCharAt(text.length() - 1, (char) (last + coda));
        return true;
    }

    private static int initial(String phone) {
        switch (phone) {
            case "B": case "V": return 7;
            case "CH": return 14;
            case "D": case "DH": return 3;
            case "F": case "P": return 17;
            case "G": return 0;
            case "HH": return 18;
            case "JH": case "Z": case "ZH": return 12;
            case "K": return 15;
            case "L": case "R": return 5;
            case "M": return 6;
            case "N": return 2;
            case "S": case "SH": case "TH": return 9;
            case "T": return 16;
            default: return 11;
        }
    }

    private static String vowel(String phone) {
        switch (phone) {
            case "AA": return "아";
            case "AE": return "애";
            case "AH": return "어";
            case "AO": return "오";
            case "AW": return "아우";
            case "AY": return "아이";
            case "EH": return "에";
            case "ER": return "어";
            case "EY": return "에이";
            case "IH": case "IY": return "이";
            case "OW": return "오우";
            case "OY": return "오이";
            case "UH": case "UW": return "우";
            default: return null;
        }
    }

    private static String looseConsonant(String phone) {
        switch (phone) {
            case "B": case "V": return "브";
            case "CH": return "치";
            case "D": case "DH": return "드";
            case "F": case "P": return "프";
            case "G": return "그";
            case "HH": return "흐";
            case "JH": return "지";
            case "K": return "크";
            case "L": case "R": return "르";
            case "M": return "므";
            case "N": return "느";
            case "NG": return "응";
            case "S": case "TH": return "스";
            case "SH": return "쉬";
            case "T": return "트";
            case "W": return "우";
            case "Y": return "이";
            case "Z": case "ZH": return "즈";
            default: return "";
        }
    }

    private static String[] guessPhonemes(String word) {
        List<String> phones = new ArrayList<>();
        String normalized = word.replace("'", "");
        for (int i = 0; i < normalized.length();) {
            String pair = i + 1 < normalized.length() ? normalized.substring(i, i + 2) : "";
            String mapped = null;
            switch (pair) {
                case "ch": mapped = "CH"; break;
                case "sh": mapped = "SH"; break;
                case "th": mapped = "TH"; break;
                case "ph": mapped = "F"; break;
                case "ng": mapped = "NG"; break;
                case "oo": mapped = "UW"; break;
                case "ee": case "ea": mapped = "IY"; break;
                case "ai": case "ay": mapped = "EY"; break;
                case "oa": mapped = "OW"; break;
                default: break;
            }
            if (mapped != null) {
                phones.add(mapped);
                i += 2;
                continue;
            }
            char letter = normalized.charAt(i++);
            if (letter == 'e' && i == normalized.length() && !phones.isEmpty()) break;
            switch (letter) {
                case 'a': phones.add("AE"); break;
                case 'e': phones.add("EH"); break;
                case 'i': case 'y': phones.add("IH"); break;
                case 'o': phones.add("AO"); break;
                case 'u': phones.add("AH"); break;
                case 'c': phones.add("K"); break;
                case 'h': phones.add("HH"); break;
                case 'j': phones.add("JH"); break;
                case 'q': phones.add("K"); break;
                case 'x': phones.add("K"); phones.add("S"); break;
                default: phones.add(String.valueOf(letter).toUpperCase(Locale.ROOT)); break;
            }
        }
        return phones.toArray(new String[0]);
    }
}
