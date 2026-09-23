package com.example.mymusic.network;

import com.example.mymusic.model.Song;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class MusicApi {

    private final String baseUrl;

    public MusicApi(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public List<Song> getSongs() throws Exception {

        URL url = new URL(baseUrl + "/api/songs");

        HttpURLConnection connection =
                (HttpURLConnection) url.openConnection();

        connection.setRequestMethod("GET");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);

        try {
            int status = connection.getResponseCode();

            if (status != 200) {
                throw new Exception(
                        "서버 응답 오류: " + status
                );
            }

            StringBuilder response = new StringBuilder();

            try (BufferedReader reader =
                         new BufferedReader(
                                 new InputStreamReader(
                                         connection.getInputStream(),
                                         StandardCharsets.UTF_8
                                 )
                         )) {

                String line;

                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
            }

            JSONArray jsonArray =
                    new JSONArray(response.toString());

            List<Song> songs = new ArrayList<>();

            for (int i = 0; i < jsonArray.length(); i++) {

                JSONObject jsonObject =
                        jsonArray.getJSONObject(i);

                int id = jsonObject.getInt("id");
                String title = jsonObject.getString("title");
                String artist = jsonObject.optString("artist", "Unknown Artist");

                songs.add(new Song(id, title, artist));
            }

            return songs;

        } finally {
            connection.disconnect();
        }
    }

    public android.graphics.Bitmap getAlbumCoverBitmap(int id) {
        try {
            URL url = new URL(baseUrl + "/api/songs/" + id + "/cover");
            HttpURLConnection connection =
                    (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(4000);
            connection.setReadTimeout(4000);

            try {
                if (connection.getResponseCode() == 200) {
                    try (InputStream input = connection.getInputStream()) {
                        return android.graphics.BitmapFactory.decodeStream(input);
                    }
                }
            } finally {
                connection.disconnect();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public File downloadSong(Context context, Song song)
            throws Exception {

        // 앱 전용 음악 저장 폴더
        File musicDir = new File(
                context.getFilesDir(),
                "music"
        );

        if (!musicDir.exists() && !musicDir.mkdirs()) {
            throw new Exception("음악 폴더 생성 실패");
        }

        // 음악 ID를 파일 이름으로 사용
        File musicFile = new File(
                musicDir,
                song.getId() + ".mp3"
        );

        String streamUrl =
                baseUrl
                        + "/api/songs/"
                        + song.getId()
                        + "/stream";

        HttpURLConnection connection =
                (HttpURLConnection)
                        new URL(streamUrl).openConnection();

        connection.setConnectTimeout(5000);
        connection.setReadTimeout(15000);

        try {

            int status = connection.getResponseCode();

            if (status != 200) {
                throw new Exception(
                        "다운로드 실패: HTTP " + status
                );
            }

            try (
                    InputStream input =
                            connection.getInputStream();

                    FileOutputStream output =
                            new FileOutputStream(musicFile)
            ) {

                byte[] buffer = new byte[8192];

                int bytesRead;

                while ((bytesRead = input.read(buffer)) != -1) {
                    output.write(buffer, 0, bytesRead);
                }
            }

            return musicFile;

        } finally {
            connection.disconnect();
        }
    }

}
