package com.sakthivelrayawoodworks.share;

import android.content.Intent;
import android.net.Uri;
import android.util.Base64;
import androidx.core.content.FileProvider;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.io.File;
import java.io.FileOutputStream;

@CapacitorPlugin(name = "PdfShare")
public class PdfSharePlugin extends Plugin {
    @PluginMethod
    public void sharePdf(PluginCall call) {
        try {
            String encoded = call.getString("pdfBase64");
            if (encoded == null || encoded.isEmpty()) {
                call.reject("PDF data is missing.");
                return;
            }
            // PDF (default), JPG image, or the app's offline backup file (.json)
            String requested = call.getString("mimeType", "application/pdf");
            String mimeType = "image/jpeg".equals(requested) || "application/json".equals(requested) ? requested : "application/pdf";
            String extension = "image/jpeg".equals(mimeType) ? ".jpg" : "application/json".equals(mimeType) ? ".json" : ".pdf";
            String requestedName = call.getString("filename", "quotation" + extension);
            // Keep Tamil/Unicode letters; only strip characters that are unsafe in file names or paths.
            String filename = requestedName.replaceAll("[\\\\/:*?\"<>|\\s\\p{Cntrl}]", "_").replaceAll("^\\.+", "");
            if (filename.isEmpty()) filename = "quotation";
            if (!filename.toLowerCase().endsWith(extension)) filename += extension;
            File dir = new File(getContext().getCacheDir(), "shared");
            if (!dir.isDirectory() && !dir.mkdirs()) throw new java.io.IOException("Cannot create share folder");
            File[] old = dir.listFiles();
            long cutoff = System.currentTimeMillis() - 24L * 60 * 60 * 1000;
            if (old != null) for (File f : old) if (f.lastModified() < cutoff) f.delete();
            File out = new File(dir, filename);
            try (FileOutputStream stream = new FileOutputStream(out)) {
                stream.write(Base64.decode(encoded, Base64.DEFAULT));
            }
            Uri uri = FileProvider.getUriForFile(getContext(), getContext().getPackageName() + ".fileprovider", out);
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType(mimeType);
            send.putExtra(Intent.EXTRA_STREAM, uri);
            String text = call.getString("text");
            if (text != null) send.putExtra(Intent.EXTRA_TEXT, text);
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            getActivity().startActivity(Intent.createChooser(send, call.getString("dialogTitle", "Share quotation PDF")));
            JSObject result = new JSObject();
            result.put("shared", true);
            call.resolve(result);
        } catch (Exception error) {
            call.reject("Could not share quotation PDF.", error);
        }
    }
}
