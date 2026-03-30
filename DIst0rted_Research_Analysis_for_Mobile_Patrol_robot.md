# Research Analysis: Autonomous Indoor Security Patrol Robot

## 1. Project Overview

This project implements a dual-mode mobile robot platform designed for autonomous indoor security patrol. The system combines manual remote control via an Android smartphone with an autonomous teach-and-replay patrol mode, enabling the robot to independently navigate and monitor indoor environments such as office buildings, warehouses, campus corridors, and restricted-access facilities.

The robot is built on a differential-drive chassis powered by two 12V wiper motors, controlled by an ESP32 microcontroller, and equipped with ultrasonic sensors for obstacle avoidance, an MPU6050 inertial measurement unit for heading stabilization, wheel encoders for odometry, and an ESP32-CAM module for live video surveillance. An operator uses a custom Android application to teach the robot a patrol route by driving it manually, after which the robot can repeat that route autonomously, detecting obstacles and environmental anomalies while streaming live camera footage to the operator's phone.

---

## 2. Problem Statement

Security patrol of indoor environments is a repetitive, fatigue-prone task. Human guards conducting routine patrols of buildings during off-hours face several challenges: attention degrades over long shifts, patrol schedules become predictable, and maintaining consistent coverage of large facilities is difficult. According to industry analysis, guard fatigue is a leading contributor to lapses in physical security at commercial and institutional facilities.

This project addresses the problem by developing a mobile robot capable of executing consistent, repeatable patrol routes through indoor environments autonomously. The robot provides live video surveillance to a remote operator, autonomously detects and avoids obstacles that appear in its patrol path, and alerts the operator when environmental anomalies are detected — such as a new object or person in a previously empty corridor. The operator retains full manual control at all times and can seamlessly switch between remote driving and autonomous patrol.

---

## 3. Project Objectives

1. Design and build a differential-drive mobile robot platform with sufficient power and durability for sustained indoor patrol operations.
2. Implement reliable real-time remote control of the robot via an Android smartphone application over a WiFi link.
3. Develop a teach-and-replay autonomous navigation system that allows an operator to record a patrol route by driving the robot once, then instruct the robot to repeat that route indefinitely.
4. Integrate ultrasonic obstacle avoidance to ensure safe autonomous operation in dynamic environments where objects may appear or move between patrol cycles.
5. Provide live video surveillance through an onboard camera module accessible from the Android application.
6. Implement environmental anomaly detection by comparing live sensor readings during patrol against the recorded baseline, alerting the operator to changes.
7. Ensure robust safety mechanisms across both manual and autonomous modes, including emergency stop, motor stall detection, and low-battery warning.

---

## 4. What Has Been Accomplished (Phase 1 — Complete)

Phase 1 established the core mobile robot platform with full remote control capability. All items below are implemented, tested, and operational.

### 4.1 Robot Hardware Platform

The physical robot is built on a custom chassis using a rear-wheel-drive differential configuration. Two 12V automotive wiper motors serve as the drive motors, providing high torque suitable for indoor floor surfaces. Each motor is driven by a BTS7960 dual H-bridge motor driver module, which supports bidirectional speed control via PWM and provides active braking capability. Two passive castor wheels at the front of the chassis provide stability and allow free pivoting during turns, including zero-radius in-place rotation.

### 4.2 Microcontroller and Networking

An ESP32 DevKit serves as the main controller. The ESP32 operates as a WiFi Access Point (SSID: `RobotControl`), creating its own wireless network that the operator's phone connects to directly. This eliminates the need for external WiFi infrastructure — the robot is self-contained and works anywhere. Communication between the phone and the ESP32 uses the UDP transport protocol on port 4210, chosen for its low latency and minimal overhead, which is critical for real-time motor control. All messages are encoded as UTF-8 JSON objects.

### 4.3 Motor Control System

The firmware implements differential-drive mixing, converting joystick X/Y coordinates from the phone into individual left and right motor speeds. The mixing formula (`left = y + x`, `right = y - x`) is normalized to prevent either motor from exceeding the configured maximum speed. An 8-bit PWM resolution (0-255) at 20 kHz (above audible range) provides smooth, silent speed control. The motor driver class supports three states: active drive (variable speed forward or reverse), active brake (both low-side FETs conduct, shorting motor terminals for rapid deceleration), and coast (half-bridges disabled, motor spins freely).

### 4.4 Safety Systems

Three safety mechanisms are implemented in firmware:

- **Dead-man's switch (command timeout):** If no valid command is received from the phone within 500 milliseconds, both motors are automatically stopped. This ensures the robot halts if the phone app crashes, the operator loses connectivity, or the phone is dropped.
- **Client disconnect detection:** The firmware monitors the WiFi AP client count. When the last connected client disconnects, both motors are immediately stopped.
- **Runtime speed limiter:** The maximum motor output is configurable at runtime (0-255) via the phone app. This allows the operator to limit the robot's top speed for indoor operation or initial testing.

