package com.example.mymusic.player;

import android.content.Context;
import android.net.Uri;

import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.MediaSession;

import java.io.File;

public class MusicPlayer {

    private final ExoPlayer player;
    private final MediaSession mediaSession;

    public MusicPlayer(Context context) {

        // 1. 오디오 포커스 자동 처리 (전화 올 때 음악 정지, 안내 음성 시 볼륨 줄임)
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build();

        player = new ExoPlayer.Builder(context)
                .setAudioAttributes(audioAttributes, true)
                .build();

        // 2. 미디어 세션 연결 (이어폰 버튼, 블루투스 리모컨, 스마트워치 연동)
        mediaSession = new MediaSession.Builder(context, player).build();
    }

    // 새로운 음악 재생 (메타데이터 포함)
    public void play(String url, String title, String artist) {

        MediaMetadata.Builder metaBuilder = new MediaMetadata.Builder();
        if (title != null) metaBuilder.setTitle(title);
        if (artist != null) metaBuilder.setArtist(artist);

        MediaItem mediaItem = new MediaItem.Builder()
                .setUri(url)
                .setMediaMetadata(metaBuilder.build())
                .build();

        player.setMediaItem(mediaItem);
        player.prepare();
        player.play();
    }

    public void play(String url) {
        play(url, null, null);
    }

    // 일시정지
    public void pause() {
        player.pause();
    }

    // 일시정지한 음악 다시 재생
    public void resume() {
        player.play();
    }

    // 현재 재생 중인지 확인
    public boolean isPlaying() {
        return player.isPlaying();
    }

    // 현재 재생 위치 (밀리초)
    public long getCurrentPosition() {
        return player.getCurrentPosition();
    }

    // 음악 전체 길이 (밀리초)
    public long getDuration() {
        return player.getDuration();
    }

    // 원하는 위치로 이동
    public void seekTo(long position) {
        player.seekTo(position);
    }

    // 재생 상태 변경 감지
    public void addListener(Player.Listener listener) {
        player.addListener(listener);
    }

    // 플레이어 및 세션 종료
    public void release() {
        if (mediaSession != null) {
            mediaSession.release();
        }
        player.release();
    }

    // 로컬 파일 재생 (메타데이터 포함)
    public void playLocal(File file, String title, String artist) {

        MediaMetadata.Builder metaBuilder = new MediaMetadata.Builder();
        if (title != null) metaBuilder.setTitle(title);
        if (artist != null) metaBuilder.setArtist(artist);

        MediaItem mediaItem = new MediaItem.Builder()
                .setUri(Uri.fromFile(file))
                .setMediaMetadata(metaBuilder.build())
                .build();

        player.setMediaItem(mediaItem);
        player.prepare();
        player.play();
    }

    public void playLocal(File file) {
        playLocal(file, null, null);
    }

}
