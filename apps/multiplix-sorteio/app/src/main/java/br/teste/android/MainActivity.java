package br.teste.android;

import android.app.Activity;
import android.graphics.Color;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;

/** Tela de sorteio Multiplix (demonstracao local, sorteio a cada 30 segundos). */
public class MainActivity extends Activity {
    private WebView web;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        try { getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); } catch (Throwable t) { }
        try {
            if (Build.VERSION.SDK_INT >= 28) {
                WindowManager.LayoutParams lp = getWindow().getAttributes();
                lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
                getWindow().setAttributes(lp);
            }
        } catch (Throwable t) { }
        try { setVolumeControlStream(AudioManager.STREAM_MUSIC); } catch (Throwable t) { }
        try {
            web = new WebView(this);
            WebSettings s = web.getSettings();
            s.setJavaScriptEnabled(true);
            s.setDomStorageEnabled(true);
            s.setUseWideViewPort(true);
            s.setLoadWithOverviewMode(false);
            s.setSupportZoom(false);
            s.setBuiltInZoomControls(false);
            try { s.setTextZoom(100); } catch (Throwable t) { }
            try { s.setMediaPlaybackRequiresUserGesture(false); } catch (Throwable t) { }
            web.setBackgroundColor(Color.rgb(10, 92, 20));
            web.setVerticalScrollBarEnabled(false);
            web.setHorizontalScrollBarEnabled(false);
            web.setWebChromeClient(new WebChromeClient());
            web.setWebViewClient(new WebViewClient());
            setContentView(web);
            web.loadUrl("file:///android_asset/sorteio.html");
        } catch (Throwable t) {
            TextView tv = new TextView(this);
            tv.setText("O navegador interno (WebView) deste aparelho nao esta funcionando. Atualize o 'Android System WebView' na loja.");
            tv.setTextSize(22);
            setContentView(tv);
        }
        telaCheia();
    }

    private void telaCheia() {
        try {
            View d = getWindow().getDecorView();
            int f = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN;
            if (Build.VERSION.SDK_INT >= 19) f |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            d.setSystemUiVisibility(f);
        } catch (Throwable t) { }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) telaCheia();
    }

    @Override
    protected void onResume() { super.onResume(); if (web != null) web.onResume(); }

    @Override
    protected void onPause() { if (web != null) web.onPause(); super.onPause(); }

    @Override
    protected void onDestroy() { if (web != null) web.destroy(); super.onDestroy(); }
}
