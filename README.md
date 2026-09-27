# Techno Installer

A user-installable (non-privileged) port of the AOSP `PackageInstaller` system app.

- Original package: `com.android.packageinstaller` (system, signature|privileged)
- New package: **`com.techno.installer`**

## Why this isn't a 1:1 decompile

The stock app only works because it ships as a *system* app signed with the
platform key and holding `INSTALL_PACKAGES` / `DELETE_PACKAGES` /
`GRANT_REVOKE_PERMISSIONS` — permissions no ordinary installed app can ever
be granted. So this isn't a smali edit + repackage; the install/uninstall
logic was rewritten in Java against the public, non-privileged replacement
APIs so it can actually run as a normal user app:

| Original (system app) | This port (user app) |
|---|---|
| `INSTALL_PACKAGES` — silent install | `REQUEST_INSTALL_PACKAGES` (normal permission, API 26+) + `PackageInstaller.Session`. The system still shows its own final "Install this app?" confirmation (`STATUS_PENDING_USER_ACTION`) — that step can't be skipped by a non-system app. |
| `DELETE_PACKAGES` — silent uninstall | `REQUEST_DELETE_PACKAGES` (normal permission) + `PackageInstaller#uninstall()`. Same deal: system shows its own confirmation. |
| `GrantActivity` (`REQUEST_PERMISSION` intent) | Dropped entirely. Since Android 6, that flow is handled exclusively by the signature-protected `PermissionController` — a third-party app can't intercept or reimplement it. |
| Full runtime permission list shown pre-install | Not reproduced (needed a privileged parsing API). Android now re-confirms dangerous permissions itself the first time the new app actually uses them. |

Everything else — activity flow, layouts (`install_start`, `install_confirm`,
`op_progress`, `uninstall_confirm`, `app_details`), and strings — is carried
over from the decompiled original with only ID/style tweaks.

## What's registered

- `PackageInstallerActivity` — handles `VIEW`/`INSTALL_PACKAGE` on
  `content://` or `file://` + `application/vnd.android.package-archive`.
- `InstallAppProgress` — runs the actual `PackageInstaller.Session`, shows
  progress and result.
- `UninstallerActivity` — handles `DELETE`/`UNINSTALL_PACKAGE` on
  `package:<name>`.
- `UninstallAppProgress` — runs `PackageInstaller#uninstall()`.
- `InstallResultReceiver` / `UninstallResultReceiver` — receive the session
  callbacks and route them back to the two progress activities.

## Building

This is a plain Gradle/AGP project (`com.android.application` 8.5.2,
compileSdk/targetSdk 34, minSdk 26 — session installs need API 26).

Easiest path: open the `TechnoInstaller/` folder in Android Studio and let it
generate the Gradle wrapper on first sync (this container has no network
access to `dl.google.com` / the Gradle distribution, so the build could not
be compiled or tested here — treat the Java as unbuilt-but-should-compile,
not verified).

```
Open in Android Studio → Sync → Run
```

or, with your own local `gradle` + Android SDK on PATH:

```
gradle assembleDebug
```

## Installing it

Because this now ships as a **normal, differently-signed, differently-named**
app, it will not silently replace the system installer. To actually use it in
place of the stock one on a device you control:

1. Install the APK normally (`adb install app-debug.apk`).
2. The first time you tap an APK file, Android will offer a chooser between
   this app and the stock installer (or you can set it as default for that
   file type in Settings → Apps → Default apps, on ROMs that expose that).
3. Grant "Allow app installs" for Techno Installer when prompted
   (`Settings → Apps → Special access → Install unknown apps`).

## Optional: seamless install/uninstall via Shizuku

`ShizukuInstaller` / `InstallerUserService` / `IInstallerUserService.aidl` add
a second, fully silent path that bypasses the confirmation UI entirely:

- If a Shizuku (or Sui) instance is running and this app has been granted
  Shizuku permission, `InstallAppProgress`/`UninstallAppProgress` skip
  `PackageInstaller.Session`/the legacy intent-forward entirely and instead
  ask Shizuku to run `pm install --bypass-low-target-sdk-block -r <apk>` /
  `pm uninstall <package>` directly, under whatever identity Shizuku itself
  runs as (adb shell, or root under Sui). No system confirmation dialog at
  all - that's the whole point of Shizuku.
- `--bypass-low-target-sdk-block` is there because Android 14+ otherwise
  refuses to install anything targeting API < 23 ("app not installed as it
  may be malicious") - relevant for every low-targetSdk app in this family
  of ports, not just this one.
- If Shizuku isn't running or hasn't been granted permission, both screens
  transparently fall back to the existing non-privileged flow from the table
  above. Nothing about the plain (non-Shizuku) path changed.

**This part is unbuilt and unverified.** Unlike the rest of this project
(compiled/dexed/apktool-built and confirmed to install), the Shizuku
integration needs `dev.rikka.shizuku:api`/`:provider` from Maven Central and
Android's real `aidl` compiler for `IInstallerUserService.aidl` - neither is
reachable from the sandbox this was written in (no Maven Central, no Android
SDK), so this was written from the documented Shizuku API but has not been
compiled, dexed, or run. Build it in Android Studio and sanity-check the
install/uninstall flow with Shizuku both running and not-running before
relying on it.

You'll also need the [Shizuku app](https://github.com/RikkaApps/Shizuku)
itself installed and started (via ADB/wireless debugging, or Sui if rooted)
on the test device - Techno Installer only talks to it, it doesn't bundle or
install it.
