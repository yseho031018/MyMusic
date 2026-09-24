package com.example.mymusic;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.Timeline;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mymusic.adapter.SongAdapter;
import com.example.mymusic.db.MusicDatabase;
import com.example.mymusic.model.Song;
import com.example.mymusic.network.MusicApi;
import com.example.mymusic.player.MusicPlayer;
import android.content.SharedPreferences;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    public static final String DEFAULT_LAPTOP_IP = "192.168.45.247";
    public static final String DEFAULT_DESKTOP_IP = "192.168.45.240";
    private static final List<String> CANDIDATE_IPS = Arrays.asList(DEFAULT_LAPTOP_IP, DEFAULT_DESKTOP_IP);

    static final String PREFS_NAME = "music_prefs";
    static final String PREF_KEY_SERVER_IP = "server_ip";
    static final String PREF_KEY_AUTO_MODE = "auto_server_discovery";
    private static final int REQUEST_SETTINGS = 1;

    private TextView txtStatus;
    private Button btnRemoteStart;
    private Button btnSettings;
    private String detectedManagerHost = null;

    private TextView txtNowPlaying;
    private TextView txtArtist;
    private TextView txtCurrentTime;
    private TextView txtDuration;

    private RecyclerView recyclerSongs;
    private SongAdapter songAdapter;
    private SeekBar seekBar;
    private ImageView imgCover;

    private Button btnPrevious;
    private Button btnPlayPause;
    private Button btnNext;
    private Button btnTabServer;
    private Button btnTabLocal;

    private MusicApi musicApi;
    private MusicPlayer musicPlayer;
    private MusicDatabase musicDb;

    private List<Song> songs = new ArrayList<>();
    private List<Song> serverSongs = new ArrayList<>();
    private boolean isLocalTab = false;

    // 현재 선택된 음악의 목록 인덱스
    private int currentSongIndex = -1;

    private final Handler handler =
            new Handler(Looper.getMainLooper());

    // 재생 위치를 주기적으로 업데이트
    private final Runnable updateProgress =
            new Runnable() {
                @Override
                public void run() {
                    updateSeekBar();
                    handler.postDelayed(this, 500);
                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        View mainView = findViewById(R.id.main);

        ViewCompat.setOnApplyWindowInsetsListener(
                mainView,
                (view, windowInsets) -> {
                    Insets insets = windowInsets.getInsets(
                            WindowInsetsCompat.Type.systemBars()
                    );

                    view.setPadding(
                            insets.left + dpToPx(24),
                            insets.top + dpToPx(24),
                            insets.right + dpToPx(24),
                            insets.bottom + dpToPx(24)
                    );

                    return windowInsets;
                }
        );

        // 상단 상태 및 목록
        txtStatus = findViewById(R.id.txtStatus);
        btnRemoteStart = findViewById(R.id.btnRemoteStart);
        btnSettings = findViewById(R.id.btnSettings);
        recyclerSongs = findViewById(R.id.recyclerSongs);

        btnRemoteStart.setOnClickListener(v -> triggerRemoteStart());
        btnSettings.setOnClickListener(v ->
                startActivityForResult(new Intent(this, SettingsActivity.class), REQUEST_SETTINGS));

        // 탭 버튼
        btnTabServer = findViewById(R.id.btnTabServer);
        btnTabLocal = findViewById(R.id.btnTabLocal);

        // 서버 탭 클릭 시 서버 목록 전환 및 최신 목록 동기화
        btnTabServer.setOnClickListener(v -> {
            switchTab(false);
            loadSongs();
        });
        btnTabLocal.setOnClickListener(v -> switchTab(true));

        // 재생 컨트롤러
        txtNowPlaying = findViewById(R.id.txtNowPlaying);
        txtArtist = findViewById(R.id.txtArtist);
        imgCover = findViewById(R.id.imgCover);
        findViewById(R.id.nowPlayingCard).setOnClickListener(v -> {
            if (musicPlayer.getCurrentMediaItem() != null) {
                openNowPlaying();
            } else {
                Toast.makeText(this, "먼저 음악을 재생해 주세요.", Toast.LENGTH_SHORT).show();
            }
        });

        txtCurrentTime = findViewById(R.id.txtCurrentTime);
        txtDuration = findViewById(R.id.txtDuration);

        seekBar = findViewById(R.id.seekBar);

        btnPrevious = findViewById(R.id.btnPrevious);
        btnPlayPause = findViewById(R.id.btnPlayPause);
        btnNext = findViewById(R.id.btnNext);

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String initialIp = prefs.getString(PREF_KEY_SERVER_IP, DEFAULT_LAPTOP_IP);
        musicApi = new MusicApi("http://" + initialIp + ":8080");
        musicPlayer = new MusicPlayer(this);
        musicDb = new MusicDatabase(this);

        // RecyclerView 및 어댑터 설정
        recyclerSongs.setLayoutManager(new LinearLayoutManager(this));
        songAdapter = new SongAdapter(
                (position, song) -> playSong(position)
        );
        songAdapter.setMusicApi(musicApi);
        songAdapter.setFilesDir(getFilesDir());
        recyclerSongs.setAdapter(songAdapter);

        // 스와이프 제스처 설정 (오른쪽: 다운로드, 왼쪽: 삭제)
        setupSwipeGestures();

        // 기본 선택: 서버 탭 활성화
        btnTabServer.setAlpha(1.0f);
        btnTabLocal.setAlpha(0.6f);

        // 음악을 선택하기 전에는 버튼 비활성화
        setControlsEnabled(false);

        // 앱 시작 시 자동 연결 시도
        loadSongs();

        // 화면과 알림의 이전/다음 버튼이 같은 재생 대기열을 사용
        btnPrevious.setOnClickListener(v -> musicPlayer.previous());
        btnNext.setOnClickListener(v -> musicPlayer.next());

        // 재생 / 일시정지
        btnPlayPause.setOnClickListener(v -> {
            if (musicPlayer.getCurrentMediaItem() == null) {
                return;
            }

            if (musicPlayer.isPlaying()) {
                musicPlayer.pause();
            } else {
                musicPlayer.resume();
                openNowPlaying();
            }

            updatePlayPauseButton();
        });

        // 재생 위치 조절
        seekBar.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(
                            SeekBar seekBar,
                            int progress,
                            boolean fromUser
                    ) {
                        if (!fromUser) {
                            return;
                        }

                        long duration = musicPlayer.getDuration();
                        if (duration <= 0 || duration == C.TIME_UNSET) {
                            return;
                        }

                        long position = duration * progress / 1000;
                        txtCurrentTime.setText(formatTime(position));
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                        long duration = musicPlayer.getDuration();
                        if (duration <= 0 || duration == C.TIME_UNSET) {
                            return;
                        }

                        long position = duration * seekBar.getProgress() / 1000;
                        musicPlayer.seekTo(position);
                    }
                }
        );

        // ExoPlayer 상태 변경 감지
        musicPlayer.addListener(new Player.Listener() {
            @Override
            public void onMediaItemTransition(MediaItem mediaItem, int reason) {
                seekBar.setProgress(0);
                txtCurrentTime.setText("0:00");
                txtDuration.setText("0:00");
                restoreCurrentPlayback();
            }

            @Override
            public void onTimelineChanged(Timeline timeline, int reason) {
                updateNavigationButtons();
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                updatePlayPauseButton();
            }

            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY) {
                    updateSeekBar();
                }

            }
        });
        musicPlayer.setOnConnectedListener(this::restoreCurrentPlayback);

        // 재생 위치 업데이트 시작
        handler.post(updateProgress);
    }

    // 스와이프 제스처 설정 (항상 왼쪽으로 슬라이드: 서버 음악에선 다운로드, 보관함에선 삭제)
    private void setupSwipeGestures() {
        ItemTouchHelper.SimpleCallback swipeCallback = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            private final Paint paint = new Paint();

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getBindingAdapterPosition();
                if (position == RecyclerView.NO_POSITION || position >= songs.size()) {
                    return;
                }
                Song song = songs.get(position);

                if (isLocalTab) {
                    // 보관함 탭에서는 왼쪽 스와이프가 "삭제"
                    handleSwipeDelete(position, song);
                } else {
                    // 서버 음악 탭에서는 왼쪽 스와이프가 "다운로드"
                    handleSwipeDownload(position, song);
                }
            }

            @Override
            public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView,
                                    @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY,
                                    int actionState, boolean isCurrentlyActive) {
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                    View itemView = viewHolder.itemView;
                    paint.setAntiAlias(true);

                    if (dX < 0) {
                        c.save();
                        c.clipRect((float) itemView.getRight() + dX, (float) itemView.getTop(), (float) itemView.getRight(), (float) itemView.getBottom());

                        if (isLocalTab) {
                            // 보관함: 삭제 (빨간색 배경)
                            paint.setColor(0xFFC62828);
                            c.drawRect((float) itemView.getRight() + dX, (float) itemView.getTop(), (float) itemView.getRight(), (float) itemView.getBottom(), paint);

                            paint.setColor(0xFFFFFFFF);
                            paint.setTextSize(dpToPx(14));
                            paint.setFakeBoldText(true);
                            float textY = itemView.getTop() + (itemView.getHeight() / 2f) + dpToPx(5);
                            float textWidth = paint.measureText("삭제");
                            c.drawText("삭제", itemView.getRight() - textWidth - dpToPx(20), textY, paint);
                        } else {
                            // 서버 음악: 다운로드 (초록색 배경)
                            paint.setColor(0xFF2E7D32);
                            c.drawRect((float) itemView.getRight() + dX, (float) itemView.getTop(), (float) itemView.getRight(), (float) itemView.getBottom(), paint);

                            paint.setColor(0xFFFFFFFF);
                            paint.setTextSize(dpToPx(14));
                            paint.setFakeBoldText(true);
                            float textY = itemView.getTop() + (itemView.getHeight() / 2f) + dpToPx(5);
                            float textWidth = paint.measureText("다운로드");
                            c.drawText("다운로드", itemView.getRight() - textWidth - dpToPx(20), textY, paint);
                        }

                        c.restore();
                    }
                }
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
            }
        };

        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(swipeCallback);
        itemTouchHelper.attachToRecyclerView(recyclerSongs);
    }

    // 서버 음악 탭에서 왼쪽 스와이프: 음악 다운로드 처리
    private void handleSwipeDownload(int position, Song song) {
        if (musicDb.isDownloaded(song.getId())) {
            Toast.makeText(this, "이미 다운로드된 곡입니다: " + song.getTitle(), Toast.LENGTH_SHORT).show();
            songAdapter.notifyItemChanged(position);
            return;
        }

        // 스와이프 상태를 원래대로 복귀
        songAdapter.notifyItemChanged(position);
        Toast.makeText(this, "다운로드 시작: " + song.getTitle(), Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            try {
                File downloadedFile = musicApi.downloadSong(getApplicationContext(), song);
                musicDb.saveSong(song, downloadedFile.getAbsolutePath());

                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "다운로드 완료: " + song.getTitle(), Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "다운로드 실패: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    // 보관함 탭에서 왼쪽 스와이프: 음악 삭제 처리
    private void handleSwipeDelete(int position, Song song) {
        File localFile = new File(
                new File(getFilesDir(), "music"),
                song.getId() + ".mp3"
        );
        if (localFile.exists()) {
            localFile.delete();
        }
        musicDb.deleteSong(song.getId());

        songs.remove(position);
        songAdapter.notifyItemRemoved(position);
        if (currentSongIndex == position) {
            currentSongIndex = -1;
        } else if (currentSongIndex > position) {
            currentSongIndex--;
        }
        updateNavigationButtons();
        txtStatus.setText("보관함 (" + songs.size() + "곡)");
        Toast.makeText(this, "보관함에서 삭제되었습니다: " + song.getTitle(), Toast.LENGTH_SHORT).show();
    }

    // 탭 전환 (서버 음악 <-> 다운로드 보관함)
    private void switchTab(boolean showLocal) {
        isLocalTab = showLocal;

        Song currentPlayingSong = (currentSongIndex >= 0 && currentSongIndex < songs.size())
                ? songs.get(currentSongIndex) : null;

        if (isLocalTab) {
            songs = musicDb.getAllSongs();
            btnTabServer.setAlpha(0.6f);
            btnTabLocal.setAlpha(1.0f);
            txtStatus.setText("보관함 (" + songs.size() + "곡)");
        } else {
            songs = new ArrayList<>(serverSongs);
            btnTabServer.setAlpha(1.0f);
            btnTabLocal.setAlpha(0.6f);
            txtStatus.setText("서버 음악 (" + songs.size() + "곡)");
        }

        // 현재 재생 중인 곡의 인덱스를 새 목록에서 찾음
        currentSongIndex = -1;
        if (currentPlayingSong != null) {
            for (int i = 0; i < songs.size(); i++) {
                if (songs.get(i).getId() == currentPlayingSong.getId()) {
                    currentSongIndex = i;
                    break;
                }
            }
        }
        updateNavigationButtons();
        songAdapter.setSongs(songs);
        restoreCurrentPlayback();
    }

    // 1. 서버에서 음악 목록 불러오기 (다중 IP 자동 감지 & 원격 매니저 탐색)
    private void loadSongs() {
        if (!isLocalTab) {
            txtStatus.setText("서버 연결 확인 중...");
        }

        new Thread(() -> {
            String activeUrl = resolveActiveServer();

            if (activeUrl != null) {
                musicApi.setBaseUrl(activeUrl);
                try {
                    List<Song> result = musicApi.getSongs();
                    runOnUiThread(() -> {
                        serverSongs = result;
                        btnRemoteStart.setVisibility(View.GONE);
                        if (!isLocalTab) {
                            songs = new ArrayList<>(serverSongs);
                            songAdapter.setSongs(songs);
                            txtStatus.setText(getServerDisplayName(activeUrl) + " (" + songs.size() + "곡)");
                            restoreCurrentPlayback();
                        }
                    });
                    return;
                } catch (Exception ignored) {
                }
            }

            // 음악 서버(8080)가 오프라인인 경우: PC 데스크톱 매니저(8088) 확인
            String managerHost = findReachableManager();
            runOnUiThread(() -> {
                if (managerHost != null) {
                    detectedManagerHost = managerHost;
                    btnRemoteStart.setVisibility(View.VISIBLE);
                    btnRemoteStart.setText("⚡ " + getHostNickname(managerHost) + " 켜기");
                    txtStatus.setText(getHostNickname(managerHost) + " 대기 중 (8088)");
                } else {
                    btnRemoteStart.setVisibility(View.GONE);
                }

                // 서버 연결 실패 시 다운로드 보관함으로 자동 전환
                switchTab(true);

                if (songs.isEmpty()) {
                    txtStatus.setText("서버 오프라인 (보관함에 저장된 곡 없음)");
                } else {
                    txtStatus.setText("보관함: " + songs.size() + "곡 (오프라인)");
                }
            });
        }).start();
    }

    // 켜져 있는 활성 서버(8080) 찾기 (설정된 선호 서버 우선, 실패 시 후보 IP 자동 탐색)
    private String resolveActiveServer() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean autoDiscovery = prefs.getBoolean(PREF_KEY_AUTO_MODE, true);
        String savedIp = prefs.getString(PREF_KEY_SERVER_IP, DEFAULT_LAPTOP_IP);

        // 수동 지정 모드인 경우 해당 IP 먼저 검사
        if (!autoDiscovery && savedIp != null && !savedIp.isEmpty()) {
            String candidateUrl = "http://" + savedIp + ":8080";
            if (MusicApi.checkServerHealth(candidateUrl, 2500)) {
                return candidateUrl;
            }
        }

        // 자동 탐색 모드 (또는 수동 서버가 꺼져 있을 때 failover)
        List<String> candidates = new ArrayList<>();
        if (savedIp != null && !savedIp.isEmpty()) {
            candidates.add("http://" + savedIp + ":8080");
        }
        for (String ip : CANDIDATE_IPS) {
            String url = "http://" + ip + ":8080";
            if (!candidates.contains(url)) {
                candidates.add(url);
            }
        }

        for (String url : candidates) {
            if (MusicApi.checkServerHealth(url, 2000)) {
                return url;
            }
        }
        return null;
    }

    // 원격 제어 데몬(8088)이 살아있는 호스트 찾기
    private String findReachableManager() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String savedIp = prefs.getString(PREF_KEY_SERVER_IP, DEFAULT_LAPTOP_IP);

        List<String> ips = new ArrayList<>();
        if (savedIp != null && !savedIp.isEmpty()) ips.add(savedIp);
        for (String ip : CANDIDATE_IPS) {
            if (!ips.contains(ip)) ips.add(ip);
        }

        for (String ip : ips) {
            JSONObject obj = MusicApi.checkManagerStatus(ip, 1500);
            if (obj != null) {
                return ip;
            }
        }
        return null;
    }

    // 스마트폰에서 PC 서버 원격 기동 트리거
    private void triggerRemoteStart() {
        if (detectedManagerHost == null) {
            Toast.makeText(this, "연결 가능한 서버 매니저가 없습니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        btnRemoteStart.setEnabled(false);
        btnRemoteStart.setText("서버 켜는 중...");
        Toast.makeText(this, getHostNickname(detectedManagerHost) + " 서버를 원격으로 켭니다...", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            boolean requested = MusicApi.remoteStartServer(detectedManagerHost, 3000);
            if (!requested) {
                runOnUiThread(() -> {
                    btnRemoteStart.setEnabled(true);
                    btnRemoteStart.setText("⚡ " + getHostNickname(detectedManagerHost) + " 켜기");
                    Toast.makeText(MainActivity.this, "원격 기동 요청 실패 (매니저 응답 없음)", Toast.LENGTH_SHORT).show();
                });
                return;
            }

            // 서버가 켜질 때까지 최대 10초간 폴링 (매 1.5초마다 헬스체크)
            String targetServerUrl = "http://" + detectedManagerHost + ":8080";
            boolean isUp = false;
            for (int i = 0; i < 7; i++) {
                try {
                    Thread.sleep(1500);
                } catch (InterruptedException ignored) {}

                if (MusicApi.checkServerHealth(targetServerUrl, 2000)) {
                    isUp = true;
                    break;
                }
            }

            final boolean success = isUp;
            runOnUiThread(() -> {
                btnRemoteStart.setEnabled(true);
                if (success) {
                    Toast.makeText(MainActivity.this, "✅ 서버가 켜졌습니다!", Toast.LENGTH_SHORT).show();
                    switchTab(false);
                    loadSongs();
                } else {
                    btnRemoteStart.setText("⚡ " + getHostNickname(detectedManagerHost) + " 켜기");
                    Toast.makeText(MainActivity.this, "서버 시작 지연 중입니다. 잠시 후 새로고침하세요.", Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SETTINGS && resultCode == RESULT_OK) {
            switchTab(false);
            loadSongs();
        }
    }

    private String getHostNickname(String host) {
        if (DEFAULT_LAPTOP_IP.equals(host)) return "노트북";
        if (DEFAULT_DESKTOP_IP.equals(host)) return "데스크톱";
        return host;
    }

    private String getServerDisplayName(String url) {
        String host = MusicApi.extractHost(url);
        return getHostNickname(host) + " (" + host + ")";
    }

    // 선택한 음악 재생
    private void playSong(int index) {
        if (index < 0 || index >= songs.size()) {
            return;
        }

        currentSongIndex = index;
        Song selectedSong = songs.get(index);
        songAdapter.setPlayingSongId(selectedSong.getId());

        // 스마트폰에 저장된 음악 파일 확인
        File localFile = new File(
                new File(getFilesDir(), "music"),
                selectedSong.getId() + ".mp3"
        );
        musicPlayer.playQueue(songs, index, musicApi.getBaseUrl(), getFilesDir());

        if (localFile.exists()) {
            // 다운로드한 음악이 있으면 로컬 재생
            txtStatus.setText("오프라인 재생");
        } else {
            // 다운로드한 음악이 없으면 서버 스트리밍
            txtStatus.setText("서버 스트리밍");
        }

        txtNowPlaying.setText(selectedSong.getTitle());
        txtArtist.setText(selectedSong.getArtist());

        // 앨범 커버 로드 (캐시 우선, 오프라인 로컬 커버 폴백)
        SongAdapter.loadCover(musicApi, getFilesDir(), selectedSong.getId(), imgCover);

        seekBar.setProgress(0);
        txtCurrentTime.setText("0:00");
        txtDuration.setText("0:00");

        setControlsEnabled(musicPlayer.isConnected());
        updatePlayPauseButton();

        // 현재 재생 곡 위치로 목록 부드럽게 스크롤
        recyclerSongs.smoothScrollToPosition(index);
        openNowPlaying();
    }

    private void openNowPlaying() {
        startActivity(new Intent(this, NowPlayingActivity.class));
    }

    private void restoreCurrentPlayback() {
        MediaItem item = musicPlayer.getCurrentMediaItem();
        if (item == null) return;

        if (item.mediaMetadata.title != null) {
            txtNowPlaying.setText(item.mediaMetadata.title);
        }
        if (item.mediaMetadata.artist != null) {
            txtArtist.setText(item.mediaMetadata.artist);
        }

        currentSongIndex = -1;
        try {
            int songId = Integer.parseInt(item.mediaId);
            songAdapter.setPlayingSongId(songId);
            for (int i = 0; i < songs.size(); i++) {
                if (songs.get(i).getId() == songId) {
                    currentSongIndex = i;
                    break;
                }
            }
            SongAdapter.loadCover(musicApi, getFilesDir(), songId, imgCover);
        } catch (NumberFormatException ignored) {
        }
        setControlsEnabled(true);
        updatePlayPauseButton();
        updateSeekBar();
    }

    // 재생 컨트롤러 활성화
    private void setControlsEnabled(boolean enabled) {
        btnPlayPause.setEnabled(enabled);
        seekBar.setEnabled(enabled);
        updateNavigationButtons();
    }

    // 현재 재생 대기열에 2곡 이상 있으면 이전/다음 곡으로 이동 가능
    private void updateNavigationButtons() {
        boolean canNavigate = musicPlayer.getMediaItemCount() > 1;
        btnPrevious.setEnabled(canNavigate);
        btnNext.setEnabled(canNavigate);
    }

    // 재생 / 일시정지 버튼 표시
    private void updatePlayPauseButton() {
        if (musicPlayer.isPlaying()) {
            btnPlayPause.setText("일시정지");
        } else {
            btnPlayPause.setText("재생");
        }
    }

    // 재생 위치와 전체 시간 업데이트
    private void updateSeekBar() {
        if (musicPlayer.getCurrentMediaItem() == null) {
            return;
        }

        long duration = musicPlayer.getDuration();
        if (duration <= 0 || duration == C.TIME_UNSET) {
            return;
        }

        long position = musicPlayer.getCurrentPosition();
        int progress = (int) (position * 1000 / duration);

        seekBar.setProgress(progress);
        txtCurrentTime.setText(formatTime(position));
        txtDuration.setText(formatTime(duration));
    }

    // 밀리초를 분:초 형식으로 변환
    private String formatTime(long milliseconds) {
        long totalSeconds = milliseconds / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;

        return String.format(
                Locale.getDefault(),
                "%d:%02d",
                minutes,
                seconds
        );
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(updateProgress);

        if (musicPlayer != null) {
            musicPlayer.release();
        }

        if (musicDb != null) {
            musicDb.close();
        }

        super.onDestroy();
    }

    private int dpToPx(int dp) {
        return Math.round(
                dp * getResources().getDisplayMetrics().density
        );
    }
}
