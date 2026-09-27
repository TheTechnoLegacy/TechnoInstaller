package com.techno.installer;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

import rikka.shizuku.Shizuku;

/**
 * Seamless install/uninstall via Shizuku: no PackageInstaller.Session, no
 * system confirmation dialog, no per-app "unknown sources" toggle - Shizuku
 * just runs `pm install`/`pm uninstall` as a shell (or root, under Sui)
 * command on our behalf.
 *
 * `--bypass-low-target-sdk-block` is the flag `pm install` needs since
 * Android 14 (API 34), which otherwise refuses to install any APK whose
 * targetSdkVersion is below 23 ("app not installed as it may be
 * malicious"). Techno Installer's own targetSdk is 26, so this only matters
 * for APKs *this app installs*, not for itself - but every other retro-ROM
 * app in this family targets old SDKs too, so it's on by default here.
 */
final class ShizukuInstaller {

    private static final String TAG = "TechnoInstaller";
    private static final int REQUEST_CODE_PERMISSION = 300;

    interface Callback {
        void onResult(boolean success, String output);
    }

    private static IInstallerUserService sService;
    private static Callback sPendingCallback;
    private static String sPendingCommand;

    private ShizukuInstaller() {}

    /** True if a Shizuku (or Sui) instance is actually running right now. */
    static boolean isAvailable() {
        try {
            return Shizuku.pingBinder();
        } catch (Throwable t) {
            // Shizuku not installed at all - the provider/binder simply isn't there.
            return false;
        }
    }

    static boolean hasPermission() {
        try {
            return Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Call once, e.g. from Application/first Activity, before install() is ever used. */
    static void requestPermission() {
        if (isAvailable() && !hasPermission() && !Shizuku.shouldShowRequestPermissionRationale()) {
            Shizuku.requestPermission(REQUEST_CODE_PERMISSION);
        }
    }

    /**
     * Installs apkPath seamlessly. Falls back is the caller's job: check
     * {@link #isAvailable()} / {@link #hasPermission()} first and use the
     * ordinary PackageInstaller.Session flow (see InstallAppProgress) if
     * either is false.
     */
    static void install(Context context, String apkPath, Callback callback) {
        String quoted = "'" + apkPath.replace("'", "'\\''") + "'";
        runAsRoot(context, "pm install --bypass-low-target-sdk-block -r " + quoted, callback);
    }

    static void uninstall(Context context, String packageName, Callback callback) {
        runAsRoot(context, "pm uninstall " + packageName, callback);
    }

    private static void runAsRoot(Context context, String cmd, Callback callback) {
        if (!isAvailable() || !hasPermission()) {
            callback.onResult(false, "Shizuku not available or permission not granted");
            return;
        }

        if (sService != null) {
            runCommand(cmd, callback);
            return;
        }

        sPendingCommand = cmd;
        sPendingCallback = callback;

        Shizuku.UserServiceArgs args = new Shizuku.UserServiceArgs(
                new ComponentName(context.getPackageName(), InstallerUserService.class.getName()))
                .daemon(false)
                .processNameSuffix("installer_service")
                .debuggable(false)
                .version(1);

        Shizuku.bindUserService(args, CONNECTION);
    }

    private static void runCommand(String cmd, Callback callback) {
        try {
            String output = sService.execCommand(cmd);
            boolean success = !output.toLowerCase().contains("failure")
                    && !output.toLowerCase().contains("exception");
            callback.onResult(success, output);
        } catch (Exception e) {
            Log.e(TAG, "Shizuku execCommand failed", e);
            callback.onResult(false, String.valueOf(e));
        }
    }

    private static final ServiceConnection CONNECTION = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            sService = IInstallerUserService.Stub.asInterface(binder);
            if (sPendingCallback != null) {
                Callback cb = sPendingCallback;
                String cmd = sPendingCommand;
                sPendingCallback = null;
                sPendingCommand = null;
                runCommand(cmd, cb);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            sService = null;
        }
    };
}
