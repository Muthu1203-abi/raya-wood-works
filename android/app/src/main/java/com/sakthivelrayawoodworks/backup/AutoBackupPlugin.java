package com.sakthivelrayawoodworks.backup;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.UriPermission;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import androidx.activity.result.ActivityResult;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Automatic offline backup: the user picks a folder once (Android's folder picker); after that the app
 * writes the same file name into that folder after every change, replacing the previous copy.
 * The folder permission survives app updates; after a reinstall the user picks the folder again.
 */
@CapacitorPlugin(name = "AutoBackup")
public class AutoBackupPlugin extends Plugin {
    private static final String PREFS = "auto_backup";
    private static final String KEY_TREE = "tree_uri";

    private SharedPreferences prefs() {
        return getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private Uri savedTree() {
        String value = prefs().getString(KEY_TREE, null);
        if (value == null) return null;
        Uri tree = Uri.parse(value);
        for (UriPermission permission : getContext().getContentResolver().getPersistedUriPermissions()) {
            if (permission.getUri().equals(tree) && permission.isWritePermission()) return tree;
        }
        return null; // permission was lost (e.g. folder deleted) — the user must pick again
    }

    private String folderName(Uri tree) {
        try {
            Uri doc = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree));
            try (Cursor c = getContext().getContentResolver().query(doc, new String[] { DocumentsContract.Document.COLUMN_DISPLAY_NAME }, null, null, null)) {
                if (c != null && c.moveToFirst()) return c.getString(0);
            }
        } catch (Exception ignored) {}
        return tree.getLastPathSegment();
    }

    @PluginMethod
    public void status(PluginCall call) {
        Uri tree = savedTree();
        JSObject result = new JSObject();
        result.put("configured", tree != null);
        if (tree != null) result.put("folder", folderName(tree));
        call.resolve(result);
    }

    @PluginMethod
    public void pickFolder(PluginCall call) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(call, intent, "onFolderPicked");
    }

    @ActivityCallback
    private void onFolderPicked(PluginCall call, ActivityResult result) {
        if (call == null) return;
        Intent data = result.getData();
        if (result.getResultCode() != Activity.RESULT_OK || data == null || data.getData() == null) {
            call.reject("No folder chosen.", "CANCELED");
            return;
        }
        Uri tree = data.getData();
        try {
            ContentResolver resolver = getContext().getContentResolver();
            // Drop the old folder's permission so permissions don't pile up.
            String old = prefs().getString(KEY_TREE, null);
            if (old != null && !old.equals(tree.toString())) {
                try { resolver.releasePersistableUriPermission(Uri.parse(old), Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION); } catch (Exception ignored) {}
            }
            resolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            prefs().edit().putString(KEY_TREE, tree.toString()).apply();
            JSObject response = new JSObject();
            response.put("configured", true);
            response.put("folder", folderName(tree));
            call.resolve(response);
        } catch (Exception error) {
            call.reject("This folder can't be used for backups. Choose a folder on the phone (for example Documents).", "FOLDER", error);
        }
    }

    /** Writes {data} to {filename} in the chosen folder, replacing the file if it is already there. */
    @PluginMethod
    public void write(PluginCall call) {
        String data = call.getString("data");
        String filename = call.getString("filename", "raya-wood-works-backup.json");
        Uri tree = savedTree();
        if (tree == null) {
            call.reject("Backup folder is not set or its permission was removed.", "NO_FOLDER");
            return;
        }
        if (data == null) {
            call.reject("Nothing to back up.");
            return;
        }
        new Thread(() -> {
            try {
                ContentResolver resolver = getContext().getContentResolver();
                String rootId = DocumentsContract.getTreeDocumentId(tree);
                Uri file = findChild(resolver, tree, rootId, filename);
                if (file == null) {
                    Uri parent = DocumentsContract.buildDocumentUriUsingTree(tree, rootId);
                    file = DocumentsContract.createDocument(resolver, parent, "application/json", filename);
                    if (file == null) throw new java.io.IOException("Could not create the backup file");
                }
                try (OutputStream out = resolver.openOutputStream(file, "wt")) { // "wt" = replace old contents
                    if (out == null) throw new java.io.IOException("Could not open the backup file");
                    out.write(data.getBytes(StandardCharsets.UTF_8));
                }
                JSObject result = new JSObject();
                result.put("folder", folderName(tree));
                call.resolve(result);
            } catch (Exception error) {
                call.reject("Automatic backup failed: " + error.getMessage(), "WRITE", error);
            }
        }).start();
    }

    @PluginMethod
    public void disable(PluginCall call) {
        String old = prefs().getString(KEY_TREE, null);
        if (old != null) {
            try {
                getContext().getContentResolver().releasePersistableUriPermission(Uri.parse(old), Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            } catch (Exception ignored) {}
        }
        prefs().edit().remove(KEY_TREE).apply();
        call.resolve();
    }

    private Uri findChild(ContentResolver resolver, Uri tree, String parentId, String name) {
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId);
        String[] cols = { DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME };
        try (Cursor c = resolver.query(children, cols, null, null, null)) {
            if (c == null) return null;
            while (c.moveToNext()) {
                if (name.equals(c.getString(1))) return DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0));
            }
        }
        return null;
    }
}
