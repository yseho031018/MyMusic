package com.example.mymusic.player;

import android.content.ComponentName;
import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.core.content.ContextCompat;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MusicPlayer {

    private final ListenableFuture<MediaController> controllerFuture;
    private final List<Player.Listener> listeners = new ArrayList<>();
    private MediaController controller;
    private MediaItem pendingItem;
    private Runnable onConnected;
    private boolean released;

    public MusicPlayer(Context context) {
        Context appContext = context.getApplicationContext();
        SessionToken token = new SessionToken(appContext,
                new ComponentName(appContext, PlaybackService.class));
        controllerFuture = new MediaController.Builder(appContext, token).buildAsync();
        controllerFuture.addListener(() -> {
            if (released) return;
            try {
                controller = controllerFuture.get();
                for (Player.Listener listener : listeners) {
                    controller.addListener(listener);
                }
                if (pendingItem != null) {
                    startItem(pendingItem);
                    pendingItem = null;
                }
                if (onConnected != null) onConnected.run();
            } catch (Exception e) {
                Log.e("MusicPlayer", "Playback service connection failed", e);
            }
        }, ContextCompat.getMainExecutor(appContext));
    }

    public void setOnConnectedListener(Runnable listener) {
        onConnected = listener;
        if (controller != null && listener != null) listener.run();
    }

    public boolean isConnected() {
        return controller != null;
    }

    public void play(String url, int id, String title, String artist, String artworkUrl) {
        playItem(Uri.parse(url), id, title, artist, artworkUrl);
    }

    public void playLocal(File file, int id, String title, String artist, String artworkUrl) {
        playItem(Uri.fromFile(file), id, title, artist, artworkUrl);
    }

    private void playItem(Uri uri, int id, String title, String artist, String artworkUrl) {
        MediaMetadata.Builder metadata = new MediaMetadata.Builder();
        if (title != null) metadata.setTitle(title);
        if (artist != null) metadata.setArtist(artist);
        if (artworkUrl != null) metadata.setArtworkUri(Uri.parse(artworkUrl));

        MediaItem item = new MediaItem.Builder()
                .setMediaId(String.valueOf(id))
                .setUri(uri)
                .setMediaMetadata(metadata.build())
                .build();
        if (controller == null) {
            pendingItem = item;
        } else {
            startItem(item);
        }
    }

    private void startItem(MediaItem item) {
        controller.setMediaItem(item);
        controller.prepare();
        controller.play();
    }

    public void pause() {
        if (controller != null) controller.pause();
    }

    public void resume() {
        if (controller != null) controller.play();
    }

    public boolean isPlaying() {
        return controller != null && controller.isPlaying();
    }

    public long getCurrentPosition() {
        return controller != null ? controller.getCurrentPosition() : 0;
    }

    public long getDuration() {
        return controller != null ? controller.getDuration() : C.TIME_UNSET;
    }

    public MediaItem getCurrentMediaItem() {
        return controller != null ? controller.getCurrentMediaItem() : null;
    }

    public void seekTo(long position) {
        if (controller != null) controller.seekTo(position);
    }

    public void addListener(Player.Listener listener) {
        listeners.add(listener);
        if (controller != null) controller.addListener(listener);
    }

    public void release() {
        released = true;
        pendingItem = null;
        onConnected = null;
        listeners.clear();
        MediaController.releaseFuture(controllerFuture);
        controller = null;
    }
}
