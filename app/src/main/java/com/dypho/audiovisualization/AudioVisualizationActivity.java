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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import android.os.Handler;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.audiofx.Visualizer;
import org.jtransforms.fft.DoubleFFT_1D;

public class AudioVisualizationActivity extends Activity {

    private WaveformView waveformView;
    private TextView frequencyTextView;
    private Thread recordingThread;
    private volatile boolean isRecording = false;
    private static final int SAMPLE_RATE = 44100;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int FFT_SIZE =
            32768; // 频率分辨率为SAMPLE_RATE/FFT_SIZE也就是说FFT_SIZE越大分辨率越高越丝滑，相应的计算成本也增加，会拉低读取频率；同时也应该是2的幂
    private TextView loudnessTextView;
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private ExecutorService executorService;
    private BlockingQueue<ShortBufferTask> taskQueue;
    private Handler uiHandler;
    private AudioRecord audioRecord;
    private DoubleFFT_1D fft;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_audio_visualization);
        waveformView = findViewById(R.id.waveformView);
        frequencyTextView = findViewById(R.id.frequencyTextView);
        loudnessTextView = findViewById(R.id.loudnessTextView);
        fft = new DoubleFFT_1D(FFT_SIZE);
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
                Toast.makeText(this, "不给爷权限还想玩？", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopRecording();
    }
    
    private static class ShortBufferTask {
        short[] buffer;
        double frequency;
        double loudness;

        ShortBufferTask(short[] buffer) {
            this.buffer = buffer;
        }
    }
    
    public void startRecording() {
        int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
        audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize);

        executorService = Executors.newCachedThreadPool();
        taskQueue = new LinkedBlockingQueue<>();

        uiHandler = new Handler(); // 用于在主线程更新UI

        // 启动音频记录线程
        Thread recordingThread = new Thread(() -> {
            audioRecord.startRecording();
            isRecording = true;
            short[] buffer = new short[bufferSize];
            while (isRecording) {
                int readSize = audioRecord.read(buffer, 0, buffer.length);
                if (readSize > 0) {
                    ShortBufferTask task = new ShortBufferTask(buffer);
                    executorService.submit(() -> processAudioData(task));
                }
            }
            audioRecord.stop();
            audioRecord.release();
            isRecording = false;
            executorService.shutdown();
        });

        recordingThread.start();
    }
    
    
private void processAudioData(ShortBufferTask task) {
    // 创建一个长度为FFT_SIZE * 2的数组，用于存储实部和虚部
    double[] audioData = new double[FFT_SIZE * 2];
    for (int i = 0; i < task.buffer.length; i++) {
        // 将short音频数据转换为double，并放置在数组的实部位置
        // 虚部初始化为0
        audioData[2 * i] = task.buffer[i] / 32768.0;
        audioData[2 * i + 1] = 0.0;
    }

    // 如果音频数据长度小于FFT_SIZE，则剩余部分填充0
    if (task.buffer.length < FFT_SIZE) {
        for (int i = task.buffer.length; i < FFT_SIZE; i++) {
            audioData[2 * i] = 0.0;
            audioData[2 * i + 1] = 0.0;
        }
    }

    // 对数组执行FFT运算，结果仍然存储在audioData中
    fft.complexForward(audioData);

    // 计算响度
    task.loudness = calculateLoudness(audioData);

    // 寻找最大幅度（跳过直流分量，从索引1开始）
    double maxMagnitude = 0;
    int maxIndex = 0;
    for (int i = 1; i < FFT_SIZE / 2; i++) { // 注意从1开始，到FFT_SIZE/2结束
        // 计算幅度
        double real = audioData[2 * i];
        double imag = audioData[2 * i + 1];
        double magnitude = Math.sqrt(real * real + imag * imag);
        if (magnitude > maxMagnitude) {
            maxMagnitude = magnitude;
            maxIndex = i;
        }
    }

    // 将最大幅度的索引转换为频率
    double frequency = maxIndex * (SAMPLE_RATE / FFT_SIZE)*1.351-1.82;//后来发现和实际频率成线性关系，拟合了一下，应该差不多了
    /*不知道为什么计算出来的频率比实际的要小几十,试过了两种不同的依赖包结果都是一样的*/
    // 更新UI
    uiHandler.post(() -> {
        // 更新UI使用frequency变量
        updateFrequency(frequency);
        updateLoudness(task.loudness);
    });
}
    private void updateFrequency(double frequency) {
        // 更新频率显示
        frequencyTextView.setText(
                                    String.format("Frequency: %.2f Hz", frequency));
                            //loudnessTextView.setText(String.format("Loudness: %.2f dB", loudness));
                            waveformView.updateFrequencyHistory(frequency);
                           // waveformView.updateLoudnessHistory(loudness);
    }

    private void updateLoudness(double loudness) {
        // 更新响度显示
        //frequencyTextView.setText(
                                   // String.format("Frequency: %.2f Hz", frequency));
                            loudnessTextView.setText(String.format("Loudness: %.2f dB", loudness));
                           // waveformView.updateFrequencyHistory(frequency);
                            waveformView.updateLoudnessHistory(loudness);
    }

    // 停止录音
    public void stopRecording() {
        isRecording = false;
    }
    
    /*......*/

    private double calculateLoudness(double[] audioData) {
        double sumOfSquares = 0.0;
        for (double sample : audioData) {
            sumOfSquares += sample * sample;
        }
        double rms = Math.sqrt(sumOfSquares / audioData.length);
        // 转换为分贝
        return 20 * Math.log10(rms) + 58.0; // 加上一个常数以获得正数
    }
}
