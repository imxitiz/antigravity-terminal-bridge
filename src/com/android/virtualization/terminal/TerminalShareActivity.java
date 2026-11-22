package com.android.virtualization.terminal;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TerminalShareActivity extends Activity {
    private static final String TAG = "MockShareActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "TerminalShareActivity created");

        Intent intent = getIntent();
        if (intent != null) {
            Log.i(TAG, "Received intent: " + intent + ", action=" + intent.getAction() + ", data=" + intent.getData() + ", clip=" + intent.getClipData());
            Uri uri = intent.getData();

            // Check ClipData for content URI if data is android-terminal://
            if (intent.getClipData() != null && intent.getClipData().getItemCount() > 0) {
                for (int i = 0; i < intent.getClipData().getItemCount(); i++) {
                    Uri clipUri = intent.getClipData().getItemAt(i).getUri();
                    Log.i(TAG, "ClipData URI [" + i + "]: " + clipUri);
                    if (clipUri != null && "content".equals(clipUri.getScheme())) {
                        uri = clipUri;
                        break;
                    }
                }
            }

            if (uri != null && "content".equals(uri.getScheme())) {
                processSetupScript(uri);
            }
        }

        // Return RESULT_OK to Antigravity Desktop
        setResult(RESULT_OK);
        Log.i(TAG, "Finished with RESULT_OK");
        finish();
    }

    private void processSetupScript(Uri uri) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            if (is == null) {
                Log.e(TAG, "Failed to open input stream for URI: " + uri);
                return;
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            reader.close();

            String scriptContent = sb.toString();
            Log.i(TAG, "Read setup script (" + scriptContent.length() + " bytes)");

            // Extract SSH_PORT and SSH_PUB_KEY
            Pattern portPattern = Pattern.compile("SSH_PORT=[\"']?([0-9]+)[\"']?");
            Pattern keyPattern = Pattern.compile("SSH_PUB_KEY=[\"']([^\"']+)[\"']");

            Matcher portMatcher = portPattern.matcher(scriptContent);
            Matcher keyMatcher = keyPattern.matcher(scriptContent);

            String port = "2222";
            if (portMatcher.find()) {
                port = portMatcher.group(1);
            }

            String pubKey = null;
            if (keyMatcher.find()) {
                pubKey = keyMatcher.group(1);
            }

            Log.i(TAG, "Extracted SSH_PORT=" + port + ", PubKey=" + (pubKey != null ? pubKey.substring(0, Math.min(30, pubKey.length())) + "..." : "null"));

            if (pubKey != null && !pubKey.isEmpty()) {
                installSshKeyAndForwardPort(port, pubKey);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error processing setup script", e);
        }
    }

    private void installSshKeyAndForwardPort(String port, String pubKey) {
        try {
            // Write to a temporary file in our app cache
            File tempKeyFile = new File(getCacheDir(), "pending_key.pub");
            FileOutputStream fos = new FileOutputStream(tempKeyFile);
            fos.write((pubKey + "\n").getBytes());
            fos.close();

            // Execute root helper script to install the key and configure sshd / iptables redirect
            String termuxSshDir = "/data/data/com.termux/files/home/.ssh";
            String termuxAuthKeys = termuxSshDir + "/authorized_keys";
            String droidAuthKeys = "/home/droid/.ssh/authorized_keys";

            // Commands to execute via su
            String cmd = String.format(
                "mkdir -p %s /home/droid/.ssh && " +
                "cat %s >> %s && " +
                "cat %s >> %s && " +
                "chmod 700 %s /home/droid/.ssh && " +
                "chmod 600 %s %s && " +
                "chown -R 10624:10624 %s /home/droid && " +
                // Start sshd on the requested port if not already listening
                "/data/data/com.termux/files/usr/bin/sshd -p %s 2>/dev/null || true && " +
                // Also add iptables port redirect just in case
                "iptables -t nat -A OUTPUT -p tcp -d 127.0.0.1 --dport %s -j REDIRECT --to-ports 8022 2>/dev/null || true",
                termuxSshDir,
                tempKeyFile.getAbsolutePath(), termuxAuthKeys,
                tempKeyFile.getAbsolutePath(), droidAuthKeys,
                termuxSshDir,
                termuxAuthKeys, droidAuthKeys,
                termuxSshDir,
                port, port
            );

            Log.i(TAG, "Executing bridge root command: " + cmd);
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
            p.waitFor();
            Log.i(TAG, "Root bridge setup completed with exit code: " + p.exitValue());

        } catch (Exception e) {
            Log.e(TAG, "Failed to install SSH key or forward port", e);
        }
    }
}
