package com.sakthivelrayawoodworks;

import android.webkit.WebView;
import androidx.activity.OnBackPressedCallback;
import com.getcapacitor.BridgeActivity;
import com.sakthivelrayawoodworks.share.PdfSharePlugin;
import com.sakthivelrayawoodworks.update.UpdaterPlugin;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(android.os.Bundle savedInstanceState) {
        registerPlugin(PdfSharePlugin.class);
        registerPlugin(UpdaterPlugin.class);
        super.onCreate(savedInstanceState);

        // Let the web app handle the Android back button first (close sheet/sidebar, go to previous page).
        // Only when it reports nothing to go back to (home page) does the app close.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                WebView webView = getBridge() != null ? getBridge().getWebView() : null;
                if (webView == null) {
                    exitApp(this);
                    return;
                }
                webView.evaluateJavascript(
                    "(function(){try{return !!(window.srwHandleBack&&window.srwHandleBack());}catch(e){return false;}})()",
                    value -> {
                        if (!"true".equals(value)) exitApp(this);
                    }
                );
            }
        });
    }

    private void exitApp(OnBackPressedCallback callback) {
        callback.setEnabled(false);
        getOnBackPressedDispatcher().onBackPressed();
        callback.setEnabled(true);
    }
}
