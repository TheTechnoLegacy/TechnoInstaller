package com.techno.installer;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Instantiated by Shizuku itself (via reflection, in its own remote process)
 * when we call {@code Shizuku.bindUserService(...)} - this class is never
 * started through the normal Android Service lifecycle, so it must have a
 * public no-arg constructor and must not touch anything that needs a real
 * Context/Application (there isn't one).
 *
 * Whatever identity Shizuku itself is running as (adb shell by default, or
 * root if the user is using Sui instead) is the identity {@link
 * Runtime#exec} inherits here - that's the whole trick.
 */
public class InstallerUserService extends IInstallerUserService.Stub {

    public InstallerUserService() {
        // Required no-arg constructor; Shizuku instantiates this directly.
    }

    @Override
    public String execCommand(String cmd) {
        StringBuilder output = new StringBuilder();
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"sh", "-c", cmd});
            try (BufferedReader stdout = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
                 BufferedReader stderr = new BufferedReader(
                    new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = stdout.readLine()) != null) {
                    output.append(line).append('\n');
                }
                while ((line = stderr.readLine()) != null) {
                    output.append(line).append('\n');
                }
            }
            int exitCode = process.waitFor();
            if (exitCode != 0 && output.length() == 0) {
                output.append("exit code ").append(exitCode);
            }
        } catch (Exception e) {
            output.append("execCommand failed: ").append(e);
        }
        return output.toString();
    }

    @Override
    public void destroy() {
        System.exit(0);
    }
}
