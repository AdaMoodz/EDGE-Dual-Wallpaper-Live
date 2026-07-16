package com.edge.cityray.themephase1;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.view.Surface;
import android.view.TextureView;

import java.io.File;

/** Texture-backed muted looping preview with deterministic center-crop geometry. */
final class CropVideoView extends TextureView implements TextureView.SurfaceTextureListener {
    private String videoPath;
    private MediaPlayer player;
    private MediaPlayer.OnPreparedListener preparedListener;
    private MediaPlayer.OnErrorListener errorListener;
    private boolean playRequested = true;
    private int videoWidth;
    private int videoHeight;

    CropVideoView(Context context) {
        super(context);
        setSurfaceTextureListener(this);
        setOpaque(true);
    }

    void setVideoPath(String path) {
        videoPath = path;
        openIfReady();
    }

    void setOnPreparedListener(MediaPlayer.OnPreparedListener listener) {
        preparedListener = listener;
    }

    void setOnErrorListener(MediaPlayer.OnErrorListener listener) {
        errorListener = listener;
    }

    void start() {
        playRequested = true;
        if (player != null) {
            try {
                player.start();
            } catch (IllegalStateException ignored) {
            }
        }
    }

    void pause() {
        playRequested = false;
        if (player != null && player.isPlaying()) player.pause();
    }

    boolean isPlaying() {
        return player != null && player.isPlaying();
    }

    void release() {
        if (player != null) {
            player.reset();
            player.release();
            player = null;
        }
    }

    private void openIfReady() {
        if (videoPath == null || !new File(videoPath).isFile() || !isAvailable()) return;
        release();
        try {
            Surface surface = new Surface(getSurfaceTexture());
            player = new MediaPlayer();
            player.setSurface(surface);
            surface.release();
            player.setDataSource(videoPath);
            player.setLooping(true);
            player.setVolume(0f, 0f);
            player.setOnPreparedListener(mediaPlayer -> {
                videoWidth = mediaPlayer.getVideoWidth();
                videoHeight = mediaPlayer.getVideoHeight();
                applyCenterCrop();
                if (preparedListener != null) preparedListener.onPrepared(mediaPlayer);
                if (playRequested) mediaPlayer.start();
            });
            player.setOnErrorListener((mediaPlayer, what, extra) ->
                    errorListener != null && errorListener.onError(mediaPlayer, what, extra));
            player.prepareAsync();
        } catch (Throwable error) {
            release();
        }
    }

    private void applyCenterCrop() {
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0 || videoWidth <= 0 || videoHeight <= 0) return;
        float scale = Math.max(width / (float) videoWidth, height / (float) videoHeight);
        float scaledWidth = videoWidth * scale;
        float scaledHeight = videoHeight * scale;
        Matrix matrix = new Matrix();
        matrix.setScale(scaledWidth / width, scaledHeight / height, width / 2f, height / 2f);
        setTransform(matrix);
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        applyCenterCrop();
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        openIfReady();
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        applyCenterCrop();
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        release();
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
    }
}
