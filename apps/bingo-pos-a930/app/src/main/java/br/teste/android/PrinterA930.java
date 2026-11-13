package br.teste.android;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Impressora interna da PAX A930 exposta como impressora Bluetooth ("Bluetooth Printer",
 * app com.pax.paxbtprinter). Comandos ESC/POS.
 * Testado: texto OK; QR próprio da impressora não sai; imagens ainda sem sucesso.
 * O botão de teste imprime 6 variantes de QR, cada uma em impressão separada e numerada,
 * para descobrir qual a impressora aceita.
 */
public final class PrinterA930 {

    public static final class Result {
        public final boolean ok;
        public final String message;
        public final String code;

        Result(boolean ok, String message, String code) {
            this.ok = ok;
            this.message = message;
            this.code = code;
        }
    }

    private static final UUID SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final int COLUNAS = 32;
    private static final ExecutorService trabalho = Executors.newCachedThreadPool();

    private PrinterA930() {}

    // ---------- escolha da impressora ----------

    public static List<BluetoothDevice> pareadas() {
        List<BluetoothDevice> out = new ArrayList<>();
        try {
            BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter();
            if (a == null) return out;
            Set<BluetoothDevice> s = a.getBondedDevices();
            if (s != null) out.addAll(s);
        } catch (Throwable ignored) {
        }
        return out;
    }

    public static String nome(BluetoothDevice d) {
        try {
            String n = d.getName();
            return n == null ? d.getAddress() : n;
        } catch (Throwable e) {
            return d.getAddress();
        }
    }

    private static boolean pareceImpressora(BluetoothDevice d) {
        String n = nome(d).toLowerCase();
        return n.contains("print") || n.contains("pax") || n.contains("inner") || n.contains("pos")
                || n.contains("impress") || n.contains("pt-") || n.contains("mpt");
    }

    private static BluetoothDevice escolher(Context ctx) throws Exception {
        BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter();
        if (a == null) throw new Exception("esta POS não tem Bluetooth");
        if (!a.isEnabled()) {
            try { a.enable(); } catch (Throwable ignored) {}
            for (int i = 0; i < 30 && !a.isEnabled(); i++) Thread.sleep(200);
            if (!a.isEnabled()) throw new Exception("ligue o Bluetooth da POS");
        }
        List<BluetoothDevice> lista = pareadas();
        String salvo = Prefs.bt(ctx);
        for (BluetoothDevice d : lista) if (d.getAddress().equalsIgnoreCase(salvo)) return d;
        for (BluetoothDevice d : lista) if (pareceImpressora(d)) return d;
        if (lista.size() == 1) return lista.get(0);
        throw new Exception(lista.isEmpty()
                ? "nenhuma impressora pareada. Em Configurar impressora, toque em 'Abrir Bluetooth' e pareie a impressora da POS"
                : "escolha a impressora em Configurar impressora");
    }

    private static BluetoothSocket conectar(BluetoothDevice d) throws Exception {
        try { BluetoothAdapter.getDefaultAdapter().cancelDiscovery(); } catch (Throwable ignored) {}
        Exception ultimo = null;
        try {
            BluetoothSocket s = d.createRfcommSocketToServiceRecord(SPP);
            s.connect();
            return s;
        } catch (Exception e) { ultimo = e; }
        try {
            BluetoothSocket s = d.createInsecureRfcommSocketToServiceRecord(SPP);
            s.connect();
            return s;
        } catch (Exception e) { ultimo = e; }
        try {
            Method m = d.getClass().getMethod("createRfcommSocket", int.class);
            BluetoothSocket s = (BluetoothSocket) m.invoke(d, 1);
            s.connect();
            return s;
        } catch (Exception e) { ultimo = e; }
        throw new Exception("não conectou na impressora " + nome(d) + ": " + (ultimo == null ? "" : ultimo.getMessage()));
    }

    // ---------- montagem ESC/POS ----------

    private static final byte[] INIT = {0x1B, 0x40};
    private static final byte[] CENTRO = {0x1B, 0x61, 1};
    private static final byte[] ESQUERDA = {0x1B, 0x61, 0};
    private static final byte[] NEGRITO = {0x1B, 0x45, 1};
    private static final byte[] NORMAL = {0x1B, 0x45, 0, 0x1D, 0x21, 0};
    private static final byte[] DUPLO = {0x1D, 0x21, 0x11};

    private static boolean preto(int c) {
        if ((c >>> 24) < 128) return false;
        int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
        return !(r > 160 && g > 160 && b > 160);
    }

