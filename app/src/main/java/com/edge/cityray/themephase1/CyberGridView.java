package com.edge.cityray.themephase1;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.provider.Settings;
import android.view.View;

final class CyberGridView extends View {
    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tracer = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix shaderMatrix = new Matrix();
    private LinearGradient tracerGradient;
    private float tracerLength;
    private float phase;
    private boolean running;

    CyberGridView(Context context) {
        super(context);
        grid.setColor(Color.argb(20, 64, 207, 255));
        grid.setStrokeWidth(dp(1));
        tracer.setStrokeWidth(dp(2));
        tracer.setStrokeCap(Paint.Cap.ROUND);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        running = animationsEnabled();
        if (running) postInvalidateOnAnimation();
    }

    @Override
    protected void onDetachedFromWindow() {
        running = false;
        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        tracerLength = Math.max(dp(180), width * 0.22f);
        tracerGradient = new LinearGradient(0, 0, tracerLength, 0,
                new int[]{Color.TRANSPARENT, Color.argb(170, 38, 216, 255), Color.TRANSPARENT},
                null, Shader.TileMode.CLAMP);
        tracer.setShader(tracerGradient);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cell = dp(92);
        for (float x = 0; x <= getWidth(); x += cell) canvas.drawLine(x, 0, x, getHeight(), grid);
        for (float y = 0; y <= getHeight(); y += cell) canvas.drawLine(0, y, getWidth(), y, grid);

        float x = (phase % (getWidth() + tracerLength)) - tracerLength;
        float y = cell * 2.05f;
        if (tracerGradient != null) {
            shaderMatrix.setTranslate(x, y);
            tracerGradient.setLocalMatrix(shaderMatrix);
            canvas.drawLine(x, y, x + tracerLength, y, tracer);
        }

        if (running) {
            phase += dp(2.2f);
            postInvalidateOnAnimation();
        }
    }

    private boolean animationsEnabled() {
        try {
            return Settings.Global.getFloat(getContext().getContentResolver(),
                    Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f;
        } catch (Throwable ignored) {
            return true;
        }
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
