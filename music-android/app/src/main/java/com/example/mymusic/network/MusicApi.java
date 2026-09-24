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

    private volatile String baseUrl;

    public MusicApi(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
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

    public List<Song> refreshMetadata() throws Exception {
        HttpURLConnection connection = (HttpURLConnection)
                new URL(baseUrl + "/api/songs/refresh").openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(5000);
        // The server may search online metadata for every song.
        connection.setReadTimeout(600000);
        try {
            int status = connection.getResponseCode();
            if (status != 200) throw new Exception("서버 응답 오류: " + status);
            try (InputStream ignored = connection.getInputStream()) {
                // The endpoint has completed; fetch its updated song list below.
            }
        } finally {
            connection.disconnect();
        }
        return getSongs();
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

    // 서버(포트 8080) 헬스체크
    public static boolean checkServerHealth(String baseUrl, int timeoutMs) {
        try {
            URL url = new URL(baseUrl + "/api/songs");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            int code = conn.getResponseCode();
            conn.disconnect();
            return code == 200;
        } catch (Exception e) {
            return false;
        }
    }

    // PC 데스크톱 매니저(포트 8088) 대기 상태 확인
    public static JSONObject checkManagerStatus(String hostIp, int timeoutMs) {
        try {
            URL url = new URL("http://" + hostIp + ":8088/api/control/status");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            if (conn.getResponseCode() == 200) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    return new JSONObject(sb.toString());
                }
            }
            conn.disconnect();
        } catch (Exception ignored) {
        }
        return null;
    }

    // 스마트폰에서 PC 데스크톱 매니저(포트 8088)로 서버 시작 명령 전송
    public static boolean remoteStartServer(String hostIp, int timeoutMs) {
        try {
            URL url = new URL("http://" + hostIp + ":8088/api/control/start");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            conn.setDoOutput(true);
            int code = conn.getResponseCode();
            conn.disconnect();
            return code == 200;
        } catch (Exception e) {
            return false;
        }
    }

    // URL에서 호스트 IP 추출 (예: http://192.168.45.247:8080 -> 192.168.45.247)
    public static String extractHost(String urlStr) {
        try {
            URL url = new URL(urlStr);
            return url.getHost();
        } catch (Exception e) {
            return urlStr.replace("http://", "").replace("https://", "").split(":")[0];
        }
    }
}
