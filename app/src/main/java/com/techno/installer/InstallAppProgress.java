package com.techno.installer;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Shows an "Installing..." spinner, performs the install via the public
 * PackageInstaller.Session API (no INSTALL_PACKAGES needed), then re-shows
 * itself with the final result once InstallResultReceiver hears back.
 *
 * android:launchMode="singleTask" (see manifest) means the second launch,
 * from the receiver, lands on the same instance via onNewIntent() instead of
 * stacking a duplicate screen.
 */
public class InstallAppProgress extends Activity implements View.OnClickListener {

    private static final String TAG = "TechnoInstaller";
    static final String EXTRA_RESULT_STATUS = "com.techno.installer.extra.RESULT_STATUS";
    static final String EXTRA_RESULT_MESSAGE = "com.techno.installer.extra.RESULT_MESSAGE";

    // PendingIntent.FLAG_MUTABLE's literal value (added API 31). Required at
    // runtime on S+ so the system can fill in EXTRA_STATUS/EXTRA_INTENT when
    // it calls this PendingIntent back; written as a literal so this still
    // compiles against an older SDK stub.
    private static final int FLAG_MUTABLE_COMPAT = 0x02000000;

    private String mAppLabel;
    private String mStagedApkPath;

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
            showResult(intent.getIntExtra(EXTRA_RESULT_STATUS, PackageInstaller.STATUS_FAILURE),
                    intent.getStringExtra(EXTRA_RESULT_MESSAGE));
            return;
        }

        mAppLabel = intent.getStringExtra(PackageInstallerActivity.EXTRA_APP_LABEL);
        mStagedApkPath = intent.getStringExtra(PackageInstallerActivity.EXTRA_STAGED_APK_PATH);
        if (mStagedApkPath == null) {
            finish();
            return;
        }

        showInstalling();

        if (ShizukuInstaller.isAvailable() && ShizukuInstaller.hasPermission()) {
            // Seamless path: Shizuku runs `pm install --bypass-low-target-sdk-block`
            // directly with its own (adb-shell or, under Sui, root) identity, so
            // there's no PackageInstaller-session dance and no system "install
            // this app?" confirmation to wait on.
            ShizukuInstaller.install(this, mStagedApkPath, (success, output) ->
                    runOnUiThread(() -> showResult(
                            success ? PackageInstaller.STATUS_SUCCESS : PackageInstaller.STATUS_FAILURE,
                            output)));
        } else {
            new Thread(this::runInstallSession, "techno-installer-session").start();
        }
    }

    private void showInstalling() {
        ((TextView) findViewById(R.id.app_name)).setText(mAppLabel);
        findViewById(R.id.app_icon).setVisibility(View.GONE);
        ((TextView) findViewById(R.id.center_text)).setText(R.string.installing);
        findViewById(R.id.buttons_panel).setVisibility(View.GONE);
    }

    private void runInstallSession() {
        try {
            PackageInstaller installer = getPackageManager().getPackageInstaller();
            PackageInstaller.SessionParams params =
                    new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            int sessionId = installer.createSession(params);

            try (PackageInstaller.Session session = installer.openSession(sessionId);
                 InputStream in = new FileInputStream(mStagedApkPath)) {
                File apk = new File(mStagedApkPath);
                try (OutputStream out = session.openWrite("staged.apk", 0, apk.length())) {
                    byte[] buf = new byte[64 * 1024];
                    int read;
                    while ((read = in.read(buf)) != -1) {
                        out.write(buf, 0, read);
                    }
                    session.fsync(out);
                }

                Intent selfIntent = new Intent(this, InstallResultReceiver.class);
                selfIntent.setAction("com.techno.installer.INSTALL_COMPLETE");
                PendingIntent pending = PendingIntent.getBroadcast(
                        this, sessionId, selfIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | FLAG_MUTABLE_COMPAT);
                session.commit(pending.getIntentSender());
            }
        } catch (IOException | RuntimeException e) {
            Log.e(TAG, "Install session failed", e);
            runOnUiThread(() -> showResult(PackageInstaller.STATUS_FAILURE, e.getMessage()));
        }
    }

    private void showResult(int status, String message) {
        new File(getCacheDir(), "staged_apks/staged.apk").delete();

        findViewById(R.id.buttons_panel).setVisibility(View.VISIBLE);
        Button done = findViewById(R.id.done_button);
        Button launch = findViewById(R.id.launch_button);
        TextView explanation = findViewById(R.id.center_explanation);

        if (status == PackageInstaller.STATUS_SUCCESS) {
            ((TextView) findViewById(R.id.center_text)).setText(R.string.install_done);
            String pkg = getIntent().getStringExtra("android.content.pm.extra.PACKAGE_NAME");
            Intent launchIntent = pkg != null
                    ? getPackageManager().getLaunchIntentForPackage(pkg) : null;
            if (launchIntent != null) {
                launch.setVisibility(View.VISIBLE);
                launch.setOnClickListener(v -> { startActivity(launchIntent); finish(); });
            }
        } else {
            ((TextView) findViewById(R.id.center_text))
                    .setText(getString(R.string.install_failed_msg,
                            mAppLabel != null ? mAppLabel : getString(R.string.unknown)));
            if (message != null) {
                explanation.setText(message);
                explanation.setVisibility(View.VISIBLE);
            }
        }
        done.setOnClickListener(v -> finish());
    }

    @Override
    public void onClick(View v) {
        finish();
    }
}
