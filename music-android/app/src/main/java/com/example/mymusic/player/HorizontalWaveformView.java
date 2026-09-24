package com.example.mymusic.player;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

/** A horizontal wave that replaces unavailable synced captions. */
public final class HorizontalWaveformView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path wave = new Path();
    private boolean playing;
    private boolean animationsEnabled;
    private float shownLevel;
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
            float target = playing ? Math.min(1f, AudioLevels.bass() * .55f
                    + AudioLevels.energy() * .35f + AudioLevels.kick() * .5f) : 0f;
            shownLevel += (target - shownLevel) * Math.min(1f, elapsed / 110f);
            if (playing) phase += elapsed * .018f;
        }

        float density = getResources().getDisplayMetrics().density;
        float left = 18f * density;
        float right = getWidth() - left;
        float middle = getHeight() / 2f;
        if (right <= left) return;

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeWidth(1f * density);
        paint.setColor(Color.argb(65, Color.red(accentColor),
                Color.green(accentColor), Color.blue(accentColor)));
        canvas.drawLine(left, middle, right, middle, paint);

        wave.reset();
        float amplitude = Math.min(getHeight() * .32f, 18f * density) * shownLevel;
        int segments = Math.max(32, Math.round((right - left) / (3f * density)));
        for (int i = 0; i <= segments; i++) {
            float progress = (float) i / segments;
            float envelope = (float) Math.sin(Math.PI * progress);
            float shape = (float) (Math.sin(progress * Math.PI * 18 + phase) * .7
                    + Math.sin(progress * Math.PI * 31 - phase * .7f) * .3);
            float x = left + (right - left) * progress;
            float y = middle + shape * envelope * amplitude;
            if (i == 0) wave.moveTo(x, y); else wave.lineTo(x, y);
        }
        paint.setStrokeWidth(2f * density);
        paint.setColor(accentColor);
        canvas.drawPath(wave, paint);

        if (animationsEnabled && isShown() && (playing || shownLevel > .01f)) {
            postInvalidateOnAnimation();
        }
    }
}
