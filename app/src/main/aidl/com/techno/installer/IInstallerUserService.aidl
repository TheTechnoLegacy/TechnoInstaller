// IInstallerUserService.aidl
package com.techno.installer;

/**
 * Runs inside Shizuku's own remote process, under whatever identity Shizuku
 * itself was started as (adb shell, or root if using Sui) - NOT this app's
 * normal UID. That's what lets execCommand() call `pm install` directly,
 * with no INSTALL_PACKAGES/DELETE_PACKAGES of our own and no system
 * confirmation dialog.
 */
interface IInstallerUserService {

    String execCommand(String cmd);

    /**
     * Shizuku calls this itself right before it destroys the service; the
     * exact transaction id is Shizuku's documented convention, not something
     * we chose. Implementation just needs to exist and can be a no-op.
     */
    void destroy() = 16777114;
}
