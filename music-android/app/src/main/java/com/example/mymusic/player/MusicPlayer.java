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
import com.example.mymusic.model.Song;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MusicPlayer {

    private final ListenableFuture<MediaController> controllerFuture;
    private final List<Player.Listener> listeners = new ArrayList<>();
    private MediaController controller;
    private List<MediaItem> pendingItems;
    private int pendingStartIndex;
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
                if (pendingItems != null) {
                    startQueue(pendingItems, pendingStartIndex);
                    pendingItems = null;
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

    public void playQueue(List<Song> songs, int startIndex, String serverBaseUrl, File filesDir) {
        List<MediaItem> items = new ArrayList<>(songs.size());
        File musicDir = new File(filesDir, "music");
        for (Song song : songs) {
            File localFile = new File(musicDir, song.getId() + ".mp3");
            String songUrl = serverBaseUrl + "/api/songs/" + song.getId();
            Uri source = localFile.exists() ? Uri.fromFile(localFile)
                    : Uri.parse(songUrl + "/stream");
            MediaMetadata metadata = new MediaMetadata.Builder()
                    .setTitle(song.getTitle())
                    .setArtist(song.getArtist())
                    .setArtworkUri(Uri.parse(songUrl + "/cover"))
                    .build();
            items.add(new MediaItem.Builder()
                    .setMediaId(String.valueOf(song.getId()))
                    .setUri(source)
                    .setMediaMetadata(metadata)
                    .build());
        }
        if (controller == null) {
            pendingItems = items;
            pendingStartIndex = startIndex;
        } else {
            startQueue(items, startIndex);
        }
    }

    private void startQueue(List<MediaItem> items, int startIndex) {
        controller.setRepeatMode(Player.REPEAT_MODE_ALL);
        controller.setMediaItems(items, startIndex, 0);
        controller.prepare();
        controller.play();
    }

    public void next() {
        if (controller != null && controller.hasNextMediaItem()) {
            controller.seekToNextMediaItem();
            controller.play();
        }
    }

    public void previous() {
        if (controller != null && controller.hasPreviousMediaItem()) {
            controller.seekToPreviousMediaItem();
            controller.play();
        }
    }

    public int getMediaItemCount() {
        return controller != null ? controller.getMediaItemCount() : 0;
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
        pendingItems = null;
        onConnected = null;
        listeners.clear();
        MediaController.releaseFuture(controllerFuture);
        controller = null;
    }
}
