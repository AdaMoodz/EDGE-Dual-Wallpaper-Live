package com.edge.cityray.themephase1;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Local Android 11 wireless-ADB transport used only for the wallpaper activation command. */
final class BuiltInAdbBridge {
    interface Callback {
        void onResult(boolean success, boolean needsPairing, String message);
    }

    private final Context context;
    private final String adbPath;

    private static final class AdbEndpoint {
        final String host;
        final int port;

        AdbEndpoint(String host, int port) {
            this.host = host;
            this.port = port;
        }

        String serial() {
            return host + ":" + port;
        }
    }

    BuiltInAdbBridge(Context context) {
        this.context = context.getApplicationContext();
        this.adbPath = context.getApplicationInfo().nativeLibraryDir + "/libadb.so";
    }

    void apply(Callback callback) {
        new Thread(() -> applyBlocking(callback), "edge-local-adb-apply").start();
    }

    void pairAndApply(String pairingCode, Callback callback) {
        new Thread(() -> {
            try {
                AdbEndpoint pairingEndpoint = discoverAdbEndpoint("_adb-tls-pairing._tcp");
                if (pairingEndpoint == null) {
                    callback.onResult(false, false,
                            "PAIRING WINDOW NOT FOUND • OPEN PAIR DEVICE WITH CODE");
                    return;
                }
                Process pair = process(Arrays.asList("pair",
                        "127.0.0.1:" + pairingEndpoint.port));
                Thread.sleep(1200);
                try (PrintStream input = new PrintStream(pair.getOutputStream())) {
                    input.println(pairingCode);
                    input.flush();
                }
                boolean finished = pair.waitFor(15, TimeUnit.SECONDS);
                String output = readProcess(pair);
                if (!finished || pair.exitValue() != 0
                        || !output.toLowerCase().contains("successfully paired")) {
                    if (!finished) pair.destroyForcibly();
                    callback.onResult(false, false, "PAIRING FAILED • REQUEST A NEW CODE");
                    return;
                }
                execute(Arrays.asList("kill-server"), 5);
                applyBlocking(callback);
            } catch (Throwable error) {
                callback.onResult(false, false, "PAIRING FAILED • TRY A NEW CODE");
            }
        }, "edge-local-adb-pair").start();
    }

    private void applyBlocking(Callback callback) {
        try {
            AdbEndpoint endpoint = discoverConnectEndpoint();
            if (endpoint == null) {
                callback.onResult(false, true, "WIRELESS DEBUGGING PORT NOT FOUND");
                return;
            }
            execute(Arrays.asList("start-server"), 12);
            // On this Android 11 HU adbd rejects a connection to its own Wi-Fi address,
            // while its numeric IPv4 loopback listener is authorized and reachable.
            // Avoid "localhost" because Android may resolve it to the unused IPv6 ::1 listener.
            AdbEndpoint activeEndpoint = new AdbEndpoint("127.0.0.1", endpoint.port);
            String connect = execute(Arrays.asList("connect", activeEndpoint.serial()), 12);
            if (connectFailed(connect) && !"127.0.0.1".equals(endpoint.host)) {
                activeEndpoint = endpoint;
                connect = execute(Arrays.asList("connect", activeEndpoint.serial()), 12);
            }
            Log.i("EdgeBuiltInAdb", "connect endpoint=" + activeEndpoint.serial()
                    + " output=" + connect.trim());
            if (connectFailed(connect)) {
                callback.onResult(false, true, "BUILT-IN ADB NEEDS PAIRING");
                return;
            }
            String localDevice = activeEndpoint.serial();
            String result = execute(Arrays.asList("-s", localDevice,
                    "shell", "service", "call", "wallpaper", "3", "i32", "1",
                    "s16", "com.edge.cityray.themephase1", "s16",
                    "com.edge.cityray.themephase1.EdgeLiveWallpaperService"), 12);
            Log.i("EdgeBuiltInAdb", "wallpaper output=" + result.trim());
            if (result.contains("Parcel(") && !result.toLowerCase().contains("exception")) {
                boolean launcherRefreshed = refreshGeelyLauncher(localDevice);
                callback.onResult(true, false, launcherRefreshed
                        ? "LIVE WALLPAPER APPLIED • SHORTCUTS RESTORED"
                        : "LIVE WALLPAPER APPLIED • PRESS HOME TO REFRESH");
            } else {
                boolean pairing = result.toLowerCase().contains("unauthorized")
                        || result.toLowerCase().contains("authentication");
                callback.onResult(false, pairing, pairing
                        ? "BUILT-IN ADB NEEDS PAIRING" : "WALLPAPER COMMAND FAILED");
            }
        } catch (Throwable error) {
            Log.e("EdgeBuiltInAdb", "apply failed at " + adbPath, error);
            callback.onResult(false, true, "BUILT-IN ADB NEEDS PAIRING");
        }
    }

    private static boolean connectFailed(String output) {
        String normalized = output.toLowerCase();
        return normalized.contains("failed") || normalized.contains("unable")
                || normalized.contains("cannot connect");
    }

