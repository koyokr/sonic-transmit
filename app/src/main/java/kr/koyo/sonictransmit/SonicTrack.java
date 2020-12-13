package kr.koyo.sonictransmit;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.util.Log;

public class SonicTrack {
    private final static int NUM_SAMPLES = (int) (SonicParam.SAMPLE_RATE * SonicParam.DURATION);
    private final int[] freqHzList;
    private AudioTrack track;

    SonicTrack(final int[] _freqHzList) {
        freqHzList = _freqHzList;
        Log.i("SonicTrack", "" + 2 * NUM_SAMPLES * freqHzList.length);
        track = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SonicParam.SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build())
                .setBufferSizeInBytes(2 * NUM_SAMPLES * freqHzList.length)
                .build();
    }

    public void play() {
        byte[] data = new byte[2 * NUM_SAMPLES * freqHzList.length];
        int index = 0;
        for (final int freqHz : freqHzList) {
            double[] sample = new double[NUM_SAMPLES];
            for (int i = 0; i < NUM_SAMPLES; i++) {
                sample[i] = Math.sin(freqHz * 2 * Math.PI * i / SonicParam.SAMPLE_RATE);
            }
            for (final double dVal : sample) {
                final short val = (short) ((dVal * Short.MAX_VALUE));
                data[index++] = (byte) (val & 0x00FF);
                data[index++] = (byte) ((val & 0xFF00) >>> 8);
            }
        }

        track.write(data, 0, data.length);
        track.play();
    }

    public void stop() {
        track.stop();
        track.release();
    }
}
