package com.edge.cityray.themephase1;

import android.content.Context;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.provider.Settings;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;

final class AccentFrameLayout extends FrameLayout {
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rail = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint beam = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path perimeter = new Path();
    private final Matrix beamMatrix = new Matrix();
    private LinearGradient beamGradient;
    private ValueAnimator beamAnimator;
    private float reveal = 1f;
    private float beamPhase;
    private boolean beamEnabled;

    AccentFrameLayout(Context context) {
        super(context);
        setWillNotDraw(false);
        line.setColor(Color.rgb(38, 216, 255));
        line.setStrokeWidth(dp(3));
        line.setStrokeCap(Paint.Cap.SQUARE);
        rail.setColor(Color.argb(125, 38, 216, 255));
        rail.setStyle(Paint.Style.STROKE);
        rail.setStrokeWidth(dp(1));
        beam.setStyle(Paint.Style.STROKE);
        beam.setStrokeWidth(dp(3));
        beam.setStrokeCap(Paint.Cap.ROUND);
    }

    void setBeamEnabled(boolean enabled) {
        beamEnabled = enabled;
        if (isAttachedToWindow()) {
            if (enabled) startBeam(); else stopBeam();
        }
        invalidate();
    }

    void setReveal(float value) {
        reveal = Math.max(0f, Math.min(1f, value));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float inset = dp(4);
        float arm = Math.min(getWidth(), getHeight()) * 0.16f * reveal;
        float r = getWidth() - inset;
        float b = getHeight() - inset;
        canvas.drawLine(inset, inset, inset + arm, inset, line);
        canvas.drawLine(inset, inset, inset, inset + arm, line);
        canvas.drawLine(r - arm, inset, r, inset, line);
        canvas.drawLine(r, inset, r, inset + arm, line);
        canvas.drawLine(inset, b, inset + arm, b, line);
        canvas.drawLine(inset, b - arm, inset, b, line);
        canvas.drawLine(r - arm, b, r, b, line);
        canvas.drawLine(r, b - arm, r, b, line);

        if (beamEnabled && beamGradient != null) {
            canvas.drawPath(perimeter, rail);
            beamMatrix.setTranslate(beamPhase, 0f);
            beamGradient.setLocalMatrix(beamMatrix);
            canvas.drawPath(perimeter, beam);
        }
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        float inset = dp(5);
        float cut = Math.min(dp(26), Math.min(width, height) * 0.12f);
        perimeter.reset();
        perimeter.moveTo(inset + cut, inset);
        perimeter.lineTo(width - inset, inset);
        perimeter.lineTo(width - inset, height - inset - cut);
        perimeter.lineTo(width - inset - cut, height - inset);
        perimeter.lineTo(inset, height - inset);
        perimeter.lineTo(inset, inset + cut);
        perimeter.close();

        float span = Math.max(dp(520), width * 0.92f);
        beamGradient = new LinearGradient(-span, 0, 0, 0,
                new int[]{Color.TRANSPARENT, Color.TRANSPARENT,
                        Color.rgb(38, 216, 255), Color.rgb(47, 111, 255),
                        Color.rgb(157, 78, 255), Color.rgb(255, 55, 199),
                        Color.TRANSPARENT, Color.TRANSPARENT},
                new float[]{0f, .2f, .36f, .48f, .60f, .72f, .84f, 1f},
                Shader.TileMode.CLAMP);
        beam.setShader(beamGradient);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (beamEnabled) startBeam();
    }

    @Override
    protected void onDetachedFromWindow() {
        stopBeam();
        super.onDetachedFromWindow();
    }

    private void startBeam() {
        if (!animationsEnabled() || beamAnimator != null) return;
        float travel = Math.max(getResources().getDisplayMetrics().widthPixels, dp(800));
        beamAnimator = ValueAnimator.ofFloat(-travel * .25f, travel * 1.25f);
        beamAnimator.setDuration(4300L);
        beamAnimator.setRepeatCount(ValueAnimator.INFINITE);
        beamAnimator.setInterpolator(new LinearInterpolator());
        beamAnimator.addUpdateListener(value -> {
            beamPhase = (float) value.getAnimatedValue();
            invalidate();
        });
        beamAnimator.start();
    }

    private void stopBeam() {
        if (beamAnimator == null) return;
        beamAnimator.cancel();
        beamAnimator = null;
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
