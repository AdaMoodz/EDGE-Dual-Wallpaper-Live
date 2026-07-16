package com.edge.cityray.themephase1;

import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.os.IBinder;
import android.os.Parcel;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

final class DashboardThemeBridge {
    static final int PROPERTY_SET_WALLPAPER_TO_DIM = 540283136;

    interface Callback {
        void onResult(boolean success, String message);
    }

    private final Context appContext;

    DashboardThemeBridge(Context context) {
        appContext = context.getApplicationContext();
    }

    Bitmap loadFactoryPreview(int themeId) {
        return loadFactoryPreview(themeId, false);
    }

    Bitmap loadFactoryPreview(int themeId, boolean mainHu) {
        return loadFactoryPreview(themeId, mainHu, false);
    }

    Bitmap loadFactoryPreview(int themeId, boolean mainHu, boolean night) {
        if (themeId < 1 || themeId > 9) return null;
        try {
            Context assetContext = themeId <= 6 ? themeContext() : appContext;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 4;
            String suffix = night ? "_night" : "";
            String asset = (themeId <= 6 ? "" : "paired_themes/") + (mainHu
                    ? "CsdStatusWallpaper/lynk_four_" + themeId + "_csd" + suffix + ".png"
                    : "MeterStatusWallpaper/lynk_four_" + themeId + "_meter" + suffix + ".png");
            try (InputStream input = assetContext.getAssets().open(asset)) {
                return BitmapFactory.decodeStream(input, null, options);
            }
        } catch (Throwable ignored) {
            return null;
        }
    }

    void applyFactoryMainHu(int themeId, Callback callback) {
        new Thread(() -> {
            if (themeId < 1 || themeId > 6) {
                callback.onResult(false, "UNKNOWN FACTORY MAIN HU THEME");
                return;
            }
            try {
                File staging = new File(Environment.getExternalStorageDirectory(),
                        "XUI/static_wallpaper");
                if (!staging.isDirectory() && !staging.mkdirs()) {
                    throw new IllegalStateException("Cannot create Geely staging folder");
                }
                Context source = themeContext();
                copyAsset(source, "CsdStatusWallpaper/lynk_four_" + themeId + "_csd.png",
                        new File(staging, "app_saved_csd_static_wallpaper.png"));
                copyAsset(source, "CsdStatusWallpaper/lynk_four_" + themeId
                                + "_csd_night.png",
                        new File(staging, "dark_app_saved_csd_static_wallpaper.png"));
                invokeStaticThemeService(themeId, callback);
            } catch (Throwable error) {
                callback.onResult(false, "MAIN HU STAGING BLOCKED • ALLOW FILE ACCESS");
            }
        }, "edge-factory-main-hu").start();
    }

