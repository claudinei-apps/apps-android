package br.teste.android;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView t = new TextView(this);
        t.setText("Teste do modulo do agente: build corrigido.");
        t.setTextSize(22);
        setContentView(t);
    }
}
