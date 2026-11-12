package br.teste.android;

import android.content.Context;
import android.content.SharedPreferences;

/** Configurações guardadas no aparelho (endereço e largura do papel). */
public final class Prefs {
    public static final String HOST_PRINCIPAL = "https://bingo90.apostabrasil.online";
    public static final String HOST_RESERVA = "https://bingo.turfkeno.online";
    public static final String CAMINHO_POS = "/onlinegames/bingo-pix/pos.php";

    private Prefs() {}

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("bingo_pos", Context.MODE_PRIVATE);
    }

    public static String host(Context c) {
        return sp(c).getString("host", HOST_PRINCIPAL);
    }

    public static void setHost(Context c, String host) {
        sp(c).edit().putString("host", host).apply();
    }

    /** Largura da imagem enviada à impressora, em pixels. */
    public static int largura(Context c) {
        return sp(c).getInt("largura", 384);
    }

    public static void setLargura(Context c, int px) {
        sp(c).edit().putInt("largura", px).apply();
    }

    /** Avanço de papel no final (passos da impressora). */
    public static int avanco(Context c) {
        return sp(c).getInt("avanco", 100);
    }

    public static void setAvanco(Context c, int passos) {
        sp(c).edit().putInt("avanco", passos).apply();
    }
}
