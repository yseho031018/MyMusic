package com.mymusic.music_server.controller;

import com.mymusic.music_server.model.Song;
import com.mymusic.music_server.service.MusicService;

import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

@RestController
@RequestMapping("/api/songs")
@CrossOrigin(origins = "*")
public class MusicController {

    private final MusicService musicService;

    // 생성자를 통한 의존성 주입
    public MusicController(MusicService musicService) {
        this.musicService = musicService;
    }

    // 1. 음악 목록 API
    @GetMapping
    public List<Song> getSongs() throws IOException {
        return musicService.getSongs();
    }

    // 2. 음악 스트리밍 API
    @GetMapping("/{id}/stream")
    public ResponseEntity<Resource> streamSong(@PathVariable int id) throws IOException {
        Path path = musicService.getMusicFile(id);

        if (path == null) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new UrlResource(path.toUri());

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("audio/mpeg"))
                .body(resource);
    }

    // 3. 앨범 커버 이미지 API
    @GetMapping("/{id}/cover")
    public ResponseEntity<byte[]> getAlbumCover(@PathVariable int id) throws IOException {
        MusicService.AlbumCover cover = musicService.getAlbumCover(id);

        if (cover == null || cover.data() == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                .contentType(MediaType.parseMediaType(cover.mimeType()))
                .body(cover.data());
    }

    // 4. 메타데이터 및 앨범 커버 전체 새로고침 API
    @PostMapping("/refresh")
    public ResponseEntity<List<Song>> refreshMetadata() throws IOException {
        List<Song> songs = musicService.refreshAllMetadata();
        return ResponseEntity.ok(songs);
    }

    // 4. 음원 파일 업로드 API
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadSong(@RequestParam("file") MultipartFile file) {
        try {
            Song song = musicService.saveUploadedFile(file);
            return ResponseEntity.status(HttpStatus.CREATED).body(song);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("파일 업로드 실패: " + e.getMessage());
        }
    }

    // 5. 음원 파일 삭제 API
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSong(@PathVariable int id) throws IOException {
        boolean deleted = musicService.deleteSongFile(id);
        if (deleted) {
            return ResponseEntity.noContent().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    // 6. 파일 용량 초과 예외 처리
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<String> handleMaxSizeException(org.springframework.web.multipart.MaxUploadSizeExceededException exc) {
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE)
                .body("파일 용량이 너무 큽니다. (최대 100MB까지 업로드 가능)");
    }
}
