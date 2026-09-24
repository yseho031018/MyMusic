package com.example.mymusic.player;

import android.os.SystemClock;

import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.audio.TeeAudioProcessor;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Reads a sparse sample of decoded playback PCM without altering the audio stream. */
@OptIn(markerClass = UnstableApi.class)
public final class AudioLevels implements TeeAudioProcessor.AudioBufferSink {
    private static volatile boolean enabled;
    private static volatile float bass;
    private static volatile float energy;
    private static volatile float kick;
    private static volatile long updatedAtMs;

    private int sampleRateHz;
    private int channelCount;
    private int encoding;
    private float lowPass;
    private float previousBass;

    public static void setEnabled(boolean value) {
        enabled = value;
        if (!value) resetLevels();
    }

    public static float bass() {
        return isFresh() ? bass : 0f;
    }

    public static float energy() {
        return isFresh() ? energy : 0f;
    }

    public static float kick() {
        return isFresh() ? kick : 0f;
    }

    public static boolean hasRecentData() {
        return isFresh();
    }

    private static boolean isFresh() {
        return enabled && SystemClock.elapsedRealtime() - updatedAtMs < 350;
    }

    private static void resetLevels() {
        bass = 0f;
        energy = 0f;
        kick = 0f;
        updatedAtMs = 0;
    }

    @Override public void flush(int sampleRateHz, int channelCount, int encoding) {
        this.sampleRateHz = sampleRateHz;
        this.channelCount = channelCount;
        this.encoding = encoding;
        lowPass = 0f;
        previousBass = 0f;
        resetLevels();
    }

    @Override public void handleBuffer(ByteBuffer buffer) {
        if (!enabled || sampleRateHz <= 0 || channelCount <= 0) return;
        int bytesPerSample = encoding == C.ENCODING_PCM_16BIT ? 2
                : encoding == C.ENCODING_PCM_FLOAT ? 4 : 0;
        if (bytesPerSample == 0) return;

        ByteBuffer samples = buffer.duplicate().order(ByteOrder.nativeOrder());
        int stride = bytesPerSample * channelCount * 4;
        float coefficient = Math.min(1f, 2f * (float) Math.PI * 140f * 4f / sampleRateHz);
        double lowSquares = 0;
        double fullSquares = 0;
        int count = 0;
        for (int index = samples.position(); index + bytesPerSample <= samples.limit(); index += stride) {
            float sample = bytesPerSample == 2 ? samples.getShort(index) / 32768f : samples.getFloat(index);
            lowPass += coefficient * (sample - lowPass);
            lowSquares += lowPass * lowPass;
            fullSquares += sample * sample;
            count++;
        }
        if (count == 0) return;
        float lowRms = (float) Math.sqrt(lowSquares / count);
        float fullRms = (float) Math.sqrt(fullSquares / count);
        float transientLevel = Math.max(0f, lowRms - previousBass * .75f);
        previousBass = previousBass * .45f + lowRms * .55f;
        bass = clamp(lowRms * 7f);
        energy = clamp(fullRms * 4f);
        kick = clamp(transientLevel * 13f);
        updatedAtMs = SystemClock.elapsedRealtime();
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
