# Antigravity Linux Terminal Bridge (`com.android.virtualization.terminal`)

An autonomous lightweight bridge and mock provider enabling **Google Antigravity Desktop** to run on Android devices lacking hardware-level Protected KVM / Android Virtualization Framework (AVF) support (such as custom ROMs like crDroid, LineageOS, or legacy Qualcomm SoCs like Snapdragon 860 / POCO X3 Pro).

---

## The Architectural Problem
Google Antigravity Desktop on Android is a graphical companion that offloads its agent execution, language servers, and development environment to Android's built-in Linux Virtual Machine (`com.android.virtualization.terminal`).

On non-Pixel or legacy-kernel devices:
1. `com.android.virtualization.terminal` does not exist on the ROM.
2. Antigravity Desktop fails at onboarding with:
   ```text
   Failed to find provider info for com.android.virtualization.terminal.provider
   ```
3. Attempting to start the Terminal VM results in:
   ```text
   No Activity found to handle Intent { act=android.intent.action.VIEW dat=android-terminal://start/... }
   ```

---

## How This Bridge Solves It
This bridge claims the `com.android.virtualization.terminal` package namespace and satisfies Google Antigravity's VM contract:

1. **ContentProvider (`TerminalProvider`)**:
   - Authority: `com.android.virtualization.terminal.provider`
   - Handles `call("getVmStatus")` -> Returns `{ is_installed: true, is_running: true }`.
   - Handles `call("setAutoPortForwardEnabled")` -> Returns `{ result: true }`.
2. **Activity Handlers (`TerminalShareActivity`)**:
   - Handles `android.intent.action.VIEW` for `android-terminal://start`.
   - Handles `com.android.virtualization.terminal.action.SHARE_DIRECTORY`.
   - Extracts the generated SSH public key (`vm_ssh_id_rsa.pub`) and SSH port from `setup_terminal.sh`.
   - Injects the key into Termux's `~/.ssh/authorized_keys`.
   - Bridges communication into native Termux OpenSSH.
3. **Bare-Metal Glibc Execution**:
   - Executes Antigravity's 104 MB `language_server_shim` natively on bare metal via standard Linux `/lib/ld-linux-aarch64.so.1` (no PRoot or virtualization overhead).

---

## Building & Installing On-Device (Termux)

```bash
chmod +x build.sh
./build.sh

# Install with root privileges
su -c "pm install -r -d -g build/mock-terminal-provider.apk"
```
