package br.teste.android;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/** Tela "Configurar impressora": escolher a impressora, modo, largura, teste e endereço. */
public class SettingsActivity extends Activity {

    private static final int[] LARGURAS = {384, 576};
    private TextView status;
    private RadioGroup impressoras;
    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= 31) {
            requestPermissions(new String[]{"android.permission.BLUETOOTH_CONNECT"}, 1);
        }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad, pad, pad);

        box.addView(titulo("1. Impressora"));
        box.addView(texto("Escolha a impressora da POS (normalmente o nome tem PAX ou Printer). Se não aparecer, toque em Abrir Bluetooth, ligue o Bluetooth e pareie a impressora; depois volte e toque em Procurar."));
        impressoras = new RadioGroup(this);
        box.addView(impressoras);
        LinearLayout botoes = new LinearLayout(this);
        Button procurar = new Button(this);
        procurar.setText("Procurar");
        procurar.setOnClickListener(v -> listar());
        Button abrirBt = new Button(this);
        abrirBt.setText("Abrir Bluetooth");
        abrirBt.setOnClickListener(v -> {
            try { startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); } catch (Exception ignored) {}
        });
        botoes.addView(procurar);
        botoes.addView(abrirBt);
        box.addView(botoes);

        box.addView(titulo("2. Tipo de impressão"));
        RadioGroup modo = new RadioGroup(this);
        String[] modos = {"Imagem (recomendado)", "Texto + QR da impressora (se a imagem não sair)"};
        for (int i = 0; i < modos.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setText(modos[i]);
            rb.setId(30000 + i);
            rb.setChecked(Prefs.modo(this) == i);
            modo.addView(rb);
        }
        modo.setOnCheckedChangeListener((g, id) -> Prefs.setModo(this, id - 30000));
        box.addView(modo);

        box.addView(texto("Largura (modo imagem):"));
        RadioGroup rg = new RadioGroup(this);
        for (int px : LARGURAS) {
            RadioButton rb = new RadioButton(this);
            rb.setText(px + " px" + (px == 384 ? " (padrão, papel 58 mm)" : " (papel 80 mm)"));
            rb.setId(px);
            rb.setChecked(px == Prefs.largura(this));
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

        box.addView(titulo("3. Teste"));
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
        listar();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (impressoras != null) listar();
    }

    private void listar() {
        impressoras.setOnCheckedChangeListener(null);
        impressoras.removeAllViews();
        final List<BluetoothDevice> lista = PrinterA930.pareadas();
        String salvo = Prefs.bt(this);
        if (lista.isEmpty()) {
            TextView v = texto("(nenhuma impressora pareada — toque em Abrir Bluetooth)");
            impressoras.addView(v);
            return;
        }
        for (int i = 0; i < lista.size(); i++) {
            BluetoothDevice d = lista.get(i);
            RadioButton rb = new RadioButton(this);
            rb.setText(PrinterA930.nome(d) + "  (" + d.getAddress() + ")");
            rb.setId(40000 + i);
            impressoras.addView(rb);
            if (d.getAddress().equalsIgnoreCase(salvo)) rb.setChecked(true);
        }
        impressoras.setOnCheckedChangeListener((g, id) -> {
            int i = id - 40000;
            if (i >= 0 && i < lista.size()) Prefs.setBt(this, lista.get(i).getAddress());
        });
    }

    private String diagnostico() {
        StringBuilder sb = new StringBuilder();
        try {
            PackageInfo p = getPackageManager().getPackageInfo(getPackageName(), 0);
            sb.append("App: ").append(p.versionName).append('\n');
        } catch (Exception ignored) {
        }
        sb.append("Android: ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        sb.append("Aparelho: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n');
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
        final String url = Prefs.host(this) + "/onlinegames/bingo-pix/ticket.php";
        new Thread(() -> {
            PrinterA930.Result r = PrinterA930.printTest(SettingsActivity.this, url);
            final String fim = r.ok ? r.message + " Confira se a folha saiu com o QR Code." : r.message;
            ui.post(() -> status.setText(fim));
        }).start();
    }
}
