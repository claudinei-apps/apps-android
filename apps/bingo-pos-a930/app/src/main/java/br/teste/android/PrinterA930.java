package br.teste.android;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Impressora interna da POS pelo serviço do sistema "com.xcheng.printerservice"
 * (o mesmo usado pelo app que já imprime QR Code nessa A930).
 * A interface foi reproduzida à mão (códigos de chamada iguais aos do serviço).
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

    static final String PKG = "com.xcheng.printerservice";
    static final String DESC = "com.xcheng.printerservice.IPrinterService";
    static final String DESC_CB = "com.xcheng.printerservice.IPrinterCallback";

    // códigos de chamada do serviço
    private static final int TX_FIRMWARE = 2;
    private static final int TX_INIT = 4;
    private static final int TX_WRAP = 6;
    private static final int TX_BITMAP = 10;

    private static final int LIMITE_SEGUNDOS = 60;
    private static final int FATIA = 240; // linhas por pedaço de imagem

    private static volatile IBinder remote;
    private static ServiceConnection conn;
    private static final ExecutorService trabalho = Executors.newCachedThreadPool();

    private PrinterA930() {}

    private interface Escrita { void em(Parcel p); }

    /** Recebe o retorno da impressora (fim ou erro). */
    private static final class Retorno extends Binder {
        final CountDownLatch fim = new CountDownLatch(1);
        volatile String erro;

        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            if (code == INTERFACE_TRANSACTION) {
                if (reply != null) reply.writeString(DESC_CB);
                return true;
            }
            try {
                data.enforceInterface(DESC_CB);
            } catch (Throwable ignored) {
            }
            switch (code) {
                case 1: { // onException(int, String)
                    int c = data.readInt();
                    String m = data.readString();
                    erro = m + " (" + c + ")";
                    fim.countDown();
                    return true;
                }
                case 4: // onComplete
                    fim.countDown();
                    return true;
                case 2: // onLength
                case 3: // onRealLength
                    return true;
                default:
                    return super.onTransact(code, data, reply, flags);
            }
        }

        boolean esperar(int segundos) {
            try {
                return fim.await(segundos, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                return false;
            }
        }
    }

    private static synchronized IBinder conectar(Context ctx) throws Exception {
        IBinder b = remote;
        if (b != null && b.isBinderAlive()) return b;
        final CountDownLatch latch = new CountDownLatch(1);
        final IBinder[] caixa = new IBinder[1];
        if (conn != null) {
            try { ctx.getApplicationContext().unbindService(conn); } catch (Throwable ignored) {}
        }
        conn = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                caixa[0] = service;
                remote = service;
                latch.countDown();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                remote = null;
            }
        };
        Intent i = new Intent(DESC);
        i.setPackage(PKG);
        boolean ok = ctx.getApplicationContext().bindService(i, conn, Context.BIND_AUTO_CREATE);
        if (!ok) throw new Exception("serviço de impressão (" + PKG + ") não existe nesta POS");
        if (!latch.await(8, TimeUnit.SECONDS)) throw new Exception("serviço de impressão não respondeu ao conectar");
        return caixa[0];
    }

    private static void chamar(IBinder b, int code, Escrita w) throws RemoteException {
        Parcel d = Parcel.obtain();
        Parcel r = Parcel.obtain();
        try {
            d.writeInterfaceToken(DESC);
            if (w != null) w.em(d);
            b.transact(code, d, r, 0);
            r.readException();
        } finally {
            d.recycle();
            r.recycle();
        }
    }

    private static Result fazer(Context ctx, Bitmap bmp) throws Exception {
        IBinder b = conectar(ctx);

        final Retorno ini = new Retorno();
        chamar(b, TX_INIT, p -> p.writeStrongBinder(ini));
        ini.esperar(3);

        int altura = bmp.getHeight();
        for (int y = 0; y < altura; y += FATIA) {
            int h = Math.min(FATIA, altura - y);
            final Bitmap parte = Bitmap.createBitmap(bmp, 0, y, bmp.getWidth(), h);
            final Retorno rt = new Retorno();
            chamar(b, TX_BITMAP, p -> {
                p.writeInt(1);
                parte.writeToParcel(p, 0);
                p.writeStrongBinder(rt);
            });
            rt.esperar(10);
            if (parte != bmp) parte.recycle();
            if (rt.erro != null) return new Result(false, "A impressora recusou: " + rt.erro, "PRINTER_ERROR");
        }

        final int linhas = Math.max(1, Prefs.avanco(ctx) / 25);
        final Retorno fim = new Retorno();
        chamar(b, TX_WRAP, p -> {
            p.writeInt(linhas);
            p.writeStrongBinder(fim);
        });
        fim.esperar(5);
        if (fim.erro != null) return new Result(false, "A impressora recusou: " + fim.erro, "PRINTER_ERROR");
        return new Result(true, "Impresso.", "PRINTED");
    }

    /** Bloqueia até terminar (no máximo 60 s): chamar fora da tela principal. */
    public static synchronized Result print(final Context context, final Bitmap bmp) {
        Future<Result> f = trabalho.submit(new Callable<Result>() {
            @Override
            public Result call() throws Exception {
                return fazer(context, bmp);
            }
        });
        try {
            return f.get(LIMITE_SEGUNDOS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            f.cancel(true);
            remote = null;
            return new Result(false, "A impressora não respondeu em " + LIMITE_SEGUNDOS + " s.", "PRINTER_TIMEOUT");
        } catch (Throwable e) {
            remote = null;
            Throwable c = e;
            while (c.getCause() != null && c.getCause() != c) c = c.getCause();
            return new Result(false, "Impressora indisponível: "
                    + (c.getMessage() == null ? c.getClass().getSimpleName() : c.getMessage()), "PRINTER_UNAVAILABLE");
        }
    }

    /** Versão do firmware da impressora (para a tela de informações). */
    public static String firmware(Context ctx) {
        try {
            IBinder b = conectar(ctx);
            Parcel d = Parcel.obtain();
            Parcel r = Parcel.obtain();
            try {
                d.writeInterfaceToken(DESC);
                b.transact(TX_FIRMWARE, d, r, 0);
                r.readException();
                return r.readString();
            } finally {
                d.recycle();
                r.recycle();
            }
        } catch (Throwable e) {
            return "indisponível (" + e.getMessage() + ")";
        }
    }
}
