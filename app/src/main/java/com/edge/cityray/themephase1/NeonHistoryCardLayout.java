package com.edge.cityray.themephase1;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.widget.FrameLayout;

/** Lightweight clipped preview card with its own two-tone neon edge. */
final class NeonHistoryCardLayout extends FrameLayout {
    private final Paint surface = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path shape = new Path();
    private final int accent;
    private final boolean selected;

    NeonHistoryCardLayout(Context context, int accent, boolean selected) {
        super(context);
        this.accent = accent;
        this.selected = selected;
        setWillNotDraw(false);
        surface.setColor(Color.rgb(5, 15, 25));
        surface.setStyle(Paint.Style.FILL);
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(dp(selected ? 3f : 1.5f));
        border.setStrokeCap(Paint.Cap.SQUARE);
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        float inset = dp(3);
        float cut = Math.min(dp(18), Math.min(width, height) * .14f);
        shape.reset();
        shape.moveTo(inset + cut, inset);
        shape.lineTo(width - inset, inset);
        shape.lineTo(width - inset, height - inset - cut);
        shape.lineTo(width - inset - cut, height - inset);
        shape.lineTo(inset, height - inset);
        shape.lineTo(inset, inset + cut);
        shape.close();
        int partner = selected ? Color.WHITE : Color.rgb(72, 112, 156);
        border.setShader(new LinearGradient(0, 0, width, height,
                new int[]{accent, partner, accent}, null, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawPath(shape, surface);
        canvas.drawPath(shape, border);
        super.onDraw(canvas);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        int checkpoint = canvas.save();
        canvas.clipPath(shape);
        super.dispatchDraw(canvas);
        canvas.restoreToCount(checkpoint);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
