package com.example.mymusic.storage;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.provider.OpenableColumns;

import com.example.mymusic.db.MusicDatabase;
import com.example.mymusic.model.Song;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** Copies a selected MP3 into the same private library used by server downloads. */
public final class LocalMusicImporter {
    private LocalMusicImporter() {}

    public static Song importSong(Context context, MusicDatabase database, Uri uri) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        File musicDir = new File(context.getFilesDir(), "music");
        if (!musicDir.isDirectory() && !musicDir.mkdirs()) {
            throw new IOException("음악 폴더를 만들 수 없습니다.");
        }

        File temporary = File.createTempFile("import-", ".mp3", musicDir);
        try {
            try (InputStream input = resolver.openInputStream(uri);
                 FileOutputStream output = new FileOutputStream(temporary)) {
                if (input == null) throw new IOException("선택한 파일을 읽을 수 없습니다.");
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            }

            verifyMp3(temporary);

            String displayName = getDisplayName(resolver, uri);
            String title = displayName.replaceFirst("(?i)\\.mp3$", "").trim();
            String artist = null;
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            try {
                retriever.setDataSource(temporary.getAbsolutePath());
                String duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                if (duration == null || Long.parseLong(duration) <= 0) {
                    throw new IOException("재생 가능한 MP3 파일이 아닙니다.");
                }
                String tagTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
                if (tagTitle != null && !tagTitle.isBlank()) title = tagTitle.trim();
            } catch (RuntimeException e) {
                throw new IOException("MP3 정보를 읽을 수 없습니다.", e);
            } finally {
                retriever.release();
            }

            if (title.isBlank()) title = "제목 없음";
            int id = database.getNextImportedId();
            File destination = new File(musicDir, id + ".mp3");
            while (destination.exists()) {
                if (id == Integer.MIN_VALUE) throw new IOException("가져올 수 있는 곡 수를 초과했습니다.");
                destination = new File(musicDir, (--id) + ".mp3");
            }
            if (!temporary.renameTo(destination)) throw new IOException("MP3 파일을 저장할 수 없습니다.");

            Song song = new Song(id, title, artist);
            try {
                database.saveSong(song, destination.getAbsolutePath());
            } catch (RuntimeException e) {
                destination.delete();
                throw e;
            }
            return song;
        } finally {
            temporary.delete();
        }
    }

    private static void verifyMp3(File file) throws IOException {
        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(file.getAbsolutePath());
            for (int track = 0; track < extractor.getTrackCount(); track++) {
                String mime = extractor.getTrackFormat(track).getString(MediaFormat.KEY_MIME);
                if (MediaFormat.MIMETYPE_AUDIO_MPEG.equals(mime)) return;
            }
            throw new IOException("MP3 파일만 가져올 수 있습니다.");
        } catch (RuntimeException e) {
            throw new IOException("MP3 파일을 읽을 수 없습니다.", e);
        } finally {
            extractor.release();
        }
    }

    private static String getDisplayName(ContentResolver resolver, Uri uri) {
        try (Cursor cursor = resolver.query(uri, new String[]{OpenableColumns.DISPLAY_NAME},
                null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String name = cursor.getString(0);
                if (name != null && !name.isBlank()) return name;
            }
        } catch (RuntimeException ignored) {
        }
        String fallback = uri.getLastPathSegment();
        return fallback == null ? "가져온 음악.mp3" : fallback;
    }
}
