package com.techno.installer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Replaces the system-privileged entry point of the original PackageInstaller app.
 *
 * The original activity ran as a signature|system component and relied on
 * INSTALL_PACKAGES to silently install the app after showing this confirmation.
 * A normal user app can never hold INSTALL_PACKAGES, so this version:
 *   1. Stages the incoming APK into our own cache dir (so we always have a
 *      plain java.io.File to read, regardless of the source scheme).
 *   2. Shows the same "do you want to install this?" confirmation the
 *      original app showed.
 *   3. Hands off to InstallAppProgress, which performs the real install via
 *      PackageInstaller.Session (REQUEST_INSTALL_PACKAGES, API 26+). The
 *      final "are you sure" prompt the user sees is actually shown by the
 *      system itself (STATUS_PENDING_USER_ACTION) - that's expected and is
 *      the normal, non-privileged install flow.
 */
public class PackageInstallerActivity extends Activity implements View.OnClickListener {

    private static final String TAG = "TechnoInstaller";
    static final String EXTRA_STAGED_APK_PATH = "com.techno.installer.extra.STAGED_APK_PATH";
    static final String EXTRA_APP_LABEL = "com.techno.installer.extra.APP_LABEL";

    private File mStagedApk;
    private PackageInfo mPkgInfo;
    private CharSequence mLabel;
    private Drawable mIcon;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Uri sourceUri = getIntent().getData();
        if (sourceUri == null) {
            Log.e(TAG, "No APK Uri in the incoming intent");
            finish();
            return;
        }

        mStagedApk = stageIncomingApk(sourceUri);
        if (mStagedApk == null) {
            showErrorAndFinish(R.string.Parse_error_dlg_title, R.string.Parse_error_dlg_text);
            return;
        }

        PackageManager pm = getPackageManager();
        mPkgInfo = pm.getPackageArchiveInfo(mStagedApk.getPath(), 0);
        if (mPkgInfo == null) {
            showErrorAndFinish(R.string.Parse_error_dlg_title, R.string.Parse_error_dlg_text);
            return;
        }

        // getPackageArchiveInfo() doesn't resolve resources against the archive by
        // default; point applicationInfo at the staged file so label/icon load correctly.
        ApplicationInfo appInfo = mPkgInfo.applicationInfo;
        appInfo.sourceDir = mStagedApk.getPath();
        appInfo.publicSourceDir = mStagedApk.getPath();
        mLabel = appInfo.loadLabel(pm);
        mIcon = appInfo.loadIcon(pm);

        if (!pm.canRequestPackageInstalls()) {
            showUnknownSourcesBlockedDialog();
            return;
        }

        showConfirmUi();
    }

    /** Copies whatever URI scheme we were handed into our own cache dir. */
    private File stageIncomingApk(Uri sourceUri) {
        File outDir = new File(getCacheDir(), "staged_apks");
        if (!outDir.exists() && !outDir.mkdirs()) {
            Log.e(TAG, "Could not create staging dir");
            return null;
        }
        File outFile = new File(outDir, "staged.apk");
        try (InputStream in = getContentResolver().openInputStream(sourceUri);
             OutputStream out = new FileOutputStream(outFile)) {
            if (in == null) return null;
            byte[] buf = new byte[64 * 1024];
            int read;
            while ((read = in.read(buf)) != -1) {
                out.write(buf, 0, read);
            }
            return outFile;
        } catch (IOException e) {
            Log.e(TAG, "Failed to stage incoming APK", e);
            return null;
        }
    }

    private void showConfirmUi() {
        setContentView(R.layout.install_start);

        ((ImageView) findViewById(R.id.app_icon)).setImageDrawable(mIcon);
        ((TextView) findViewById(R.id.app_name)).setText(mLabel);

        boolean isUpdate = isAlreadyInstalled(mPkgInfo.packageName);
        TextView question = findViewById(R.id.install_confirm_question);
        question.setText(isUpdate
                ? R.string.install_confirm_question_update_no_perms
                : R.string.install_confirm_question_no_perms);
        // Note: the runtime-permission list the original app showed here
        // (READ_CONTACTS, INTERNET, etc.) came from a privileged API
        // (PackageParser + AppSecurityPermissions) that a regular app can't
        // call for an *uninstalled* APK. Modern Android also re-confirms
        // dangerous permissions itself the first time the new app uses them,
        // so we intentionally don't try to reproduce that list here.
        findViewById(R.id.permission_list).setVisibility(View.GONE);

        Button ok = findViewById(R.id.ok_button);
        ok.setText(R.string.install);
        ok.setOnClickListener(this);
        findViewById(R.id.cancel_button).setOnClickListener(this);
    }

    private boolean isAlreadyInstalled(String packageName) {
        try {
            getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private void showUnknownSourcesBlockedDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.unknown_apps_dlg_title)
                .setMessage(R.string.unknown_apps_dlg_text)
                .setCancelable(false)
                .setNegativeButton(R.string.cancel, (d, w) -> finish())
                .setPositiveButton(R.string.settings, (d, w) -> {
                    try {
                        startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:" + getPackageName())));
                    } catch (ActivityNotFoundException e) {
                        Log.e(TAG, "No settings screen for unknown app sources", e);
                    }
                    finish();
                })
                .show();
    }

    private void showErrorAndFinish(int title, int message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton(R.string.ok, (d, w) -> finish())
                .show();
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.ok_button) {
            Intent intent = new Intent(this, InstallAppProgress.class);
            intent.putExtra(EXTRA_STAGED_APK_PATH, mStagedApk.getPath());
            intent.putExtra(EXTRA_APP_LABEL, mLabel);
            startActivity(intent);
            finish();
        } else if (v.getId() == R.id.cancel_button) {
            if (mStagedApk != null) {
                mStagedApk.delete();
            }
            finish();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // User may be returning from the "allow unknown sources" settings screen.
        if (mPkgInfo != null && getPackageManager().canRequestPackageInstalls()
                && findViewById(R.id.ok_button) == null) {
            showConfirmUi();
        }
    }
}
