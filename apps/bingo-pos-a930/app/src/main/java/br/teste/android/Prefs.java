package br.teste.android;

import android.content.Context;
import android.content.SharedPreferences;

/** Configurações guardadas no aparelho. */
public final class Prefs {
    public static final String HOST_PRINCIPAL = "https://bingo90.apostabrasil.online";
    public static final String HOST_RESERVA = "https://bingo.turfkeno.online";
    public static final String CAMINHO_POS = "/onlinegames/bingo-pix/pos.php";

    /**
     * Modos: 0 = tudo imagem A (ESC *), 1 = tudo imagem B (GS v 0),
     * 2 = texto + QR em imagem A, 3 = texto + QR em imagem B.
     */
    public static final int MODO_IMAGEM_A = 0;
    public static final int MODO_IMAGEM_B = 1;
    public static final int MODO_TEXTO = 2;
    public static final int MODO_TEXTO_B = 3;

    private Prefs() {}

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("bingo_pos", Context.MODE_PRIVATE);
    }

    public static String host(Context c) { return sp(c).getString("host", HOST_PRINCIPAL); }
    public static void setHost(Context c, String v) { sp(c).edit().putString("host", v).apply(); }

    public static int largura(Context c) { return sp(c).getInt("largura", 384); }
    public static void setLargura(Context c, int v) { sp(c).edit().putInt("largura", v).apply(); }

    public static int avanco(Context c) { return sp(c).getInt("avanco", 100); }
    public static void setAvanco(Context c, int v) { sp(c).edit().putInt("avanco", v).apply(); }

    /** Endereço Bluetooth da impressora escolhida (vazio = escolher sozinho). */
    public static String bt(Context c) { return sp(c).getString("bt", ""); }
    public static void setBt(Context c, String v) { sp(c).edit().putString("bt", v).apply(); }

    public static int modo(Context c) { return sp(c).getInt("modo2", MODO_TEXTO); }
    public static void setModo(Context c, int v) { sp(c).edit().putInt("modo2", v).apply(); }

    /** 0 = Bluetooth; outro número = porta da rede interna da POS (127.0.0.1). */
    public static int porta(Context c) { return sp(c).getInt("porta", 0); }
    public static void setPorta(Context c, int v) { sp(c).edit().putInt("porta", v).apply(); }
}
