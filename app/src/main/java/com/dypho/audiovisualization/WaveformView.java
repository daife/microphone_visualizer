package com.dypho.audiovisualization;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import java.util.Arrays;

public class WaveformView extends View {

    //private short[] buffer = new short[1024];
    private double[] frequencyHistory = new double[120]; // 同一时刻能显示在历史数据数量多少
    private double[] loudnessHistory = new double[120]; // 
    private int historyIndex = 0;
    private Paint paintWaveform = new Paint();
    private Paint paintFrequency = new Paint();
    private Paint referenceLinePaint = new Paint();
    private Paint paintLoudness = new Paint(); // 响度曲线画笔
    private int lastN = 3;//去噪声中位数数目，只能是奇数
    private float frearea = 200f;//频率范围
    public WaveformView(Context context) {
        super(context);
        init();
    }

    public WaveformView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public WaveformView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
       // paintWaveform.setColor(0xFF0000FF); // 设置波形画笔颜色
      //  paintWaveform.setStrokeWidth(2f); // 设置波形画笔宽度

        paintFrequency.setColor(0xFFFF0000); // 设置频率曲线画笔颜色
        paintFrequency.setStrokeWidth(2f); // 设置频率曲线画笔宽度
        
        referenceLinePaint.setColor(0xFF00FF00); // 设置参考线画笔颜色
        referenceLinePaint.setStrokeWidth(2f); // 设置参考线画笔宽度
        
        
                paintLoudness.setColor(0xFF0000FF); // 设置响度曲线画笔颜色
        paintLoudness.setStrokeWidth(2f); // 设置响度曲线画笔宽度
    }

   /* public void updateWaveform(short[] buffer) {
        this.buffer = buffer;
        invalidate(); // 请求重绘视图
    }*/

    public void updateFrequencyHistory(double frequency) {
        frequencyHistory[historyIndex] = frequency;
        historyIndex = (historyIndex + 1) % frequencyHistory.length;
        invalidate(); // 请求重绘视图
    }
    public void updateLoudnessHistory(double loudness) {
        loudnessHistory[historyIndex] = loudness;
        historyIndex = (historyIndex + 1) % loudnessHistory.length;
        invalidate(); // 请求重绘视图
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        float centerY = height / 2f;

        // 绘制波形
     /*   if (buffer != null) {
            // ... (省略波形绘制代码)
            float lastX = -1;
            float lastY = -1;
            for (int i = 0; i < buffer.length; i++) {
                float x = width * i / (buffer.length - 1);
                float y = centerY - (buffer[i] / 32768f) * (height / 2f);//2f可以改成1f，敏感度更高
                if (lastX != -1) {
                    canvas.drawLine(lastX, lastY, x, y, paintWaveform);
                }
                lastX = x;
                lastY = y;
        }
            }*/

        // 绘制频率曲线
        if (frequencyHistory != null) {
            float lastX = -1;
            float lastY = -1;
            for (int i = 0; i < frequencyHistory.length-lastN+1; i++) {
                float x = width * i / (frequencyHistory.length - 1);
                double[] lastFive = new double[lastN];
    for (int j = 0; j < lastN; j++) {
        int index = (historyIndex + j+i) % frequencyHistory.length;
        lastFive[j] = frequencyHistory[index];
    }
                 Arrays.sort(lastFive);
                float medianFre = (float) lastFive[(lastN-1)/2];//获取最新五个数据中位数
                
                float y = height - (medianFre / frearea) * height; // 假设频率范围在200Hz
                if (lastX != -1) {
                    canvas.drawLine(lastX, lastY, x, y, paintFrequency);
                }
                lastX = x;
                lastY = y;
            }
        }
        // 绘制响度曲线
if (loudnessHistory != null) {
    float lastX = -1;
    float lastY = -1;
    for (int i = 0; i < loudnessHistory.length - lastN + 1; i++) {
        float x = width * i / (loudnessHistory.length - 1);
        double[] lastFive = new double[lastN];
        for (int j = 0; j < lastN; j++) {
            int index = (historyIndex + j + i) % loudnessHistory.length;
            lastFive[j] = loudnessHistory[index];
        }
        Arrays.sort(lastFive);
        float medianLoudness = (float) lastFive[(lastN - 1) / 2]; // 获取最新五个数据中位数

        float y = height - (medianLoudness / 160f) * height; // 假设响度范围在0-160
        if (lastX != -1) {
            canvas.drawLine(lastX, lastY, x, y, paintLoudness);
        }
        lastX = x;
        lastY = y;
    }
}
        // 绘制参考频率线
        drawReferenceLines(canvas);
    }
    private void drawReferenceLines(Canvas canvas) {
    int width = getWidth();
    int height = getHeight();

    // 计算参考线的y坐标，从视图底部向上
    float y1 = height - (height * (133-33) / frearea);
    float y2 = height - (height * (189-49) / frearea);//不知道为什么算出来的频率和实际的不一样。只能先这样修正一下。

    // 绘制第一条参考线
    canvas.drawLine(0, y1, width, y1, referenceLinePaint);

    // 绘制第二条参考线
    canvas.drawLine(0, y2, width, y2, referenceLinePaint);
}
}