    /** GS v 0 em blocos de até "bloco" linhas, cada bloco seguido de quebra de linha. */
    private static void rasterGsv0(ByteArrayOutputStream o, Bitmap bmp, int bloco) {
        int w = bmp.getWidth(), h = bmp.getHeight();
        int bpr = (w + 7) / 8;
        int[] px = new int[w];
        for (int y0 = 0; y0 < h; y0 += bloco) {
            int bh = Math.min(bloco, h - y0);
            o.write(0x1D); o.write(0x76); o.write(0x30); o.write(0);
            o.write(bpr & 0xFF); o.write((bpr >> 8) & 0xFF);
            o.write(bh & 0xFF); o.write((bh >> 8) & 0xFF);
            for (int y = y0; y < y0 + bh; y++) {
                bmp.getPixels(px, 0, w, 0, y, w, 1);
                for (int bx = 0; bx < bpr; bx++) {
                    int b = 0;
                    for (int k = 0; k < 8; k++) {
                        int x = bx * 8 + k;
                        if (x < w && preto(px[x])) b |= (0x80 >> k);
                    }
                    o.write(b);
                }
            }
            o.write('\n');
        }
    }

    /** ESC * m: m=33 (24 pontos, 3 bytes por coluna) ou m=0/1 (8 pontos, 1 byte por coluna). */
    private static void rasterEscStar(ByteArrayOutputStream o, Bitmap bmp, int m) {
        int faixa = (m >= 32) ? 24 : 8;
        int bytesCol = faixa / 8;
        int w = bmp.getWidth(), h = bmp.getHeight();
        int[] px = new int[w * faixa];
        o.write(0x1B); o.write(0x33); o.write(faixa);
        for (int y0 = 0; y0 < h; y0 += faixa) {
            int bh = Math.min(faixa, h - y0);
            java.util.Arrays.fill(px, 0xFFFFFFFF);
            bmp.getPixels(px, 0, w, 0, y0, w, bh);
            o.write(0x1B); o.write(0x2A); o.write(m);
            o.write(w & 0xFF); o.write((w >> 8) & 0xFF);
            for (int x = 0; x < w; x++) {
                for (int k = 0; k < bytesCol; k++) {
                    int b = 0;
                    for (int bit = 0; bit < 8; bit++) {
                        int yy = k * 8 + bit;
                        if (yy < bh && preto(px[yy * w + x])) b |= (0x80 >> bit);
                    }
                    o.write(b);
                }
            }
            o.write('\n');
        }
        o.write(0x1B); o.write(0x32);
    }

