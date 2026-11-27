package com.sakthivelrayawoodworks.update;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import androidx.core.content.FileProvider;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * In-app updates from GitHub Releases: reports the installed version, downloads a newer APK
 * into the app cache, and hands it to the Android installer (which updates the app in place).
 */
@CapacitorPlugin(name = "Updater")
public class UpdaterPlugin extends Plugin {

    @PluginMethod
    public void getVersion(PluginCall call) {
        try {
            String pkg = getContext().getPackageName();
            PackageInfo info = getContext().getPackageManager().getPackageInfo(pkg, 0);
            long code = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ? info.getLongVersionCode() : info.versionCode;
            JSObject result = new JSObject();
            result.put("versionCode", code);
            result.put("versionName", info.versionName);
            call.resolve(result);
        } catch (Exception error) {
            call.reject("Could not read the app version.", error);
        }
    }

    /** Android 8+ needs the user to allow "Install unknown apps" for this app once. */
    @PluginMethod
    public void canInstall(PluginCall call) {
        boolean allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.O
            || getContext().getPackageManager().canRequestPackageInstalls();
        JSObject result = new JSObject();
        result.put("allowed", allowed);
        call.resolve(result);
    }

    @PluginMethod
    public void openInstallSettings(PluginCall call) {
        try {
            Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getContext().getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(intent);
            call.resolve();
        } catch (Exception error) {
            call.reject("Could not open the install permission screen.", error);
        }
    }

    @PluginMethod
    public void downloadAndInstall(PluginCall call) {
        String url = call.getString("url");
        if (url == null || !url.startsWith("https://")) {
            call.reject("Update link is missing or not secure.");
            return;
        }
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                File dir = new File(getContext().getCacheDir(), "updates");
                if (!dir.isDirectory() && !dir.mkdirs()) throw new java.io.IOException("Cannot create update folder");
                File apk = new File(dir, "update.apk");

                // GitHub release links redirect (https -> https) to the file host; follow up to 5 hops.
                URL current = new URL(url);
                for (int hop = 0; ; hop++) {
                    connection = (HttpURLConnection) current.openConnection();
                    connection.setInstanceFollowRedirects(false);
                    connection.setConnectTimeout(20000);
                    connection.setReadTimeout(60000);
                    connection.setRequestProperty("Accept", "application/octet-stream");
                    int status = connection.getResponseCode();
                    if (status >= 300 && status < 400 && hop < 5) {
                        String location = connection.getHeaderField("Location");
                        connection.disconnect();
                        current = new URL(current, location);
                        if (!"https".equals(current.getProtocol())) throw new java.io.IOException("Insecure redirect");
                        continue;
                    }
                    if (status != HttpURLConnection.HTTP_OK) throw new java.io.IOException("Server answered " + status);
                    break;
                }

                long total = connection.getContentLengthLong();
                long done = 0;
                int lastPercent = -1;
                try (InputStream in = connection.getInputStream(); OutputStream out = new FileOutputStream(apk)) {
                    byte[] buffer = new byte[64 * 1024];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                        done += read;
                        if (total > 0) {
                            int percent = (int) (done * 100 / total);
                            if (percent != lastPercent) {
                                lastPercent = percent;
                                JSObject progress = new JSObject();
                                progress.put("percent", percent);
                                notifyListeners("downloadProgress", progress);
                            }
                        }
                    }
                }
                if (total > 0 && done != total) throw new java.io.IOException("Download incomplete");

                Uri uri = FileProvider.getUriForFile(getContext(), getContext().getPackageName() + ".fileprovider", apk);
                Intent install = new Intent(Intent.ACTION_VIEW);
                install.setDataAndType(uri, "application/vnd.android.package-archive");
                install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(install);
                call.resolve();
            } catch (Exception error) {
                call.reject("Update download failed: " + error.getMessage(), error);
            } finally {
                if (connection != null) connection.disconnect();
            }
        }).start();
    }
}
