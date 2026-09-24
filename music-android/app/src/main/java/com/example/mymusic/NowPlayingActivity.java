package com.example.mymusic;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
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
import com.example.mymusic.lyrics.Lyrics;
import com.example.mymusic.lyrics.LyricsRepository;
import com.example.mymusic.network.MusicApi;
import com.example.mymusic.player.AlbumAccent;
import com.example.mymusic.player.AudioLevels;
import com.example.mymusic.player.HorizontalWaveformView;
import com.example.mymusic.player.MusicPlayer;
import com.example.mymusic.player.TurntableView;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Full-screen record player connected to the same playback session as the library. */
public final class NowPlayingActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService lyricWorker = Executors.newSingleThreadExecutor();
    private final ExecutorService paletteWorker = Executors.newSingleThreadExecutor();
    private MusicPlayer musicPlayer;
    private LyricsRepository lyricsRepository;
    private TurntableView turntable;
    private TextView title, artist, positionText, durationText, playPause, previous, next;
    private TextView backButton, headerTitle;
    private TextView captionButton, caption, captionPrevious, captionNext;
    private View captionContainer, captionLines;
    private HorizontalWaveformView captionWaveform;
    private SeekBar seekBar;
    private boolean trackingSeek;
    private boolean captionsEnabled;
    private boolean captionEmbeddedOnly;
    private int syncOffsetMs;
    private int captionGeneration;
    private Future<?> captionLoad;
    private ValueAnimator accentTransition;
    private int currentAccent = AlbumAccent.DEFAULT;
    private int accentGeneration;
    private Lyrics captionLyrics = Lyrics.fromText("", "");
    private String trackKey = "";

    private final Runnable progressUpdate = new Runnable() {
        @Override public void run() {
            refreshTrack();
            updateProgress();
            handler.postDelayed(this, 100);
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
        backButton = findViewById(R.id.btnNowPlayingBack);
        headerTitle = findViewById(R.id.nowPlayingHeaderTitle);
        title = findViewById(R.id.nowPlayingTitle);
        artist = findViewById(R.id.nowPlayingArtist);
        positionText = findViewById(R.id.nowPlayingPosition);
        durationText = findViewById(R.id.nowPlayingDuration);
        playPause = findViewById(R.id.nowPlayingPlayPause);
        previous = findViewById(R.id.nowPlayingPrevious);
        next = findViewById(R.id.nowPlayingNext);
        captionButton = findViewById(R.id.btnNowPlayingCaption);
        captionContainer = findViewById(R.id.nowPlayingCaptionContainer);
        captionLines = findViewById(R.id.nowPlayingCaptionLines);
        captionWaveform = findViewById(R.id.nowPlayingCaptionWaveform);
        captionPrevious = findViewById(R.id.nowPlayingCaptionPrevious);
        caption = findViewById(R.id.nowPlayingCaption);
        captionNext = findViewById(R.id.nowPlayingCaptionNext);
        seekBar = findViewById(R.id.nowPlayingSeekBar);
        applyAccent(currentAccent);

        backButton.setOnClickListener(v -> finish());
        captionContainer.setOnClickListener(v -> openLyrics());
        captionsEnabled = getPreferences(MODE_PRIVATE).getBoolean("synced_caption", false);
        captionButton.setOnClickListener(v -> {
            captionsEnabled = !captionsEnabled;
            getPreferences(MODE_PRIVATE).edit().putBoolean("synced_caption", captionsEnabled).apply();
            updateCaptionVisibility();
            if (captionsEnabled) loadCaption();
            else cancelCaptionLoad();
        });
        updateCaptionVisibility();
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

        lyricsRepository = new LyricsRepository(this);
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
        AudioLevels.setEnabled(true);
        turntable.setAnimationsEnabled(true);
        captionWaveform.setAnimationsEnabled(true);
        syncOffsetMs = readSyncOffset();
        MediaItem item = musicPlayer.getCurrentMediaItem();
        if (captionsEnabled && item != null && readEmbeddedOnly() != captionEmbeddedOnly) loadCaption();
        handler.post(progressUpdate);
    }

    @Override protected void onStop() {
        handler.removeCallbacks(progressUpdate);
        turntable.setAnimationsEnabled(false);
        captionWaveform.setAnimationsEnabled(false);
        AudioLevels.setEnabled(false);
        super.onStop();
    }

    @Override protected void onDestroy() {
        accentGeneration++;
        if (accentTransition != null) accentTransition.cancel();
        paletteWorker.shutdownNow();
        cancelCaptionLoad();
        lyricWorker.shutdownNow();
        musicPlayer.release();
        super.onDestroy();
    }

    private void refreshTrack() {
        MediaItem item = musicPlayer.getCurrentMediaItem();
        String key = item == null ? "NO_TRACK" : item.mediaId + "|"
                + (item.localConfiguration == null ? "" : item.localConfiguration.uri);
        if (key.equals(trackKey)) {
            return;
        }
        trackKey = key;
        int accentRequest = ++accentGeneration;
        cancelCaptionLoad();
        captionLyrics = Lyrics.fromText("", "");
        clearCaptionNeighbors();
        caption.setText("");
        if (item != null) showCaptionWaveform();
        syncOffsetMs = readSyncOffset();
        if (item == null) {
            animateAccent(AlbumAccent.DEFAULT);
            title.setText("재생 중인 음악 없음");
            artist.setText("목록에서 음악을 재생해 주세요");
            turntable.setImageResource(R.drawable.ic_music_placeholder);
            turntable.setPlaybackProgress(0f);
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
            turntable.setPlaybackProgress(0f);
            try {
                int songId = Integer.parseInt(item.mediaId);
                Uri artwork = item.mediaMetadata.artworkUri;
                MusicApi api = artwork != null && artwork.getScheme() != null && artwork.getAuthority() != null
                        ? new MusicApi(artwork.getScheme() + "://" + artwork.getAuthority()) : null;
                SongAdapter.loadCover(api, getFilesDir(), songId, turntable,
                        cover -> onCoverLoaded(cover, accentRequest));
            } catch (NumberFormatException ignored) {
                animateAccent(AlbumAccent.DEFAULT);
            }
        }
        updatePlaybackState();
        updateProgress();
        if (captionsEnabled && item != null) loadCaption();
    }

    private void updatePlaybackState() {
        boolean hasTrack = musicPlayer.getCurrentMediaItem() != null;
        boolean playing = musicPlayer.isPlaying();
        turntable.setPlaybackState(hasTrack, playing);
        captionWaveform.setPlaybackActive(playing);
        playPause.setText(playing ? "Ⅱ" : "▶");
        playPause.setContentDescription(playing ? "일시정지" : "재생");
        playPause.setEnabled(hasTrack);
        playPause.setAlpha(hasTrack ? 1f : .45f);
        seekBar.setEnabled(hasTrack);
        captionContainer.setEnabled(hasTrack);
        updateCaptionVisibility();
        captionButton.setEnabled(hasTrack);
        captionButton.setAlpha(hasTrack ? 1f : .45f);
        boolean canNavigate = musicPlayer.getMediaItemCount() > 1;
        previous.setEnabled(canNavigate);
        next.setEnabled(canNavigate);
        previous.setAlpha(canNavigate ? 1f : .4f);
        next.setAlpha(canNavigate ? 1f : .4f);
    }

    private void updateProgress() {
        long duration = musicPlayer.getDuration();
        long position = Math.max(0, musicPlayer.getCurrentPosition());
        updateCaption(position);
        if (duration <= 0 || duration == C.TIME_UNSET) return;
        turntable.setPlaybackProgress(Math.min(1f, (float) position / duration));
        if (!trackingSeek) {
            seekBar.setProgress((int) Math.min(1000, position * 1000 / duration));
            positionText.setText(formatTime(position));
        }
        durationText.setText(formatTime(duration));
    }

    private void updateCaptionVisibility() {
        boolean hasTrack = musicPlayer != null && musicPlayer.getCurrentMediaItem() != null;
        captionContainer.setVisibility(hasTrack ? View.VISIBLE : View.GONE);
        captionButton.setTextColor(captionsEnabled ? currentAccent : 0xFF8C8798);
        captionButton.setContentDescription(captionsEnabled ? "싱크 자막 끄기" : "싱크 자막 켜기");
        if (!captionsEnabled) {
            caption.setText("");
            clearCaptionNeighbors();
            if (hasTrack) showCaptionWaveform();
        }
    }

    private void updateCaption(long positionMs) {
        if (!captionsEnabled || !captionLyrics.isSynced()) return;
        captionLines.setVisibility(View.VISIBLE);
        captionWaveform.setVisibility(View.GONE);
        int index = captionLyrics.lineAt(positionMs, syncOffsetMs);
        String line = index < 0 ? "" : captionLyrics.timedLines.get(index).text;
        boolean currentChanged = setCaptionText(caption, line);
        setCaptionText(captionPrevious, neighborCaptionText(index, -1));
        setCaptionText(captionNext, neighborCaptionText(index, 1));
        if (currentChanged) captionContainer.setContentDescription(line.isEmpty()
                ? "전체 가사 펼쳐 보기" : line + " · 전체 가사 펼쳐 보기");
    }

    private void showCaptionWaveform() {
        captionLines.setVisibility(View.GONE);
        captionWaveform.setVisibility(View.VISIBLE);
        captionContainer.setContentDescription("전체 가사 펼쳐 보기");
    }

    private String neighborCaptionText(int index, int direction) {
        for (int i = index + direction; i >= 0 && i < captionLyrics.timedLines.size(); i += direction) {
            String text = captionLyrics.timedLines.get(i).text;
            if (!text.isEmpty()) return text;
        }
        return "";
    }

    private void clearCaptionNeighbors() {
        captionPrevious.setText("");
        captionNext.setText("");
    }

    private static boolean setCaptionText(TextView view, String text) {
        if (text.contentEquals(view.getText())) return false;
        view.setText(text);
        return true;
    }

    private void loadCaption() {
        MediaItem item = musicPlayer.getCurrentMediaItem();
        if (!captionsEnabled || item == null) return;
        cancelCaptionLoad();
        captionEmbeddedOnly = readEmbeddedOnly();
        boolean embeddedOnly = captionEmbeddedOnly;
        int request = captionGeneration;
        long duration = musicPlayer.getDuration();
        clearCaptionNeighbors();
        caption.setText("");
        showCaptionWaveform();
        captionLoad = lyricWorker.submit(() -> {
            Lyrics result = null;
            try { result = lyricsRepository.loadSynced(item, duration, embeddedOnly); }
            catch (Exception ignored) {}
            Lyrics loaded = result;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || request != captionGeneration || !captionsEnabled) return;
                captionLyrics = loaded == null ? Lyrics.fromText("", "") : loaded;
                if (captionLyrics.isSynced()) updateCaption(musicPlayer.getCurrentPosition());
                else showCaptionWaveform();
            });
        });
    }

    private void cancelCaptionLoad() {
        captionGeneration++;
        if (captionLoad != null) captionLoad.cancel(true);
    }

    private boolean readEmbeddedOnly() {
        return getSharedPreferences(LyricsActivity.PREFS_NAME, MODE_PRIVATE)
                .getBoolean("embedded_only", false);
    }

    private int readSyncOffset() {
        MediaItem item = musicPlayer.getCurrentMediaItem();
        if (item == null) return 0;
        String uri = item.localConfiguration == null ? "" : item.localConfiguration.uri.toString();
        SharedPreferences prefs = getSharedPreferences(LyricsActivity.PREFS_NAME, MODE_PRIVATE);
        boolean embeddedOnly = prefs.getBoolean("embedded_only", false);
        return prefs.getInt("lyrics_sync_offset_" + item.mediaId + "|" + uri + "|" + embeddedOnly, 0);
    }

    private void openLyrics() {
        if (musicPlayer.getCurrentMediaItem() != null) {
            ActivityOptions expansion = ActivityOptions.makeClipRevealAnimation(captionContainer,
                    0, 0, captionContainer.getWidth(), captionContainer.getHeight());
            startActivity(new Intent(this, LyricsActivity.class), expansion.toBundle());
        }
    }

    private void onCoverLoaded(Bitmap cover, int request) {
        if (request != accentGeneration || isFinishing() || isDestroyed()) return;
        if (cover == null) {
            animateAccent(AlbumAccent.DEFAULT);
            return;
        }
        paletteWorker.execute(() -> {
            int accent;
            try { accent = AlbumAccent.fromCover(cover); }
            catch (RuntimeException ignored) { accent = AlbumAccent.DEFAULT; }
            int extracted = accent;
            runOnUiThread(() -> {
                if (request == accentGeneration && !isFinishing() && !isDestroyed()) {
                    animateAccent(extracted);
                }
            });
        });
    }

    private void animateAccent(int target) {
        if (accentTransition != null) accentTransition.cancel();
        if (target == currentAccent) return;
        accentTransition = ValueAnimator.ofArgb(currentAccent, target);
        accentTransition.setDuration(1100);
        accentTransition.addUpdateListener(animation -> applyAccent((int) animation.getAnimatedValue()));
        accentTransition.start();
    }

    private void applyAccent(int color) {
        currentAccent = color;
        turntable.setAccentColor(color);
        captionWaveform.setAccentColor(color);
        playPause.getBackground().mutate().setTint(color);
        seekBar.setProgressTintList(ColorStateList.valueOf(color));
        seekBar.setThumbTintList(ColorStateList.valueOf(color));
        caption.setTextColor(color);
        backButton.setTextColor(color);
        headerTitle.setTextColor(color);
        if (captionsEnabled) captionButton.setTextColor(color);
    }

    private static String formatTime(long milliseconds) {
        long seconds = Math.max(0, milliseconds) / 1000;
        return String.format(Locale.getDefault(), "%d:%02d", seconds / 60, seconds % 60);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