### 4.5 Android Application

A custom single-activity Android application provides the operator interface. The app features:

- A virtual dual-axis joystick for intuitive directional control, sending position updates at approximately 30 Hz.
- A connection status LED indicator with pulse animation, driven by periodic ping/pong health checks every 1.5 seconds.
- A speed limit slider (0-255) that sends runtime configuration commands to the ESP32.
- A status card displaying firmware version, system uptime, and connected client count, polled every 2 seconds.
- Real-time left and right motor speed indicators displayed as progress bars.
- A prominent emergency stop button with haptic feedback.
- The UI follows a neobrutalism design language with bold typography and high-contrast elements.

The app uses a lightweight, zero-dependency UDP client architecture with a single-thread executor for non-blocking sends and a dedicated daemon thread for blocking receives. All response callbacks are posted to the main thread for safe UI updates. Connection health is determined by ping response timeout — if no pong is received within 3 seconds, the app transitions to a disconnected state.

### 4.6 Communication Protocol (v1.0.0)

The protocol supports six command types (phone to ESP32): `joy` (joystick control), `motor` (direct motor speed), `stop` (emergency brake), `ping` (health check), `status` (state query), and `config` (runtime parameter adjustment). The ESP32 responds with four response types: `pong`, `status`, `ack`, and `error`. The full protocol specification is documented in `Esp32_Firmware/PROTOCOL.md`.

---

## 5. What Is Being Implemented (Phase 2 — In Progress)

Phase 2 transforms the remote-controlled robot into an autonomous security patrol system by adding sensors, a camera module, and an onboard intelligence layer for route recording, route replay, obstacle avoidance, and anomaly detection.

### 5.1 Dual-Mode Architecture

The system operates in two primary modes, selectable by the operator via the phone app or a physical button on the robot chassis:

- **Manual Mode (default):** Identical to the current Phase 1 behavior. The operator has full joystick control. All sensor data is read and streamed to the phone as telemetry, but the robot takes no autonomous action. The existing safety systems (command timeout, client disconnect) apply.
- **Autonomous Patrol Mode:** The robot replays a previously recorded patrol route, controlling its own motors using closed-loop feedback from encoders and the gyroscope. Ultrasonic sensors provide obstacle avoidance. The operator can monitor the camera feed and sensor telemetry from the phone, but joystick commands are ignored. The emergency stop command and mode switch command are always accepted regardless of mode.

A critical design decision: in autonomous mode, the robot continues to patrol even if the phone disconnects from the WiFi AP. This differs from manual mode, where client disconnection triggers an immediate motor stop. Alerts generated while the phone is disconnected are buffered in memory and transmitted when the phone reconnects.

### 5.2 Sensor Suite

Four sensor systems are being added to the platform:

**Ultrasonic Distance Sensors (3x HC-SR04P):**
Three ultrasonic rangefinders are mounted at the front of the chassis. One faces directly forward for primary collision detection. Two are angled at approximately 45 degrees to the left and right, providing peripheral obstacle detection for approaching walls and objects at oblique angles. The HC-SR04P variant is used because it operates natively at 3.3V logic levels, which is compatible with ESP32 GPIO without requiring voltage dividers. Each sensor provides distance measurements from 2 cm to 400 cm. The three sensors are triggered sequentially (not simultaneously) to prevent acoustic cross-talk, with each measurement cycle taking approximately 25 ms, yielding a full three-sensor scan at roughly 13 Hz.

**Inertial Measurement Unit (1x MPU6050 / GY-521):**
A six-axis IMU mounted on the chassis provides gyroscope data for heading measurement and accelerometer data for tilt detection. The sensor communicates with the ESP32 over the I2C bus. The onboard Digital Motion Processor (DMP) is used rather than raw gyro readings, as the DMP performs internal sensor fusion that significantly reduces gyroscopic drift. Heading data is used for two purposes: executing accurate turns by angle (particularly the 180-degree turnarounds at each end of the patrol route) and correcting straight-line drift during forward driving via a proportional feedback loop.

**Wheel Encoders (2x LM393 with slotted disc):**
Optical encoder modules are attached to each drive wheel to count rotational increments. As the wheel turns, slots in an encoder disc interrupt an optical beam, generating pulses counted by the ESP32 via hardware interrupts. The tick count provides odometry — a measure of distance traveled by each wheel. This data is essential for the teach-and-replay system: during route recording, encoder ticks are logged at each waypoint so that during replay, the robot can measure its progress along the route and match recorded positions. A resolution of at least 10-20 ticks per wheel revolution is targeted, providing a distance resolution of approximately 1.5-3 cm per tick depending on wheel circumference.

