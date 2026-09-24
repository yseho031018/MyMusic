package com.example.mymusic;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
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
import com.example.mymusic.player.MusicPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class LyricsActivity extends Activity {
    private static final int ACTIVE_COLOR = 0xFFE4CFFF;
    private static final int INACTIVE_COLOR = 0xFF9290A2;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private MusicPlayer musicPlayer;
    private LyricsRepository repository;
    private ImageView cover;
    private TextView title, artist, source, tabSynced, tabFull, fullText;
    private TextView positionText, durationText, playPause, previous, next;
    private LinearLayout syncedLines;
    private ScrollView syncedScroll, fullScroll;
    private SeekBar seekBar;
    private Lyrics lyrics = Lyrics.fromText("", "");
    private final List<TextView> timedViews = new ArrayList<>();
    private String currentTrackKey = "";
    private int loadGeneration;
    private int highlightedLine = -2;
    private boolean fullMode;
    private boolean trackingSeek;
    private boolean embeddedOnly;
    private Future<?> pendingLoad;

    private final Runnable progressUpdate = new Runnable() {
        @Override public void run() {
            refreshTrack();
            updateProgress();
            handler.postDelayed(this, 500);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lyrics);
        View root = findViewById(R.id.lyricsRoot);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left + dp(16), bars.top + dp(4), bars.right + dp(16), bars.bottom + dp(4));
            return insets;
        });
        getWindow().setStatusBarColor(0xFF1A1A21);
        getWindow().setNavigationBarColor(0xFF1A1A21);

        cover = findViewById(R.id.lyricsCover);
        title = findViewById(R.id.lyricsTitle);
        artist = findViewById(R.id.lyricsArtist);
        source = findViewById(R.id.lyricsSource);
        tabSynced = findViewById(R.id.tabSyncedLyrics);
        tabFull = findViewById(R.id.tabFullLyrics);
        fullText = findViewById(R.id.fullLyricsText);
        syncedLines = findViewById(R.id.syncedLyricsLines);
        syncedScroll = findViewById(R.id.syncedLyricsScroll);
        fullScroll = findViewById(R.id.fullLyricsScroll);
        seekBar = findViewById(R.id.lyricsSeekBar);
        positionText = findViewById(R.id.lyricsPosition);
        durationText = findViewById(R.id.lyricsDuration);
        playPause = findViewById(R.id.lyricsPlayPause);
        previous = findViewById(R.id.lyricsPrevious);
        next = findViewById(R.id.lyricsNext);

        embeddedOnly = getPreferences(MODE_PRIVATE).getBoolean("embedded_only", false);
        repository = new LyricsRepository(this);
        musicPlayer = new MusicPlayer(this);
        musicPlayer.addListener(new Player.Listener() {
            @Override public void onMediaItemTransition(MediaItem mediaItem, int reason) { refreshTrack(); }
            @Override public void onIsPlayingChanged(boolean isPlaying) { updateControls(); }
            @Override public void onPlaybackStateChanged(int state) { updateProgress(); }
        });
        musicPlayer.setOnConnectedListener(this::refreshTrack);

        findViewById(R.id.btnLyricsBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnLyricsOptions).setOnClickListener(this::showOptions);
        tabSynced.setOnClickListener(v -> showMode(false));
        tabFull.setOnClickListener(v -> showMode(true));
        previous.setOnClickListener(v -> musicPlayer.previous());
        next.setOnClickListener(v -> musicPlayer.next());
        playPause.setOnClickListener(v -> {
            if (musicPlayer.isPlaying()) musicPlayer.pause(); else musicPlayer.resume();
            updateControls();
        });
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser) positionText.setText(formatTime(musicPlayer.getDuration() * progress / 1000));
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { trackingSeek = true; }
            @Override public void onStopTrackingTouch(SeekBar bar) {
                trackingSeek = false;
                long duration = musicPlayer.getDuration();
                if (duration > 0 && duration != C.TIME_UNSET) musicPlayer.seekTo(duration * bar.getProgress() / 1000);
            }
        });
        updateControls();
    }

    @Override protected void onStart() {
        super.onStart();
        handler.post(progressUpdate);
    }

    @Override protected void onStop() {
        handler.removeCallbacks(progressUpdate);
        super.onStop();
    }

    @Override protected void onDestroy() {
        loadGeneration++;
        if (pendingLoad != null) pendingLoad.cancel(true);
        worker.shutdownNow();
        musicPlayer.release();
        super.onDestroy();
    }

    private void showOptions(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, 1, 0, embeddedOnly ? "✓ MP3 내장 가사만" : "MP3 내장 가사만");
        menu.getMenu().add(0, 2, 1, !embeddedOnly ? "✓ 내장 가사 우선 · 없으면 온라인 검색" : "내장 가사 우선 · 없으면 온라인 검색");
        menu.getMenu().add(0, 3, 2, "가사 다시 확인");
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1 || item.getItemId() == 2) {
                embeddedOnly = item.getItemId() == 1;
                getPreferences(MODE_PRIVATE).edit().putBoolean("embedded_only", embeddedOnly).apply();
            } else if (item.getItemId() == 3) {
                repository.clearOnlineCache(musicPlayer.getCurrentMediaItem());
            }
            currentTrackKey = "";
            refreshTrack();
            return true;
        });
        menu.show();
    }

    private void refreshTrack() {
        MediaItem item = musicPlayer.getCurrentMediaItem();
        if (item == null) {
            title.setText("재생 중인 음악 없음");
            artist.setText("목록에서 음악을 재생해 주세요");
            source.setText("가사를 표시할 곡이 없습니다");
            return;
        }
        String uri = item.localConfiguration == null ? "" : item.localConfiguration.uri.toString();
        String key = item.mediaId + "|" + uri + "|" + embeddedOnly;
        if (key.equals(currentTrackKey)) return;
        currentTrackKey = key;
        int generation = ++loadGeneration;
        if (pendingLoad != null) pendingLoad.cancel(true);
        title.setText(item.mediaMetadata.title == null ? "제목 없음" : item.mediaMetadata.title);
        artist.setText(item.mediaMetadata.artist == null ? "" : item.mediaMetadata.artist);
        source.setText(embeddedOnly ? "MP3 내장 가사 확인 중…" : "내장 가사 확인 · 없으면 온라인 검색 중…");
        positionText.setText("0:00");
        durationText.setText("0:00");
        seekBar.setProgress(0);
        cover.setImageResource(R.drawable.ic_music_placeholder);
        lyrics = Lyrics.fromText("", "");
        renderLyrics();
        try {
            int id = Integer.parseInt(item.mediaId);
            android.net.Uri artwork = item.mediaMetadata.artworkUri;
            if (artwork != null && artwork.getScheme() != null && artwork.getAuthority() != null) {
                String baseUrl = artwork.getScheme() + "://" + artwork.getAuthority();
                SongAdapter.loadCover(new MusicApi(baseUrl), getFilesDir(), id, cover);
            }
        } catch (NumberFormatException ignored) {}
        long duration = musicPlayer.getDuration();
        pendingLoad = worker.submit(() -> {
            Lyrics result = null;
            String error = null;
            try { result = repository.load(item, duration, embeddedOnly); }
            catch (Exception e) { error = e.getMessage(); }
            Lyrics loaded = result;
            String failure = error;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || generation != loadGeneration) return;
                lyrics = loaded == null ? Lyrics.fromText("", "") : loaded;
                source.setText(failure != null ? failure : lyrics.hasLyrics()
                        ? lyrics.source + (lyrics.isSynced() ? " · 싱크 지원" : " · 전체 가사")
                        : embeddedOnly ? "이 MP3에 내장된 가사가 없습니다" : "내장 가사와 온라인 검색 결과가 없습니다");
                if (!lyrics.isSynced()) fullMode = true;
                else fullMode = false;
                renderLyrics();
            });
        });
        updateProgress();
    }

    private void renderLyrics() {
        syncedLines.removeAllViews();
        timedViews.clear();
        highlightedLine = -2;
        fullText.setText(lyrics.hasLyrics() ? lyrics.plainText : "표시할 가사가 없습니다.");
        if (lyrics.isSynced()) {
            for (Lyrics.Line line : lyrics.timedLines) {
                TextView lineView = new TextView(this);
                lineView.setText(line.text.isEmpty() ? "♪" : line.text);
                lineView.setTextColor(INACTIVE_COLOR);
                lineView.setTextSize(18);
                lineView.setPadding(0, dp(12), 0, dp(12));
                lineView.setOnClickListener(v -> musicPlayer.seekTo(line.timeMs));
                syncedLines.addView(lineView);
                timedViews.add(lineView);
            }
        } else {
            TextView message = new TextView(this);
            message.setText(lyrics.hasLyrics() ? "이 가사에는 시간 정보가 없습니다. 전체 가사에서 볼 수 있습니다." : "싱크 가사가 없습니다.");
            message.setTextColor(INACTIVE_COLOR);
            message.setTextSize(16);
            syncedLines.addView(message);
        }
        showMode(fullMode);
        updateProgress();
    }

    private void showMode(boolean full) {
        fullMode = full;
        syncedScroll.setVisibility(full ? View.GONE : View.VISIBLE);
        fullScroll.setVisibility(full ? View.VISIBLE : View.GONE);
        tabFull.setBackground(full ? getDrawable(R.drawable.lyrics_tab_active) : null);
        tabSynced.setBackground(full ? null : getDrawable(R.drawable.lyrics_tab_active));
        tabFull.setTextColor(full ? 0xFFFFFFFF : INACTIVE_COLOR);
        tabSynced.setTextColor(full ? INACTIVE_COLOR : 0xFFFFFFFF);
    }

    private void updateProgress() {
        long duration = musicPlayer.getDuration();
        long position = musicPlayer.getCurrentPosition();
        if (duration > 0 && duration != C.TIME_UNSET) {
            if (!trackingSeek) {
                seekBar.setProgress((int) Math.min(1000, position * 1000 / duration));
                positionText.setText(formatTime(position));
            }
            durationText.setText(formatTime(duration));
        }
        if (lyrics.isSynced()) highlight(lyrics.lineAt(position));
        updateControls();
    }

    private void highlight(int index) {
        if (index == highlightedLine) return;
        if (highlightedLine >= 0 && highlightedLine < timedViews.size()) {
            TextView old = timedViews.get(highlightedLine);
            old.setTextColor(INACTIVE_COLOR);
            old.setTextSize(18);
            old.setTypeface(null, android.graphics.Typeface.NORMAL);
        }
        highlightedLine = index;
        if (index < 0 || index >= timedViews.size()) return;
        TextView active = timedViews.get(index);
        active.setTextColor(ACTIVE_COLOR);
        active.setTextSize(21);
        active.setTypeface(null, android.graphics.Typeface.BOLD);
        syncedScroll.post(() -> syncedScroll.smoothScrollTo(0, Math.max(0,
                active.getTop() - syncedScroll.getHeight() / 2 + active.getHeight() / 2)));
    }

    private void updateControls() {
        boolean hasSong = musicPlayer.getCurrentMediaItem() != null;
        playPause.setText(musicPlayer.isPlaying() ? "Ⅱ" : "▶");
        playPause.setEnabled(hasSong);
        seekBar.setEnabled(hasSong);
        previous.setEnabled(musicPlayer.getMediaItemCount() > 1);
        next.setEnabled(musicPlayer.getMediaItemCount() > 1);
        previous.setAlpha(previous.isEnabled() ? 1f : .4f);
        next.setAlpha(next.isEnabled() ? 1f : .4f);
    }

    private static String formatTime(long milliseconds) {
        long seconds = Math.max(0, milliseconds) / 1000;
        return String.format(Locale.getDefault(), "%d:%02d", seconds / 60, seconds % 60);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
