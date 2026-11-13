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
    private TextView escolhida;
    private LinearLayout impressoras;
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
        box.addView(texto("Toque no nome da impressora da POS (ex.: Bluetooth Printer). Se não aparecer, toque em Abrir Bluetooth, pareie a impressora, volte e toque em Procurar."));
        escolhida = texto("");
        escolhida.setTextSize(17);
        box.addView(escolhida);
        impressoras = new LinearLayout(this);
        impressoras.setOrientation(LinearLayout.VERTICAL);
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
        box.addView(texto("Se o QR não sair, troque o tipo e teste de novo."));
        RadioGroup modo = new RadioGroup(this);
        String[] modos = {"Tudo em imagem A", "Tudo em imagem B", "Texto + QR em imagem A (recomendado)", "Texto + QR em imagem B"};
        for (int i = 0; i < modos.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setText(modos[i]);
            rb.setId(30000 + i);
            modo.addView(rb);
        }
        modo.check(30000 + Prefs.modo(this));
        modo.setOnCheckedChangeListener((g, id) -> Prefs.setModo(this, id - 30000));
        box.addView(modo);

        box.addView(texto("Largura (só para \"Tudo em imagem\"):"));
        RadioGroup rg = new RadioGroup(this);
        for (int px : LARGURAS) {
            RadioButton rb = new RadioButton(this);
            rb.setText(px + " px" + (px == 384 ? " (padrão, papel 58 mm)" : " (papel 80 mm)"));
            rb.setId(px);
            rg.addView(rb);
        }
        rg.check(Prefs.largura(this) == 576 ? 576 : 384);
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
            av.addView(rb);
        }
        av.check(10000 + Prefs.avanco(this));
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
            hosts.addView(rb);
        }
        hosts.check(Prefs.host(this).equals(Prefs.HOST_RESERVA) ? 20001 : 20000);
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

    @Override
    protected void onResume() {
        super.onResume();
        listar();
    }

    private void listar() {
        impressoras.removeAllViews();
        final List<BluetoothDevice> lista = PrinterA930.pareadas();
        String salvo = Prefs.bt(this);
        String nomeSalvo = null;
        if (lista.isEmpty()) {
            impressoras.addView(texto("(nenhuma impressora pareada — toque em Abrir Bluetooth)"));
        }
        for (final BluetoothDevice d : lista) {
            Button b = new Button(this);
            final String nome = PrinterA930.nome(d);
            boolean marcada = d.getAddress().equalsIgnoreCase(salvo);
            if (marcada) nomeSalvo = nome;
            b.setText((marcada ? "✔ " : "") + nome + "  (" + d.getAddress() + ")");
            b.setOnClickListener(v -> {
                Prefs.setBt(this, d.getAddress());
                listar();
            });
            impressoras.addView(b);
        }
        escolhida.setText(nomeSalvo != null
                ? "Impressora escolhida: " + nomeSalvo
                : "Nenhuma escolhida (o app usa a que tiver \"Printer\" no nome).");
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