    private static Bitmap qrBitmap(String url, int lado) throws Exception {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.MARGIN, 2);
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.L);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        BitMatrix m = new QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, lado, lado, hints);
        Bitmap b = Bitmap.createBitmap(m.getWidth(), m.getHeight(), Bitmap.Config.ARGB_8888);
        b.eraseColor(Color.WHITE);
        for (int y = 0; y < m.getHeight(); y++)
            for (int x = 0; x < m.getWidth(); x++)
                if (m.get(x, y)) b.setPixel(x, y, Color.BLACK);
        return b;
    }

    private static void qrNativo(ByteArrayOutputStream o, String url, int modulo) {
        byte[] d = url.getBytes(Charset.forName("UTF-8"));
        int len = d.length + 3;
        byte[][] cmds = {
                {0x1D, 0x28, 0x6B, 4, 0, 0x31, 0x41, 0x32, 0},
                {0x1D, 0x28, 0x6B, 3, 0, 0x31, 0x43, (byte) modulo},
                {0x1D, 0x28, 0x6B, 3, 0, 0x31, 0x45, 0x31},
        };
        for (byte[] c : cmds) o.write(c, 0, c.length);
        o.write(0x1D); o.write(0x28); o.write(0x6B); o.write(len & 0xFF); o.write((len >> 8) & 0xFF);
        o.write(0x31); o.write(0x50); o.write(0x30);
        o.write(d, 0, d.length);
        byte[] p = {0x1D, 0x28, 0x6B, 3, 0, 0x31, 0x51, 0x30};
        o.write(p, 0, p.length);
        o.write('\n');
    }

    /** QR usado no comprovante (variante 1 por enquanto: 160 px, GS v 0 em blocos de 24 linhas). */
    private static void qrComprovante(ByteArrayOutputStream o, String url) {
        try {
            Bitmap b = qrBitmap(url, 160);
            w(o, CENTRO);
            rasterGsv0(o, b, 24);
            w(o, ESQUERDA);
            b.recycle();
        } catch (Exception e) {
            txt(o, url);
        }
    }

    private static String ascii(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.replaceAll("[^\\x20-\\x7E\\n]", "");
    }

    private static void txt(ByteArrayOutputStream o, String s) {
        byte[] b = (ascii(s) + "\n").getBytes(Charset.forName("US-ASCII"));
        o.write(b, 0, b.length);
    }

    private static void par(ByteArrayOutputStream o, String a, String b) {
        a = ascii(a); b = ascii(b);
        int sp = Math.max(1, COLUNAS - a.length() - b.length());
        StringBuilder sb = new StringBuilder(a);
        for (int i = 0; i < sp; i++) sb.append(' ');
        txt(o, sb.append(b).toString());
    }

    private static void w(ByteArrayOutputStream o, byte[] b) { o.write(b, 0, b.length); }

    private static String dois(String v) {
        String t = v == null ? "" : v.trim();
        if (t.startsWith("C")) t = t.substring(1);
        return t.length() == 1 ? "0" + t : t;
    }

    private static void fim(ByteArrayOutputStream o, Context ctx) {
        int linhas = Math.max(4, Prefs.avanco(ctx) / 20);
        for (int i = 0; i < linhas; i++) o.write('\n');
    }

    private static void textoTicket(ByteArrayOutputStream o, JSONObject t) {
        boolean resumo = "BINGO_SUMMARY".equals(t.optString("receiptKind"));
        String linha = "--------------------------------";
        w(o, CENTRO); w(o, NEGRITO);
        txt(o, t.optString("tenant"));
        w(o, NORMAL);
        txt(o, t.optString("salePoint", t.optString("point")));
        txt(o, linha);
        w(o, DUPLO); txt(o, "BINGO 90"); w(o, NORMAL);
        w(o, NEGRITO);
        txt(o, resumo ? t.optInt("quantity") + " CARTELAS - " + t.optString("ticketId") : "CARTELA " + t.optString("ticketId"));
        w(o, NORMAL);
        w(o, ESQUERDA);
        par(o, "SORTEIO #" + t.optString("round"), t.optString("drawDate"));
        txt(o, linha);
        JSONArray arr = resumo ? t.optJSONArray("cardIds") : t.optJSONArray("numbers");
        if (arr != null) {
            int cols = resumo ? 3 : 5;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < arr.length(); i++) {
                sb.append(dois(arr.optString(i)));
                if ((i + 1) % cols == 0 || i == arr.length() - 1) {
                    w(o, CENTRO); w(o, resumo ? NEGRITO : DUPLO);
                    txt(o, sb.toString());
                    sb.setLength(0);
                } else sb.append(resumo ? "   " : " ");
            }
            w(o, NORMAL); w(o, ESQUERDA);
        }
        txt(o, linha);
        w(o, NEGRITO); par(o, "VALOR", t.optString("bet")); w(o, NORMAL);
        par(o, "PAGAMENTO", t.optString("paymentMethod"));
        par(o, "SITUACAO", t.optString("statusText"));
        if (t.optBoolean("isReprint")) {
            w(o, CENTRO); w(o, NEGRITO);
            txt(o, "REIMPRESSAO - COPIA " + t.optInt("copyNumber"));
            w(o, NORMAL);
            txt(o, t.optString("printLabel"));
            w(o, ESQUERDA);
        }
        JSONArray pr = t.optJSONArray("paytablePrizes");
        if (pr != null && pr.length() > 0) {
            txt(o, linha);
            w(o, CENTRO); txt(o, t.optString("paytableTitle", "PREMIOS")); w(o, ESQUERDA);
            for (int i = 0; i < pr.length(); i++) {
                JSONObject p = pr.optJSONObject(i);
                if (p == null) continue;
                int m = p.optInt("matches");
                par(o, m == 4 ? "QUADRA" : m == 5 ? "QUINA" : m == 15 ? "CARTELA CHEIA" : m + " ACERTOS", p.optString("amount"));
            }
        }
        String url = t.optString("validationUrl");
        if (!url.isEmpty()) {
            txt(o, linha);
            w(o, CENTRO); w(o, NEGRITO); txt(o, "CONFIRA SEU PREMIO"); w(o, NORMAL); w(o, ESQUERDA);
            txt(o, "");
        }
    }

    /** Envia um trabalho numa conexão própria. "lento" = pedaços pequenos com pausa maior. */
    private static void enviar(Context ctx, byte[] dados, boolean lento) throws Exception {
        BluetoothDevice d = escolher(ctx);
        BluetoothSocket s = conectar(d);
        try {
            OutputStream out = s.getOutputStream();
            int pedaco = lento ? 128 : 512;
            int pausa = lento ? 40 : 15;
            for (int i = 0; i < dados.length; i += pedaco) {
                out.write(dados, i, Math.min(pedaco, dados.length - i));
                out.flush();
                Thread.sleep(pausa);
            }
            Thread.sleep(Math.min(9000, 2000 + dados.length / 8));
        } finally {
            try { s.close(); } catch (Throwable ignored) {}
        }
    }

    private static Result rodar(final Callable<Result> job, int limite) {
        Future<Result> f = trabalho.submit(job);
        try {
            return f.get(limite, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            f.cancel(true);
            return new Result(false, "A impressora não respondeu a tempo.", "PRINTER_TIMEOUT");
        } catch (Throwable e) {
            Throwable c = e;
            while (c.getCause() != null && c.getCause() != c) c = c.getCause();
            return new Result(false, "Não imprimiu: "
                    + (c.getMessage() == null ? c.getClass().getSimpleName() : c.getMessage()), "PRINTER_UNAVAILABLE");
        }
    }

    /**
     * Comprovante: texto (que já funciona) numa impressão e o QR numa segunda impressão separada,
     * para que um QR recusado nunca apague o comprovante.
     */
    public static synchronized Result printTicket(final Context ctx, final JSONObject t) {
        return rodar(() -> {
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            w(o, INIT);
            textoTicket(o, t);
            String url = t.optString("validationUrl");
            if (url.isEmpty()) fim(o, ctx);
            enviar(ctx, o.toByteArray(), false);
            if (!url.isEmpty()) {
                ByteArrayOutputStream q = new ByteArrayOutputStream();
                qrComprovante(q, url);
                fim(q, ctx);
                try { enviar(ctx, q.toByteArray(), true); } catch (Exception ignored) {}
            }
            return new Result(true, "Impresso.", "PRINTED");
        }, 90);
    }

    /** Teste completo: 6 variantes de QR, cada uma numa impressão separada e numerada. */
    public static synchronized Result printTest(final Context ctx, final String url) {
        return rodar(() -> {
            String[] nomes = {
                    "1: imagem 160px, blocos de 24 linhas, lento",
                    "2: imagem 250px, bloco unico, lento",
                    "3: imagem 160px, ESC * 8 pontos, lento",
                    "4: imagem 160px, ESC * 24 pontos, lento",
                    "5: imagem 96px, blocos de 8 linhas, lento",
                    "6: QR da impressora, modulo 3 (igual DG)",
            };
            StringBuilder falhas = new StringBuilder();
            for (int v = 0; v < nomes.length; v++) {
                ByteArrayOutputStream o = new ByteArrayOutputStream();
                w(o, INIT);
                txt(o, "=== TESTE QR " + nomes[v] + " ===");
                w(o, CENTRO);
                try {
                    switch (v) {
                        case 0: { Bitmap b = qrBitmap(url, 160); rasterGsv0(o, b, 24); b.recycle(); break; }
                        case 1: { Bitmap b = qrBitmap(url, 250); rasterGsv0(o, b, 250); b.recycle(); break; }
                        case 2: { Bitmap b = qrBitmap(url, 160); rasterEscStar(o, b, 1); b.recycle(); break; }
                        case 3: { Bitmap b = qrBitmap(url, 160); rasterEscStar(o, b, 33); b.recycle(); break; }
                        case 4: { Bitmap b = qrBitmap(url, 96); rasterGsv0(o, b, 8); b.recycle(); break; }
                        default: qrNativo(o, url, 3);
                    }
                } catch (Exception e) {
                    txt(o, "erro: " + e.getMessage());
                }
                w(o, ESQUERDA);
                txt(o, "=== FIM DO TESTE " + (v + 1) + " ===");
                for (int i = 0; i < 4; i++) o.write('\n');
                try {
                    enviar(ctx, o.toByteArray(), true);
                } catch (Exception e) {
                    falhas.append(" ").append(v + 1);
                }
                Thread.sleep(2500);
            }
            return new Result(true, "6 testes enviados" + (falhas.length() > 0 ? " (falha ao enviar:" + falhas + ")" : "")
                    + ". Veja quais números saíram com QR.", "PRINTED");
        }, 170);
    }
}
