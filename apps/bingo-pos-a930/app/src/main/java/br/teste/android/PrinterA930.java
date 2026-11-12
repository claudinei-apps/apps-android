package br.teste.android;

import android.content.Context;
import android.graphics.Bitmap;

import java.io.File;
import java.io.FileOutputStream;

import br.com.uol.pagseguro.plugpagservice.wrapper.PlugPag;
import br.com.uol.pagseguro.plugpagservice.wrapper.PlugPagPrintResult;
import br.com.uol.pagseguro.plugpagservice.wrapper.PlugPagPrinterData;

/** Envia uma imagem para a impressora da Moderninha pelo serviço oficial da PagBank. */
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

    private static PlugPag plugPag;

    private PrinterA930() {}

    private static File folder(Context c) {
        File dir = c.getExternalFilesDir(null);
        if (dir == null) dir = c.getFilesDir();
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    /** Bloqueia até terminar: chamar fora da tela principal. */
    public static synchronized Result print(Context context, Bitmap bmp) {
        File file = new File(folder(context), "comprovante.png");
        try {
            FileOutputStream out = new FileOutputStream(file);
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.flush();
            out.close();
            file.setReadable(true, false);
        } catch (Exception e) {
            return new Result(false, "Não consegui preparar a imagem: " + e.getMessage(), "FILE");
        }
        try {
            if (plugPag == null) plugPag = new PlugPag(context.getApplicationContext());
            PlugPagPrinterData data = new PlugPagPrinterData(file.getAbsolutePath(), 4, Prefs.avanco(context));
            PlugPagPrintResult r = plugPag.printFromFile(data);
            if (r != null && r.getResult() == PlugPag.RET_OK) {
                return new Result(true, "Impresso.", "PRINTED");
            }
            String msg = r == null ? "sem resposta" : (r.getMessage() + " (" + r.getErrorCode() + ")");
            return new Result(false, "A impressora recusou: " + msg, "PRINTER_ERROR");
        } catch (Throwable e) {
            plugPag = null;
            return new Result(false,
                    "Impressora da Moderninha indisponível: " + e.getClass().getSimpleName()
                            + (e.getMessage() == null ? "" : " - " + e.getMessage()),
                    "PRINTER_UNAVAILABLE");
        }
    }
}
