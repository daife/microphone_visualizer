package com.dypho.audiovisualization;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.app.Activity;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class AudioVisualizationActivity extends Activity {

    private WaveformView waveformView;
    private TextView frequencyTextView;
    private Thread recordingThread;
    private boolean isRecording = false;
    private static final int SAMPLE_RATE = 44100;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int FFT_SIZE = 1024;
    private FFT fft;
    private TextView loudnessTextView;
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_audio_visualization);
        waveformView = findViewById(R.id.waveformView);
        frequencyTextView = findViewById(R.id.frequencyTextView);
        loudnessTextView = findViewById(R.id.loudnessTextView);
        fft = new FFT(FFT_SIZE);
        // Check permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[] {Manifest.permission.RECORD_AUDIO},
                    REQUEST_RECORD_AUDIO_PERMISSION);
        } else {
            startRecording();
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startRecording();
            } else {
                Toast.makeText(this, "不给爷权限还想玩？", Toast.LENGTH_SHORT)
                        .show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopRecording();
    }

private void startRecording() {
    int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
    final AudioRecord audioRecord =
            new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize);

    recordingThread =
            new Thread(
                    new Runnable() {
                        @Override
                        public void run() {
                            audioRecord.startRecording();
                            isRecording = true;
                            short[] buffer = new short[FFT_SIZE];
                            double[] audioData = new double[FFT_SIZE];
                            double[] fftDataReal = new double[FFT_SIZE];
                            double[] fftDataImag = new double[FFT_SIZE];
                            double[] frequencyBuffer = new double[FFT_SIZE / 2];

                            while (isRecording) {
                                int read = audioRecord.read(buffer, 0, buffer.length);
                                if (read > 0) {
                                    for (int i = 0; i < read; i++) {
                                        audioData[i] = buffer[i] / 32768.0;
                                    }
                                    //waveformView.updateWaveform(buffer);

                                    System.arraycopy(audioData, 0, fftDataReal, 0, FFT_SIZE);
                                    java.util.Arrays.fill(fftDataImag, 0.0);
                                    fft.fft(fftDataReal, fftDataImag);

                                    double maxMagnitude = 0;
                                    int maxIndex = 0;
                                    for (int i = 0; i < FFT_SIZE / 2; i++) {
                                        double real = fftDataReal[i];
                                        double imag = fftDataImag[i];
                                        double magnitude = Math.sqrt(real * real + imag * imag);
                                        frequencyBuffer[i] = magnitude;
                                        if (magnitude > maxMagnitude) {
                                            maxMagnitude = magnitude;
                                            maxIndex = i;
                                        }
                                    }

                                    final double frequency = maxIndex * (SAMPLE_RATE / FFT_SIZE);
                                    final String frequencyText = String.format("Frequency: %.2f Hz", frequency);
                                    runOnUiThread(
                                            new Runnable() {
                                                @Override
                                                public void run() {
                                                    frequencyTextView.setText(frequencyText);
                                                }
                                            });

                                    double loudness = calculateLoudness(audioData);
                                    final String loudnessText = String.format("Loudness: %.2f dB", loudness);
                                    runOnUiThread(
                                            new Runnable() {
                                                @Override
                                                public void run() {
                                                    loudnessTextView.setText(loudnessText);
                                                }
                                            });
                                    runOnUiThread(
                                            new Runnable() {
                                                @Override
                                                public void run() {
                                                    waveformView.updateFrequencyHistory(frequency);
                                                }
                                            });
                                    runOnUiThread(
                                            new Runnable() {
                                                @Override
                                                public void run() {
                                                    waveformView.updateLoudnessHistory(loudness);
                                                }
                                            });
                                }
                            }
                            audioRecord.stop();
                            audioRecord.release();
                        }
                    });
    recordingThread.start();
}

    private double calculateLoudness(double[] audioData) {
        double sumOfSquares = 0.0;
        for (double sample : audioData) {
            sumOfSquares += sample * sample;
        }
        double rms = Math.sqrt(sumOfSquares / audioData.length);
        // 转换为分贝
        return 20 * Math.log10(rms) + 90.0; // 加上一个常数以获得正数
    }

    private void stopRecording() {
        isRecording = false;
        if (recordingThread != null) {
            try {
                recordingThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