**Camera Module (1x ESP32-CAM / AI-Thinker):**
A separate ESP32-CAM module provides live video surveillance via MJPEG streaming over HTTP. The ESP32-CAM is an independent microcontroller with an onboard OV2640 camera. It connects to the main ESP32's WiFi Access Point as a client and runs its own HTTP server, providing a video stream accessible at its assigned IP address (typically `192.168.4.3:81/stream`). This two-MCU architecture is used because the ESP32-CAM has insufficient GPIO pins to also handle motor control and sensor reading, while the main ESP32 DevKit lacks a camera interface. The two boards share the WiFi network but operate independently, with no direct wired communication required.

### 5.3 Teach-and-Replay Route System

The autonomous navigation system uses a teach-and-replay approach rather than pre-programmed coordinates or SLAM-based mapping. The operator physically drives the robot through the desired patrol route once, and the robot records the path. On subsequent autonomous runs, the robot replays the recorded path using closed-loop sensor feedback.

**Recording Phase:**
The operator activates recording mode from the phone app, then drives the robot from the patrol start point (Point A) to the patrol end point (Point B) using the normal joystick controls. During recording, the ESP32 captures a waypoint at a fixed interval (every 100 ms, yielding 10 waypoints per second). Each waypoint contains: the current heading from the IMU, cumulative encoder tick counts for both wheels, and the three ultrasonic distance readings at that moment. Motor commands from the joystick are still executed normally during recording — the robot simply logs its sensor state at each interval while the operator drives. When the operator ends recording, the waypoint array is saved to the ESP32's flash filesystem (LittleFS) for persistence across power cycles. At 10 Hz recording with approximately 28 bytes per waypoint, the ESP32 can store roughly 18 minutes of route data, which is sufficient for typical indoor patrol paths.

**Replay Phase (Autonomous Patrol):**
When the operator activates patrol mode, the robot executes a continuous back-and-forth patrol between the recorded start point (A) and end point (B):

1. The robot is at Point B (where recording ended). It executes a 180-degree in-place turn using the gyroscope for precise angle measurement.
2. The robot replays the recorded waypoints in reverse order (from the last waypoint back to the first), driving from B toward A. At each waypoint, the target heading is the recorded heading plus 180 degrees (since the robot is now facing the opposite direction). The robot uses proportional heading correction via the gyro and distance matching via the encoders to follow the recorded path.
3. Upon reaching Point A (first waypoint), the robot executes another 180-degree turn.
4. The robot replays the waypoints in forward order (first to last), driving from A toward B.
5. The cycle repeats indefinitely until the operator sends a stop command or switches to manual mode.

**Path-Following Feedback Control:**
During replay, two feedback loops run simultaneously to keep the robot on the recorded path:

- **Heading correction (gyro PID):** The robot continuously compares its current heading to the target heading for the current waypoint. A proportional correction term adjusts the differential between left and right motor speeds to steer toward the target heading. This corrects for motor mismatch and surface friction differences that would otherwise cause the robot to curve off course.
- **Lateral correction (wall distance):** When walls or fixed objects were within sensor range during recording, the recorded ultrasonic distances serve as reference landmarks during replay. If the robot's live left-wall distance is greater than the recorded left-wall distance at the same waypoint, the robot knows it has drifted to the right and applies a corrective steering input. This directly addresses lateral drift, which is the primary limitation of heading-only correction.

### 5.4 Obstacle Avoidance

Obstacle avoidance operates as a priority-based interrupt layer between the route replayer and the motor output. It runs every control loop iteration and can override the route planner's desired speeds. Four priority levels are defined:

1. **Emergency stop (front distance < 15 cm):** Both motors brake immediately. Buzzer sounds. An alert is sent to the phone app. The robot waits until the obstacle clears (front distance exceeds 40 cm), then resumes the current route step.
2. **Avoidance maneuver (front distance < 35 cm):** The route step timer is paused. The robot compares left and right ultrasonic readings and turns in place toward the side with more clearance, using the gyroscope to measure the turn angle (typically 30-45 degrees). After the front path is clear, the robot resumes patrol from the nearest matching waypoint.
3. **Proportional slowdown (front distance 35-60 cm):** Motor speed is scaled proportionally to the distance. At 60 cm, full speed; at 35 cm, near-zero speed. The route heading correction remains active. This provides a smooth deceleration as the robot approaches an obstacle rather than an abrupt stop.
4. **Clear path (front distance > 60 cm):** Normal route replay at full programmed speed.

After an avoidance maneuver, the robot determines its approximate position along the route by comparing its current encoder count to the recorded waypoint encoder counts, snapping to the nearest matching waypoint. The heading correction then gradually steers it back toward the recorded path over subsequent waypoints.

