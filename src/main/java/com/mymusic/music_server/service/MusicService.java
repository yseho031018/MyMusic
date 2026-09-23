package com.mymusic.music_server.service;

import com.mpatric.mp3agic.ID3v1;
import com.mpatric.mp3agic.ID3v2;
import com.mpatric.mp3agic.Mp3File;
import com.mymusic.music_server.model.Song;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Service
public class MusicService {

    public record AlbumCover(byte[] data, String mimeType) {}

    public record OnlineTrackInfo(String title, String artist, String album, String artworkUrl) {}

    public static class CachedMetadata {
        public int id;
        public String title;
        public String artist;
        public String album;
        public String artworkUrl;
        public long lastModified;

        public CachedMetadata() {}

        public CachedMetadata(int id, String title, String artist, String album, String artworkUrl, long lastModified) {
            this.id = id;
            this.title = title;
            this.artist = artist;
            this.album = album;
            this.artworkUrl = artworkUrl;
            this.lastModified = lastModified;
        }
    }

    private final Path musicDir = Path.of("D:/Dev/seho/Project/music-server/music");
    private final Path cacheDir = musicDir.resolve(".cache");
    private final Path coversDir = cacheDir.resolve("covers");
    private final Path metadataFile = cacheDir.resolve("metadata_cache.json");

