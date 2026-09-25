package com.example.mymusic.adapter;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mymusic.R;
import com.example.mymusic.model.Song;
import com.example.mymusic.network.MusicApi;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SongAdapter extends RecyclerView.Adapter<SongAdapter.SongViewHolder> {

    public interface CoverLoadListener {
        /** Called on the main thread with the cover, or null when none is available. */
        void onCoverLoaded(Bitmap bitmap);
    }

    public interface OnItemClickListener {
        void onItemClick(int position, Song song);
    }

    private static final int CACHE_SIZE = (int) (Runtime.getRuntime().maxMemory() / 1024) / 8;
    private static final LruCache<String, Bitmap> COVER_CACHE = new LruCache<String, Bitmap>(Math.max(CACHE_SIZE, 1024)) {
        @Override
        protected int sizeOf(String key, Bitmap bitmap) {
            return bitmap.getByteCount() / 1024;
        }
    };

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(4);
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    private List<Song> songs = new ArrayList<>();
    private final OnItemClickListener listener;
    private int playingSongId = -1;
    private MusicApi musicApi;
    private File filesDir;

    public SongAdapter(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setMusicApi(MusicApi musicApi) {
        this.musicApi = musicApi;
    }

    public void setFilesDir(File filesDir) {
        this.filesDir = filesDir;
    }

    public void setSongs(List<Song> songs) {
        this.songs = songs != null ? songs : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void setPlayingSongId(int playingSongId) {
        if (this.playingSongId == playingSongId) return;
        int previous = this.playingSongId;
        this.playingSongId = playingSongId;
        // Only rebind the two rows whose selection changed.
        for (int i = 0; i < songs.size(); i++) {
            int id = songs.get(i).getId();
            if (id == previous || id == playingSongId) notifyItemChanged(i);
        }
    }

    public Song getSong(int position) {
        if (position >= 0 && position < songs.size()) {
            return songs.get(position);
        }
        return null;
    }

    public List<Song> getSongs() {
        return songs;
    }

    public static Bitmap getCachedCover(int songId) {
        return COVER_CACHE.get(coverKey(null, songId));
    }

    public static void putCachedCover(int songId, Bitmap bitmap) {
        if (bitmap != null) {
            COVER_CACHE.put(coverKey(null, songId), bitmap);
        }
    }

    public static void clearCoverCache() {
        COVER_CACHE.evictAll();
    }

    public static void loadCover(MusicApi musicApi, File filesDir, int songId, ImageView targetView) {
        loadCover(musicApi, filesDir, songId, targetView, null);
    }

    public static void loadCover(MusicApi musicApi, File filesDir, int songId, ImageView targetView,
                                 CoverLoadListener listener) {
        if (targetView == null) return;

        if (songId < 0) musicApi = null;

        Object requestTag = new Object();
        targetView.setTag(requestTag);
        String cacheKey = coverKey(musicApi, songId);
        Bitmap cached = COVER_CACHE.get(cacheKey);
        if (cached != null) {
            targetView.setImageBitmap(cached);
            if (listener != null) listener.onCoverLoaded(cached);
            return;
        }

        targetView.setImageResource(R.drawable.ic_music_placeholder);

        MusicApi coverApi = musicApi;
        EXECUTOR.execute(() -> {
            Bitmap bitmap = null;

            // 1. 서버 API를 통해 커버 이미지 가져오기
            if (coverApi != null) {
                try {
                    bitmap = coverApi.getAlbumCoverBitmap(songId);
                } catch (Exception ignored) {
                }
            }

            // 2. 오프라인이거나 서버에서 가져오지 못한 경우 로컬 mp3 파일에서 앨범 아트 추출
            if (bitmap == null && filesDir != null) {
                bitmap = extractCoverFromLocalFile(filesDir, songId);
            }

            if (bitmap != null) COVER_CACHE.put(cacheKey, bitmap);
            final Bitmap finalBitmap = bitmap;
            MAIN_HANDLER.post(() -> {
                if (targetView.getTag() != requestTag) return;
                if (finalBitmap != null) targetView.setImageBitmap(finalBitmap);
                if (listener != null) listener.onCoverLoaded(finalBitmap);
            });
        });
    }

    private static String coverKey(MusicApi musicApi, int songId) {
        return (musicApi == null ? "local" : musicApi.getBaseUrl()) + "|" + songId;
    }

    private static Bitmap extractCoverFromLocalFile(File filesDir, int songId) {
        try {
            File localFile = new File(new File(filesDir, "music"), songId + ".mp3");
            if (localFile.exists()) {
                MediaMetadataRetriever mmr = new MediaMetadataRetriever();
                mmr.setDataSource(localFile.getAbsolutePath());
                byte[] art = mmr.getEmbeddedPicture();
                mmr.release();
                if (art != null) {
                    return BitmapFactory.decodeByteArray(art, 0, art.length);
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    @NonNull
    @Override
    public SongViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_song, parent, false);
        return new SongViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SongViewHolder holder, int position) {
        Song song = songs.get(position);
        holder.txtTitle.setText(song.getTitle());
        holder.txtArtist.setText(song.getArtist());

        // 현재 재생 중인 곡 텍스트 강조
        if (song.getId() == playingSongId) {
            holder.txtTitle.setTextColor(0xFFC9A9FF);
            holder.itemView.setSelected(true);
            holder.playingIndicator.setVisibility(View.VISIBLE);
        } else {
            holder.txtTitle.setTextColor(holder.defaultTitleColor);
            holder.itemView.setSelected(false);
            holder.playingIndicator.setVisibility(View.INVISIBLE);
        }

        // 앨범 커버 로드 (캐시 우선, 비동기 로딩)
        loadCover(musicApi, filesDir, song.getId(), holder.imgCover);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                int adapterPos = holder.getBindingAdapterPosition();
                if (adapterPos != RecyclerView.NO_POSITION) {
                    listener.onItemClick(adapterPos, songs.get(adapterPos));
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return songs.size();
    }

    public static class SongViewHolder extends RecyclerView.ViewHolder {
        ImageView imgCover;
        TextView txtTitle;
        TextView txtArtist;
        ImageView playingIndicator;
        int defaultTitleColor;

        public SongViewHolder(@NonNull View itemView) {
            super(itemView);
            imgCover = itemView.findViewById(R.id.imgSongCover);
            txtTitle = itemView.findViewById(R.id.txtItemTitle);
            txtArtist = itemView.findViewById(R.id.txtItemArtist);
            playingIndicator = itemView.findViewById(R.id.imgPlayingIndicator);
            defaultTitleColor = txtTitle.getCurrentTextColor();
        }
    }
}