### 5.5 Environmental Anomaly Detection

During recording, the ultrasonic distances to walls and fixed objects are saved as part of each waypoint. During autonomous patrol, the robot continuously compares its live sensor readings to the recorded baseline. A significant discrepancy indicates something in the environment has changed — a door that was open is now closed, an object has been placed in the corridor, or a person is standing in a previously empty area.

When the difference between a live reading and the recorded reading exceeds a configurable threshold (default: 40 cm), the robot classifies this as an anomaly event. The event is logged with the waypoint index, the sensor that triggered it, the expected distance, and the measured distance. An alert JSON message is sent to the phone app, and the buzzer emits a short pulse. If the discrepancy exceeds a higher threshold (indicating a major obstruction), the obstacle avoidance system is triggered.

This provides a basic but functional intrusion and change detection capability that operates independently of the camera — it works in complete darkness and does not require any image processing.

### 5.6 Live Video Surveillance

The ESP32-CAM module runs an MJPEG HTTP stream server that the Android app displays in an embedded WebView component. The camera provides visual context that the ultrasonic sensors cannot: identifying what an obstacle or anomaly actually is. When the robot sends an anomaly alert, the operator can view the camera feed to assess the situation and decide whether to take manual control for closer investigation.

The camera stream is available whenever the phone is connected to the robot's WiFi AP, regardless of whether the robot is in manual or autonomous mode.

### 5.7 Extended Communication Protocol (v2.0.0)

The Phase 1 UDP/JSON protocol is extended with the following additions:

**New commands (phone to ESP32):**

| Command | Fields | Description |
|---------|--------|-------------|
| `mode` | `mode`: `"manual"` / `"auto"` / `"toggle"` | Switch operating mode |
| `record_start` | — | Begin route recording |
| `record_stop` | — | End route recording, save to flash |
| `patrol_start` | — | Begin autonomous patrol of the saved route |
| `patrol_stop` | — | End autonomous patrol, return to manual mode |
| `get_sensors` | — | Request a single telemetry snapshot |
| `auto_config` | `stop_dist`, `slow_dist`, `turn_angle`, `drive_speed` | Configure autonomous patrol parameters |

**New responses (ESP32 to phone):**

| Response | Fields | Description |
|----------|--------|-------------|
| `mode` | `mode` | Confirms current operating mode |
| `telemetry` | `dist_f`, `dist_l`, `dist_r`, `heading`, `enc_l`, `enc_r`, `auto_state`, `waypoint`, `total_waypoints` | Periodic sensor and patrol state broadcast |
| `alert` | `type`, `waypoint`, `expected`, `measured`, `sensor` | Obstacle or anomaly event notification |
| `record` | `state`: `"started"` / `"stopped"`, `waypoints` | Recording state confirmation |

The existing Phase 1 commands (`joy`, `motor`, `stop`, `ping`, `status`, `config`) remain unchanged and backward-compatible. The `status` response is extended to include `mode` and sensor fields.

### 5.8 Android Application Extensions

The Android app is extended with the following features for Phase 2:

- **Mode control panel:** A toggle or button group for switching between Manual, Record, and Patrol modes. The mode state is visually distinct — the joystick area is active in Manual and Record modes, greyed out with an overlay message in Patrol mode.
- **Recording controls:** A record button that starts/stops route teaching. A recording indicator (duration, waypoint count) is displayed while active.
- **Patrol controls:** A patrol start/stop button. During patrol, the app displays current waypoint progress, patrol cycle count, obstacles avoided, and anomalies detected.
- **Camera feed viewer:** A WebView or MJPEG decoder component that displays the live ESP32-CAM stream. This is visible in all modes.
- **Sensor telemetry display:** Three distance indicators (front, left, right) shown as colored bars or numerical readouts. The color transitions from green (far/clear) through yellow to red (close/danger) based on distance thresholds.
- **Alert notifications:** When the ESP32 sends an alert, the app displays a popup notification with the alert details and optionally plays a sound. An alert log is maintained for review.
- **Autonomous configuration panel:** An expandable settings panel for adjusting patrol parameters — stop distance, slow distance, turn angle, and drive speed.

---

## 6. Complete Hardware List

### 6.1 Chassis and Drivetrain

| Component | Qty | Specification | Role |
|-----------|-----|---------------|------|
| Robot chassis | 1 | Custom frame | Structural platform |
| 12V wiper motor | 2 | Automotive type, high-torque geared DC | Left and right drive motors |
| Drive wheels | 2 | Sized to wiper motor output shaft | Rear drive wheels (left/right) |
| Castor wheel | 2 | Free-swiveling, passive | Front support, free rotation during turns |

### 6.2 Power System