    private final Map<Integer, CachedMetadata> metadataCache = new ConcurrentHashMap<>();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(coversDir);
            loadCacheFromDisk();
        } catch (Exception e) {
            System.err.println("캐시 초기화 경고: " + e.getMessage());
        }
    }

    // 1. 음악 폴더에서 MP3 파일 검색
    private List<Path> getMusicFiles() throws IOException {
        Files.createDirectories(musicDir);

        try (Stream<Path> files = Files.list(musicDir)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(path -> path
                            .getFileName()
                            .toString()
                            .toLowerCase()
                            .endsWith(".mp3"))
                    .sorted()
                    .toList();
        }
    }

    // 파일명 기반 고유 ID 생성 (곡 순서가 바뀌어도 ID가 영구 고정됨)
    public int generateId(Path path) {
        return path.getFileName().toString().hashCode() & 0x7fffffff;
    }

    // 2. 음악 목록 생성 (메타데이터 자동 추출 및 캐시 활용)
    public List<Song> getSongs() throws IOException {
        List<Path> files = getMusicFiles();
        List<Song> songs = new ArrayList<>();

        for (Path file : files) {
            songs.add(extractSong(generateId(file), file));
        }

        return songs;
    }

    // 3. ID에 해당하는 음악 파일 조회
    public Path getMusicFile(int id) throws IOException {
        List<Path> files = getMusicFiles();
        for (Path file : files) {
            if (generateId(file) == id) {
                return file;
            }
        }
        return null;
    }

    // 4. 앨범 커버 이미지 추출 (임베디드 -> 로컬 이미지 파일 -> 디스크 캐시 -> 온라인 자동 다운로드)
    public AlbumCover getAlbumCover(int id) throws IOException {
        Path path = getMusicFile(id);
        if (path == null) {
            return null;
        }

        // 1) MP3 파일 내 자체 임베디드 ID3v2 앨범 커버 확인
        try {
            Mp3File mp3File = new Mp3File(path.toAbsolutePath().toString());
            if (mp3File.hasId3v2Tag()) {
                ID3v2 id3v2 = mp3File.getId3v2Tag();
                byte[] albumImage = id3v2.getAlbumImage();
                if (albumImage != null && albumImage.length > 0) {
                    String mimeType = id3v2.getAlbumImageMimeType();
                    if (mimeType == null || mimeType.isBlank()) {
                        mimeType = "image/jpeg";
                    }
                    return new AlbumCover(albumImage, mimeType);
                }
            }
        } catch (Exception ignored) {}

        // 2) 음원 폴더 내 동일 이름의 이미지 파일 확인 (예: 곡명.jpg, 곡명.png, cover.jpg)
        AlbumCover localCover = findLocalCover(path);
        if (localCover != null) {
            return localCover;
        }

        // 3) 디스크 캐시 (.cache/covers/{id}.jpg) 확인
        Path cachedCoverPath = coversDir.resolve(id + ".jpg");
        if (Files.exists(cachedCoverPath)) {
            try {
                byte[] data = Files.readAllBytes(cachedCoverPath);
                if (data.length > 0) {
                    return new AlbumCover(data, "image/jpeg");
                }
            } catch (IOException ignored) {}
        }

        // 4) 캐시된 온라인 커버 URL을 통한 다운로드 및 디스크 캐싱
        CachedMetadata cached = metadataCache.get(id);
        String artworkUrl = (cached != null) ? cached.artworkUrl : null;

        if (artworkUrl == null || artworkUrl.isBlank()) {
            // 아직 온라인 조회가 안 된 경우 즉시 보정 시도
            extractSong(id, path);
            cached = metadataCache.get(id);
            artworkUrl = (cached != null) ? cached.artworkUrl : null;
        }

        if (artworkUrl != null && !artworkUrl.isBlank()) {
            byte[] downloaded = downloadImage(artworkUrl);
            if (downloaded != null && downloaded.length > 0) {
                try {
                    Files.createDirectories(coversDir);
                    Files.write(cachedCoverPath, downloaded);
                    return new AlbumCover(downloaded, "image/jpeg");
                } catch (IOException ignored) {}
            }
        }

        return null;
    }

    // 로컬 동반 이미지 파일(동일 이름의 .jpg/.png 또는 cover.jpg) 탐색
    private AlbumCover findLocalCover(Path musicFile) {
        String baseName = musicFile.getFileName().toString().replaceFirst("(?i)\\.mp3$", "");
        String[] extensions = {".jpg", ".jpeg", ".png", ".webp"};

        for (String ext : extensions) {
            Path companion = musicDir.resolve(baseName + ext);
            if (Files.exists(companion) && Files.isRegularFile(companion)) {
                try {
                    byte[] data = Files.readAllBytes(companion);
                    String mime = ext.contains("png") ? "image/png" : (ext.contains("webp") ? "image/webp" : "image/jpeg");
                    return new AlbumCover(data, mime);
                } catch (IOException ignored) {}
            }
        }

        for (String name : List.of("cover.jpg", "cover.png", "folder.jpg", "folder.png")) {
            Path folderImg = musicDir.resolve(name);
            if (Files.exists(folderImg) && Files.isRegularFile(folderImg)) {
                try {
                    byte[] data = Files.readAllBytes(folderImg);
                    String mime = name.endsWith(".png") ? "image/png" : "image/jpeg";
                    return new AlbumCover(data, mime);
                } catch (IOException ignored) {}
            }
        }

        return null;
    }

    // 5. 음원 파일 업로드 저장
    public Song saveUploadedFile(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("업로드할 파일이 비어있습니다.");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".mp3")) {
            throw new IllegalArgumentException("MP3 파일만 업로드할 수 있습니다.");
        }

        String safeName = new File(originalFilename).getName();
        Path targetPath = musicDir.resolve(safeName);

        Files.createDirectories(musicDir);
        Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

        int id = generateId(targetPath);
        // 신규 업로드 시 기존 캐시 갱신
        metadataCache.remove(id);
        return extractSong(id, targetPath);
    }

    // 6. 음원 파일 삭제
    public boolean deleteSongFile(int id) throws IOException {
        Path path = getMusicFile(id);
        if (path != null && Files.exists(path)) {
            Files.delete(path);
            metadataCache.remove(id);
            try {
                Files.deleteIfExists(coversDir.resolve(id + ".jpg"));
            } catch (IOException ignored) {}
            saveCacheToDisk();
            return true;
        }
        return false;
    }

    // 7. 전체 메타데이터 및 앨범 커버 강제 새로고침
    public List<Song> refreshAllMetadata() throws IOException {
        metadataCache.clear();
        try {
            Files.deleteIfExists(metadataFile);
        } catch (IOException ignored) {}
        return getSongs();
    }

    // MP3 태그 파싱, 파일명 스마트 분석 및 온라인 메타데이터 보정 로직
    public Song extractSong(int id, Path path) {
        long lastMod = 0;
        try {
            lastMod = Files.getLastModifiedTime(path).toMillis();
        } catch (IOException ignored) {}

        // 유효한 캐시가 있으면 즉시 반환
        CachedMetadata cached = metadataCache.get(id);
        if (cached != null && cached.lastModified == lastMod && !"Unknown Artist".equals(cached.artist) && !"Unknown Album".equals(cached.album)) {
            return new Song(cached.id, cached.title, cached.artist, cached.album);
        }

        String fileName = path.getFileName().toString();
        String rawName = fileName.replaceFirst("(?i)\\.mp3$", "").trim();
        String title = rawName;
        String artist = "Unknown Artist";
        String album = "Unknown Album";
        boolean hasEmbeddedCover = false;

        // 1. ID3 태그 읽기
        try {
            Mp3File mp3File = new Mp3File(path.toAbsolutePath().toString());
            if (mp3File.hasId3v2Tag()) {
                ID3v2 id3v2 = mp3File.getId3v2Tag();
                if (id3v2.getTitle() != null && !id3v2.getTitle().isBlank()) {
                    title = id3v2.getTitle().trim();
                }
                if (id3v2.getArtist() != null && !id3v2.getArtist().isBlank()) {
                    artist = id3v2.getArtist().trim();
                }
                if (id3v2.getAlbum() != null && !id3v2.getAlbum().isBlank()) {
                    album = id3v2.getAlbum().trim();
                }
                byte[] img = id3v2.getAlbumImage();
                if (img != null && img.length > 0) {
                    hasEmbeddedCover = true;
                }
            } else if (mp3File.hasId3v1Tag()) {
                ID3v1 id3v1 = mp3File.getId3v1Tag();
                if (id3v1.getTitle() != null && !id3v1.getTitle().isBlank()) {
                    title = id3v1.getTitle().trim();
                }
                if (id3v1.getArtist() != null && !id3v1.getArtist().isBlank()) {
                    artist = id3v1.getArtist().trim();
                }
                if (id3v1.getAlbum() != null && !id3v1.getAlbum().isBlank()) {
                    album = id3v1.getAlbum().trim();
                }
            }
        } catch (Exception ignored) {}

        // 2. 파일명 스마트 분리 (전각 대시 －, en-dash –, em-dash —, 언더바 _, 슬래시 / 지원)
        String normName = rawName.replaceAll("[－–—]", " - ")
                .replaceAll("\\s{2,}", " ")
                .trim();

        if ("Unknown Artist".equals(artist) && normName.contains(" - ")) {
            String[] parts = normName.split(" - ", 2);
            artist = parts[0].trim();
            title = parts[1].trim();
        }

        // 3. 곡 제목 정제 (MV/OFFICIAL, 콜론 태그, 가사 태그, 괄호 부가설명)
        title = cleanTitle(title);

        // 4. 제목 앞에 중복으로 들어간 아티스트명 제거 (예: 'ヨルシカ - 火星人' -> '火星人', '羊文学 - more than words' -> 'more than words')
        title = stripRedundantArtist(title, artist);

        // 5. 누락된 정보(가수, 앨범, 커버)가 있는 경우 Apple iTunes Search API로 고화질 정보 탐색
        String artworkUrl = null;
        if (cached != null) {
            artworkUrl = cached.artworkUrl;
        }

        boolean needOnlineSearch = "Unknown Artist".equals(artist) ||
                "Unknown Album".equals(album) ||
                (!hasEmbeddedCover && (artworkUrl == null || artworkUrl.isBlank()));

        if (needOnlineSearch) {
            OnlineTrackInfo online = searchOnline(title, artist, rawName);
            if (online != null) {
                if ("Unknown Artist".equals(artist) && online.artist() != null && !online.artist().isBlank()) {
                    artist = online.artist();
                }
                if ("Unknown Album".equals(album) && online.album() != null && !online.album().isBlank()) {
                    album = online.album();
                }
                if (online.artworkUrl() != null && !online.artworkUrl().isBlank()) {
                    artworkUrl = online.artworkUrl();
                }
                // 만약 제목에 가수명이 섞여 있었거나 미정제 상태였다면 온라인 정식 곡명으로 정제
                if (title.contains("Vaundy") || title.contains("vaundy") || title.equalsIgnoreCase(rawName)) {
                    if (online.title() != null && !online.title().isBlank()) {
                        title = online.title();
                    }
                }
            }
        }

        // 6. 캐시 갱신 및 디스크 저장
        CachedMetadata newMeta = new CachedMetadata(id, title, artist, album, artworkUrl, lastMod);
        metadataCache.put(id, newMeta);
        saveCacheToDisk();

        return new Song(id, title, artist, album);
    }

    // 제목 앞에 붙은 중복 아티스트명 정제
    public String stripRedundantArtist(String title, String artist) {
        if (title == null || artist == null || "Unknown Artist".equals(artist)) {
            return title;
        }

        // 기본 '아티스트 - ' 패턴 검사
        String prefix = artist + " - ";
        if (title.toLowerCase().startsWith(prefix.toLowerCase())) {
            return title.substring(prefix.length()).trim();
        }

        // 아티스트가 '가수 / 피처링' 형태인 경우 첫 번째 주요 아티스트로 검사
        if (artist.contains("/")) {
            String primary = artist.split("/")[0].trim();
            if (!primary.isBlank()) {
                String pPrefix = primary + " - ";
                if (title.toLowerCase().startsWith(pPrefix.toLowerCase())) {
                    return title.substring(pPrefix.length()).trim();
                }
            }
        }

        return title;
    }

    // 곡 제목 정제: 전각/반각 괄호 속 OFFICIAL VIDEO/MV, 콜론 태그, 가사 태그, 타이업(애니/OST) 태그, 대괄호 부가설명 및 빈 괄호 제거
    public String cleanTitle(String rawTitle) {
        if (rawTitle == null) {
            return "";
        }
        String title = rawTitle;

        // 1. 끝부분의 콜론 기반 비디오/MV 태그 제거 (예: " ：MUSIC VIDEO", ": MV", ": OFFICIAL VIDEO")
        title = title.replaceAll("(?i)[\\s:：]+(official\\s*(music\\s*)?(video|audio|mv|lyric\\s*video)?|music\\s*video|official|mv|video|audio|lyric\\s*video)\\s*$", "");

        // 2. 괄호 속 OFFICIAL / MV / VIDEO / AUDIO / LYRIC 태그 제거
        String tagPattern = "(?i)\\s*(official\\s*(music\\s*)?(video|audio|mv|lyric\\s*video)?|music\\s*video|official|mv|video|audio|lyric\\s*video)\\s*";
        title = title.replaceAll("\\(" + tagPattern + "\\)", "");
        title = title.replaceAll("（" + tagPattern + "）", "");
        title = title.replaceAll("（" + tagPattern + "\\)", "");
        title = title.replaceAll("\\(" + tagPattern + "）", "");
        title = title.replaceAll("\\[" + tagPattern + "\\]", "");
        title = title.replaceAll("【" + tagPattern + "】", "");

        // 3. 가사 및 자막 관련 대괄호/괄호 태그 제거 (예: [가사발음해석], [한글자막], [Color Coded Lyrics])
        String lyricTagPattern = "(?i)[\\s]*[\\[【(（][^\\]】)）]*(가사|발음|해석|자막|lyrics?|color coded|sub|eng|han|rom)[\\]】)）]";
        title = title.replaceAll(lyricTagPattern, "");

        // 4. 제목 끝부분에 오는 대괄호 태그([...], 【...】) 반복 제거 (애니메이션 타이업, 앨범 정보 등)
        while (title.matches(".*\\s*(\\[[^\\]]*\\]|【[^】]*】)\\s*$")) {
            title = title.replaceAll("\\s*(\\[[^\\]]*\\]|【[^】]*】)\\s*$", "");
        }

        // 5. 타이업/애니메이션/OST 관련 괄호 태그 제거 (둥근 괄호 안에 tv 애니, ost 등이 있는 경우)
        String tieUpRegex = "(?i)[^)]*(tv\\s*アニメ|アニメ|anime|ost|soundtrack|theme|ending|opening|엔딩|오프닝|주제가|挿入歌|テーマ|エンディング|オープニング)[^)]*";
        title = title.replaceAll("\\(" + tieUpRegex + "\\)", "");
        title = title.replaceAll("（" + tieUpRegex + "）", "");
        title = title.replaceAll("（" + tieUpRegex + "\\)", "");

        // 6. 내용이 비어있는 빈 괄호 제거
        title = title.replaceAll("(\\(\\s*\\)|（\\s*）|（\\s*\\)|\\[\\s*\\]|【\\s*】)", "");

        // 7. 연속된 공백 하나로 축소 및 양끝 공백 제거
        title = title.replaceAll("\\s{2,}", " ");

        return title.trim();
    }

    // Apple iTunes Search API를 통한 온라인 음원 정보 및 600x600 고화질 커버 검색
    public OnlineTrackInfo searchOnline(String title, String artist, String rawFileName) {
        List<String> queryCandidates = new ArrayList<>();

        // 주요 아티스트 추출 (예: 'ヨルシカ / n-buna Official' -> 'ヨルシカ')
        String primaryArtist = artist;
        if (primaryArtist != null && primaryArtist.contains("/")) {
            primaryArtist = primaryArtist.split("/")[0].trim();
        }

        // 괄호 속 외국어/원제 추출 (예: '괴수(怪獣)' -> '怪獣')
        String altTitle = null;
        Matcher m = Pattern.compile("[\\(（]([\\u3040-\\u30ff\\u4e00-\\u9faf]+)[\\)）]").matcher(title);
        if (m.find()) {
            altTitle = m.group(1).trim();
        }

        // 1) 아티스트 + 곡명
        if (primaryArtist != null && !"Unknown Artist".equals(primaryArtist)) {
            queryCandidates.add(primaryArtist + " " + title);
            if (altTitle != null) {
                queryCandidates.add(primaryArtist + " " + altTitle);
                // 사카낙션 같은 대표 일본 아티스트 매핑
                if (primaryArtist.equals("사카낙션")) {
                    queryCandidates.add("サカナクション " + altTitle);
                }
            }
        }

        // 2) 원제 단독 검색
        if (altTitle != null && !altTitle.isBlank()) {
            queryCandidates.add(altTitle);
        }

        // 3) 곡명 단독 검색 (곡명 안에 아티스트가 포함된 경우 포함, 예: '東京フラッシュ Vaundy')
        queryCandidates.add(title);

        // 4) 파일명에서 비디오 태그를 뺀 검색어
        String cleanRaw = cleanTitle(rawFileName).replaceAll("[－–—_]", " ");
        if (!queryCandidates.contains(cleanRaw)) {
            queryCandidates.add(cleanRaw);
        }

        // 각 검색어로 iTunes API 조회
        for (String query : queryCandidates) {
            if (query == null || query.isBlank()) continue;

            // 일본어/동양권 음원은 country=JP를 우선 적용
            boolean isAsian = query.matches(".*[\\u3040-\\u30ff\\u4e00-\\u9faf\\uac00-\\ud7a3].*");
            String[] countries = isAsian ? new String[]{"JP", "KR", "US"} : new String[]{"US", "JP"};

            for (String country : countries) {
                try {
                    String url = "https://itunes.apple.com/search?term=" +
                            URLEncoder.encode(query, StandardCharsets.UTF_8) +
                            "&country=" + country +
                            "&entity=song&limit=3";

                    HttpRequest req = HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .timeout(Duration.ofSeconds(3))
                            .header("User-Agent", "Mozilla/5.0")
                            .GET()
                            .build();

                    HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                    if (resp.statusCode() == 200) {
                        OnlineTrackInfo info = parseItunesResponse(resp.body());
                        if (info != null) {
                            return info;
                        }
                    }
                } catch (Exception e) {
                    // 네트워크 타임아웃 시 다음 후보 진행
                }
            }
        }

        return null;
    }

    // iTunes 응답 JSON 파싱
    public OnlineTrackInfo parseItunesResponse(String json) {
        if (json == null || !json.contains("\"results\"")) return null;

        String trackName = extractJsonString(json, "trackName");
        String artistName = extractJsonString(json, "artistName");
        String collectionName = extractJsonString(json, "collectionName");
        String artworkUrl100 = extractJsonString(json, "artworkUrl100");

        if (trackName == null && artistName == null) return null;

        String artworkUrl600 = null;
        if (artworkUrl100 != null) {
            artworkUrl600 = artworkUrl100.replace("100x100bb.jpg", "600x600bb.jpg");
        }

        return new OnlineTrackInfo(trackName, artistName, collectionName, artworkUrl600);
    }

    // 온라인 커버 이미지 바이너리 다운로드
    private byte[] downloadImage(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .header("User-Agent", "Mozilla/5.0")
                    .GET()
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() == 200) {
                return response.body();
            }
        } catch (Exception e) {
            System.err.println("커버 다운로드 실패: " + url + " - " + e.getMessage());
        }
        return null;
    }

    // 디스크에서 캐시 읽기
    private synchronized void loadCacheFromDisk() {
        if (!Files.exists(metadataFile)) return;
        try {
            String content = Files.readString(metadataFile, StandardCharsets.UTF_8);
            Pattern objPattern = Pattern.compile("\\{[^\\}]*\\}");
            Matcher m = objPattern.matcher(content);
            while (m.find()) {
                String obj = m.group();
                String idStr = extractJsonNumber(obj, "id");
                if (idStr != null) {
                    int id = Integer.parseInt(idStr);
                    String title = extractJsonString(obj, "title");
                    String artist = extractJsonString(obj, "artist");
                    String album = extractJsonString(obj, "album");
                    String artworkUrl = extractJsonString(obj, "artworkUrl");
                    String modStr = extractJsonNumber(obj, "lastModified");
                    long mod = modStr != null ? Long.parseLong(modStr) : 0;
                    metadataCache.put(id, new CachedMetadata(id, title, artist, album, artworkUrl, mod));
                }
            }
        } catch (Exception e) {
            System.err.println("캐시 파일 로딩 경고: " + e.getMessage());
        }
    }

    // 디스크로 캐시 저장
    private synchronized void saveCacheToDisk() {
        try {
            Files.createDirectories(cacheDir);
            StringBuilder sb = new StringBuilder("[\n");
            int count = 0;
            for (CachedMetadata m : metadataCache.values()) {
                if (count++ > 0) sb.append(",\n");
                sb.append("  {");
                sb.append("\"id\":").append(m.id).append(",");
                sb.append("\"title\":").append(escapeJson(m.title)).append(",");
                sb.append("\"artist\":").append(escapeJson(m.artist)).append(",");
                sb.append("\"album\":").append(escapeJson(m.album)).append(",");
                sb.append("\"artworkUrl\":").append(escapeJson(m.artworkUrl)).append(",");
                sb.append("\"lastModified\":").append(m.lastModified);
                sb.append("}");
            }
            sb.append("\n]");
            Files.writeString(metadataFile, sb.toString(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.err.println("캐시 파일 저장 경고: " + e.getMessage());
        }
    }

    // JSON 유틸리티
    private static String escapeJson(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        sb.append("\"");
        return sb.toString();
    }

    private static String extractJsonString(String json, String key) {
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:\\\\\"|[^\"])*)\"");
        Matcher m = p.matcher(json);
        if (m.find()) {
            return unescapeJson(m.group(1));
        }
        return null;
    }

    private static String extractJsonNumber(String json, String key) {
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*([0-9]+)");
        Matcher m = p.matcher(json);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    private static String unescapeJson(String input) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '\\' && i + 1 < input.length()) {
                char next = input.charAt(i + 1);
                if (next == '"') { sb.append('"'); i++; }
                else if (next == '\\') { sb.append('\\'); i++; }
                else if (next == '/') { sb.append('/'); i++; }
                else if (next == 'b') { sb.append('\b'); i++; }
                else if (next == 'f') { sb.append('\f'); i++; }
                else if (next == 'n') { sb.append('\n'); i++; }
                else if (next == 'r') { sb.append('\r'); i++; }
                else if (next == 't') { sb.append('\t'); i++; }
                else if (next == 'u' && i + 5 < input.length()) {
                    String hex = input.substring(i + 2, i + 6);
                    try {
                        sb.append((char) Integer.parseInt(hex, 16));
                        i += 5;
                    } catch (NumberFormatException e) {
                        sb.append(c);
                    }
                } else {
                    sb.append(c);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
