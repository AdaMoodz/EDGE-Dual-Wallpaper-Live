package com.edge.cityray.themephase1;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

final class CornerCutDrawable extends Drawable {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final float cut;

    CornerCutDrawable(int fillColor, int strokeColor, float strokeWidth, float cut) {
        this.cut = cut;
        fill.setColor(fillColor);
        fill.setStyle(Paint.Style.FILL);
        stroke.setColor(strokeColor);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(strokeWidth);
    }

    @Override
    protected void onBoundsChange(android.graphics.Rect bounds) {
        float l = bounds.left + stroke.getStrokeWidth() * 0.5f;
        float t = bounds.top + stroke.getStrokeWidth() * 0.5f;
        float r = bounds.right - stroke.getStrokeWidth() * 0.5f;
        float b = bounds.bottom - stroke.getStrokeWidth() * 0.5f;
        float c = Math.min(cut, Math.min((r - l) * 0.18f, (b - t) * 0.45f));
        path.reset();
        path.moveTo(l + c, t);
        path.lineTo(r, t);
        path.lineTo(r, b - c);
        path.lineTo(r - c, b);
        path.lineTo(l, b);
        path.lineTo(l, t + c);
        path.close();
    }

    @Override
    public void draw(Canvas canvas) {
        canvas.drawPath(path, fill);
        if (stroke.getStrokeWidth() > 0f) canvas.drawPath(path, stroke);
    }

    @Override public void setAlpha(int alpha) { fill.setAlpha(alpha); stroke.setAlpha(alpha); }
    @Override public void setColorFilter(ColorFilter filter) { fill.setColorFilter(filter); stroke.setColorFilter(filter); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