| Component | Qty | Specification | Role |
|-----------|-----|---------------|------|
| 12V lead-acid battery | 2 | 12V SLA (connected in parallel for extended runtime, or one for motors and one for logic — see Section 8) | Main power source |
| DC-DC buck converter | 1 | 12V input, 5V output, minimum 3A rated | Steps battery voltage down to 5V for ESP32, sensors, and ESP32-CAM |
| Battery voltage divider | 1 | Two resistors (e.g., 100K + 33K) to scale 12V range into ESP32 ADC 0-3.3V range | Battery level monitoring via ESP32 ADC |
| Electrolytic capacitor | 2 | 100 uF, 25V rated | Decoupling on motor driver power input (one per BTS7960) |
| Ceramic capacitor | 2 | 100 nF | High-frequency noise filtering on motor driver power input |
| Inline fuse or fuse holder | 1 | Rated for total motor stall current | Overcurrent protection |

### 6.3 Motor Drivers

| Component | Qty | Specification | Role |
|-----------|-----|---------------|------|
| BTS7960 dual H-bridge motor driver module | 2 | 43A continuous, PWM input, enable pins | One per motor — forward/reverse speed control and active braking |

### 6.4 Microcontrollers

| Component | Qty | Specification | Role |
|-----------|-----|---------------|------|
| ESP32 DevKit V1 (or equivalent 38-pin) | 1 | 240 MHz dual-core, WiFi, 520 KB SRAM, 4 MB flash | Main controller — WiFi AP, motor control, sensor reading, patrol logic, UDP server |
| ESP32-CAM (AI-Thinker) | 1 | ESP32-S with OV2640 camera, WiFi | Video surveillance — connects to main ESP32 AP as a client, runs MJPEG HTTP stream |

### 6.5 Sensors

| Component | Qty | Specification | Role |
|-----------|-----|---------------|------|
| HC-SR04P ultrasonic sensor | 3 | 3.3V logic compatible, 2–400 cm range, ~30° beam angle | Obstacle detection — front, left-45°, right-45° |
| MPU6050 (GY-521 breakout) | 1 | 6-axis IMU (gyro + accelerometer), I2C, onboard DMP | Heading measurement for turns and straight-line correction |
| LM393 speed sensor module with slotted disc | 2 | Optical interrupter, digital output pulse on each slot | Wheel odometry — one per drive wheel |

### 6.6 Output Devices

| Component | Qty | Specification | Role |
|-----------|-----|---------------|------|
| Active buzzer | 1 | 3.3V or 5V, driven via GPIO (with transistor if needed) | Audible alerts for obstacles and anomalies |
| LED (onboard) | 1 | ESP32 DevKit built-in on GPIO 2 | Visual status indication (mode, connection state, motor activity) |

### 6.7 User Input

| Component | Qty | Specification | Role |
|-----------|-----|---------------|------|
| Tactile push button | 1 | Momentary, normally open | Physical mode toggle on the robot body (manual/auto) without requiring the phone |
| Android smartphone | 1 | Android 10+ (API 29+), WiFi capable | Operator interface — joystick, camera feed, controls, alerts |

### 6.8 Passive Components and Wiring

| Component | Qty | Specification | Role |
|-----------|-----|---------------|------|
| 10K pull-down resistor | 1 | For physical button GPIO with internal pull-up | Button debounce circuit |
| Breadboard or perfboard | 1 | For sensor and logic wiring | Intermediate wiring platform |
| Dupont jumper wires | Assorted | Male-male, male-female, female-female | Interconnections |
| Mounting hardware | Assorted | Standoffs, brackets, cable ties, hot glue | Physical mounting of sensors and boards to chassis |

---

## 7. GPIO Pin Assignment (ESP32 DevKit — Main Controller)

| GPIO | Direction | Connected To | Notes |
|------|-----------|--------------|-------|
| 2 | Output | Status LED | Built-in LED on most ESP32 DevKit boards |
| 4 | Output | Ultrasonic Front — TRIG | Trigger pulse output |
| 5 | Input | Ultrasonic Front — ECHO | 3.3V compatible (HC-SR04P) |
| 13 | Output | Ultrasonic Left — TRIG | Trigger pulse output |
| 14 | Output | Motor Right — L_EN | BTS7960 left half-bridge enable |
| 15 | Input | Physical mode button | Internal pull-up, active LOW |
| 16 | Output | Ultrasonic Right — TRIG | Trigger pulse output |
| 17 | Output | Buzzer | Active buzzer drive (HIGH = on) |
| 18 | Output | Motor Left — R_EN | BTS7960 right half-bridge enable |
| 19 | Output | Motor Left — L_EN | BTS7960 left half-bridge enable |
| 21 | Bidirectional | MPU6050 — SDA | I2C data (default ESP32 I2C bus) |
| 22 | Output | MPU6050 — SCL | I2C clock (default ESP32 I2C bus) |
| 23 | Input (interrupt) | Encoder Left — signal | Rising edge interrupt for tick counting |
| 25 | Output (PWM) | Motor Right — RPWM | BTS7960 forward PWM |
| 26 | Output (PWM) | Motor Right — LPWM | BTS7960 reverse PWM |
| 27 | Output | Motor Right — R_EN | BTS7960 right half-bridge enable |
| 32 | Output (PWM) | Motor Left — RPWM | BTS7960 forward PWM |
| 33 | Output (PWM) | Motor Left — LPWM | BTS7960 reverse PWM |
| 34 | Input | Ultrasonic Left — ECHO | Input-only GPIO, 3.3V compatible |
| 35 | Input | Ultrasonic Right — ECHO | Input-only GPIO, 3.3V compatible |
| 36 (VP) | Input | Battery voltage divider | ADC input for battery level monitoring |
| 39 (VN) | Input (interrupt) | Encoder Right — signal | Rising edge interrupt for tick counting |

