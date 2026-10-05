package com.techno.installer;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionGroupInfo;
import android.content.pm.PermissionInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Step 1: shows the app and the permissions it requests, like the 4.3 installer. */
public class PackageInstallerActivity extends Activity {

    private File staged;
    private PackageInfo pkgInfo;
    private CharSequence label;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Uri uri = getIntent().getData();
        if (uri == null) { finish(); return; }

        if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            Toast.makeText(this, "Allow installs from this source, then try again", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + getPackageName())));
            finish();
            return;
        }

        setContentView(R.layout.install_confirm);
        findViewById(R.id.ok_button).setEnabled(false);
        findViewById(R.id.cancel_button).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { cleanup(); finish(); }
        });

        final Uri src = uri;
        new Thread(new Runnable() {
            @Override public void run() {
                final boolean ok = stage(src);
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (ok) bind(); else {
                            Toast.makeText(PackageInstallerActivity.this,
                                    "Problem parsing the package", Toast.LENGTH_LONG).show();
                            finish();
                        }
                    }
                });
            }
        }).start();
    }

    private boolean stage(Uri uri) {
        try {
            staged = new File(getCacheDir(), "staged.apk");
            InputStream in = getContentResolver().openInputStream(uri);
            if (in == null) return false;
            OutputStream out = new FileOutputStream(staged);
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            out.close(); in.close();
            pkgInfo = getPackageManager().getPackageArchiveInfo(
                    staged.getAbsolutePath(), PackageManager.GET_PERMISSIONS);
            return pkgInfo != null;
        } catch (Exception e) {
            return false;
        }
    }

    private void bind() {
        PackageManager pm = getPackageManager();
        ApplicationInfo ai = pkgInfo.applicationInfo;
        ai.sourceDir = staged.getAbsolutePath();
        ai.publicSourceDir = staged.getAbsolutePath();
        label = ai.loadLabel(pm);
        Drawable icon = ai.loadIcon(pm);
        ((ImageView) findViewById(R.id.app_icon)).setImageDrawable(icon);
        ((TextView) findViewById(R.id.app_name)).setText(label);

        // Group permission labels by permission group (PRIVACY, DEVICE ACCESS...)
        Map<String, Set<String>> groups = new LinkedHashMap<String, Set<String>>();
        if (pkgInfo.requestedPermissions != null) {
            for (String perm : pkgInfo.requestedPermissions) {
                try {
                    PermissionInfo pi = pm.getPermissionInfo(perm, 0);
                    CharSequence pl = pi.loadLabel(pm);
                    String g = "OTHER";
                    if (pi.group != null) {
                        PermissionGroupInfo gi = pm.getPermissionGroupInfo(pi.group, 0);
                        g = gi.loadLabel(pm).toString().toUpperCase();
                    }
                    Set<String> set = groups.get(g);
                    if (set == null) { set = new LinkedHashSet<String>(); groups.put(g, set); }
                    set.add(pl.toString());
                } catch (PackageManager.NameNotFoundException ignored) { }
            }
        }

        LinearLayout list = (LinearLayout) findViewById(R.id.permissions_list);
        TextView question = (TextView) findViewById(R.id.install_confirm_question);
        boolean isUpdate = false;
        try {
            PackageInfo installed = pm.getPackageInfo(pkgInfo.packageName, 0);
            isUpdate = pkgInfo.versionCode > installed.versionCode;
        } catch (PackageManager.NameNotFoundException ignored) { }

        if (groups.isEmpty()) {
            question.setText(isUpdate ? R.string.update_question_no_perms
                    : R.string.install_confirm_question_no_perms);
        } else {
            question.setText(isUpdate ? R.string.update_question
                    : R.string.install_confirm_question);
            for (Map.Entry<String, Set<String>> e : groups.entrySet()) {
                TextView h = new TextView(this);
                h.setText(e.getKey());
                h.setTextColor(0xffcccccc);
                h.setTextSize(16);
                h.setPadding(0, dp(14), 0, dp(4));
                list.addView(h);
                View rule = new View(this);
                rule.setBackgroundColor(0xff555555);
                list.addView(rule, new LinearLayout.LayoutParams(-1, dp(1)));
                for (String s : e.getValue()) {
                    LinearLayout row = new LinearLayout(this);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setPadding(dp(12), dp(8), 0, dp(8));
                    TextView dot = new TextView(this);
                    dot.setText("\u2022");
                    dot.setTextColor(0xffffffff);
                    dot.setTextSize(18);
                    dot.setPadding(0, 0, dp(12), 0);
                    TextView t = new TextView(this);
                    t.setText(s);
                    t.setTextColor(0xffffffff);
                    t.setTextSize(18);
                    row.addView(dot);
                    row.addView(t);
                    list.addView(row);
                }
            }
        }

        View ok = findViewById(R.id.ok_button);
        ok.setEnabled(true);
        ok.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Intent i = new Intent(PackageInstallerActivity.this, InstallAppProgress.class);
                i.putExtra(InstallAppProgress.EXTRA_APK_PATH, staged.getAbsolutePath());
                i.putExtra(InstallAppProgress.EXTRA_LABEL, label.toString());
                i.putExtra(InstallAppProgress.EXTRA_PACKAGE, pkgInfo.packageName);
                startActivity(i);
                finish();
            }
        });
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void cleanup() {
        if (staged != null) staged.delete();
    }
}
