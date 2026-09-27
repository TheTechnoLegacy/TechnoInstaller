package com.techno.installer;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class UninstallAppProgress extends Activity {

    static final String EXTRA_PACKAGE_NAME = "com.techno.installer.extra.PACKAGE_NAME";
    static final String EXTRA_APP_LABEL = "com.techno.installer.extra.APP_LABEL";
    static final String EXTRA_RESULT_STATUS = "com.techno.installer.extra.RESULT_STATUS";
    static final String EXTRA_RESULT_MESSAGE = "com.techno.installer.extra.RESULT_MESSAGE";

    // See InstallAppProgress for why this is a literal instead of
    // PendingIntent.FLAG_MUTABLE (API 31+ symbol).
    private static final int FLAG_MUTABLE_COMPAT = 0x02000000;
    private static final int REQUEST_LEGACY_UNINSTALL = 1001;

    private String mAppLabel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.op_progress);
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent.hasExtra(EXTRA_RESULT_STATUS)) {
            showResult(intent.getIntExtra(EXTRA_RESULT_STATUS, PackageInstaller.STATUS_FAILURE));
            return;
        }

        String packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME);
        mAppLabel = intent.getStringExtra(EXTRA_APP_LABEL);
        if (packageName == null) {
            finish();
            return;
        }

        findViewById(R.id.app_icon).setVisibility(View.GONE);
        ((TextView) findViewById(R.id.app_name)).setText(mAppLabel);
        ((TextView) findViewById(R.id.center_text)).setText(R.string.uninstalling);
        findViewById(R.id.buttons_panel).setVisibility(View.GONE);

        if (ShizukuInstaller.isAvailable() && ShizukuInstaller.hasPermission()) {
            // Seamless path, same idea as the install side: `pm uninstall`
            // run directly under Shizuku's identity, no system confirmation.
            ShizukuInstaller.uninstall(this, packageName, (success, output) ->
                    runOnUiThread(() -> showResult(
                            success ? PackageInstaller.STATUS_SUCCESS : PackageInstaller.STATUS_FAILURE)));
        } else if (Build.VERSION.SDK_INT >= 26) {
            startModernUninstall(packageName);
        } else {
            startLegacyUninstall(packageName);
        }
    }

    /**
     * API 26+: PackageInstaller#uninstall() only became callable by a normal
     * (non-privileged) app once REQUEST_DELETE_PACKAGES existed. The system
     * still shows its own confirmation before actually removing anything.
     */
    private void startModernUninstall(String packageName) {
        Intent selfIntent = new Intent(this, UninstallResultReceiver.class);
        selfIntent.setAction("com.techno.installer.UNINSTALL_COMPLETE");
        PendingIntent pending = PendingIntent.getBroadcast(
                this, packageName.hashCode(), selfIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | FLAG_MUTABLE_COMPAT);

        getPackageManager().getPackageInstaller()
                .uninstall(packageName, pending.getIntentSender());
    }

    /**
     * Below API 26 there is no non-privileged uninstall call at all -
     * PackageInstaller#uninstall() throws SecurityException without the
     * privileged DELETE_PACKAGES permission, which a normal app can never
     * hold. The only thing a regular app has ever been able to do is ask
     * for the system's own uninstall confirmation via this intent (the same
     * trick launcher "long press to uninstall" shortcuts use) and read back
     * the result. That means the actual removal is performed by whichever
     * app the system resolves this to - normally the device's real,
     * privileged package installer - not by this app; if this app is the
     * *only* handler installed for it, the system will just loop the intent
     * back to our own UninstallerActivity instead of removing anything, so
     * this fallback only works alongside a real installer already on the
     * device (which is the normal case on an unmodified device).
     */
    private void startLegacyUninstall(String packageName) {
        Intent intent = new Intent(Intent.ACTION_UNINSTALL_PACKAGE,
                Uri.parse("package:" + packageName));
        intent.putExtra(Intent.EXTRA_RETURN_RESULT, true);
        startActivityForResult(intent, REQUEST_LEGACY_UNINSTALL);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_LEGACY_UNINSTALL) {
            showResult(resultCode == RESULT_OK
                    ? PackageInstaller.STATUS_SUCCESS : PackageInstaller.STATUS_FAILURE);
        }
    }

    private void showResult(int status) {
        findViewById(R.id.buttons_panel).setVisibility(View.VISIBLE);
        Button done = findViewById(R.id.done_button);
        done.setOnClickListener(v -> finish());

        ((TextView) findViewById(R.id.center_text)).setText(
                status == PackageInstaller.STATUS_SUCCESS
                        ? getString(R.string.uninstall_done)
                        : getString(R.string.uninstall_failed_msg,
                                mAppLabel != null ? mAppLabel : getString(R.string.unknown)));
    }
}
