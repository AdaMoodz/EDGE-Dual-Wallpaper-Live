package com.edge.cityray.themephase1;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;

import java.io.InputStream;

/** Temporary, self-closing renderer used to identify the native meter display. */
public final class DashboardProbeActivity extends Activity {
    private static final long PROBE_DURATION_MS = 8000L;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable closeProbe = this::finishAndRemoveTask;
    private Bitmap bitmap;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);

        ImageView image = new ImageView(this);
        image.setBackgroundColor(Color.BLACK);
        image.setScaleType(ImageView.ScaleType.FIT_XY);
        image.setOnClickListener(v -> finishAndRemoveTask());
        try (InputStream input = getAssets().open(
                "paired_themes/MeterStatusWallpaper/lynk_four_7_meter.png")) {
            bitmap = BitmapFactory.decodeStream(input);
            image.setImageBitmap(bitmap);
        } catch (Throwable error) {
            Log.e("EDGE_DASH_PROBE", "Image load failed", error);
        }
        setContentView(image);
        int displayId = getDisplay() == null ? -1 : getDisplay().getDisplayId();
        Log.i("EDGE_DASH_PROBE", "Probe visible on display=" + displayId
                + " for " + PROBE_DURATION_MS + "ms");
        handler.postDelayed(closeProbe, PROBE_DURATION_MS);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(closeProbe);
        if (bitmap != null) bitmap.recycle();
        bitmap = null;
        super.onDestroy();
    }
}
