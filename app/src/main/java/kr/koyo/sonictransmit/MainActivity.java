package kr.koyo.sonictransmit;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import java.nio.charset.StandardCharsets;

public class MainActivity extends AppCompatActivity {
    SonicTrack sonicTrack;
    SamplingLoop samplingThread;
    Handler handler;
    Button buttonSend;
    Button buttonReceive;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        buttonSend = findViewById(R.id.button_send);
        buttonSend.setOnClickListener(v -> {
            final String textStart = getResources().getString(R.string.button_send);
            final String textStop = getResources().getString(R.string.button_stop);
            final Button b = (Button)v;
            final boolean stopped = b.getText().toString().equals(textStart);
            if (stopped) {
                buttonReceive.setEnabled(false);
                b.setText(textStop);
                startTrack();
            } else {
                buttonReceive.setEnabled(true);
                b.setText(textStart);
                stopTrack();
            }
        });

        buttonReceive = findViewById(R.id.button_receive);
        buttonReceive.setOnClickListener(v -> {
            final String textStart = getResources().getString(R.string.button_receive);
            final String textStop = getResources().getString(R.string.button_stop);
            final Button b = (Button)v;
            final boolean stopped = b.getText().toString().equals(textStart);
            if (stopped) {
                buttonSend.setEnabled(false);
                b.setText(textStop);
                startSampling();
            } else {
                buttonSend.setEnabled(true);
                b.setText(textStart);
                pauseSampling();
            }
        });
    }

    private void startTrack() {
        stopTrack();

        final EditText editText = findViewById(R.id.editText);
        final String text = editText.getText().toString();
        final byte[] bytes = text.getBytes(StandardCharsets.UTF_16BE);
        final int[] freqHzList = new int[bytes.length * 17];

        int index = 0;
        for (int i = 0; i < bytes.length; i += 2) {
            byte b = bytes[i];
            freqHzList[index++] = SonicParam.BEGIN_BIT;
            freqHzList[index++] = (b & 0x01) != 0 ? SonicParam.ONE_BIT : SonicParam.ZERO_BIT;
            freqHzList[index++] = SonicParam.BETWEEN_BIT;
            freqHzList[index++] = (b & 0x02) != 0  ? SonicParam.ONE_BIT : SonicParam.ZERO_BIT;
            freqHzList[index++] = SonicParam.BETWEEN_BIT;
            freqHzList[index++] = (b & 0x04) != 0  ? SonicParam.ONE_BIT : SonicParam.ZERO_BIT;
            freqHzList[index++] = SonicParam.BETWEEN_BIT;
            freqHzList[index++] = (b & 0x08) != 0  ? SonicParam.ONE_BIT : SonicParam.ZERO_BIT;
            freqHzList[index++] = SonicParam.BETWEEN_BIT;
            freqHzList[index++] = (b & 0x10) != 0  ? SonicParam.ONE_BIT : SonicParam.ZERO_BIT;
            freqHzList[index++] = SonicParam.BETWEEN_BIT;
            freqHzList[index++] = (b & 0x20) != 0  ? SonicParam.ONE_BIT : SonicParam.ZERO_BIT;
            freqHzList[index++] = SonicParam.BETWEEN_BIT;
            freqHzList[index++] = (b & 0x40) != 0  ? SonicParam.ONE_BIT : SonicParam.ZERO_BIT;
            freqHzList[index++] = SonicParam.BETWEEN_BIT;
            freqHzList[index++] = (b & 0x80) != 0  ? SonicParam.ONE_BIT : SonicParam.ZERO_BIT;
            freqHzList[index++] = SonicParam.SUBEND_BIT;
            b = bytes[i+1];
            freqHzList[index++] = SonicParam.BEGIN_BIT;
            freqHzList[index++] = (b & 0x01) != 0 ? SonicParam.ONE_BIT : SonicParam.ZERO_BIT;
            freqHzList[index++] = SonicParam.BETWEEN_BIT;
            freqHzList[index++] = (b & 0x02) != 0  ? SonicParam.ONE_BIT : SonicParam.ZERO_BIT;
            freqHzList[index++] = SonicParam.BETWEEN_BIT;
            freqHzList[index++] = (b & 0x04) != 0  ? SonicParam.ONE_BIT : SonicParam.ZERO_BIT;
            freqHzList[index++] = SonicParam.BETWEEN_BIT;
            freqHzList[index++] = (b & 0x08) != 0  ? SonicParam.ONE_BIT : SonicParam.ZERO_BIT;
            freqHzList[index++] = SonicParam.BETWEEN_BIT;
            freqHzList[index++] = (b & 0x10) != 0  ? SonicParam.ONE_BIT : SonicParam.ZERO_BIT;
            freqHzList[index++] = SonicParam.BETWEEN_BIT;
            freqHzList[index++] = (b & 0x20) != 0  ? SonicParam.ONE_BIT : SonicParam.ZERO_BIT;
            freqHzList[index++] = SonicParam.BETWEEN_BIT;
            freqHzList[index++] = (b & 0x40) != 0  ? SonicParam.ONE_BIT : SonicParam.ZERO_BIT;
            freqHzList[index++] = SonicParam.BETWEEN_BIT;
            freqHzList[index++] = (b & 0x80) != 0  ? SonicParam.ONE_BIT : SonicParam.ZERO_BIT;
            freqHzList[index++] = SonicParam.END_BIT;
        }
        Log.i("startTrack", text + " " + bytes.length + " " + freqHzList.length);
        sonicTrack = new SonicTrack(freqHzList);
        sonicTrack.play();
    }

    private void stopTrack() {
        if (sonicTrack != null) {
            sonicTrack.stop();
            sonicTrack = null;
        }
    }

    @SuppressLint("HandlerLeak")
    private void startSampling() {
        pauseSampling();
        if (!checkAndRequestPermissions())
            return;
        EditText editText = findViewById(R.id.editText);
        editText.setText("");
        handler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                final String letter = (String) msg.obj;
                editText.setText(editText.getText().toString() + letter);
            }
        };
        samplingThread = new SamplingLoop(handler);
        samplingThread.start();
    }

    private void pauseSampling() {
        if (samplingThread != null) {
            samplingThread.finish();
            try {
                samplingThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            samplingThread = null;
        }
    }

    private int count_permission_explanation = 0;
    private int count_permission_request = 0;

    private boolean checkAndRequestPermissions() {
        final String perm = Manifest.permission.RECORD_AUDIO;
        if (ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED) {
            return true;
        }
        else if (ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this, perm) && count_permission_explanation < 1) {
            count_permission_explanation++;
        }
        else if (count_permission_request < 3) {
            ActivityCompat.requestPermissions(this, new String[]{perm}, 1);
            count_permission_explanation = 0;
            count_permission_request++;
        }
        else {
            this.runOnUiThread(() -> {
                Toast.makeText(getApplicationContext(), "Permission denied.", Toast.LENGTH_SHORT).show();
            });
        }
        return false;
    }
}