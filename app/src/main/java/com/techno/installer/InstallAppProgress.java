package com.techno.installer;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;

/** Step 2: progress bar while the session is written, then the result status. */
public class InstallAppProgress extends Activity {

    public static final String EXTRA_APK_PATH = "com.techno.installer.extra.APK_PATH";
    public static final String EXTRA_LABEL = "com.techno.installer.extra.LABEL";
    public static final String EXTRA_PACKAGE = "com.techno.installer.extra.PACKAGE";
    private static final String ACTION_RESULT = "com.techno.installer.INSTALL_COMPLETE";

    private ProgressBar bar;
    private TextView status, statusCheck, error;
    private View buttons;
    private String pkg, apkPath;
    private boolean started;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            int st = i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
            String msg = i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
            if (st == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                Intent confirm = i.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirm != null) startActivity(confirm);
            } else if (st == PackageInstaller.STATUS_SUCCESS) {
                showResult(true, null);
            } else {
                showResult(false, msg);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.op_progress);
        apkPath = getIntent().getStringExtra(EXTRA_APK_PATH);
        pkg = getIntent().getStringExtra(EXTRA_PACKAGE);
        String label = getIntent().getStringExtra(EXTRA_LABEL);

        ((TextView) findViewById(R.id.app_name)).setText(label);
        bar = (ProgressBar) findViewById(R.id.progress_bar);
        status = (TextView) findViewById(R.id.center_text);
        statusCheck = (TextView) findViewById(R.id.status_check);
        error = (TextView) findViewById(R.id.error_text);
        buttons = findViewById(R.id.button_bar);

        try {
            ApplicationInfo ai = getPackageManager()
                    .getPackageArchiveInfo(apkPath, 0).applicationInfo;
            ai.sourceDir = apkPath; ai.publicSourceDir = apkPath;
            ((ImageView) findViewById(R.id.app_icon))
                    .setImageDrawable(ai.loadIcon(getPackageManager()));
        } catch (Exception ignored) { }

        registerReceiver(receiver, new IntentFilter(ACTION_RESULT));

        findViewById(R.id.done_button).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        findViewById(R.id.launch_button).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Intent l = getPackageManager().getLaunchIntentForPackage(pkg);
                if (l != null) startActivity(l);
                finish();
            }
        });

        if (savedInstanceState == null && !started) {
            started = true;
            new Thread(new Runnable() {
                @Override public void run() { install(); }
            }).start();
        }
    }

    private void install() {
        try {
            PackageInstaller pi = getPackageManager().getPackageInstaller();
            PackageInstaller.SessionParams p =
                    new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            p.setAppPackageName(pkg);
            int id = pi.createSession(p);
            PackageInstaller.Session s = pi.openSession(id);

            File apk = new File(apkPath);
            long total = apk.length(), done = 0;
            InputStream in = new FileInputStream(apk);
            OutputStream out = s.openWrite("techno-installer-session", 0, total);
            byte[] buf = new byte[64 * 1024];
            int n, last = -1;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                done += n;
                final int pct = (int) (done * 100 / Math.max(total, 1));
                if (pct != last) {
                    last = pct;
                    runOnUiThread(new Runnable() {
                        @Override public void run() { bar.setProgress(pct); }
                    });
                }
            }
            s.fsync(out);
            out.close(); in.close();

            Intent cb = new Intent(ACTION_RESULT).setPackage(getPackageName());
            PendingIntent pend = PendingIntent.getBroadcast(this, id, cb,
                    PendingIntent.FLAG_UPDATE_CURRENT);
            s.commit(pend.getIntentSender());
            s.close();
        } catch (final Exception e) {
            runOnUiThread(new Runnable() {
                @Override public void run() { showResult(false, e.getMessage()); }
            });
        }
    }

    private void showResult(boolean ok, String msg) {
        new File(apkPath).delete();
        bar.setVisibility(View.GONE);
        buttons.setVisibility(View.VISIBLE);
        if (ok) {
            statusCheck.setVisibility(View.VISIBLE);
            status.setText(R.string.install_done);
            findViewById(R.id.launch_button).setEnabled(
                    getPackageManager().getLaunchIntentForPackage(pkg) != null);
        } else {
            status.setText(R.string.install_failed);
            findViewById(R.id.launch_button).setVisibility(View.GONE);
            if (msg != null) { error.setText(msg); error.setVisibility(View.VISIBLE); }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(receiver); } catch (Exception ignored) { }
    }
}