**Total GPIOs used:** 22 of the available ESP32 DevKit pins.

**Reserved/unavailable:** GPIO 0 (boot mode), GPIO 1 (TX0), GPIO 3 (RX0), GPIO 6-11 (internal flash).

---

## 8. Power System Design

### 8.1 Battery Configuration

The robot is powered by two 12V sealed lead-acid (SLA) batteries. Two configurations are viable:

**Option A — Parallel (extended runtime):** Both batteries connected in parallel, providing 12V at double the amp-hour capacity. A single 12V rail feeds both the motor drivers and the logic power supply (via a buck converter). This maximizes patrol duration but requires careful noise isolation between motor and logic circuits.

**Option B — Split (isolated power domains):** Battery 1 is dedicated to the motor drivers. Battery 2 is dedicated to the logic subsystem (ESP32, ESP32-CAM, sensors) via a buck converter. This provides natural electrical isolation — motor current spikes and brush noise cannot affect the logic supply. The trade-off is that the two batteries may drain at different rates.

Option B is recommended for reliability. Motor noise coupling into the ESP32's power rail can cause erratic sensor readings, WiFi dropouts, and random resets. Physical separation of power domains eliminates this class of problems.

### 8.2 Voltage Regulation

The motor drivers (BTS7960) operate directly from the 12V battery rail and handle their own power switching. All logic-level components operate at 3.3V or 5V:

- The ESP32 DevKit has an onboard AMS1117 3.3V regulator fed from its VIN pin (accepts 5-12V) or USB 5V.
- The ESP32-CAM requires a 5V input.
- The ultrasonic sensors (HC-SR04P) operate at 3.3V–5V.
- The MPU6050 breakout has an onboard regulator and accepts 3.3V–5V on VCC.

A single DC-DC buck converter stepping the 12V battery down to 5V (rated at 3A minimum) powers the entire logic domain. The ESP32 DevKit's onboard regulator then produces 3.3V for its own processor and GPIO.

### 8.3 Noise Mitigation

Wiper motors generate significant electrical noise from brush commutation and back-EMF spikes during speed changes and braking. To prevent this noise from affecting sensors and microcontrollers:

- 100 uF electrolytic capacitors are placed across the power input terminals of each BTS7960 module to absorb current spikes.
- 100 nF ceramic capacitors are placed in parallel with the electrolytics for high-frequency noise suppression.
- If using the parallel battery configuration (Option A), a ferrite bead or LC filter is placed between the battery and the buck converter input to attenuate conducted motor noise.
- The motor power wiring and sensor/logic wiring are physically separated on the chassis to minimize radiated interference.

### 8.4 Battery Monitoring

A resistive voltage divider (100K / 33K) scales the 12V battery voltage to the ESP32's ADC range (0–3.3V). The ESP32 reads this on GPIO 36 (VP), an input-only ADC pin. A 12V reading maps to approximately 3.0V at the ADC, well within range. The firmware periodically samples battery voltage and can trigger a low-battery alert when voltage drops below a configurable threshold (e.g., 11.0V for a 12V SLA, indicating roughly 20% charge remaining). On low battery, the robot stops the patrol, sends an alert to the phone, and saves the current waypoint index to flash so patrol can resume from the correct position after recharging.

---

## 9. Firmware Architecture

### 9.1 Module Structure

The firmware is organized into the following modules within the PlatformIO project:

