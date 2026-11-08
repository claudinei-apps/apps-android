package br.teste.android;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.TextView;

// Tela inicial do modelo: troque pelo que o app precisa fazer.
public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView texto = new TextView(this);
        texto.setText("App novo, pronto para o primeiro build.");
        texto.setTextSize(22);
        texto.setTextColor(Color.BLACK);
        texto.setGravity(Gravity.CENTER);
        texto.setPadding(48, 48, 48, 48);
        setContentView(texto);
    }
}
