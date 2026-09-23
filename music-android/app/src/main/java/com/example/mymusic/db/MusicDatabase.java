package com.example.mymusic.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.example.mymusic.model.Song;

import java.util.ArrayList;
import java.util.List;

public class MusicDatabase extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "mymusic.db";
    private static final int DATABASE_VERSION = 1;

    public static final String TABLE_SONGS = "downloaded_songs";
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_TITLE = "title";
    public static final String COLUMN_ARTIST = "artist";
    public static final String COLUMN_FILE_PATH = "file_path";
    public static final String COLUMN_DOWNLOADED_AT = "downloaded_at";
    public static final String COLUMN_IS_FAVORITE = "is_favorite";

    public MusicDatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTableQuery = "CREATE TABLE " + TABLE_SONGS + " ("
                + COLUMN_ID + " INTEGER PRIMARY KEY, "
                + COLUMN_TITLE + " TEXT NOT NULL, "
                + COLUMN_ARTIST + " TEXT, "
                + COLUMN_FILE_PATH + " TEXT NOT NULL, "
                + COLUMN_DOWNLOADED_AT + " INTEGER, "
                + COLUMN_IS_FAVORITE + " INTEGER DEFAULT 0"
                + ");";
        db.execSQL(createTableQuery);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_SONGS);
        onCreate(db);
    }

    // 1. 다운로드한 음악 정보 저장 (이미 존재하면 업데이트)
    public void saveSong(Song song, String filePath) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_ID, song.getId());
        values.put(COLUMN_TITLE, song.getTitle());
        values.put(COLUMN_ARTIST, song.getArtist());
        values.put(COLUMN_FILE_PATH, filePath);
        values.put(COLUMN_DOWNLOADED_AT, System.currentTimeMillis());

        db.insertWithOnConflict(
                TABLE_SONGS,
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE
        );
    }

    // 2. 저장된 모든 다운로드 곡 목록 조회 (최신 다운로드순)
    public List<Song> getAllSongs() {
        List<Song> songs = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        String query = "SELECT " + COLUMN_ID + ", " + COLUMN_TITLE + ", " + COLUMN_ARTIST
                + " FROM " + TABLE_SONGS
                + " ORDER BY " + COLUMN_DOWNLOADED_AT + " DESC";

        try (Cursor cursor = db.rawQuery(query, null)) {
            while (cursor.moveToNext()) {
                int id = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ID));
                String title = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TITLE));
                String artist = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ARTIST));

                songs.add(new Song(id, title, artist));
            }
        }

        return songs;
    }

    // 3. 특정 곡이 다운로드되어 있는지 확인
    public boolean isDownloaded(int songId) {
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT 1 FROM " + TABLE_SONGS + " WHERE " + COLUMN_ID + " = ?";

        try (Cursor cursor = db.rawQuery(query, new String[]{String.valueOf(songId)})) {
            return cursor.moveToFirst();
        }
    }

    // 4. 다운로드된 곡 삭제
    public boolean deleteSong(int songId) {
        SQLiteDatabase db = this.getWritableDatabase();
        int rows = db.delete(TABLE_SONGS, COLUMN_ID + " = ?", new String[]{String.valueOf(songId)});
        return rows > 0;
    }

    // 5. 검색 기능 (곡명 또는 가수명 검색)
    public List<Song> searchSongs(String keyword) {
        List<Song> songs = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        String query = "SELECT " + COLUMN_ID + ", " + COLUMN_TITLE + ", " + COLUMN_ARTIST
                + " FROM " + TABLE_SONGS
                + " WHERE " + COLUMN_TITLE + " LIKE ? OR " + COLUMN_ARTIST + " LIKE ?"
                + " ORDER BY " + COLUMN_TITLE + " ASC";

        String searchPattern = "%" + keyword + "%";

        try (Cursor cursor = db.rawQuery(query, new String[]{searchPattern, searchPattern})) {
            while (cursor.moveToNext()) {
                int id = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ID));
                String title = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TITLE));
                String artist = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ARTIST));

                songs.add(new Song(id, title, artist));
            }
        }

        return songs;
    }
}