    /**
     * oneOS Launcher can keep its wallpaper page while dropping the custom-shortcut layer
     * after IWallpaperManager changes the live component. Restarting the launcher process
     * rebuilds that layer from its existing database; no launcher data or settings are cleared.
     */
    private boolean refreshGeelyLauncher(String localDevice) {
        try {
            Thread.sleep(1200);
            execute(Arrays.asList("-s", localDevice, "shell", "am", "force-stop",
                    "com.android.launcher3"), 8);
            Thread.sleep(700);
            String home = execute(Arrays.asList("-s", localDevice, "shell", "am",
                    "start", "-a", "android.intent.action.MAIN", "-c",
                    "android.intent.category.HOME"), 10);
            Log.i("EdgeBuiltInAdb", "launcher refresh output=" + home.trim());
            boolean homeStarted = !home.toLowerCase().contains("error")
                    && !home.toLowerCase().contains("exception");
            if (homeStarted) {
                refreshButtonAppWidget(localDevice);
            }
            return homeStarted;
        } catch (Throwable error) {
            // Wallpaper activation already succeeded. Do not report it as failed merely because
            // the OEM launcher rejected a refresh command; the next HOME press will still reload it.
            Log.w("EdgeBuiltInAdb", "launcher refresh failed", error);
            return false;
        }
    }

    /**
     * Button/AdaModz owns the configured third-party app row. It already exposes an
     * idempotent package-scoped refresh action, used by its own Functions screen whenever
     * the Apps Widget preference changes. Sending the same action after Geely HOME settles
     * makes the existing row re-evaluate the launcher foreground state and attach above the
     * live-wallpaper surface. This neither changes Button preferences nor duplicates shortcuts.
     */
    private void refreshButtonAppWidget(String localDevice) {
        try {
            Thread.sleep(1400);
            String widget = execute(Arrays.asList("-s", localDevice, "shell", "am",
                    "broadcast", "--user", "0", "-a",
                    "com.aleksan.button.UPDATE_DESKTOP_WIDGETS", "-p",
                    "com.aleksan.button"), 8);
            Log.i("EdgeBuiltInAdb", "Button apps widget refresh output=" + widget.trim());
        } catch (Throwable error) {
            // Button is optional. Wallpaper and Geely HOME are already active if it is absent.
            Log.w("EdgeBuiltInAdb", "Button apps widget refresh unavailable", error);
        }
    }

    private AdbEndpoint discoverConnectEndpoint() throws InterruptedException {
        return discoverAdbEndpoint("_adb-tls-connect._tcp");
    }

    private AdbEndpoint discoverAdbEndpoint(String serviceType) throws InterruptedException {
        NsdManager nsd = (NsdManager) context.getSystemService(Context.NSD_SERVICE);
        CountDownLatch found = new CountDownLatch(1);
        AtomicReference<AdbEndpoint> endpoint = new AtomicReference<>();
        List<NsdServiceInfo> pending = new ArrayList<>();

        NsdManager.DiscoveryListener listener = new NsdManager.DiscoveryListener() {
            @Override public void onDiscoveryStarted(String type) { }
            @Override public void onServiceLost(NsdServiceInfo service) { }
            @Override public void onDiscoveryStopped(String type) { }
            @Override public void onStartDiscoveryFailed(String type, int code) { found.countDown(); }
            @Override public void onStopDiscoveryFailed(String type, int code) { }
            @Override public void onServiceFound(NsdServiceInfo service) {
                synchronized (pending) {
                    if (endpoint.get() != null) return;
                    pending.add(service);
                }
                nsd.resolveService(service, new NsdManager.ResolveListener() {
                    @Override public void onResolveFailed(NsdServiceInfo info, int code) { }
                    @Override public void onServiceResolved(NsdServiceInfo info) {
                        String host = info.getHost() == null
                                ? currentWifiHost() : info.getHost().getHostAddress();
                        if (host == null || host.isEmpty() || host.contains(":")) {
                            host = currentWifiHost();
                        }
                        if (info.getPort() > 0
                                && endpoint.compareAndSet(null,
                                new AdbEndpoint(host, info.getPort()))) {
                            found.countDown();
                        }
                    }
                });
            }
        };
        nsd.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener);
        found.await(8, TimeUnit.SECONDS);
        try { nsd.stopServiceDiscovery(listener); } catch (Throwable ignored) { }
        return endpoint.get();
    }

    /** Wireless debugging listens on the HU Wi-Fi interface, not its loopback interface. */
    private String currentWifiHost() {
        try {
            WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            int ip = wifi.getConnectionInfo().getIpAddress();
            if (ip != 0) {
                return (ip & 0xff) + "." + ((ip >> 8) & 0xff) + "."
                        + ((ip >> 16) & 0xff) + "." + ((ip >> 24) & 0xff);
            }
        } catch (Throwable error) {
            Log.w("EdgeBuiltInAdb", "Wi-Fi address unavailable", error);
        }
        return "127.0.0.1";
    }

    private String execute(List<String> args, int timeoutSeconds) throws Exception {
        Process process = process(args);
        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) process.destroyForcibly();
        return readProcess(process);
    }

    private Process process(List<String> args) throws Exception {
        ArrayList<String> command = new ArrayList<>();
        command.add(adbPath);
        command.addAll(args);
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(context.getFilesDir());
        builder.redirectErrorStream(true);
        builder.environment().put("HOME", context.getFilesDir().getAbsolutePath());
        builder.environment().put("TMPDIR", context.getCacheDir().getAbsolutePath());
        return builder.start();
    }

    private static String readProcess(Process process) throws Exception {
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) output.append(line).append('\n');
        }
        return output.toString();
    }
}
