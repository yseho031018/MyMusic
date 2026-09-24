package com.example.mymusic;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;

import com.example.mymusic.adapter.SongAdapter;
import com.example.mymusic.network.MusicApi;
import com.example.mymusic.player.MusicPlayer;
import com.example.mymusic.player.TurntableView;

import java.util.Locale;

/** Full-screen record player connected to the same playback session as the library. */
public final class NowPlayingActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private MusicPlayer musicPlayer;
    private TurntableView turntable;
    private TextView title, artist, positionText, durationText, playPause, previous, next, lyricsButton, lyricsTop;
    private SeekBar seekBar;
    private boolean trackingSeek;
    private String trackKey = "";

    private final Runnable progressUpdate = new Runnable() {
        @Override public void run() {
            refreshTrack();
            updateProgress();
            handler.postDelayed(this, 250);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_now_playing);
        View root = findViewById(R.id.nowPlayingRoot);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left + dp(20), bars.top + dp(4), bars.right + dp(20), bars.bottom + dp(4));
            return insets;
        });
        getWindow().setStatusBarColor(0xFF16161D);
        getWindow().setNavigationBarColor(0xFF16161D);

        turntable = findViewById(R.id.turntable);
        title = findViewById(R.id.nowPlayingTitle);
        artist = findViewById(R.id.nowPlayingArtist);
        positionText = findViewById(R.id.nowPlayingPosition);
        durationText = findViewById(R.id.nowPlayingDuration);
        playPause = findViewById(R.id.nowPlayingPlayPause);
        previous = findViewById(R.id.nowPlayingPrevious);
        next = findViewById(R.id.nowPlayingNext);
        lyricsButton = findViewById(R.id.btnNowPlayingLyrics);
        lyricsTop = findViewById(R.id.btnNowPlayingLyricsTop);
        seekBar = findViewById(R.id.nowPlayingSeekBar);

        findViewById(R.id.btnNowPlayingBack).setOnClickListener(v -> finish());
        lyricsTop.setOnClickListener(v -> openLyrics());
        lyricsButton.setOnClickListener(v -> openLyrics());
        previous.setOnClickListener(v -> musicPlayer.previous());
        next.setOnClickListener(v -> musicPlayer.next());
        playPause.setOnClickListener(v -> {
            if (musicPlayer.isPlaying()) musicPlayer.pause(); else musicPlayer.resume();
            updatePlaybackState();
        });
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (!fromUser) return;
                long duration = musicPlayer.getDuration();
                if (duration > 0 && duration != C.TIME_UNSET) {
                    positionText.setText(formatTime(duration * progress / 1000));
                }
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { trackingSeek = true; }
            @Override public void onStopTrackingTouch(SeekBar bar) {
                trackingSeek = false;
                long duration = musicPlayer.getDuration();
                if (duration > 0 && duration != C.TIME_UNSET) {
                    musicPlayer.seekTo(duration * bar.getProgress() / 1000);
                }
            }
        });

        musicPlayer = new MusicPlayer(this);
        musicPlayer.addListener(new Player.Listener() {
            @Override public void onMediaItemTransition(MediaItem mediaItem, int reason) { refreshTrack(); }
            @Override public void onIsPlayingChanged(boolean isPlaying) { updatePlaybackState(); }
            @Override public void onPlaybackStateChanged(int state) { updateProgress(); }
        });
        musicPlayer.setOnConnectedListener(this::refreshTrack);
        updatePlaybackState();
    }

    @Override protected void onStart() {
        super.onStart();
        turntable.setAnimationsEnabled(true);
        handler.post(progressUpdate);
    }

    @Override protected void onStop() {
        handler.removeCallbacks(progressUpdate);
        turntable.setAnimationsEnabled(false);
        super.onStop();
    }

    @Override protected void onDestroy() {
        musicPlayer.release();
        super.onDestroy();
    }

    private void refreshTrack() {
        MediaItem item = musicPlayer.getCurrentMediaItem();
        String key = item == null ? "NO_TRACK" : item.mediaId + "|"
                + (item.localConfiguration == null ? "" : item.localConfiguration.uri);
        if (key.equals(trackKey)) {
            updatePlaybackState();
            return;
        }
        trackKey = key;
        if (item == null) {
            title.setText("재생 중인 음악 없음");
            artist.setText("목록에서 음악을 재생해 주세요");
            turntable.setImageResource(R.drawable.ic_music_placeholder);
            seekBar.setProgress(0);
            positionText.setText("0:00");
            durationText.setText("0:00");
        } else {
            title.setText(item.mediaMetadata.title == null ? "제목 없음" : item.mediaMetadata.title);
            artist.setText(item.mediaMetadata.artist == null ? "" : item.mediaMetadata.artist);
            seekBar.setProgress(0);
            positionText.setText("0:00");
            durationText.setText("0:00");
            turntable.setImageResource(R.drawable.ic_music_placeholder);
            try {
                int songId = Integer.parseInt(item.mediaId);
                Uri artwork = item.mediaMetadata.artworkUri;
                MusicApi api = artwork != null && artwork.getScheme() != null && artwork.getAuthority() != null
                        ? new MusicApi(artwork.getScheme() + "://" + artwork.getAuthority()) : null;
                SongAdapter.loadCover(api, getFilesDir(), songId, turntable);
            } catch (NumberFormatException ignored) {}
        }
        updatePlaybackState();
        updateProgress();
    }

    private void updatePlaybackState() {
        boolean hasTrack = musicPlayer.getCurrentMediaItem() != null;
        boolean playing = musicPlayer.isPlaying();
        turntable.setPlaybackState(hasTrack, playing);
        playPause.setText(playing ? "Ⅱ" : "▶");
        playPause.setContentDescription(playing ? "일시정지" : "재생");
        playPause.setEnabled(hasTrack);
        playPause.setAlpha(hasTrack ? 1f : .45f);
        seekBar.setEnabled(hasTrack);
        lyricsButton.setEnabled(hasTrack);
        lyricsButton.setAlpha(hasTrack ? 1f : .45f);
        lyricsTop.setEnabled(hasTrack);
        lyricsTop.setAlpha(hasTrack ? 1f : .45f);
        boolean canNavigate = musicPlayer.getMediaItemCount() > 1;
        previous.setEnabled(canNavigate);
        next.setEnabled(canNavigate);
        previous.setAlpha(canNavigate ? 1f : .4f);
        next.setAlpha(canNavigate ? 1f : .4f);
    }

    private void updateProgress() {
        long duration = musicPlayer.getDuration();
        if (duration <= 0 || duration == C.TIME_UNSET) return;
        long position = Math.max(0, musicPlayer.getCurrentPosition());
        if (!trackingSeek) {
            seekBar.setProgress((int) Math.min(1000, position * 1000 / duration));
            positionText.setText(formatTime(position));
        }
        durationText.setText(formatTime(duration));
    }

    private void openLyrics() {
        if (musicPlayer.getCurrentMediaItem() != null) {
            startActivity(new Intent(this, LyricsActivity.class));
        }
    }

    private static String formatTime(long milliseconds) {
        long seconds = Math.max(0, milliseconds) / 1000;
        return String.format(Locale.getDefault(), "%d:%02d", seconds / 60, seconds % 60);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
