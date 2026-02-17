package com.example.mobile_robot_android;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Single-activity robot controller with neobrutalism UI.
 * <p>
 * Communicates with the ESP32 via UDP (see {@link UdpClient}).
 * Sends joystick commands at ~30 Hz and monitors connection health
 * with periodic pings. A prominent LED indicator shows whether the
 * phone can reach the ESP32 access point.
 */
public class MainActivity extends AppCompatActivity implements UdpClient.ResponseListener {

    // ── Timing constants ────────────────────────────────────────

    private static final long PING_INTERVAL  = 1500;  // ms between pings
    private static final long STATUS_INTERVAL = 2000; // ms between status polls
    private static final long JOY_INTERVAL   = 33;    // ms ≈ 30 Hz send rate

    // ── Views ───────────────────────────────────────────────────

    private View         ledIndicator;
    private TextView     connectionText;
    private TextView     firmwareText;
    private TextView     uptimeText;
    private TextView     clientsText;
    private JoystickView joystick;
    private SeekBar      speedBar;
    private TextView     speedValue;
    private ProgressBar  leftBar;
    private ProgressBar  rightBar;
    private TextView     leftValue;
    private TextView     rightValue;
    private Button       stopBtn;

    // ── State ───────────────────────────────────────────────────

    private final UdpClient udp   = new UdpClient();
    private final Handler   timer = new Handler(Looper.getMainLooper());
    private long lastJoySend;
    private boolean isConnected;
    private ObjectAnimator ledPulse;

    // ══════════════════════════════════════════════════════════════
    //  Lifecycle
    // ══════════════════════════════════════════════════════════════

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        findViews();
        wireListeners();
        udp.setResponseListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        udp.start();
        timer.post(pingTask);
        timer.postDelayed(statusTask, STATUS_INTERVAL);
    }

    @Override
    protected void onPause() {
        super.onPause();
        timer.removeCallbacksAndMessages(null);
        udp.sendStop();
        udp.stop();
        stopLedPulse();
    }

    // ══════════════════════════════════════════════════════════════
    //  View wiring
    // ══════════════════════════════════════════════════════════════

    private void findViews() {
        ledIndicator   = findViewById(R.id.led_indicator);
        connectionText = findViewById(R.id.connection_text);
        firmwareText   = findViewById(R.id.firmware_text);
        uptimeText     = findViewById(R.id.uptime_text);
        clientsText    = findViewById(R.id.clients_text);
        joystick       = findViewById(R.id.joystick_view);
        speedBar       = findViewById(R.id.speed_seekbar);
        speedValue     = findViewById(R.id.speed_value);
        leftBar        = findViewById(R.id.left_motor_bar);
        rightBar       = findViewById(R.id.right_motor_bar);
        leftValue      = findViewById(R.id.left_motor_value);
        rightValue     = findViewById(R.id.right_motor_value);
        stopBtn        = findViewById(R.id.stop_button);

        speedBar.setProgress(255);
        updateConnectionUI(false);
    }

    private void wireListeners() {
        // Joystick → send joy commands at 30 Hz, stop on release
        joystick.setJoystickListener(new JoystickView.JoystickListener() {
            @Override
            public void onJoystickMoved(float x, float y) {
                long now = System.currentTimeMillis();
                if (now - lastJoySend >= JOY_INTERVAL) {
                    lastJoySend = now;
                    udp.sendJoystick(x, y);
                }
            }

            @Override
            public void onJoystickReleased() {
                udp.sendStop();
            }
        });

        // Emergency stop with haptic feedback
        stopBtn.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            udp.sendStop();
        });

        // Speed slider → send config on change
        speedBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int val, boolean fromUser) {
                speedValue.setText(String.valueOf(val));
                if (fromUser) udp.sendConfig(val);
            }

            @Override public void onStartTrackingTouch(SeekBar sb) { }
            @Override public void onStopTrackingTouch(SeekBar sb)  { }
        });
    }

    // ══════════════════════════════════════════════════════════════
    //  Periodic tasks
    // ══════════════════════════════════════════════════════════════

    private final Runnable pingTask = new Runnable() {
        @Override
        public void run() {
            udp.sendPing();
            timer.postDelayed(this, PING_INTERVAL);
        }
    };

    private final Runnable statusTask = new Runnable() {
        @Override
        public void run() {
            if (isConnected) udp.sendStatus();
            timer.postDelayed(this, STATUS_INTERVAL);
        }
    };

    // ══════════════════════════════════════════════════════════════
    //  UDP response callbacks (always on main thread)
    // ══════════════════════════════════════════════════════════════

    @Override
    public void onPong(String version, long uptime) {
        firmwareText.setText("v" + version);
        uptimeText.setText("Up: " + fmtUptime(uptime));
    }

    @Override
    public void onStatus(int left, int right, int maxSpeed, long uptime, int clients) {
        uptimeText.setText("Up: " + fmtUptime(uptime));
        clientsText.setText(clients + " client" + (clients != 1 ? "s" : ""));
        leftBar.setProgress(Math.abs(left));
        rightBar.setProgress(Math.abs(right));
        leftValue.setText(String.valueOf(left));
        rightValue.setText(String.valueOf(right));
    }

    @Override
    public void onAck(String command, int maxSpeed) {
        if ("config".equals(command) && maxSpeed >= 0) {
            speedBar.setProgress(maxSpeed);
            speedValue.setText(String.valueOf(maxSpeed));
        }
    }

    @Override
    public void onError(String message) {
        // Silently ignored — controller stays operational
    }

    @Override
    public void onConnectionChanged(boolean connected) {
        isConnected = connected;
        updateConnectionUI(connected);
    }

    // ══════════════════════════════════════════════════════════════
    //  UI helpers
    // ══════════════════════════════════════════════════════════════

    private void updateConnectionUI(boolean connected) {
        // LED indicator
        ledIndicator.setBackgroundResource(
                connected ? R.drawable.led_connected : R.drawable.led_disconnected);

        // LED pulse animation (mimics a real blinking LED when connected)
        if (connected) {
            startLedPulse();
        } else {
            stopLedPulse();
        }

        // Connection label
        connectionText.setText(connected ? "CONNECTED" : "OFFLINE");
        connectionText.setTextColor(
                getColor(connected ? R.color.neo_green : R.color.neo_red));

        // Status card hint when disconnected
        if (!connected) {
            firmwareText.setText("\u2014");          // em-dash
            uptimeText.setText("Connect to WiFi");
            clientsText.setText("'RobotControl'");
            leftBar.setProgress(0);
            rightBar.setProgress(0);
            leftValue.setText("0");
            rightValue.setText("0");
        }
    }

    /** Gentle pulse on the LED to mimic a real indicator light. */
    private void startLedPulse() {
        if (ledPulse != null && ledPulse.isRunning()) return;
        ledPulse = ObjectAnimator.ofFloat(ledIndicator, View.ALPHA, 1f, 0.45f, 1f);
        ledPulse.setDuration(1800);
        ledPulse.setRepeatCount(ValueAnimator.INFINITE);
        ledPulse.start();
    }

    private void stopLedPulse() {
        if (ledPulse != null) {
            ledPulse.cancel();
            ledPulse = null;
        }
        ledIndicator.setAlpha(1f);
    }

    /** Format milliseconds into a compact human-readable string. */
    private static String fmtUptime(long ms) {
        long s = ms / 1000;
        long m = s / 60;
        long h = m / 60;
        if (h > 0) return h + "h " + (m % 60) + "m";
        if (m > 0) return m + "m " + (s % 60) + "s";
        return s + "s";
    }
}
