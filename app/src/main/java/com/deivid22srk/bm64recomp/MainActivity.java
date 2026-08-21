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
    // storage, which is where the native code expects them (<files>/assets).
    private void extractAssets() {
        try {
            File destRoot = getFilesDir();
            String[] entries = getAssets().list("game");
            if (entries == null || entries.length == 0) {
                return;
            }
            copyAssetDirectory("game", destRoot);

            // Controller mappings live at the top level of the APK assets.
            File db = new File(destRoot, "recompcontrollerdb.txt");
            if (!db.exists()) {
                copyAssetFile("recompcontrollerdb.txt", db);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to extract game assets", e);
        }
    }

    private void copyAssetDirectory(String assetPath, File destDir) throws IOException {
        String[] children = getAssets().list(assetPath);
        File destDirAbs = new File(destDir, assetPath.substring(assetPath.lastIndexOf('/') + 1));
        if (children == null || children.length == 0) {
            copyAssetFile(assetPath, destDirAbs);
            return;
        }
        if (!destDirAbs.exists()) {
            destDirAbs.mkdirs();
        }
        for (String child : children) {
            copyAssetDirectory(assetPath + "/" + child, destDirAbs);
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
