package br.teste.android;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Desenha o bilhete do Multiplix como imagem (preto e branco), com QR Code opcional. */
public final class ReceiptRenderer {

    private interface Step { int draw(Canvas c, int y); }

    private final int w;
    private final float s; // escala em relação a 384 px
    private final List<Step> steps = new ArrayList<>();
    private final List<Integer> heights = new ArrayList<>();

    private ReceiptRenderer(int width) {
        this.w = width;
        this.s = width / 384f;
    }

    private Paint paint(float size, boolean bold) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.BLACK);
        p.setTextSize(size * s);
        p.setTypeface(Typeface.create(Typeface.MONOSPACE, bold ? Typeface.BOLD : Typeface.NORMAL));
        return p;
    }

    private List<String> wrap(String text, Paint p, int max) {
        List<String> out = new ArrayList<>();
        if (text == null) return out;
        for (String para : text.split("\n")) {
            String[] words = para.split(" ");
            StringBuilder line = new StringBuilder();
            for (String word : words) {
                String test = line.length() == 0 ? word : line + " " + word;
                if (p.measureText(test) <= max || line.length() == 0) {
                    line.setLength(0);
                    line.append(test);
                } else {
                    out.add(line.toString());
                    line.setLength(0);
                    line.append(word);
                }
            }
            out.add(line.toString());
        }
        return out;
    }

    private void text(final String value, float size, final boolean bold, final boolean center) {
        if (value == null || value.trim().isEmpty()) return;
        final Paint p = paint(size, bold);
        final int margin = Math.round(4 * s);
        final List<String> lines = wrap(value, p, w - margin * 2);
        final int lineH = Math.round(p.getFontSpacing());
        heights.add(lineH * lines.size() + Math.round(2 * s));
        steps.add((c, y) -> {
            int yy = y;
            for (String line : lines) {
                float x = center ? (w - p.measureText(line)) / 2f : margin;
                c.drawText(line, x, yy - p.ascent(), p);
                yy += lineH;
            }
            return yy + Math.round(2 * s);
        });
    }

    private void row(final String left, final String right, float size, final boolean bold) {
        final Paint p = paint(size, bold);
        final int lineH = Math.round(p.getFontSpacing());
        final int margin = Math.round(4 * s);
        heights.add(lineH);
        steps.add((c, y) -> {
            c.drawText(left, margin, y - p.ascent(), p);
            c.drawText(right, w - margin - p.measureText(right), y - p.ascent(), p);
            return y + lineH;
        });
    }

    private void line() {
        final int h = Math.round(10 * s);
        heights.add(h);
        steps.add((c, y) -> {
            Paint p = paint(10, false);
            p.setStrokeWidth(Math.max(2, 2 * s));
            int mid = y + h / 2;
            for (int x = 0; x < w; x += Math.round(12 * s)) {
                c.drawLine(x, mid, x + Math.round(7 * s), mid, p);
            }
            return y + h;
        });
    }

    private void space(final int px) {
        final int h = Math.round(px * s);
        heights.add(h);
        steps.add((c, y) -> y + h);
    }

    private void qr(final String content) {
        if (content == null || content.isEmpty()) return;
        final int size = Math.round(w * 0.62f);
        final BitMatrix m;
        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.MARGIN, 1);
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            m = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints);
        } catch (Exception e) {
            text("Confira em: " + content, 14, false, true);
            return;
        }
        heights.add(size + Math.round(6 * s));
        steps.add((c, y) -> {
            Paint black = new Paint();
            black.setColor(Color.BLACK);
            int x0 = (w - size) / 2;
            for (int yy = 0; yy < m.getHeight(); yy++) {
                int start = -1;
                for (int xx = 0; xx <= m.getWidth(); xx++) {
                    boolean on = xx < m.getWidth() && m.get(xx, yy);
                    if (on && start < 0) start = xx;
                    if (!on && start >= 0) {
                        c.drawRect(x0 + start, y + yy, x0 + xx, y + yy + 1, black);
                        start = -1;
                    }
                }
            }
            return y + size + Math.round(6 * s);
        });
    }

    private Bitmap render() {
        int total = 0;
        for (int h : heights) total += h;
        Bitmap bmp = Bitmap.createBitmap(w, Math.max(total, 10), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(Color.WHITE);
        int y = 0;
        for (Step st : steps) y = st.draw(c, y);
        return bmp;
    }

    /** Bilhete a partir do JSON de impressão. */
    public static Bitmap ticket(JSONObject t, int width) {
        ReceiptRenderer r = new ReceiptRenderer(width);
        r.text(t.optString("tenant"), 22, true, true);
        r.text(t.optString("salePoint", t.optString("point")), 18, false, true);
        r.line();
        r.text("MULTIPLIX", 30, true, true);
        r.text("JOGO DE MILHAR", 16, false, true);
        r.text("BILHETE " + t.optString("ticketId"), 20, true, true);
        r.row("CONCURSO " + t.optString("round"), t.optString("drawDate"), 16, true);
        r.line();

        JSONArray bets = t.optJSONArray("bets");
        if (bets != null) {
            for (int i = 0; i < bets.length(); i++) {
                JSONObject b = bets.optJSONObject(i);
                if (b == null) continue;
                r.row(b.optString("type") + " " + b.optString("number"), b.optString("amount"), 20, true);
                r.row("  premio", b.optString("prize"), 16, false);
            }
        }
        r.line();
        r.row("TOTAL", t.optString("total"), 22, true);
        if (!t.optString("paymentMethod").isEmpty()) r.row("PAGAMENTO", t.optString("paymentMethod"), 15, false);
        if (!t.optString("statusText").isEmpty()) r.row("SITUACAO", t.optString("statusText"), 15, false);
        if (!t.optString("operator").isEmpty()) r.row("OPERADOR", t.optString("operator"), 15, false);
        if (t.optBoolean("isReprint")) {
            r.space(4);
            r.text("REIMPRESSAO - COPIA " + t.optInt("copyNumber"), 18, true, true);
            r.text(t.optString("printLabel"), 14, false, true);
        }

        JSONArray pt = t.optJSONArray("paytable");
        if (pt != null && pt.length() > 0) {
            r.line();
            r.text(t.optString("paytableTitle", "COTACAO POR R$ 1,00"), 16, true, true);
            for (int i = 0; i < pt.length(); i++) {
                JSONObject p = pt.optJSONObject(i);
                if (p == null) continue;
                r.row(p.optString("label"), p.optString("amount"), 16, false);
            }
        }
        if (t.optBoolean("demo")) {
            r.line();
            r.text("DEMONSTRACAO - SEM VALOR", 20, true, true);
        }

        String url = t.optString("validationUrl");
        if (!url.isEmpty()) {
            r.line();
            r.text("CONFIRA SEU PREMIO", 18, true, true);
            r.text("Aponte a camera do celular", 14, false, true);
            r.space(4);
            r.qr(url);
        }
        r.space(30);
        return r.render();
    }

    /** Folha de teste (botão Testar impressão). */
    public static Bitmap test(int width, String url) {
        ReceiptRenderer r = new ReceiptRenderer(width);
        r.text("TESTE DE IMPRESSAO", 24, true, true);
        r.text("Multiplix POS A930", 18, false, true);
        r.text("Largura: " + width + " px", 16, false, true);
        r.line();
        r.row("MILHAR 1899", "R$ 1,00", 20, true);
        r.row("  premio", "R$ 3.000,00", 16, false);
        r.line();
        r.text("O QR abaixo deve abrir o site", 14, false, true);
        r.qr(url);
        r.space(30);
        return r.render();
    }
}
