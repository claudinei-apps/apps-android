package br.teste.android;

import android.content.Context;
import android.graphics.Bitmap;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Constructor;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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

    private static final int LIMITE_SEGUNDOS = 30;
    private static volatile PlugPag plugPag;
    private static final ExecutorService trabalho = Executors.newCachedThreadPool();

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
            Constructor<PlugPag> c = PlugPag.class.getConstructor(Context.class);
            return c.newInstance(ctx);
        } catch (NoSuchMethodException e) {
            Class<?> idCls = Class.forName("br.com.uol.pagseguro.plugpagservice.wrapper.PlugPagAppIdentification");
            Object id = idCls.getConstructor(String.class, String.class).newInstance("BingoPOS", "2.0");
            Constructor<PlugPag> c = PlugPag.class.getConstructor(Context.class, idCls);
            return c.newInstance(ctx, id);
        }
    }

    private static Throwable raiz(Throwable e) {
        while (e.getCause() != null && e.getCause() != e) e = e.getCause();
        return e;
    }

    /** Bloqueia até terminar (no máximo 30 s): chamar fora da tela principal. */
    public static synchronized Result print(final Context context, Bitmap bmp) {
        final File file = new File(folder(context), "comprovante.png");
        try {
            FileOutputStream out = new FileOutputStream(file);
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.flush();
            out.close();
            file.setReadable(true, false);
        } catch (Exception e) {
            return new Result(false, "Não consegui preparar a imagem: " + e.getMessage(), "FILE");
        }
        final int passos = Prefs.avanco(context);
        Future<Result> f = trabalho.submit(new Callable<Result>() {
            @Override
            public Result call() throws Exception {
                if (plugPag == null) plugPag = criar(context.getApplicationContext());
                PlugPagPrinterData data = new PlugPagPrinterData(file.getAbsolutePath(), 4, passos);
                PlugPagPrintResult r = plugPag.printFromFile(data);
                if (r != null && r.getResult() == PlugPag.RET_OK) {
                    return new Result(true, "Impresso.", "PRINTED");
                }
                String msg = r == null ? "sem resposta" : (r.getMessage() + " (" + r.getErrorCode() + ")");
                return new Result(false, "A impressora recusou: " + msg, "PRINTER_ERROR");
            }
        });
        try {
            return f.get(LIMITE_SEGUNDOS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            f.cancel(true);
            plugPag = null;
            return new Result(false,
                    "A impressora da Moderninha não respondeu em " + LIMITE_SEGUNDOS + " s.", "PRINTER_TIMEOUT");
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
