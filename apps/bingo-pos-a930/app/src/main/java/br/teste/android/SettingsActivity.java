package br.teste.android;

import android.app.Activity;
import android.content.pm.PackageInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

/** Tela "Configurar impressora": largura do papel, endereço e teste com QR. */
public class SettingsActivity extends Activity {

    private static final int[] LARGURAS = {384, 576, 1155};
    private TextView status;
    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad, pad, pad);

        box.addView(titulo("Impressora da POS"));
        box.addView(texto("Largura da imagem (se o comprovante sair pequeno ou cortado, troque e teste):"));
        RadioGroup rg = new RadioGroup(this);
        int atual = Prefs.largura(this);
        for (int px : LARGURAS) {
            RadioButton rb = new RadioButton(this);
            rb.setText(px + " px" + (px == 384 ? " (padrão)" : ""));
            rb.setId(px);
            rb.setChecked(px == atual);
            rg.addView(rb);
        }
        rg.setOnCheckedChangeListener((g, id) -> Prefs.setLargura(this, id));
        box.addView(rg);

        box.addView(texto("Espaço de papel no final:"));
        RadioGroup av = new RadioGroup(this);
        int[] avancos = {50, 100, 160};
        String[] nomes = {"Curto", "Médio (padrão)", "Longo"};
        for (int i = 0; i < avancos.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setText(nomes[i]);
            rb.setId(10000 + avancos[i]);
            rb.setChecked(avancos[i] == Prefs.avanco(this));
            av.addView(rb);
        }
        av.setOnCheckedChangeListener((g, id) -> Prefs.setAvanco(this, id - 10000));
        box.addView(av);

        Button teste = new Button(this);
        teste.setText("Testar impressão com QR Code");
        teste.setOnClickListener(v -> testar());
        box.addView(teste);

        status = texto("");
        box.addView(status);

        box.addView(titulo("Endereço do Bingo"));
        RadioGroup hosts = new RadioGroup(this);
        String[] lista = {Prefs.HOST_PRINCIPAL, Prefs.HOST_RESERVA};
        for (int i = 0; i < lista.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setText(lista[i].replace("https://", ""));
            rb.setId(20000 + i);
            rb.setChecked(lista[i].equals(Prefs.host(this)));
            hosts.addView(rb);
        }
        hosts.setOnCheckedChangeListener((g, id) -> Prefs.setHost(this, lista[id - 20000]));
        box.addView(hosts);

        Button voltar = new Button(this);
        voltar.setText("Voltar para a POS");
        voltar.setOnClickListener(v -> finish());
        box.addView(voltar);

        box.addView(titulo("Informações técnicas"));
        TextView info = texto(diagnostico());
        info.setTextSize(13);
        box.addView(info);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Color.WHITE);
        sv.addView(box);
        setContentView(sv);
    }

    /** Versão do app, do Android e dos serviços de impressão instalados. */
    private String diagnostico() {
        StringBuilder sb = new StringBuilder();
        try {
            sb.append("App: ").append(getPackageManager().getPackageInfo(getPackageName(), 0).versionName).append('\n');
        } catch (Exception ignored) {
        }
        sb.append("Android: ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        sb.append("Aparelho: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n');
        sb.append("Serviços de impressão:\n");
        int n = 0;
        try {
            for (PackageInfo p : getPackageManager().getInstalledPackages(0)) {
                String name = p.packageName.toLowerCase();
                if (name.contains("xcheng") || name.contains("print") || name.contains("pagseguro")
                        || name.contains("plugpag") || name.contains("pax")) {
                    sb.append("- ").append(p.packageName).append(" ").append(p.versionName).append('\n');
                    n++;
                }
            }
        } catch (Exception e) {
            sb.append("(não consegui listar)\n");
        }
        if (n == 0) sb.append("(nenhum encontrado)\n");
        return sb.toString();
    }

    private TextView titulo(String t) {
        TextView v = texto(t);
        v.setTextSize(20);
        v.setPadding(0, 24, 0, 8);
        return v;
    }

    private TextView texto(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextColor(Color.BLACK);
        v.setTextSize(16);
        v.setGravity(Gravity.START);
        return v;
    }

    private void testar() {
        status.setText("Imprimindo teste... (aguarde)");
        final int largura = Prefs.largura(this);
        final String url = Prefs.host(this) + "/onlinegames/bingo-pix/ticket.php";
        new Thread(() -> {
            String msg;
            try {
                Bitmap bmp = ReceiptRenderer.test(largura, url);
                PrinterA930.Result r = PrinterA930.print(SettingsActivity.this, bmp);
                msg = r.ok ? "Teste enviado à impressora. Confira se o QR Code saiu nítido." : r.message;
                if (r.ok) msg += "\nFirmware da impressora: " + PrinterA930.firmware(SettingsActivity.this);
            } catch (Throwable e) {
                msg = "Erro: " + e.getMessage();
            }
            final String fim = msg;
            ui.post(() -> status.setText(fim));
        }).start();
    }
}
