package br.teste.android;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Abre a POS do Bingo e oferece a ponte KenoPrinter, imprimindo com QR Code. */
public class MainActivity extends Activity {

    private WebView web;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService fila = Executors.newSingleThreadExecutor();
    private final Set<String> jaImpressos = new HashSet<>();
    private String hostCarregado;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = new WebView(this);
        setContentView(web);

        WebSettings ws = web.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setDatabaseEnabled(true);
        ws.setUserAgentString(ws.getUserAgentString() + " BingoPOS-A930/" + versao());
        CookieManager.getInstance().setAcceptCookie(true);

        web.addJavascriptInterface(new Ponte(), "KenoPrinter");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                return abrirFora(req.getUrl());
            }

            @SuppressWarnings("deprecation")
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return abrirFora(Uri.parse(url));
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                CookieManager.getInstance().flush();
            }
        });
        web.setDownloadListener((url, ua, cd, mime, len) -> abrirNoNavegador(Uri.parse(url)));

        carregar();
    }

    private void carregar() {
        hostCarregado = Prefs.host(this);
        web.loadUrl(hostCarregado + Prefs.CAMINHO_POS);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (hostCarregado != null && !hostCarregado.equals(Prefs.host(this))) carregar();
    }

    /** Fica dentro do app só nos endereços do Bingo; o resto abre no navegador. */
    private boolean abrirFora(Uri uri) {
        String host = uri.getHost() == null ? "" : uri.getHost();
        boolean nosso = host.equals(Uri.parse(Prefs.HOST_PRINCIPAL).getHost())
                || host.equals(Uri.parse(Prefs.HOST_RESERVA).getHost())
                || host.equals(Uri.parse(Prefs.host(this)).getHost());
        String path = uri.getPath() == null ? "" : uri.getPath();
        if (nosso && !path.endsWith(".apk")) return false;
        abrirNoNavegador(uri);
        return true;
    }

    private void abrirNoNavegador(Uri uri) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (Exception ignored) {
        }
    }

    private String versao() {
        try {
            PackageInfo p = getPackageManager().getPackageInfo(getPackageName(), 0);
            return p.versionName;
        } catch (Exception e) {
            return "2";
        }
    }

    private void evento(String requestId, String status, String message, String code) {
        try {
            JSONObject o = new JSONObject();
            o.put("printRequestId", requestId);
            o.put("status", status);
            o.put("message", message);
            o.put("code", code);
            final String js = "window.onKenoPrintEvent&&window.onKenoPrintEvent(" + o + ")";
            ui.post(() -> web.evaluateJavascript(js, null));
        } catch (Exception ignored) {
        }
    }

    private static String resposta(boolean ok, String code, String message) {
        try {
            JSONObject o = new JSONObject();
            o.put("ok", ok);
            o.put("code", code);
            o.put("message", message);
            return o.toString();
        } catch (Exception e) {
            return "{\"ok\":false}";
        }
    }

    /** Mesmos nomes da ponte usada pelo painel da POS (pos-ui.js). */
    private class Ponte {
        @JavascriptInterface
        public String getBingoReceiptVersion() {
            return "2";
        }

        @JavascriptInterface
        public String getBingoAppVersion() {
            return versao() + " A930-QR";
        }

        @JavascriptInterface
        public String printTicket(String json) {
            final JSONObject t;
            try {
                t = new JSONObject(json);
            } catch (Exception e) {
                return resposta(false, "INVALID", "Comprovante inválido.");
            }
            final String id = t.optString("printRequestId", String.valueOf(System.currentTimeMillis()));
            synchronized (jaImpressos) {
                if (jaImpressos.contains(id)) {
                    return resposta(false, "DUPLICATE_PRINT_REQUEST", "Este comprovante já foi enviado à impressora.");
                }
                jaImpressos.add(id);
            }
            evento(id, "queued", "Na fila da impressora...", "QUEUED");
            fila.execute(() -> {
                evento(id, "printing", "Imprimindo...", "PRINTING");
                PrinterA930.Result r;
                try {
                    r = PrinterA930.printTicket(MainActivity.this, t);
                } catch (Throwable e) {
                    r = new PrinterA930.Result(false, "Erro ao montar o comprovante: " + e.getMessage(), "RENDER");
                }
                if (!r.ok) {
                    synchronized (jaImpressos) { jaImpressos.remove(id); }
                }
                evento(id, r.ok ? "printed" : "failed", r.ok ? "Comprovante impresso." : r.message, r.code);
            });
            return resposta(true, "QUEUED", "Impressão enviada à fila da POS.");
        }

        @JavascriptInterface
        public void openPrinterManager() {
            ui.post(() -> startActivity(new Intent(MainActivity.this, SettingsActivity.class)));
        }

        @JavascriptInterface
        public String reloadWithoutCache() {
            ui.post(() -> {
                web.clearCache(true);
                web.reload();
            });
            return resposta(true, "RELOAD", "Atualizando...");
        }
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        fila.shutdown();
        super.onDestroy();
    }
}
