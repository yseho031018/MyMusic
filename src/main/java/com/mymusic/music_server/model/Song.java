package com.mymusic.music_server.model;

public class Song {

    private int id;
    private String title;
    private String artist;
    private String album;

    public Song(int id, String title) {
        this(id, title, "Unknown Artist", "Unknown Album");
    }

    public Song(int id, String title, String artist, String album) {
        this.id = id;
        this.title = title;
        this.artist = artist != null && !artist.isBlank() ? artist : "Unknown Artist";
        this.album = album != null && !album.isBlank() ? album : "Unknown Album";
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

    public String getAlbum() {
        return album;
    }
}
