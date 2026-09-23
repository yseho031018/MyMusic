package com.example.mymusic.model;

public class Song {

    private int id;
    private String title;
    private String artist;

    public Song(int id, String title) {
        this(id, title, "Unknown Artist");
    }

    public Song(int id, String title, String artist) {
        this.id = id;
        this.title = cleanTitle(title);
        this.artist = artist != null && !artist.isBlank() ? artist : "Unknown Artist";
    }

    private static String cleanTitle(String rawTitle) {
        if (rawTitle == null) {
            return "";
        }
        String t = rawTitle;
        String tagPattern = "(?i)\\s*(official\\s*(music\\s*)?(video|audio|mv|lyric\\s*video)?|music\\s*video|official|mv|video|audio|lyric\\s*video)\\s*";
        t = t.replaceAll("\\(" + tagPattern + "\\)", "");
        t = t.replaceAll("（" + tagPattern + "）", "");
        t = t.replaceAll("（" + tagPattern + "\\)", "");
        t = t.replaceAll("\\(" + tagPattern + "）", "");
        t = t.replaceAll("\\[" + tagPattern + "\\]", "");
        t = t.replaceAll("【" + tagPattern + "】", "");

        while (t.matches(".*\\s*(\\[[^\\]]*\\]|【[^】]*】)\\s*$")) {
            t = t.replaceAll("\\s*(\\[[^\\]]*\\]|【[^】]*】)\\s*$", "");
        }

        String tieUpRegex = "(?i)[^)]*(tv\\s*アニメ|アニメ|anime|ost|soundtrack|theme|ending|opening|엔딩|오프닝|주제가|挿入歌|テーマ|エンディング|オープニング)[^)]*";
        t = t.replaceAll("\\(" + tieUpRegex + "\\)", "");
        t = t.replaceAll("（" + tieUpRegex + "）", "");
        t = t.replaceAll("（" + tieUpRegex + "\\)", "");

        t = t.replaceAll("(\\(\\s*\\)|（\\s*）|（\\s*\\)|\\[\\s*\\]|【\\s*】)", "");
        t = t.replaceAll("\\s{2,}", " ");
        return t.trim();
    }

    public int getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getArtist() {
        return artist;
    }
}
