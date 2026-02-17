package com.example.mobile_robot_android;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lightweight, zero-dependency UDP client for the ESP32 robot controller.
 * <p>
 * Architecture:<br>
 * - <b>TX</b>: single-thread executor for non-blocking sends.<br>
 * - <b>RX</b>: dedicated daemon thread doing blocking {@code socket.receive()}.<br>
 * - Callbacks are posted to the main looper for safe UI updates.
 * <p>
 * Thread-safe: {@link #sendJoystick}, {@link #sendStop}, etc. may be called
 * from any thread.
 */
public class UdpClient {

    // ── Listener ────────────────────────────────────────────────

    public interface ResponseListener {
        void onPong(String version, long uptime);
        void onStatus(int left, int right, int maxSpeed, long uptime, int clients);
        void onAck(String command, int maxSpeed);
        void onError(String message);
        void onConnectionChanged(boolean connected);
    }

    // ── Protocol constants ──────────────────────────────────────

    private static final String ESP_IP       = "192.168.4.1";
    private static final int    ESP_PORT     = 4210;
    private static final int    RX_BUF       = 256;
    private static final int    SO_TIMEOUT   = 300;   // ms — receive poll interval
    private static final long   CONN_TIMEOUT = 3000;  // ms — no pong → disconnected

    // ── Networking ──────────────────────────────────────────────

    private volatile DatagramSocket socket;
    private volatile InetAddress    espAddr;
    private volatile boolean        running;
    private volatile boolean        connected;
    private volatile long           lastRxTime;

    private Thread rxThread;

    private final ExecutorService txExec =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "UdpTx");
                t.setDaemon(true);
                return t;
            });

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ResponseListener listener;

    // ── Public API ──────────────────────────────────────────────

    public void setResponseListener(ResponseListener l) { listener = l; }

    /** Open the socket and start the receive loop. Safe to call repeatedly. */
    public void start() {
        if (running) return;
        txExec.execute(() -> {
            try {
                InetAddress addr = InetAddress.getByName(ESP_IP);
                DatagramSocket sock = new DatagramSocket();
                sock.setSoTimeout(SO_TIMEOUT);

                espAddr = addr;
                socket  = sock;
                running = true;

                rxThread = new Thread(() -> receiveLoop(sock), "UdpRx");
                rxThread.setDaemon(true);
                rxThread.start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    /** Close the socket and stop all threads. Idempotent. */
    public void stop() {
        running = false;
        DatagramSocket s = socket;
        socket = null;
        if (s != null && !s.isClosed()) s.close();
        setConnected(false);
    }

    public boolean isConnected() { return connected; }

    // ── Send helpers (fire-and-forget) ──────────────────────────

    public void sendJoystick(float x, float y) {
        send(String.format(Locale.US, "{\"cmd\":\"joy\",\"x\":%.2f,\"y\":%.2f}", x, y));
    }

    public void sendStop()   { send("{\"cmd\":\"stop\"}"); }
    public void sendPing()   { send("{\"cmd\":\"ping\"}"); }
    public void sendStatus() { send("{\"cmd\":\"status\"}"); }

    public void sendConfig(int maxSpeed) {
        send("{\"cmd\":\"config\",\"max_speed\":" + maxSpeed + "}");
    }

    // ── Internals ───────────────────────────────────────────────

    private void send(String json) {
        if (!running) return;
        final DatagramSocket s = socket;
        final InetAddress    a = espAddr;
        if (s == null || a == null) return;

        txExec.execute(() -> {
            if (s.isClosed()) return;
            try {
                byte[] data = json.getBytes();
                s.send(new DatagramPacket(data, data.length, a, ESP_PORT));
            } catch (IOException ignored) { }
        });
    }

    /**
     * Blocking receive loop — runs on its own daemon thread.
     * Each iteration either delivers a response or handles a timeout.
     */
    private void receiveLoop(DatagramSocket sock) {
        byte[] buf = new byte[RX_BUF];
        while (running) {
            try {
                DatagramPacket pkt = new DatagramPacket(buf, buf.length);
                sock.receive(pkt);
                lastRxTime = System.currentTimeMillis();
                setConnected(true);
                parseResponse(new String(pkt.getData(), 0, pkt.getLength()));
            } catch (SocketTimeoutException e) {
                if (lastRxTime > 0 &&
                    System.currentTimeMillis() - lastRxTime > CONN_TIMEOUT) {
                    setConnected(false);
                }
            } catch (IOException e) {
                if (running) setConnected(false);
                break;
            }
        }
    }

    private void parseResponse(String json) {
        try {
            JSONObject o = new JSONObject(json);
            switch (o.optString("resp", "")) {
                case "pong": {
                    String v  = o.optString("v", "?");
                    long   up = o.optLong("uptime", 0);
                    post(() -> { if (listener != null) listener.onPong(v, up); });
                    break;
                }
                case "status": {
                    int  l  = o.optInt("l",       0);
                    int  r  = o.optInt("r",       0);
                    int  ms = o.optInt("max_spd", 255);
                    long up = o.optLong("uptime",  0);
                    int  cl = o.optInt("clients",  0);
                    post(() -> { if (listener != null) listener.onStatus(l, r, ms, up, cl); });
                    break;
                }
                case "ack": {
                    String cmd = o.optString("cmd", "");
                    int    ms  = o.optInt("max_spd", -1);
                    post(() -> { if (listener != null) listener.onAck(cmd, ms); });
                    break;
                }
                case "error": {
                    String msg = o.optString("msg", "Unknown");
                    post(() -> { if (listener != null) listener.onError(msg); });
                    break;
                }
            }
        } catch (Exception ignored) { }
    }

    private void setConnected(boolean c) {
        if (connected == c) return;
        connected = c;
        post(() -> { if (listener != null) listener.onConnectionChanged(c); });
    }

    private void post(Runnable r) { mainHandler.post(r); }
}