| File | Responsibility |
|------|----------------|
| `src/main.cpp` | Main loop, mode manager, command processing, UDP networking, telemetry broadcast |
| `include/config.h` | All hardware pin definitions, timing constants, threshold values, WiFi credentials |
| `include/motor_driver.h` | BTS7960 motor driver class (unchanged from Phase 1) |
| `include/sensors.h` | Ultrasonic reading (sequential trigger), MPU6050 DMP interface, encoder ISR and tick counting, battery ADC |
| `include/route_recorder.h` | Waypoint capture during teach phase, LittleFS save/load, route data structures |
| `include/patrol_controller.h` | Autonomous state machine — route replay, waypoint tracking, 180-degree turns, forward/reverse direction management |
| `include/obstacle_avoidance.h` | Priority-based avoidance layer — emergency stop, avoidance maneuver, proportional slowdown |

### 9.2 Main Loop Structure

The main loop executes at the sensor scan rate (approximately 13 Hz, governed by the sequential ultrasonic read time of ~75 ms). Each iteration:

1. **Sensor update:** Read all three ultrasonic sensors sequentially. Read MPU6050 heading via DMP. Read encoder tick counts.
2. **Mode-dependent logic:**
   - **Manual mode:** Process incoming UDP packets. Execute joystick/motor commands. Apply command timeout safety. Stream telemetry to phone.
   - **Record mode:** Process incoming UDP packets and execute joystick/motor commands (operator is driving). Additionally, capture a waypoint at each interval, storing heading, encoder counts, and ultrasonic distances.
   - **Patrol mode:** Run the patrol controller state machine (waypoint tracking, heading correction, lateral correction). Run the obstacle avoidance layer over the patrol output. Process incoming UDP packets but only accept `stop`, `mode`, `patrol_stop`, `get_sensors`, and `status` commands — joystick and motor commands are ignored. Stream telemetry and alerts to phone.
3. **Common tasks (all modes):** Handle client disconnect (stop motors only in manual mode). Update status LED. Check battery level. Broadcast telemetry at configured interval.

### 9.3 Patrol Controller State Machine

The patrol controller cycles through the following states:

| State | Behavior |
|-------|----------|
| `IDLE` | Patrol not active. Waiting for `patrol_start` command. |
| `TURNING_180` | Executing an in-place 180-degree turn using gyro feedback. Motors set to equal and opposite speeds. Transitions to `FOLLOWING` when the target heading (current + 180°) is reached within a tolerance of ±2°. |
| `FOLLOWING` | Replaying waypoints. Heading correction and lateral correction active. Encoder ticks are compared to waypoint targets to advance the waypoint index. Transitions to `TURNING_180` when the last waypoint in the current direction is reached. |
| `AVOIDING` | Obstacle avoidance has interrupted the route. Waypoint timer is paused. Transitions back to `FOLLOWING` when the avoidance maneuver completes and a matching waypoint is found. |
| `BLOCKED` | Emergency stop triggered. Motors braked. Waiting for the obstacle to clear. Transitions to `FOLLOWING` when the front sensor reads above the clearance threshold. |
| `LOW_BATTERY` | Battery voltage below threshold. Motors stopped. Alert sent to phone. Patrol halted until operator intervenes. |

### 9.4 Safety Behavior Across Modes

| Safety Mechanism | Manual Mode | Record Mode | Patrol Mode |
|------------------|-------------|-------------|-------------|
| Command timeout (500 ms) | Active — stops motors | Active — stops motors | Disabled — robot drives itself |
| Client disconnect | Stops motors | Stops motors and ends recording | No effect — patrol continues |
| Emergency stop command | Stops motors | Stops motors, ends recording | Stops motors, pauses patrol |
| Obstacle avoidance | Inactive (operator is responsible) | Inactive (operator is responsible) | Active — overrides route planner |
| Low battery alert | Alert sent to phone | Alert sent, recording saved | Patrol halted, alert sent |
| Motor stall detection | Inactive | Inactive | Active — if encoder ticks are zero but PWM is above threshold for more than 2 seconds, motors are cut and alert is sent |

---

## 10. Known Limitations and Mitigations

### 10.1 Gyroscope Drift

The MPU6050 gyroscope accumulates heading error at a rate of approximately 1-3 degrees per minute. Over an hour-long patrol, this could result in 60-180 degrees of accumulated drift, rendering heading-based navigation unreliable. This is mitigated by three mechanisms: (a) using the onboard DMP which fuses gyro and accelerometer data to reduce drift significantly, (b) resetting the heading reference at every 180-degree turn (a natural calibration point that occurs every half patrol cycle), and (c) using ultrasonic lateral correction against recorded wall distances, which corrects position independently of heading accuracy.

### 10.2 Lateral Drift Over Many Cycles

Even with heading correction, the robot may gradually drift sideways from the recorded path over many patrol cycles due to accumulated small errors in turn angles and encoder resolution. The lateral wall-distance correction system compensates for this when walls or fixed objects are within sensor range. In open areas without nearby reference surfaces, lateral drift is uncompensated and may reach 5-10 cm per patrol cycle. This is documented as a known limitation; for typical indoor patrol routes (corridors, hallways), walls are nearly always within ultrasonic range.

