package com.edge.cityray.themephase1;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.provider.Settings;
import android.view.animation.LinearInterpolator;
import android.widget.LinearLayout;

/** Native NeonBlade-inspired modal surface with corner cuts and a traveling comet beam. */
final class NeonModalLayout extends LinearLayout {
    private final Paint surface = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rail = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint beam = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path shape = new Path();
    private final Matrix gradientMatrix = new Matrix();
    private LinearGradient gradient;
    private ValueAnimator animator;
    private float phase;

    NeonModalLayout(Context context) {
        super(context);
        setWillNotDraw(false);
        // The CityRay HU compositor can lose clipped dialog layers when a VideoView surface is
        // active behind them. Software rendering keeps the modal bounds and content deterministic.
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        surface.setColor(Color.rgb(5, 15, 25));
        surface.setStyle(Paint.Style.FILL);
        rail.setColor(Color.argb(185, 31, 122, 164));
        rail.setStyle(Paint.Style.STROKE);
        rail.setStrokeWidth(dp(1));
        beam.setStyle(Paint.Style.STROKE);
        beam.setStrokeWidth(dp(3));
        beam.setStrokeCap(Paint.Cap.ROUND);
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        float inset = dp(3);
        float cut = Math.min(dp(28), Math.min(width, height) * .12f);
        shape.reset();
        shape.moveTo(inset + cut, inset);
        shape.lineTo(width - inset, inset);
        shape.lineTo(width - inset, height - inset - cut);
        shape.lineTo(width - inset - cut, height - inset);
        shape.lineTo(inset, height - inset);
        shape.lineTo(inset, inset + cut);
        shape.close();

        float span = Math.max(dp(420), width * .86f);
        gradient = new LinearGradient(-span, 0f, 0f, 0f,
                new int[]{Color.TRANSPARENT, Color.TRANSPARENT,
                        Color.rgb(38, 216, 255), Color.rgb(54, 122, 255),
                        Color.rgb(164, 83, 255), Color.rgb(255, 67, 201),
                        Color.TRANSPARENT, Color.TRANSPARENT},
                new float[]{0f, .18f, .34f, .47f, .60f, .72f, .85f, 1f},
                Shader.TileMode.CLAMP);
        beam.setShader(gradient);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawPath(shape, surface);
        canvas.drawPath(shape, rail);
        if (gradient != null) {
            gradientMatrix.setTranslate(phase, 0f);
            gradient.setLocalMatrix(gradientMatrix);
            canvas.drawPath(shape, beam);
        }
        super.onDraw(canvas);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        int checkpoint = canvas.save();
        canvas.clipPath(shape);
        super.dispatchDraw(canvas);
        canvas.restoreToCount(checkpoint);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!animationsEnabled() || animator != null) return;
        float travel = Math.max(getResources().getDisplayMetrics().widthPixels, dp(800));
        animator = ValueAnimator.ofFloat(-travel * .2f, travel * 1.25f);
        animator.setDuration(3000L);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(value -> {
            phase = (float) value.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) animator.cancel();
        animator = null;
        super.onDetachedFromWindow();
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
