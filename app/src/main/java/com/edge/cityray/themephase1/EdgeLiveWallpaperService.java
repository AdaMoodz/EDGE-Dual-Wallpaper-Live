package com.edge.cityray.themephase1;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.service.wallpaper.WallpaperService;
import android.util.Log;
import android.view.SurfaceHolder;

import java.io.File;
import java.io.InputStream;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

public final class EdgeLiveWallpaperService extends WallpaperService
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = "EDGE_CITYRAY_LIVE";
    static final String PREFS = "edge_wallpaper";
    static final String KEY_VIDEO_URI = "live_video_uri";
    static final String KEY_MAIN_VIDEO_URI = "main_live_video_uri";
    static final String KEY_MAIN_VIDEO_PATH = "main_live_video_path";
    static final String KEY_MAIN_VIDEO_NAME = "main_live_video_name";
    static final String KEY_VIDEO_REVISION = "main_live_video_revision";

    private final Set<VideoEngine> engines = Collections.newSetFromMap(new WeakHashMap<>());
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean buttonWidgetHeartbeatRunning;
    private final Runnable buttonWidgetHeartbeat = new Runnable() {
        @Override public void run() {
            if (!hasVisibleEngine()) {
                buttonWidgetHeartbeatRunning = false;
                return;
            }
            Intent showApps = new Intent("com.aleksan.button.SHOW_APPS_WIDGET");
            showApps.setPackage("com.aleksan.button");
            sendBroadcast(showApps);
            mainHandler.postDelayed(this, 3000L);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onDestroy() {
        mainHandler.removeCallbacks(buttonWidgetHeartbeat);
        buttonWidgetHeartbeatRunning = false;
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .unregisterOnSharedPreferenceChangeListener(this);
        synchronized (engines) {
            engines.clear();
        }
        super.onDestroy();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences preferences, String key) {
        if (!KEY_VIDEO_REVISION.equals(key)) return;
        VideoEngine[] snapshot;
        synchronized (engines) {
            snapshot = engines.toArray(new VideoEngine[0]);
        }
        for (VideoEngine engine : snapshot) engine.reloadSelectedVideo();
    }

    @Override
    public Engine onCreateEngine() {
        VideoEngine engine = new VideoEngine();
        synchronized (engines) {
            engines.add(engine);
        }
        return engine;
    }

    private boolean hasVisibleEngine() {
        synchronized (engines) {
            for (VideoEngine engine : engines) {
                if (engine.visible) return true;
            }
        }
        return false;
    }

    private void updateButtonWidgetHeartbeat() {
        mainHandler.post(() -> {
            boolean shouldRun = hasVisibleEngine();
            if (shouldRun && !buttonWidgetHeartbeatRunning) {
                buttonWidgetHeartbeatRunning = true;
                buttonWidgetHeartbeat.run();
            } else if (!shouldRun && buttonWidgetHeartbeatRunning) {
                mainHandler.removeCallbacks(buttonWidgetHeartbeat);
                buttonWidgetHeartbeatRunning = false;
            }
        });
    }

    private final class VideoEngine extends Engine {
        private MediaPlayer player;
        private SurfaceHolder surfaceHolder;
        private boolean visible;
        private boolean prepared;
        private int displayId = -1;

        @Override
        public void onSurfaceCreated(SurfaceHolder holder) {
            super.onSurfaceCreated(holder);
            surfaceHolder = holder;
            displayId = getDisplayContext().getDisplay().getDisplayId();
            Log.i(TAG, "Surface created display=" + displayId + " frame="
                    + holder.getSurfaceFrame());
            if (displayId == 2) drawDashboardTestImage();
            else openPlayer();
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            Log.i(TAG, "Surface changed display=" + displayId + " size=" + width + "x" + height);
            if (displayId == 2) drawDashboardTestImage();
        }

        @Override
        public void onVisibilityChanged(boolean isVisible) {
            visible = isVisible;
            updateButtonWidgetHeartbeat();
            if (displayId == 2) {
                if (isVisible) drawDashboardTestImage();
                return;
            }
            if (player == null && isVisible && surfaceHolder != null) openPlayer();
            if (player == null || !prepared) return;
            try {
                if (isVisible) player.start();
                else if (player.isPlaying()) player.pause();
            } catch (IllegalStateException ignored) {
            }
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            visible = false;
            updateButtonWidgetHeartbeat();
            surfaceHolder = null;
            releasePlayer();
            super.onSurfaceDestroyed(holder);
        }

        @Override
        public void onDestroy() {
            visible = false;
            synchronized (engines) {
                engines.remove(this);
            }
            updateButtonWidgetHeartbeat();
            releasePlayer();
            super.onDestroy();
        }

        private void reloadSelectedVideo() {
            if (surfaceHolder == null) return;
            if (displayId == 2) {
                drawDashboardTestImage();
                return;
            }
            Log.i(TAG, "Reloading selected video on active wallpaper surface");
            openPlayer();
        }

        private void drawDashboardTestImage() {
            if (surfaceHolder == null || !surfaceHolder.getSurface().isValid()) return;
            Bitmap bitmap = null;
            Canvas canvas = null;
            try (InputStream input = getAssets().open(
                    "dashboard_custom/lynk_four_7_meter.png")) {
                bitmap = BitmapFactory.decodeStream(input);
                if (bitmap == null) throw new IllegalStateException("Dashboard image decode failed");
                canvas = surfaceHolder.lockCanvas();
                if (canvas == null) return;
                int width = canvas.getWidth();
                int height = canvas.getHeight();
                canvas.drawColor(android.graphics.Color.BLACK);
                float scale = Math.max(width / (float) bitmap.getWidth(),
                        height / (float) bitmap.getHeight());
                int drawnWidth = Math.round(bitmap.getWidth() * scale);
                int drawnHeight = Math.round(bitmap.getHeight() * scale);
                int left = (width - drawnWidth) / 2;
                int top = (height - drawnHeight) / 2;
                Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
                canvas.drawBitmap(bitmap, null,
                        new Rect(left, top, left + drawnWidth, top + drawnHeight), paint);
                Log.i(TAG, "Dashboard image drawn display=" + displayId + " canvas="
                        + width + "x" + height + " source=" + bitmap.getWidth() + "x"
                        + bitmap.getHeight());
            } catch (Throwable error) {
                Log.e(TAG, "Dashboard image draw failed display=" + displayId, error);
            } finally {
                if (canvas != null) {
                    try { surfaceHolder.unlockCanvasAndPost(canvas); } catch (Throwable ignored) { }
                }
                if (bitmap != null) bitmap.recycle();
            }
        }

        private void openPlayer() {
            releasePlayer();
            SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
            int displayId = getDisplayContext().getDisplay().getDisplayId();
            String path = prefs.getString(KEY_MAIN_VIDEO_PATH, null);
            String value = prefs.getString(KEY_MAIN_VIDEO_URI,
                    prefs.getString(KEY_VIDEO_URI, null));
            Log.i(TAG, "Opening main HU display=" + displayId + " ownedPath=" + path
                    + " fallbackUri=" + value);
            if ((path == null && value == null) || surfaceHolder == null) return;
            ParcelFileDescriptor descriptor = null;
            try {
                MediaPlayer next = new MediaPlayer();
                if (path != null && new File(path).isFile()) {
                    next.setDataSource(path);
                } else {
                    descriptor = getContentResolver().openFileDescriptor(Uri.parse(value), "r");
                    if (descriptor == null) return;
                    next.setDataSource(descriptor.getFileDescriptor());
                }
                next.setSurface(surfaceHolder.getSurface());
                next.setLooping(true);
                next.setVolume(0f, 0f);
                next.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING);
                next.setOnPreparedListener(mediaPlayer -> {
                    prepared = true;
                    Log.i(TAG, "Prepared display=" + displayId + " video="
                            + mediaPlayer.getVideoWidth() + "x" + mediaPlayer.getVideoHeight());
                    if (visible) mediaPlayer.start();
                });
                next.setOnErrorListener((mediaPlayer, what, extra) -> {
                    Log.e(TAG, "Playback error display=" + displayId + " what=" + what
                            + " extra=" + extra);
                    return true;
                });
                player = next;
                next.prepareAsync();
            } catch (Throwable error) {
                Log.e(TAG, "Open failed display=" + displayId, error);
                releasePlayer();
            } finally {
                if (descriptor != null) {
                    try { descriptor.close(); } catch (Throwable ignored) { }
                }
            }
        }

        private void releasePlayer() {
            MediaPlayer old = player;
            player = null;
            prepared = false;
            if (old == null) return;
            try {
                old.stop();
            } catch (Throwable ignored) {
            }
            old.release();
        }
    }
}
