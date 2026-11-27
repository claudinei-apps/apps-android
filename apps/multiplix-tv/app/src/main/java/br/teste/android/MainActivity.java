package br.teste.android;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * TV do Multiplix (mesmo esquema da Bingo TV): abre a página de sorteio do servidor.
 * Se a página ainda não existir ou faltar internet, mostra a DEMONSTRAÇÃO local
 * (assets/demo-tv.html) e tenta o servidor de novo a cada 3 minutos.
 */
public class MainActivity extends Activity {
    private static final String HOST = "bingo.turfkeno.online";
    private static final String URL_TV = "https://" + HOST + "/onlinegames/multiplix/tv.php";
    private static final String DEMO = "file:///android_asset/demo-tv.html";
    private static final long TENTAR_DE_NOVO_MS = 180000;

    private FrameLayout raiz;
    private WebView web;
    private TextView aviso;
    private final Handler handler = new Handler();
    private boolean emDemo = false;

    private final Runnable tentarServidor = new Runnable() {
        public void run() { carregarServidor(); }
    };

    private static void log(String m) { Log.d("MultiplixTV", m); }

    // ---------- áudio nativo (para TVs em que o som da página não toca) ----------

    private static class Clip {
        MediaPlayer mp;
        int token = 0;
        boolean quer = false;
        long pedidoEm = 0;
        int inicioMs = 0;
    }
    private final Map<Integer, Clip> clips = new HashMap<Integer, Clip>();
    private final Map<String, List<Runnable>> baixando = new HashMap<String, List<Runnable>>();

    private static final String SHIM =
        "(function(){if(window.__bgNative||!window.BingoNative)return;window.__bgNative=1;var n=0;try{BingoNative.hello()}catch(e){}"
        + "function A(src){this.id=++n;this.src=src||'';this.currentTime=0;this.readyState=4;this.preload='';this.paused=true;"
        + "if(src){try{BingoNative.prefetch(this.src)}catch(e){}}}"
        + "A.prototype.play=function(){this.paused=false;try{BingoNative.play(this.id,this.src,Math.round((this.currentTime||0)*1000))}catch(e){}"
        + "return window.Promise?Promise.resolve():undefined;};"
        + "A.prototype.pause=function(){this.paused=true;try{BingoNative.stop(this.id)}catch(e){}};"
        + "A.prototype.load=function(){};A.prototype.addEventListener=function(){};A.prototype.removeEventListener=function(){};"
        + "window.Audio=A;})();";

    public class Ponte {
        @JavascriptInterface
        public void hello() { log("JS ligado ao app"); }

        @JavascriptInterface
        public void prefetch(final String src) {
            handler.post(new Runnable() { public void run() { garantirArquivo(src, null); } });
        }

        @JavascriptInterface
        public void play(final int id, final String src, final int inicioMs) {
            handler.post(new Runnable() { public void run() { tocar(id, src, inicioMs); } });
        }

        @JavascriptInterface
        public void stop(final int id) {
            handler.post(new Runnable() { public void run() { parar(id); } });
        }

        /** A demonstração pode pedir para tentar o servidor. */
        @JavascriptInterface
        public void tryServer() {
            handler.post(tentarServidor);
        }
    }

    private String urlCompleta(String src) {
        if (src == null) return null;
        if (src.startsWith("/")) return "https://" + HOST + src;
        if (src.startsWith("https://" + HOST + "/")) return src;
        return null;
    }

    private File arquivoLocal(String src) {
        File dir = new File(getCacheDir(), "snd");
        if (!dir.exists()) dir.mkdirs();
        String nome = src;
        int q = nome.indexOf('?');
        if (q >= 0) nome = nome.substring(0, q);
        nome = nome.replaceAll("[^A-Za-z0-9._-]", "_");
        return new File(dir, nome);
    }

    private void garantirArquivo(final String src, Runnable depois) {
        final String full = urlCompleta(src);
        if (full == null) return;
        final File f = arquivoLocal(src);
        if (f.exists() && f.length() > 0) {
            if (depois != null) depois.run();
            return;
        }
        List<Runnable> lista = baixando.get(src);
        if (lista != null) {
            if (depois != null) lista.add(depois);
            return;
        }
        lista = new ArrayList<Runnable>();
        if (depois != null) lista.add(depois);
        baixando.put(src, lista);
        new Thread(new Runnable() {
            public void run() {
                String r = baixar(full, f);
                final boolean fok = r == null;
                if (!fok) log("erro ao baixar som: " + r);
                handler.post(new Runnable() {
                    public void run() {
                        List<Runnable> l = baixando.remove(src);
                        if (fok && l != null) {
                            for (Runnable x : l) { try { x.run(); } catch (Throwable t) { } }
                        }
                    }
                });
            }
        }).start();
    }

