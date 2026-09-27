package com.techno.installer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;

/**
 * Target of the PendingIntent passed to PackageInstaller.Session#commit().
 *
 * Two kinds of callbacks land here:
 *  - STATUS_PENDING_USER_ACTION: the system itself needs to show its "Install
 *    this app?" dialog (this is the real gatekeeping step for a non-privileged
 *    installer - our own confirm screen earlier was just a friendly preview).
 *    We forward EXTRA_INTENT straight to startActivity().
 *  - Any final status (STATUS_SUCCESS / STATUS_FAILURE*): forwarded to
 *    InstallAppProgress so it can update its already-visible "Installing..."
 *    screen via onNewIntent().
 */
public class InstallResultReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS,
                PackageInstaller.STATUS_FAILURE);
        String message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);

        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirmIntent = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirmIntent != null) {
                confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(confirmIntent);
            }
            return;
        }

        Intent result = new Intent(context, InstallAppProgress.class);
        result.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        result.putExtra(InstallAppProgress.EXTRA_RESULT_STATUS, status);
        result.putExtra(InstallAppProgress.EXTRA_RESULT_MESSAGE, message);
        String pkgName = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME);
        if (pkgName != null) {
            result.putExtra("android.content.pm.extra.PACKAGE_NAME", pkgName);
        }
        context.startActivity(result);
    }
}
