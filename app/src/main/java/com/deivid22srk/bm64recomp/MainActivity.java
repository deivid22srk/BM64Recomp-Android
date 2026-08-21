package com.deivid22srk.bm64recomp;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;

import org.libsdl.app.SDLActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends SDLActivity {
    private static final int REQUEST_CODE_PICK_ROM = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        extractAssets();
        super.onCreate(savedInstanceState);
    }

    // Extracts the game UI assets packed into the APK into the app's internal
    // storage. APK assets live under "game/"; they are extracted to
    // <files>/assets/, which is where the native code looks for them.
    private void extractAssets() {
        try {
            String[] entries = getAssets().list("game");
            if (entries == null || entries.length == 0) {
                return;
            }
            File assetsRoot = new File(getFilesDir(), "assets");
            if (!assetsRoot.exists()) {
                assetsRoot.mkdirs();
            }
            for (String child : entries) {
                copyAssetPath("game/" + child, new File(assetsRoot, child));
            }

            // Controller mappings live at the top level of the APK assets.
            File db = new File(getFilesDir(), "recompcontrollerdb.txt");
            if (!db.exists()) {
                copyAssetFile("recompcontrollerdb.txt", db);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to extract game assets", e);
        }
    }

    private void copyAssetPath(String assetPath, File dest) throws IOException {
        String[] children = getAssets().list(assetPath);
        if (children == null || children.length == 0) {
            copyAssetFile(assetPath, dest);
            return;
        }
        if (!dest.exists()) {
            dest.mkdirs();
        }
        for (String child : children) {
            copyAssetPath(assetPath + "/" + child, new File(dest, child));
        }
    }

    private void copyAssetFile(String assetPath, File dest) throws IOException {
        InputStream in = null;
        OutputStream out = null;
        try {
            in = getAssets().open(assetPath);
            out = new FileOutputStream(dest);
            byte[] buffer = new byte[65536];
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
        } finally {
            if (in != null) {
                try { in.close(); } catch (IOException ignored) {}
            }
            if (out != null) {
                try { out.close(); } catch (IOException ignored) {}
            }
        }
    }

    // Called from native code (main thread) to open the system file picker.
    public static void requestFilePick(Activity activity) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        activity.startActivityForResult(intent, REQUEST_CODE_PICK_ROM);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_PICK_ROM) {
            String importedPath = null;
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                importedPath = importPickedFile(data.getData());
            }
            nativeOnFilePicked(importedPath);
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    // Copies the picked document into internal storage so the native code can
    // read it freely. Returns the absolute path of the imported file or null.
    private String importPickedFile(Uri uri) {
        try {
            String displayName = queryDisplayName(uri);
            if (displayName == null) {
                displayName = "bm64.us.z64";
            }
            File dest = new File(getFilesDir(), displayName);
            InputStream in = getContentResolver().openInputStream(uri);
            if (in == null) {
                return null;
            }
            OutputStream out = new FileOutputStream(dest);
            byte[] buffer = new byte[65536];
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
            in.close();
            out.close();
            return dest.getAbsolutePath();
        } catch (IOException | SecurityException e) {
            return null;
        }
    }

    private String queryDisplayName(Uri uri) {
        Cursor cursor = getContentResolver().query(uri, null, null, null, null);
        if (cursor == null) {
            return null;
        }
        String name = null;
        try {
            if (cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) {
                    name = cursor.getString(idx);
                }
            }
        } finally {
            cursor.close();
        }
        return name;
    }

    static native void nativeOnFilePicked(String path);
}
