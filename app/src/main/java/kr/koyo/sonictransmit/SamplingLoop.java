package kr.koyo.sonictransmit;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.audiofx.AutomaticGainControl;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.nio.charset.StandardCharsets;

public class SamplingLoop extends Thread {
    private volatile boolean isRunning = true;
    private STFT stft;
    private final Handler handler;

    SamplingLoop(Handler _handler) {
        handler = _handler;
    }

    @Override
    public void run() {
        int minBytes = AudioRecord.getMinBufferSize(SonicParam.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (minBytes == AudioRecord.ERROR_BAD_VALUE) {
            return;
        }

        int readChuckSize = SonicParam.HOP_LEN;
        int bufferSampleSize = Math.max(minBytes / SonicParam.BYTE_OF_SAMPLE, SonicParam.FFT_LEN / 2) * 2;
        bufferSampleSize = (int)Math.ceil(1.0 * SonicParam.SAMPLE_RATE / bufferSampleSize) * bufferSampleSize;

        AudioRecord record = new AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SonicParam.SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build())
                .setBufferSizeInBytes(SonicParam.BYTE_OF_SAMPLE * bufferSampleSize)
                .build();

        if (AutomaticGainControl.isAvailable()) {
            AutomaticGainControl agc = AutomaticGainControl.create(record.getAudioSessionId());
        }

        if (record.getState() == AudioRecord.STATE_UNINITIALIZED) {
            return;
        }

        stft = new STFT(SonicParam.FFT_LEN, SonicParam.HOP_LEN, SonicParam.SAMPLE_RATE, SonicParam.N_FFT_AVERAGE, "Hanning");
        stft.setAWeighting(false);

        try {
            record.startRecording();
        } catch (IllegalStateException e) {
            return;
        }

        final byte[] bytes_letter = new byte[2];

        boolean started = false;
        boolean between = false;
        int right = 0;
        int count = 0;

        short[] audioSamples = new short[readChuckSize];
        while (isRunning) {
            int numOfReadShort = record.read(audioSamples, 0, readChuckSize);

            stft.feedData(audioSamples, numOfReadShort);
            if (stft.nElemSpectrumAmp() >= SonicParam.N_FFT_AVERAGE) {
                final double[] spectrumDB = stft.getSpectrumAmpDB();
                stft.calculatePeak();
                final double freq = stft.maxAmpFreq;

                if ((SonicParam.ONE_BIT - 2) < freq && freq < (SonicParam.ONE_BIT + 2) && between) {
                    Log.i("SamplingLoop.run", "ONE"+count);
                    between = false;
                    bytes_letter[right] |= (byte) Math.pow(2, count++);
                }
                else if ((SonicParam.ZERO_BIT - 2) < freq && freq < (SonicParam.ZERO_BIT + 2) && between) {
                    Log.i("SamplingLoop.run", "ZERO"+count);
                    between = false;
                    count++;
                }
                else if ((SonicParam.BEGIN_BIT - 2) < freq && freq < (SonicParam.BEGIN_BIT + 2)) {
                    Log.i("SamplingLoop.run", "BEGIN"+count);
                    started = true;
                    between = true;
                    count = 0;
                }
                else if ((SonicParam.BETWEEN_BIT - 2) < freq && freq < (SonicParam.BETWEEN_BIT + 2) && !between && started) {
                    Log.i("SamplingLoop.run", "BETWEEN"+count);
                    between = true;
                }
                else if ((SonicParam.SUBEND_BIT - 2) < freq && freq < (SonicParam.SUBEND_BIT + 2) && !between) {
                    Log.i("SamplingLoop.run", "SUBEND"+count);
                    between = true;
                    right = 1;
                    count = 0;
                }
                else if ((SonicParam.END_BIT - 2) < freq && freq < (SonicParam.END_BIT + 2) && !between) {
                    Log.i("SamplingLoop.run", "END"+count);
                    between = true;
                    right = 0;
                    if (count == 8) {
                        Message message = Message.obtain();
                        message.obj = new String(bytes_letter, StandardCharsets.UTF_16BE);
                        handler.sendMessage(message);
                    }
                    bytes_letter[0] = 0;
                    bytes_letter[1] = 0;
                    count = 0;
                }
            }
        }
        record.stop();
        record.release();
    }

    public void finish() {
        isRunning = false;
        interrupt();
    }
}
