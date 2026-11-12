package br.teste.android;

import android.content.Context;
import android.graphics.Bitmap;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Constructor;

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

    /** Cria o PlugPag com o construtor que a versão do kit tiver (antiga ou nova). */
    private static PlugPag criar(Context ctx) throws Exception {
        try {
            Class<?> idCls = Class.forName("br.com.uol.pagseguro.plugpagservice.wrapper.PlugPagAppIdentification");
            Object id = idCls.getConstructor(String.class, String.class).newInstance("BingoPOS", "2.0");
            Constructor<PlugPag> c = PlugPag.class.getConstructor(Context.class, idCls);
            return c.newInstance(ctx, id);
        } catch (NoSuchMethodException | ClassNotFoundException e) {
            Constructor<PlugPag> c = PlugPag.class.getConstructor(Context.class);
            return c.newInstance(ctx);
        }
    }

    private static Throwable raiz(Throwable e) {
        while (e.getCause() != null && e.getCause() != e) e = e.getCause();
        return e;
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
            if (plugPag == null) plugPag = criar(context.getApplicationContext());
            PlugPagPrinterData data = new PlugPagPrinterData(file.getAbsolutePath(), 4, Prefs.avanco(context));
            PlugPagPrintResult r = plugPag.printFromFile(data);
            if (r != null && r.getResult() == PlugPag.RET_OK) {
                return new Result(true, "Impresso.", "PRINTED");
            }
            String msg = r == null ? "sem resposta" : (r.getMessage() + " (" + r.getErrorCode() + ")");
            return new Result(false, "A impressora recusou: " + msg, "PRINTER_ERROR");
        } catch (Throwable e) {
            plugPag = null;
            Throwable c = raiz(e);
            return new Result(false,
                    "Impressora da Moderninha indisponível: " + c.getClass().getSimpleName()
                            + (c.getMessage() == null ? "" : " - " + c.getMessage()),
                    "PRINTER_UNAVAILABLE");
        }
    }
}