### 10.3 WiFi Range

The ESP32 Access Point provides a WiFi range of approximately 30-50 meters indoors with walls. If the patrol route extends beyond this range from the operator's position, the phone will temporarily lose connection. The robot continues patrolling autonomously during disconnection, buffering alerts for later delivery. The camera feed is unavailable while disconnected.

### 10.4 Ultrasonic Sensor Blind Spots

The three front-facing ultrasonic sensors cover approximately 90 degrees of the forward arc. Objects directly beside the robot (at 90 degrees to the direction of travel) are not detected until the robot turns toward them. Low obstacles below the sensor mounting height (below ~10 cm) may also go undetected. The sensors have a minimum range of 2 cm, below which readings are unreliable.

### 10.5 Castor Wheel Behavior During In-Place Turns

The passive front castor wheels must swivel freely during in-place 180-degree turns. If the castor pivot points are stiff or have excessive friction, the turn will be inconsistent, affecting the heading accuracy at each turnaround. Castor swivels must be lubricated and tested prior to deployment. Ball castors are an alternative that eliminates directional swivel resistance entirely.

### 10.6 Encoder Mounting on Wiper Motors

Wiper motors have an enclosed gearbox and may not expose a shaft suitable for direct encoder disc mounting. The encoder disc may need to be attached to the wheel hub rather than the motor shaft. Reflective tape encoding (alternating black/white strips on the inner wheel face, read by a TCRT5000 reflective sensor) is an alternative that avoids the need for a slotted disc and optical interrupter alignment.

### 10.7 Path Reversal Geometry

When replaying the recorded route in the reverse direction (after a 180-degree turn), each waypoint's target heading must be offset by 180 degrees from the recorded value. If this offset is applied incorrectly, the robot will steer the wrong way on curves. Additionally, the left and right ultrasonic reference values are spatially mirrored — what was on the left during recording is now on the right during the return trip. The replay logic must swap the left and right reference distances when driving in the reverse direction.

---

## 11. Testing Methodology

### 11.1 Path Accuracy Test

A test course is marked on the floor with tape. The robot records the route, then executes 10, 20, and 50 patrol cycles. After each set, the robot's position at the turnaround point is measured against the intended position. The deviation (in centimeters) is recorded and plotted against cycle count to characterize drift accumulation.

### 11.2 Obstacle Detection Test

Obstacles of varying sizes (box, chair, standing person) are placed at known positions along the patrol route at distances of 20 cm, 40 cm, and 80 cm from the path centerline. The robot executes patrol runs, and each obstacle encounter is classified as: detected and avoided, detected late (avoidance triggered below 15 cm), or missed (collision). Detection rate, false positive rate, and average detection distance are reported.

### 11.3 Anomaly Detection Test

Objects are added to and removed from the patrol corridor between recording and replay. The robot's anomaly detection system is evaluated for: true positive rate (correctly identifying new objects), false positive rate (flagging anomalies where none exist, caused by sensor noise or drift), and detection range (minimum size of object that triggers detection at a given distance).

### 11.4 Battery Endurance Test

The robot is fully charged and set to autonomous patrol on a fixed route. Total patrol time, number of complete cycles, and battery voltage over time are recorded. The test determines operational endurance and validates the low-battery detection threshold.

### 11.5 Turn Accuracy Test

The robot executes 50 consecutive 180-degree in-place turns. The actual heading change for each turn is measured (via an external reference or by measuring the final facing direction). Mean error and standard deviation are reported to characterize the gyro-based turn system's precision.

---

## 12. Future Work

The following enhancements are identified as natural extensions beyond the current project scope:

- **Multiple saved routes:** Allow the operator to record and name several patrol routes, selecting between them from the app (e.g., "Ground Floor East Wing" vs "Second Floor North Corridor"). The LittleFS storage has capacity for many routes.
- **Computer vision anomaly detection:** Use the ESP32-CAM for frame-differencing-based motion detection, identifying visual changes in addition to ultrasonic distance changes.
- **Scheduled patrol:** Allow the operator to set a patrol schedule (e.g., "patrol every 2 hours from 10 PM to 6 AM") stored in the ESP32's flash, with the robot executing automatically.
- **SLAM integration:** Replace the teach-and-replay system with a lightweight SLAM implementation using a LIDAR sensor and a more powerful compute board (Raspberry Pi), enabling the robot to build and update a map of its environment.
- **Cloud connectivity:** Bridge the ESP32 AP to an external WiFi network, allowing remote monitoring from anywhere via a cloud server rather than requiring the operator to be within WiFi range.
- **Return-to-dock:** Add an IR beacon at a charging dock, enabling the robot to autonomously navigate to its charging station when the low-battery condition is detected.
