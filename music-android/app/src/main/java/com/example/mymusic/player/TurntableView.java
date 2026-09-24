package com.example.mymusic.player;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.util.AttributeSet;

import androidx.appcompat.widget.AppCompatImageView;

/** A vinyl record with rotating album art, a pivoting tonearm and a subtle sound-wave halo. */
public final class TurntableView extends AppCompatImageView {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final RectF rect = new RectF();
    private final RadialGradient vinylShader = new RadialGradient(378, 390, 680,
            new int[]{0xFF303039, 0xFF111116, 0xFF35343C, 0xFF101014},
            new float[]{0f, .38f, .74f, 1f}, Shader.TileMode.CLAMP);
    private boolean hasTrack;
    private boolean playing;
    private boolean animationsEnabled;
    private float rotation;
    private float wavePhase;
    private float waveStrength;
    private float needlePosition;
    private float playbackProgress;
    private float shownBass;
    private float shownEnergy;
    private float shownKick;
    private long lastFrameTime;

    public TurntableView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setContentDescription("회전하는 앨범 레코드와 재생 바늘");
    }

    public void setPlaybackState(boolean hasTrack, boolean playing) {
        this.hasTrack = hasTrack;
        this.playing = hasTrack && playing;
        invalidate();
    }

    public void setAnimationsEnabled(boolean enabled) {
        animationsEnabled = enabled;
        lastFrameTime = 0;
        invalidate();
    }

    public void setPlaybackProgress(float progress) {
        playbackProgress = Math.max(0f, Math.min(1f, progress));
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        long now = SystemClock.uptimeMillis();
        long elapsed = lastFrameTime == 0 ? 0 : Math.min(64, Math.max(0, now - lastFrameTime));
        lastFrameTime = now;
        if (animationsEnabled && elapsed > 0) {
            float step = Math.min(1f, elapsed / 320f);
            needlePosition += ((playing ? 1f : 0f) - needlePosition) * step;
            waveStrength += ((playing ? 1f : 0f) - waveStrength) * step;
            float audioStep = Math.min(1f, elapsed / 90f);
            shownBass += ((playing ? AudioLevels.bass() : 0f) - shownBass) * audioStep;
            shownEnergy += ((playing ? AudioLevels.energy() : 0f) - shownEnergy) * audioStep;
            shownKick += ((playing ? AudioLevels.kick() : 0f) - shownKick) * audioStep;
            if (playing) {
                rotation = (rotation + elapsed * 0.2f) % 360f; // 33⅓ RPM
                wavePhase += elapsed * 0.0045f;
            }
        }

        float size = Math.min(getWidth(), getHeight());
        if (size <= 0) return;
        canvas.save();
        canvas.translate((getWidth() - size) / 2f, (getHeight() - size) / 2f);
        canvas.scale(size / 1000f, size / 1000f);
        drawDeck(canvas);
        drawWave(canvas);
        drawRecord(canvas);
        drawNeedle(canvas);
        canvas.restore();

        if (animationsEnabled && (playing || Math.abs(needlePosition - (playing ? 1f : 0f)) > .005f
                || waveStrength > .005f && !playing)) {
            postInvalidateOnAnimation();
        }
    }

    private void drawDeck(Canvas canvas) {
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
        paint.setColor(0xFF212128);
        rect.set(12, 12, 988, 988);
        canvas.drawRoundRect(rect, 54, 54, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2);
        paint.setColor(0xFF383840);
        rect.set(14, 14, 986, 986);
        canvas.drawRoundRect(rect, 52, 52, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        paint.setTextSize(26);
        paint.setColor(0xFFCDB8F6);
        canvas.drawText("MY MUSIC", 68, 92, paint);
        paint.setTextSize(21);
        paint.setColor(0xFF92909E);
        canvas.drawText("33⅓ RPM   ·   SIDE A", 68, 940, paint);

        paint.setColor(playing ? 0xFFD2B8FF : 0xFF6A6673);
        canvas.drawCircle(920, 88, 7, paint);
    }

    private void drawWave(Canvas canvas) {
        final float centerX = 463f;
        final float centerY = 520f;
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(4);
        paint.setColor(0xFF514365);
        canvas.drawCircle(centerX, centerY, 368, paint);
        for (int i = 0; i < 90; i++) {
            double angle = i * Math.PI * 2 / 90;
            float rhythm = (float) (0.45 + 0.55 * Math.abs(Math.sin(i * .57 + wavePhase)
                    * Math.cos(i * .19 - wavePhase * .7)));
            float intensity = .18f + shownBass * .55f + shownEnergy * .35f + shownKick * .55f;
            float length = 5 + waveStrength * (5 + 38 * intensity * rhythm);
            float inner = 379;
            float outer = inner + length;
            paint.setColor(Color.argb((int) (65 + waveStrength * (100 + shownKick * 70)), 203, 174, 255));
            canvas.drawLine(centerX + (float) Math.cos(angle) * inner,
                    centerY + (float) Math.sin(angle) * inner,
                    centerX + (float) Math.cos(angle) * outer,
                    centerY + (float) Math.sin(angle) * outer, paint);
        }
        paint.setStrokeCap(Paint.Cap.BUTT);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawRecord(Canvas canvas) {
        final float centerX = 463f;
        final float centerY = 520f;
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x66000000);
        canvas.drawCircle(centerX + 8, centerY + 17, 354, paint);

        canvas.save();
        canvas.rotate(rotation, centerX, centerY);
        paint.setShader(vinylShader);
        canvas.drawCircle(centerX, centerY, 350, paint);
        paint.setShader(null);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2);
        for (int radius = 199; radius < 346; radius += 12) {
            paint.setColor(radius % 3 == 0 ? 0xFF484650 : 0xFF35343D);
            canvas.drawCircle(centerX, centerY, radius, paint);
        }
        paint.setStrokeWidth(5);
        paint.setColor(0xFF5D576B);
        canvas.drawCircle(centerX, centerY, 348, paint);
        paint.setStyle(Paint.Style.FILL);

        paint.setColor(0xFFBBA4E2);
        canvas.drawCircle(centerX, centerY, 185, paint);
        drawCover(canvas, centerX, centerY, 180);
        paint.setColor(0xFFDCD6E7);
        canvas.drawCircle(centerX, centerY, 13, paint);
        paint.setColor(0xFF27242D);
        canvas.drawCircle(centerX, centerY, 5, paint);
        canvas.restore();
    }

    private void drawCover(Canvas canvas, float centerX, float centerY, float radius) {
        Drawable cover = getDrawable();
        if (cover == null) return;
        canvas.save();
        clip.reset();
        clip.addCircle(centerX, centerY, radius, Path.Direction.CW);
        canvas.clipPath(clip);
        int width = Math.max(1, cover.getIntrinsicWidth());
        int height = Math.max(1, cover.getIntrinsicHeight());
        float scale = Math.max(radius * 2 / width, radius * 2 / height);
        float halfWidth = width * scale / 2;
        float halfHeight = height * scale / 2;
        cover.setBounds(Math.round(centerX - halfWidth), Math.round(centerY - halfHeight),
                Math.round(centerX + halfWidth), Math.round(centerY + halfHeight));
        cover.draw(canvas);
        canvas.restore();
    }

    private void drawNeedle(Canvas canvas) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF101014);
        canvas.drawCircle(820, 190, 64, paint);
        paint.setColor(0xFF706A79);
        canvas.drawCircle(820, 190, 47, paint);
        paint.setColor(0xFF29252F);
        canvas.drawCircle(820, 190, 31, paint);

        canvas.save();
        canvas.rotate(-14 + needlePosition * (29 + playbackProgress * 8), 820, 190);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(24);
        paint.setColor(0xFF17161A);
        canvas.drawLine(820, 205, 820, 530, paint);
        paint.setStrokeWidth(15);
        paint.setColor(0xFFC9C5CF);
        canvas.drawLine(820, 205, 820, 530, paint);
        paint.setStrokeWidth(7);
        paint.setColor(0xFFEEEEF1);
        canvas.drawLine(815, 220, 815, 522, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFBFB9C5);
        rect.set(794, 512, 846, 578);
        canvas.drawRoundRect(rect, 8, 8, paint);
        paint.setColor(0xFFB796EE);
        canvas.drawCircle(820, 589, 8, paint);
        canvas.restore();

        paint.setColor(0xFFE0DAE4);
        canvas.drawCircle(820, 190, 17, paint);
        paint.setColor(0xFF635D6D);
        canvas.drawCircle(820, 190, 8, paint);
    }
}
