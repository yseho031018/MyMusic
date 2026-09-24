package com.example.mymusic.player;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

/** A row of rounded bars with a wave-shaped silhouette when synced lyrics are unavailable. */
public final class HorizontalWaveformView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean playing;
    private boolean animationsEnabled;
    private float shownLevel;
    private float shownAudioAvailable;
    private float phase;
    private long lastFrameTime;
    private int accentColor = AlbumAccent.DEFAULT;

    public HorizontalWaveformView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setPlaybackActive(boolean playing) {
        this.playing = playing;
        invalidate();
    }

    public void setAnimationsEnabled(boolean enabled) {
        animationsEnabled = enabled;
        lastFrameTime = 0;
        invalidate();
    }

    public void setAccentColor(int color) {
        if (accentColor == color) return;
        accentColor = color;
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        long now = SystemClock.uptimeMillis();
        long elapsed = lastFrameTime == 0 ? 0 : Math.min(64, Math.max(0, now - lastFrameTime));
        lastFrameTime = now;
        if (animationsEnabled && elapsed > 0) {
            boolean audioAvailable = playing && AudioLevels.hasRecentData();
            float target = audioAvailable ? Math.min(1f, AudioLevels.bass() * .55f
                    + AudioLevels.energy() * .35f + AudioLevels.kick() * .5f) : 0f;
            shownLevel += (target - shownLevel) * Math.min(1f, elapsed / 220f);
            shownAudioAvailable += ((audioAvailable ? 1f : 0f) - shownAudioAvailable)
                    * Math.min(1f, elapsed / 220f);
            if (audioAvailable) phase += elapsed * .009f;
        }

        float density = getResources().getDisplayMetrics().density;
        int count = 32;
        float barWidth = 3.5f * density;
        float gap = Math.min(9f * density, (getWidth() - 40f * density - barWidth) / (count - 1));
        if (gap <= barWidth) return;
        float left = (getWidth() - ((count - 1) * gap + barWidth)) / 2f;
        float baseline = getHeight() * .76f;
        float maxHeight = getHeight() * .68f;
        paint.setStyle(Paint.Style.FILL);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(accentColor);
        for (int i = 0; i < count; i++) {
            float broadWave = ((float) Math.sin(i * .68f + .2f) + 1f) * .5f;
            float smallWave = ((float) Math.sin(i * 1.51f + 1.3f) + 1f) * .5f;
            float profile = .25f + .48f * broadWave + .27f * smallWave;
            float motion = shownAudioAvailable * shownLevel
                    * (((float) Math.sin(i * .48f + phase) + 1f) * .5f);
            float height = Math.min(maxHeight, 6f * density
                    + profile * (16f + 20f * shownLevel) * density + motion * 6f * density);
            float x = left + i * gap;
            canvas.drawRoundRect(x, baseline - height, x + barWidth, baseline,
                    barWidth / 2f, barWidth / 2f, paint);
        }

        if (animationsEnabled && isShown()) {
            if (shownLevel > .01f || shownAudioAvailable > .01f) postInvalidateOnAnimation();
            else if (playing) postInvalidateDelayed(120);
        }
    }
}
