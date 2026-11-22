package com.android.virtualization.terminal;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

public class TerminalProvider extends ContentProvider {
    private static final String TAG = "MockTerminalProvider";

    @Override
    public boolean onCreate() {
        Log.i(TAG, "MockTerminalProvider initialized");
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        Log.i(TAG, "call() invoked: method=" + method + ", arg=" + arg + ", extras=" + extras);
        Bundle result = new Bundle();

        if ("getVmStatus".equals(method)) {
            // Antigravity checks is_installed, is_running, is_installing, is_foreground
            result.putBoolean("is_installed", true);
            result.putBoolean("is_running", true);
            result.putBoolean("is_installing", false);
            result.putBoolean("is_foreground", true);
            Log.i(TAG, "getVmStatus returning: installed=true, running=true");
            return result;
        } else if ("setAutoPortForwardEnabled".equals(method)) {
            result.putBoolean("result", true);
            Log.i(TAG, "setAutoPortForwardEnabled returning result=true");
            return result;
        }

        // Return empty or true for any other method calls
        result.putBoolean("result", true);
        return result;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        Log.i(TAG, "query() invoked on uri: " + uri);
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return "vnd.android.cursor.item/vnd.com.android.virtualization.terminal.status";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