    private void invokeStaticThemeService(int themeId, Callback callback) {
        AtomicBoolean finished = new AtomicBoolean();
        Intent intent = new Intent();
        intent.setComponent(new ComponentName("com.geely.theme",
                "com.geely.theme.service.themeservice.ThemeService"));
        ServiceConnection connection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                new Thread(() -> {
                    boolean success = false;
                    String message = "GEELY THEME SERVICE REJECTED MAIN HU";
                    Parcel data = Parcel.obtain();
                    Parcel reply = Parcel.obtain();
                    try {
                        String descriptor = service.getInterfaceDescriptor();
                        if (!"com.geely.lib.oneosapi.theme.IThemeService".equals(descriptor)) {
                            throw new IllegalStateException("Unexpected Geely service");
                        }
                        data.writeInterfaceToken(descriptor);
                        data.writeInt(0); // CSD / Main HU
                        success = service.transact(10, data, reply, 0);
                        if (success) {
                            reply.readException();
                            message = "FACTORY MAIN HU THEME " + themeId + " APPLIED";
                        }
                    } catch (Throwable ignored) {
                        success = false;
                    } finally {
                        reply.recycle();
                        data.recycle();
                        try {
                            appContext.unbindService(this);
                        } catch (Throwable ignored) {
                        }
                    }
                    if (finished.compareAndSet(false, true)) callback.onResult(success, message);
                }, "edge-geely-theme-service").start();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                if (finished.compareAndSet(false, true)) {
                    callback.onResult(false, "GEELY THEME SERVICE DISCONNECTED");
                }
            }
        };
        try {
            if (!appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
                    && finished.compareAndSet(false, true)) {
                callback.onResult(false, "GEELY THEME SERVICE UNAVAILABLE");
            }
        } catch (Throwable error) {
            if (finished.compareAndSet(false, true)) {
                callback.onResult(false, "GEELY THEME SERVICE UNAVAILABLE");
            }
        }
    }

    private static void copyAsset(Context source, String asset, File destination)
            throws Exception {
        File temporary = new File(destination.getParentFile(), destination.getName() + ".tmp");
        try (InputStream input = source.getAssets().open(asset);
             FileOutputStream output = new FileOutputStream(temporary, false)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            output.flush();
            output.getFD().sync();
        }
        if (destination.exists() && !destination.delete()) {
            throw new IllegalStateException("Cannot replace staged wallpaper");
        }
        if (!temporary.renameTo(destination)) {
            throw new IllegalStateException("Cannot publish staged wallpaper");
        }
    }

    Bitmap loadCustomMeterPreview() {
        return loadCustomMeterPreview(false);
    }

    Bitmap loadCustomMeterPreview(boolean night) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 4;
            try (InputStream input = appContext.getAssets().open(
                    night
                            ? "dashboard_custom/lynk_four_7_meter_night.png"
                            : "dashboard_custom/lynk_four_7_meter.png")) {
                return BitmapFactory.decodeStream(input, null, options);
            }
        } catch (Throwable ignored) {
            return null;
        }
    }

    void applyFactoryTheme(int themeId, Callback callback) {
        new Thread(() -> {
            if (themeId < 1 || themeId > 9) {
                callback.onResult(false, "UNKNOWN DASHBOARD THEME");
                return;
            }
            try {
                Context themeContext = themeContext();
                Class<?> glyCarClass = themeContext.getClassLoader()
                        .loadClass("com.geely.os.car.GlyCar");
                Method create = glyCarClass.getMethod("create", Context.class);
                Object car = create.invoke(null, appContext);
                if (car == null) throw new IllegalStateException("Vehicle API unavailable");

                Method supportMethod = publicMethod(car, "getSupportStatus", 1);
                int support = ((Number) supportMethod.invoke(
                        car, PROPERTY_SET_WALLPAPER_TO_DIM)).intValue();
                if (support != 0) {
                    callback.onResult(false, "DASHBOARD THEMES ARE NOT ACTIVE ON THIS HU");
                    return;
                }

                Method setMethod = publicMethod(car, "setIntProperty", 2);
                Object result = setMethod.invoke(
                        car, PROPERTY_SET_WALLPAPER_TO_DIM, themeId);
                boolean success = result instanceof Boolean && (Boolean) result;
                callback.onResult(success, success
                        ? (themeId <= 6
                        ? "DASHBOARD THEME " + themeId + " APPLIED"
                        : "CUSTOM DASHBOARD COMMAND " + themeId + " SENT • VERIFY CLUSTER")
                        : "GEELY DID NOT ACCEPT THE DASHBOARD THEME");
            } catch (Throwable error) {
                callback.onResult(false, "OPENING GEELY DASHBOARD THEMES");
            }
        }, "edge-dashboard-theme").start();
    }

    void applyCustomMainHu(int themeId, Callback callback) {
        new Thread(() -> {
            if (themeId < 7 || themeId > 9) {
                callback.onResult(false, "UNKNOWN CUSTOM MAIN HU THEME");
                return;
            }
            boolean night = (appContext.getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
            String asset = "paired_themes/CsdStatusWallpaper/lynk_four_" + themeId
                    + "_csd" + (night ? "_night" : "") + ".png";
            try (InputStream input = appContext.getAssets().open(asset)) {
                Bitmap bitmap = BitmapFactory.decodeStream(input);
                if (bitmap == null) throw new IllegalStateException("Invalid wallpaper image");
                WallpaperManager.getInstance(appContext).setBitmap(bitmap, null, true,
                        WallpaperManager.FLAG_SYSTEM);
                bitmap.recycle();
                callback.onResult(true, "CUSTOM MAIN HU THEME " + themeId + " APPLIED");
            } catch (Throwable error) {
                callback.onResult(false, "MAIN HU REJECTED CUSTOM WALLPAPER");
            }
        }, "edge-custom-main-hu").start();
    }

    private Context themeContext() throws Exception {
        return appContext.createPackageContext("com.geely.theme",
                Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
    }

    private static Method publicMethod(Object target, String name, int parameterCount)
            throws Exception {
        for (Method method : target.getClass().getMethods()) {
            if (method.getName().equals(name)
                    && method.getParameterTypes().length == parameterCount) {
                method.setAccessible(true);
                return method;
            }
        }
        throw new NoSuchMethodException(name);
    }
}
