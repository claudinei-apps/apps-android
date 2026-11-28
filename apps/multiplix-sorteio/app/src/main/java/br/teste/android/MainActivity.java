package br.teste.android;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Bitmap;
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

/** Tela de sorteio Multiplix (demonstração local, sorteio a cada 30 segundos). */
public class MainActivity extends Activity {
    private WebView web;

    // Ajustes visuais aplicados por cima da página: logo menor (X visível) e laterais preenchidas.
    private static final String CSS =
        "html,body,#wrap{background:linear-gradient(90deg,#55b81d 0,#55b81d 50%,#d6ec1c 50%,#d6ec1c 100%)!important}"
        + "#logo{left:20px!important;top:70px!important;-webkit-transform:rotate(-12deg) scale(.8)!important;transform:rotate(-12deg) scale(.8)!important}"
        + "@-webkit-keyframes logoPula{0%,100%{-webkit-transform:rotate(-12deg) scale(.8)}50%{-webkit-transform:rotate(-10deg) scale(.84)}}"
        + "@keyframes logoPula{0%,100%{transform:rotate(-12deg) scale(.8)}50%{transform:rotate(-10deg) scale(.84)}}"
        + "#logo.agita{-webkit-animation:logoPula 1s ease-in-out infinite!important;animation:logoPula 1s ease-in-out infinite!important}";

    private static final String JS =
        "(function(){if(document.getElementById('ajusteBG'))return;var s=document.createElement('style');s.id='ajusteBG';"
        + "s.appendChild(document.createTextNode('" + CSS + "'));document.head.appendChild(s);})();";

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
            web.setBackgroundColor(Color.rgb(85, 184, 29));
            web.setVerticalScrollBarEnabled(false);
            web.setHorizontalScrollBarEnabled(false);
            web.setWebChromeClient(new WebChromeClient());
            web.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageStarted(WebView v, String u, Bitmap icon) {
                    try { v.evaluateJavascript(JS, null); } catch (Throwable t) { }
                }
                @Override
                public void onPageFinished(WebView v, String u) {
                    try { v.evaluateJavascript(JS, null); } catch (Throwable t) { }
                }
            });
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
    protected void onResume() {
        super.onResume();
        if (web != null) web.onResume();
    }

    @Override
    protected void onPause() {
        if (web != null) web.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
