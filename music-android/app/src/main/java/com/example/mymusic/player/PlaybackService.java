package com.example.mymusic.player;

import android.app.PendingIntent;
import android.content.Intent;

import androidx.annotation.OptIn;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.audio.DefaultAudioSink;
import androidx.media3.exoplayer.audio.TeeAudioProcessor;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;

import com.example.mymusic.MainActivity;

@OptIn(markerClass = UnstableApi.class)
public class PlaybackService extends MediaSessionService {

    private MediaSession mediaSession;

    @Override
    public void onCreate() {
        super.onCreate();

        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build();
        DefaultRenderersFactory renderers = new DefaultRenderersFactory(this) {
            @Override protected AudioSink buildAudioSink(android.content.Context context,
                    boolean enableFloatOutput, boolean enableAudioOutputPlaybackParams) {
                return new DefaultAudioSink.Builder(context)
                        .setAudioProcessors(new AudioProcessor[]{new TeeAudioProcessor(new AudioLevels())})
                        .build();
            }
        };
        ExoPlayer player = new ExoPlayer.Builder(this, renderers)
                .setAudioAttributes(audioAttributes, true)
                .setWakeMode(C.WAKE_MODE_LOCAL)
                .build();

        Intent openApp = new Intent(this, MainActivity.class);
        openApp.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent sessionActivity = PendingIntent.getActivity(this, 0, openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        mediaSession = new MediaSession.Builder(this, player)
                .setSessionActivity(sessionActivity)
                .build();
    }

    @Override
    public MediaSession onGetSession(MediaSession.ControllerInfo controllerInfo) {
        return mediaSession;
    }

    @Override
    public void onDestroy() {
        if (mediaSession != null) {
            mediaSession.getPlayer().release();
            mediaSession.release();
            mediaSession = null;
        }
        super.onDestroy();
    }
}
