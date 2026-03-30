# Mobile Robot Control Framework

A dual-mode mobile robot platform for **autonomous indoor security patrol** with real-time remote control via an Android smartphone. Built as a final-year engineering project.

The robot can be driven manually using a phone-based joystick, or set to autonomously repeat a taught patrol route while avoiding obstacles, detecting environmental anomalies, and streaming live camera footage to the operator.

---

## How It Works

**Manual Mode** — The operator connects their Android phone to the robot's WiFi network and drives it with a virtual joystick. Motor speeds, connection health, and system status are displayed in real time.

**Teach Mode** — The operator drives the robot through a patrol route while the system records encoder ticks, gyroscope headings, and ultrasonic distances at each waypoint.

**Patrol Mode** — The robot replays the recorded route autonomously, shuttling back and forth with 180-degree turns at each end. It uses gyro-based heading correction and wall-distance feedback to stay on path, a three-tier obstacle avoidance system (slowdown, avoidance maneuver, emergency stop) to handle dynamic obstacles, and ultrasonic anomaly detection to alert the operator when something in the environment has changed.

---

## System Architecture

The system has three main components:

| Component | Technology | Role |
|-----------|------------|------|
| **Robot Controller** | ESP32 (Arduino/PlatformIO) | WiFi AP, motor control, sensor fusion, patrol logic, UDP command server |
| **Camera Module** | ESP32-CAM (AI-Thinker) | MJPEG video stream over HTTP, connects to main ESP32's AP as a client |
| **Operator App** | Android (Java) | Joystick, camera feed, patrol controls, sensor display, alerts |

Communication between the phone and ESP32 uses **UDP/JSON** over the robot's own WiFi access point — no external network or internet required.

---

## Repository Structure

```
├── Esp32_Firmware/                 # PlatformIO project for the ESP32
│   ├── src/main.cpp                # Firmware entry point — networking, commands, motor control
│   ├── include/config.h            # Pin assignments, WiFi credentials, timing constants
│   ├── include/motor_driver.h      # BTS7960 motor driver class
│   ├── PROTOCOL.md                 # Full UDP/JSON command and response specification
│   └── platformio.ini              # Build configuration
│
├── android/                        # Android Studio project
│   └── app/src/main/java/.../
│       ├── MainActivity.java       # UI, event wiring, status display
│       ├── UdpClient.java          # UDP networking — send/receive, connection health
│       └── JoystickView.java       # Custom touch-based virtual joystick widget
│
├── DIst0rted_Research_Analysis_for_Mobile_Patrol_robot.md
│                                   # Full project research analysis and implementation plan
└── README.md
```

---

## Hardware

### Current (Phase 1 — Operational)

- **ESP32 DevKit V1** — main microcontroller and WiFi access point
- **2x BTS7960 motor driver modules** — dual H-bridge, supports PWM speed control and active braking
- **2x 12V wiper motors** — rear differential drive
- **2x passive castor wheels** — front support
- **2x 12V sealed lead-acid batteries** — power source

### Planned (Phase 2 — In Progress)

- **ESP32-CAM (AI-Thinker)** — live MJPEG video surveillance
- **3x HC-SR04P ultrasonic sensors** — front and angled side obstacle detection (3.3V compatible)
- **MPU6050 (GY-521)** — 6-axis IMU for heading measurement and turn accuracy
- **2x LM393 wheel encoder modules** — optical odometry for route recording and replay
- **Active buzzer** — audible proximity and anomaly alerts
- **Physical push button** — on-robot mode toggle

---

## Communication Protocol

All commands are JSON over UDP (port 4210). The ESP32 runs as an AP at `192.168.4.1`.

### Phase 1 Commands (Implemented)

| Command | Example | Description |
|---------|---------|-------------|
| `joy` | `{"cmd":"joy","x":0.5,"y":0.8}` | Joystick input — ESP32 handles differential mixing |
| `motor` | `{"cmd":"motor","l":150,"r":150}` | Direct left/right motor speed |
| `stop` | `{"cmd":"stop"}` | Emergency brake |
| `ping` | `{"cmd":"ping"}` | Connection health check |
| `status` | `{"cmd":"status"}` | Query motor speeds, uptime, client count |
| `config` | `{"cmd":"config","max_speed":180}` | Set runtime speed limit |

### Phase 2 Commands (Planned)

| Command | Description |
|---------|-------------|
| `mode` | Switch between manual / auto / toggle |
| `record_start` / `record_stop` | Begin / end route teaching |
| `patrol_start` / `patrol_stop` | Begin / end autonomous patrol |
| `get_sensors` | Request sensor telemetry snapshot |
| `auto_config` | Set patrol parameters (distances, speed, turn angle) |

Full protocol specification: [`Esp32_Firmware/PROTOCOL.md`](Esp32_Firmware/PROTOCOL.md)

---

## Safety Features

- **Dead-man's switch** — motors stop if no command received within 500 ms (manual mode)
- **Client disconnect stop** — motors stop when the phone disconnects (manual mode only; patrol continues autonomously)
- **Runtime speed limiter** — operator can cap maximum motor output from the app
- **Three-tier obstacle avoidance** — proportional slowdown, avoidance maneuver, emergency stop (patrol mode)
- **Motor stall detection** — cuts power if wheels are stuck (patrol mode)
- **Low battery alert** — monitors battery voltage, halts patrol and alerts operator

---

## Building

### Firmware

Requires [PlatformIO](https://platformio.org/).

```bash
cd Esp32_Firmware
pio run              # compile
pio run -t upload    # flash to ESP32
pio device monitor   # serial output (115200 baud)
```

### Android App

Open the `android/` directory in [Android Studio](https://developer.android.com/studio). Build and install to a device running Android 10+ (API 29).

The app connects to WiFi SSID `RobotControl` (password `robot1234`) and communicates with the ESP32 at `192.168.4.1:4210`.

---

## Usage

1. Power on the robot. The ESP32 starts a WiFi access point named **RobotControl**.
2. On your Android phone, connect to `RobotControl` (password: `robot1234`).
3. Open the app. The LED indicator turns green when the connection is established.
4. Use the joystick to drive. Adjust the speed slider as needed. Press **STOP** for emergency brake.

For autonomous patrol (Phase 2): drive the route once in Record mode, then activate Patrol mode. The robot handles the rest.

---

## Project Documentation

For a comprehensive breakdown of the project — problem statement, objectives, full hardware list with GPIO assignments, power system design, firmware architecture, patrol state machine, known limitations, and testing methodology — see:

[**Research Analysis Document**](DIst0rted_Research_Analysis_for_Mobile_Patrol_robot.md)

---

## License

This project is part of an academic final-year submission. No license is specified at this time.
