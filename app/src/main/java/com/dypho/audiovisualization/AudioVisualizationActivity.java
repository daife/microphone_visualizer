package com.dypho.audiovisualization;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.app.Activity;
import android.widget.TextView;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.app.Activity;

public class AudioVisualizationActivity extends Activity {

    private WaveformView waveformView;
    private TextView frequencyTextView; // 新增TextView用于显示频率
    private Thread recordingThread;
    private boolean isRecording = false;
    private static final int SAMPLE_RATE = 44100; // 采样率
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO; // 单声道
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT; // 16位PCM编码
    private static final int FFT_SIZE = 1024; // FFT大小
    private FFT fft; // FFT类实例

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_audio_visualization);
        waveformView = findViewById(R.id.waveformView);
        frequencyTextView = findViewById(R.id.frequencyTextView); // 初始化TextView
        fft = new FFT(FFT_SIZE); // 初始化FFT
        startRecording();
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
                                double[] frequencyBuffer = new double[FFT_SIZE / 2]; // 存储频率数据的缓冲区

                                while (isRecording) {
                                    int read = audioRecord.read(buffer, 0, buffer.length);
                                    if (read > 0) {
                                        // 归一化并更新波形视图
                                        for (int i = 0; i < read; i++) {
                                            audioData[i] = buffer[i] / 32768.0;
                                        }
                                        waveformView.updateWaveform(buffer);

                                        // 执行FFT
                                        System.arraycopy(audioData, 0, fftDataReal, 0, FFT_SIZE);
                                        java.util.Arrays.fill(fftDataImag, 0.0); // 初始化虚部为0
                                        fft.fft(fftDataReal, fftDataImag);

                                        // 分析FFT结果，获取频率信息
                                        double maxMagnitude = 0;
                                        int maxIndex = 0;
                                        for (int i = 0; i < FFT_SIZE / 2; i++) {
                                            double real = fftDataReal[i];
                                            double imag = fftDataImag[i];
                                            double magnitude = Math.sqrt(real * real + imag * imag);
                                            frequencyBuffer[i] =
                                                    magnitude; // 将FFT的幅度存储到frequencyBuffer
                                            if (magnitude > maxMagnitude) {
                                                maxMagnitude = magnitude;
                                                maxIndex = i;
                                            }
                                        }

                                        final double frequency =
                                                maxIndex * (SAMPLE_RATE / FFT_SIZE);
                                        // 在UI线程更新频率显示
                                        final String frequencyText =
                                                String.format("Frequency: %.2f Hz", frequency);
                                        runOnUiThread(
                                                new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        frequencyTextView.setText(frequencyText);
                                                    }
                                                });
                                        runOnUiThread(
                                                new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        waveformView.updateFrequencyHistory(
                                                                frequency);
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
