package com.mymusic.music_server;

import com.mymusic.music_server.model.Song;
import com.mymusic.music_server.service.MusicService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class MusicServerApplicationTests {

	@Autowired
	private MusicService musicService;

	@Test
	void contextLoads() {
	}

	@Test
	void testMusicMetadataExtraction() throws IOException {
		List<Song> songs = musicService.getSongs();
		assertNotNull(songs);
		assertFalse(songs.isEmpty(), "음악 파일이 존재해야 합니다.");

		System.out.println("=== 추출 및 보정된 음악 메타데이터 목록 ===");
		for (Song s : songs) {
			System.out.println("ID: " + s.getId() + " | Title: " + s.getTitle() + " | Artist: " + s.getArtist() + " | Album: " + s.getAlbum());
			MusicService.AlbumCover cover = musicService.getAlbumCover(s.getId());
			if (cover != null) {
				System.out.println("   -> Album Cover: 보유 (크기: " + cover.data().length + " bytes, MIME: " + cover.mimeType() + ")");
			} else {
				System.out.println("   -> Album Cover: 없음");
			}
		}
	}

	@Test
	void testCleanTitle() {
		assertEquals("ヨルシカ - 火星人", musicService.cleanTitle("ヨルシカ - 火星人（OFFICIAL VIDEO）"));
		assertEquals("ヨルシカ - 火星人", musicService.cleanTitle("ヨルシカ - 火星人（OFFICIAL VIDEO)"));
		assertEquals("ヨルシ카 - 火星人", musicService.cleanTitle("ヨルシ카 - 火星人（)"));
		assertEquals("ヨルシカ - 火星人", musicService.cleanTitle("ヨルシカ - 火星人 (Official Music Video)"));
		assertEquals("ヨルシカ - 火星人", musicService.cleanTitle("ヨルシカ - 火星人 【OFFICIAL MV】"));
		assertEquals("羊文学 - more than words", musicService.cleanTitle("羊文学 - more than words (Official Music Video) [TVアニメ『呪術廻戦』「渋谷事変」エンディングテーマ]"));
		assertEquals("羊文学 - more than words", musicService.cleanTitle("羊文学 - more than words (Official Music Video) [TVアニメ]"));
		assertEquals("東京フラッシュ Vaundy", musicService.cleanTitle("東京フラッシュ  Vaundy ：MUSIC VIDEO"));
		assertEquals("사카낙션 - 괴수(怪獣)", musicService.cleanTitle("사카낙션 - 괴수(怪獣) [가사발음해석]"));
	}

	@Test
	void testStripRedundantArtist() {
		assertEquals("火星人", musicService.stripRedundantArtist("ヨルシカ - 火星人", "ヨルシカ"));
		assertEquals("火星人", musicService.stripRedundantArtist("ヨルシカ - 火星人", "ヨルシカ / n-buna Official"));
		assertEquals("more than words", musicService.stripRedundantArtist("羊文学 - more than words", "羊文学"));
	}

	@Test
	void testUploadAndDelete() throws IOException {
		org.springframework.mock.web.MockMultipartFile mockFile =
				new org.springframework.mock.web.MockMultipartFile(
						"file",
						"Test Artist - Test Title.mp3",
						"audio/mpeg",
						"fake mp3 audio data".getBytes()
				);

		Song uploaded = musicService.saveUploadedFile(mockFile);
		assertNotNull(uploaded);
		assertEquals("Test Title", uploaded.getTitle());
		assertEquals("Test Artist", uploaded.getArtist());

		boolean deleted = musicService.deleteSongFile(uploaded.getId());
		assertTrue(deleted);
	}
}
