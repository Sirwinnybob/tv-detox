package com.ansub.tvhome;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.os.Handler;
import android.os.Looper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;

// Starts VNC Cast Receiver only when a sender connects, so it isn't kept open (about 110 MB) between
// casts. The home screen is always running, so it holds the cast port, 5500. When a sender says
// hello, this answers for the receiver straight away (senders give up after 3 seconds), starts the
// receiver in its "from home" mode (listening on this TV only, port 5501) and passes the stream
// through. Until the receiver is ready, TCP holds the sender's video, so nothing is lost. When the
// cast disconnects the receiver closes, so the TV goes back to whatever it was showing, and 30
// seconds later its process exits to free the memory (the receiver does that itself: Android 14
// doesn't let one app clear another's process).
// Needs receiver 1.2 (version 3) or later; with an older one the hook stays off and port 5500 is
// left to the receiver, as before.
final class CastHook {
    static final String RECEIVER = "com.kkc.vnccast.receiver";
    private static final String ACTIVITY = RECEIVER + ".ReceiverActivity";
    private static final String FROM_HOME = RECEIVER + ".FROM_HOME";
    private static final int PORT = 5500, LOCAL_PORT = 5501, MIN_VERSION = 3;
    private static final byte[] HELLO = {'V', 'C', 'A', 'S', 'T', 'H', '2', '6', 1};
    private static final long READY_MS = 20000; // how long the receiver gets to start listening

    private static CastHook running;

    private final Context app;
    private final Handler main = new Handler(Looper.getMainLooper());

    private CastHook(Context app) { this.app = app; }

    // Starts listening once per process, if the receiver is new enough. Safe to call often.
    static synchronized void start(Context c) {
        if (running != null || !supported(c)) return;
        running = new CastHook(c.getApplicationContext());
        Thread t = new Thread(running::listen, "cast-hook");
        t.setDaemon(true);
        t.start();
    }

    static boolean isRunning() { return running != null; }

    // Opening the receiver from the app list: in "from home" mode too, since this holds port 5500.
    static void openReceiver(Context c) {
        try { c.startActivity(receiverIntent()); } catch (Exception ignored) {}
    }

    @SuppressWarnings("deprecation")
    private static boolean supported(Context c) {
        try {
            PackageInfo p = c.getPackageManager().getPackageInfo(RECEIVER, 0);
            long version = android.os.Build.VERSION.SDK_INT >= 28 ? p.getLongVersionCode() : p.versionCode;
            return version >= MIN_VERSION;
        } catch (Exception e) {
            return false;
        }
    }

    private static Intent receiverIntent() {
        return new Intent().setClassName(RECEIVER, ACTIVITY).putExtra(FROM_HOME, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    // Holds port 5500. If it can't (the network isn't up yet, or something else has the port), it
    // tries again every few seconds.
    private void listen() {
        while (true) {
            ServerSocket server = null;
            try {
                server = new ServerSocket();
                server.setReuseAddress(true);
                server.bind(new InetSocketAddress(PORT), 4);
                while (true) {
                    final Socket sender = server.accept();
                    new Thread(() -> relay(sender), "cast-relay").start();
                }
            } catch (IOException ignored) {
            } finally {
                close(server);
            }
            try { Thread.sleep(5000); } catch (InterruptedException e) { return; }
        }
    }

    private void relay(final Socket sender) {
        Socket local = null;
        try {
            sender.setSoTimeout(5000);
            byte[] hello = new byte[HELLO.length];
            // Senders check the TV is there by connecting and closing: nothing to start for that.
            if (!readFully(sender.getInputStream(), hello) || !Arrays.equals(hello, HELLO)) return;
            sender.setTcpNoDelay(true);
            sender.setKeepAlive(true);
            OutputStream toSender = sender.getOutputStream();
            toSender.write(1); // "accepted", as the receiver would say
            toSender.flush();

            main.post(() -> openReceiver(app));
            local = connectLocal();
            if (local == null) return;
            local.setTcpNoDelay(true);
            local.setSoTimeout(5000);
            local.getOutputStream().write(HELLO);
            local.getOutputStream().flush();
            if (local.getInputStream().read() != 1) return;
            local.setSoTimeout(0);
            sender.setSoTimeout(0);

            // The receiver sends nothing after its hello: when it closes (Back, or a decoder error),
            // close the sender too.
            final Socket receiver = local;
            Thread back = new Thread(() -> {
                try { while (receiver.getInputStream().read() >= 0) { /* nothing expected */ } }
                catch (IOException ignored) {}
                close(sender);
            }, "cast-relay-back");
            back.setDaemon(true);
            back.start();

            pipe(sender.getInputStream(), local.getOutputStream()); // until the sender stops
        } catch (IOException ignored) {
        } finally {
            close(sender);
            close(local);
        }
    }

    // The receiver's port on this TV, once it's listening; null if it doesn't start in time.
    private static Socket connectLocal() {
        long until = System.currentTimeMillis() + READY_MS;
        while (System.currentTimeMillis() < until) {
            Socket s = new Socket();
            try {
                s.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), LOCAL_PORT), 1000);
                return s;
            } catch (IOException e) {
                close(s);
            }
            try { Thread.sleep(250); } catch (InterruptedException e) { return null; }
        }
        return null;
    }

    private static void pipe(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[64 * 1024];
        for (int n; (n = in.read(buf)) >= 0; ) {
            out.write(buf, 0, n);
        }
    }

    private static boolean readFully(InputStream in, byte[] b) throws IOException {
        for (int off = 0; off < b.length; ) {
            int n = in.read(b, off, b.length - off);
            if (n < 0) return false;
            off += n;
        }
        return true;
    }

    private static void close(java.io.Closeable c) {
        if (c != null) try { c.close(); } catch (IOException ignored) {}
    }
}
