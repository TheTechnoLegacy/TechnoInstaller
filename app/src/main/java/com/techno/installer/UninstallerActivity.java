package com.techno.installer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * Replaces the system-privileged UninstallerActivity. The original held
 * DELETE_PACKAGES (signature|privileged) and removed the app directly. A
 * normal app can only request REQUEST_DELETE_PACKAGES (a *normal*,
 * install-time permission since API 26) and then ask the system to confirm
 * and perform the removal via PackageInstaller#uninstall(); see
 * UninstallAppProgress for that call and the confirmation dialog it triggers.
 */
public class UninstallerActivity extends Activity implements View.OnClickListener {

    private String mPackageName;
    private CharSequence mLabel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ShizukuInstaller.requestPermission();

        Uri data = getIntent().getData();
        mPackageName = data != null ? data.getSchemeSpecificPart() : null;
        if (mPackageName == null) {
            finish();
            return;
        }

        PackageManager pm = getPackageManager();
        ApplicationInfo appInfo;
        try {
            appInfo = pm.getApplicationInfo(mPackageName, 0);
        } catch (PackageManager.NameNotFoundException e) {
            showAppNotFoundAndFinish();
            return;
        }
        mLabel = appInfo.loadLabel(pm);

        setContentView(R.layout.uninstall_confirm);
        ((ImageView) findViewById(R.id.app_icon)).setImageDrawable(appInfo.loadIcon(pm));
        ((TextView) findViewById(R.id.app_name)).setText(mLabel);
        ((TextView) findViewById(R.id.uninstall_confirm)).setText(R.string.uninstall_application_text);

        Button ok = findViewById(R.id.ok_button);
        ok.setOnClickListener(this);
        findViewById(R.id.cancel_button).setOnClickListener(this);
    }

    private void showAppNotFoundAndFinish() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.app_not_found_dlg_title)
                .setMessage(R.string.app_not_found_dlg_text)
                .setCancelable(false)
                .setPositiveButton(R.string.ok, (d, w) -> finish())
                .show();
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.ok_button) {
            startActivity(new android.content.Intent(this, UninstallAppProgress.class)
                    .putExtra(UninstallAppProgress.EXTRA_PACKAGE_NAME, mPackageName)
                    .putExtra(UninstallAppProgress.EXTRA_APP_LABEL, mLabel));
        }
        finish();
    }
}