    // devolve null se deu certo, ou o motivo do erro
    private String baixar(String full, File destino) {
        File tmp = new File(destino.getPath() + ".tmp");
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(full).openConnection();
            if (c instanceof HttpsURLConnection) {
                SSLContext ctx = SSLContext.getInstance("TLS");
                ctx.init(null, new TrustManager[] { new X509TrustManager() {
                    public void checkClientTrusted(X509Certificate[] a, String b) { }
                    public void checkServerTrusted(X509Certificate[] a, String b) { }
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                } }, new SecureRandom());
                ((HttpsURLConnection) c).setSSLSocketFactory(ctx.getSocketFactory());
            }
            c.setConnectTimeout(15000);
            c.setReadTimeout(30000);
            int code = c.getResponseCode();
            if (code != 200) return "HTTP " + code;
            InputStream in = c.getInputStream();
            FileOutputStream out = new FileOutputStream(tmp);
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            out.close();
            in.close();
            return tmp.renameTo(destino) ? null : "renomear";
        } catch (Throwable t) {
            try { tmp.delete(); } catch (Throwable t2) { }
            return String.valueOf(t);
        }
    }

    private void tocar(final int id, final String src, int inicioMs) {
        final Clip clip;
        if (clips.containsKey(id)) clip = clips.get(id);
        else { clip = new Clip(); clips.put(id, clip); }
        clip.token++;
        final int meuToken = clip.token;
        clip.quer = true;
        clip.inicioMs = inicioMs;
        clip.pedidoEm = System.currentTimeMillis();
        garantirArquivo(src, new Runnable() {
            public void run() {
                if (!clip.quer || clip.token != meuToken) return;
                if (System.currentTimeMillis() - clip.pedidoEm > 4000) return;
                try {
                    if (clip.mp == null) {
                        MediaPlayer mp = new MediaPlayer();
                        mp.setAudioStreamType(AudioManager.STREAM_MUSIC);
                        mp.setDataSource(arquivoLocal(src).getPath());
                        mp.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                            public boolean onError(MediaPlayer m, int what, int extra) { return true; }
                        });
                        mp.prepare();
                        mp.setVolume(1f, 1f);
                        mp.setOnSeekCompleteListener(new MediaPlayer.OnSeekCompleteListener() {
                            public void onSeekComplete(MediaPlayer m) {
                                if (clip.quer) {
                                    try { m.start(); } catch (Throwable t) { }
                                }
                            }
                        });
                        clip.mp = mp;
                    }
                    clip.mp.seekTo(clip.inicioMs);
                } catch (Throwable t) {
                    try { if (clip.mp != null) clip.mp.release(); } catch (Throwable t2) { }
                    clip.mp = null;
                }
            }
        });
    }

    private void parar(int id) {
        Clip clip = clips.get(id);
        if (clip == null) return;
        clip.quer = false;
        clip.token++;
        try { if (clip.mp != null && clip.mp.isPlaying()) clip.mp.pause(); } catch (Throwable t) { }
    }

    // ---------- tela ----------

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        try { getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); } catch (Throwable t) { }
        try {
            setVolumeControlStream(AudioManager.STREAM_MUSIC);
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            am.setStreamMute(AudioManager.STREAM_MUSIC, false);
            am.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        } catch (Throwable t) { }
        raiz = new FrameLayout(this);
        raiz.setBackgroundColor(Color.rgb(6, 40, 19));
        setContentView(raiz);
        aviso = new TextView(this);
        aviso.setTextColor(Color.WHITE);
        aviso.setTextSize(24);
        aviso.setGravity(Gravity.CENTER);
        aviso.setText("Carregando Multiplix TV...");
        raiz.addView(aviso, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
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
            web.setBackgroundColor(Color.rgb(6, 40, 19));
            try { CookieManager.getInstance().setAcceptCookie(true); } catch (Throwable t) { }
            try { web.addJavascriptInterface(new Ponte(), "BingoNative"); } catch (Throwable t) { log("ponte: " + t); }
            web.setWebChromeClient(new WebChromeClient());
            web.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageStarted(WebView v, String u, Bitmap icon) {
                    if (u != null && u.startsWith("file:")) return;
                    try { v.evaluateJavascript(SHIM, null); } catch (Throwable t) { }
                }

                @Override
                public void onPageFinished(WebView v, String u) {
                    aviso.setVisibility(View.GONE);
                    try { CookieManager.getInstance().flush(); } catch (Throwable t) { }
                    if (u != null && !u.startsWith("file:")) {
                        try { v.evaluateJavascript(SHIM, null); } catch (Throwable t) { }
                    }
                }

                // sem internet / servidor fora do ar (só a página principal chega aqui)
                @SuppressWarnings("deprecation")
                @Override
                public void onReceivedError(WebView v, int code, String desc, String u) {
                    if (u != null && !u.startsWith("file:")) abrirDemo();
                }

                // página do Multiplix ainda não existe no servidor (ex.: 404)
                @Override
                public void onReceivedHttpError(WebView v, WebResourceRequest req, WebResourceResponse resp) {
                    if (req.isForMainFrame() && resp.getStatusCode() >= 400
                            && !"file".equals(req.getUrl().getScheme())) {
                        abrirDemo();
                    }
                }

                @Override
                public void onReceivedSslError(WebView v, android.webkit.SslErrorHandler h, android.net.http.SslError e) {
                    String host = "";
                    try { host = android.net.Uri.parse(e.getUrl()).getHost(); } catch (Throwable t) { }
                    if (HOST.equals(host)) {
                        h.proceed();
                        return;
                    }
                    h.cancel();
                }
            });
            raiz.addView(web, 0, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            carregarServidor();
        } catch (Throwable t) {
            aviso.setVisibility(View.VISIBLE);
            aviso.setText("Esta TV nao tem o navegador interno (WebView) funcionando.\nAtualize o 'Android System WebView' ou o Chrome na loja da TV.");
        }
    }

    private void carregarServidor() {
        handler.removeCallbacks(tentarServidor);
        emDemo = false;
        if (web != null) web.loadUrl(URL_TV);
    }

    private void abrirDemo() {
        handler.post(new Runnable() {
            public void run() {
                if (emDemo || web == null) return;
                emDemo = true;
                web.loadUrl(DEMO);
                handler.removeCallbacks(tentarServidor);
                handler.postDelayed(tentarServidor, TENTAR_DE_NOVO_MS);
            }
        });
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && !emDemo && web != null && web.canGoBack()) {
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
        for (Clip c : clips.values()) {
            try { if (c.mp != null) c.mp.release(); } catch (Throwable t) { }
        }
        super.onDestroy();
    }
}
