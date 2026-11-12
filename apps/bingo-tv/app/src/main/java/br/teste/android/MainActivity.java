package br.teste.android;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final String URL = "https://bingo.turfkeno.online/onlinegames/bingo-pix/tv-ponto.php";
    private FrameLayout raiz;
    private WebView web;
    private TextView aviso;
    private final Handler handler = new Handler();
    private boolean erro = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        try {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } catch (Throwable t) { }
        raiz = new FrameLayout(this);
        raiz.setBackgroundColor(Color.BLACK);
        setContentView(raiz);
        aviso = new TextView(this);
        aviso.setTextColor(Color.WHITE);
        aviso.setTextSize(24);
        aviso.setGravity(Gravity.CENTER);
        raiz.addView(aviso, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        aviso.setText("Carregando Bingo TV...");
        iniciar();
    }

    private void iniciar() {
        try {
            web = new WebView(this);
            WebSettings s = web.getSettings();
            s.setJavaScriptEnabled(true);
            s.setDomStorageEnabled(true);
            s.setLoadWithOverviewMode(true);
            s.setCacheMode(WebSettings.LOAD_DEFAULT);
            try { s.setMediaPlaybackRequiresUserGesture(false); } catch (Throwable t) { }
            try { s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW); } catch (Throwable t) { }
            web.setBackgroundColor(Color.BLACK);
            try {
                CookieManager cm = CookieManager.getInstance();
                cm.setAcceptCookie(true);
            } catch (Throwable t) { }
            web.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageFinished(WebView v, String u) {
                    if (!erro) {
                        aviso.setVisibility(View.GONE);
                    }
                    try { CookieManager.getInstance().flush(); } catch (Throwable t) { }
                }

                @Override
                public void onReceivedError(WebView v, int code, String desc, String u) {
                    falha("Sem conexao com a internet.\nTentando novamente...");
                }

                @Override
                public void onReceivedSslError(WebView v, android.webkit.SslErrorHandler h, android.net.http.SslError e) {
                    // TVs com data/hora errada ou certificados antigos: nao deixa a TV travada
                    h.cancel();
                    falha("Erro de seguranca (confira data e hora da TV).\nTentando novamente...");
                }
            });
            raiz.addView(web, 0, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            web.loadUrl(URL);
        } catch (Throwable t) {
            aviso.setVisibility(View.VISIBLE);
            aviso.setText("Esta TV nao tem o navegador interno (WebView) funcionando.\nAtualize o 'Android System WebView' ou o Chrome na loja da TV.");
        }
    }

    private void falha(String msg) {
        erro = true;
        aviso.setVisibility(View.VISIBLE);
        aviso.setText(msg);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                erro = false;
                if (web != null) { web.loadUrl(URL); }
            }
        }, 8000);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && web != null && web.canGoBack()) {
            web.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onPause() {
        super.onPause();
        try { CookieManager.getInstance().flush(); } catch (Throwable t) { }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
