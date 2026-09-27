package com.techno.installer;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class UninstallAppProgress extends Activity {

    static final String EXTRA_PACKAGE_NAME = "com.techno.installer.extra.PACKAGE_NAME";
    static final String EXTRA_APP_LABEL = "com.techno.installer.extra.APP_LABEL";
    static final String EXTRA_RESULT_STATUS = "com.techno.installer.extra.RESULT_STATUS";
    static final String EXTRA_RESULT_MESSAGE = "com.techno.installer.extra.RESULT_MESSAGE";

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

        Intent selfIntent = new Intent(this, UninstallResultReceiver.class);
        selfIntent.setAction("com.techno.installer.UNINSTALL_COMPLETE");
        PendingIntent pending = PendingIntent.getBroadcast(
                this, packageName.hashCode(), selfIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);

        // Public, non-privileged replacement for the old DELETE_PACKAGES call.
        // Requires only the normal (auto-granted) REQUEST_DELETE_PACKAGES
        // permission; the system shows its own confirmation before removing.
        getPackageManager().getPackageInstaller()
                .uninstall(packageName, pending.getIntentSender());
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
