# Robot Controller UDP Protocol — v1.0.0

Complete specification for building a mobile app that communicates with the
ESP32 robot controller firmware.

---

## 1. Connection

| Parameter       | Value              |
|-----------------|--------------------|
| WiFi SSID       | `RobotControl`     |
| WiFi Password   | `robot1234`        |
| ESP32 IP        | `192.168.4.1`      |
| Transport       | **UDP**            |
| Port            | **4210**           |
| Encoding        | UTF-8 JSON         |
| Max packet size | 256 bytes          |

### Connection flow

1. Phone connects to WiFi AP **RobotControl** (password `robot1234`).
2. Phone obtains IP via DHCP (typically `192.168.4.2`).
3. Phone sends UDP packets to `192.168.4.1:4210`.
4. ESP32 replies to the sender's IP and port.

---

## 2. Commands  (Phone → ESP32)

Every command is a JSON object with a **`cmd`** field.

### 2.1 Joystick Control — `joy`

Primary control method for phone apps. The ESP32 performs differential-drive
mixing internally.

```json
{ "cmd": "joy", "x": 0.0, "y": 0.0 }
```

| Field | Type  | Range            | Description                     |
|-------|-------|------------------|---------------------------------|
| `x`   | float | −1.0 … +1.0     | Steering. −1 = full left, +1 = full right |
| `y`   | float | −1.0 … +1.0     | Throttle. −1 = full reverse, +1 = full forward |

**Recommended send rate:** 20–50 ms interval (20–50 Hz).

**Examples:**

```json
{ "cmd": "joy", "x": 0.0,  "y": 1.0  }   // Full forward
{ "cmd": "joy", "x": 0.0,  "y": -1.0 }   // Full reverse
{ "cmd": "joy", "x": -1.0, "y": 0.0  }   // Spin left (in place)
{ "cmd": "joy", "x": 1.0,  "y": 0.0  }   // Spin right (in place)
{ "cmd": "joy", "x": 0.5,  "y": 0.5  }   // Forward-right arc
{ "cmd": "joy", "x": 0.0,  "y": 0.0  }   // Stop (zero throttle)
```

### 2.2 Direct Motor Control — `motor`

Set individual motor speeds. Useful for testing or advanced control.

```json
{ "cmd": "motor", "l": 0, "r": 0 }
```

| Field | Type | Range          | Description               |
|-------|------|----------------|---------------------------|
| `l`   | int  | −255 … +255    | Left motor speed          |
| `r`   | int  | −255 … +255    | Right motor speed         |

Positive = forward, negative = reverse. Values are clamped to ±`max_speed`.

### 2.3 Emergency Stop — `stop`

Immediately brakes both motors.

```json
{ "cmd": "stop" }
```

**Response:** `{ "resp": "ack", "cmd": "stop" }`

### 2.4 Ping — `ping`

Connection health check.

```json
{ "cmd": "ping" }
```

**Response:**

```json
{ "resp": "pong", "v": "1.0.0", "uptime": 123456 }
```

### 2.5 Status Request — `status`

Query the current robot state.

```json
{ "cmd": "status" }
```

**Response:**

```json
{
  "resp":    "status",
  "l":       0,
  "r":       0,
  "max_spd": 255,
  "uptime":  123456,
  "clients": 1
}
```

| Field     | Type | Description                              |
|-----------|------|------------------------------------------|
| `l`       | int  | Current left motor speed (−255 … +255)   |
| `r`       | int  | Current right motor speed (−255 … +255)  |
| `max_spd` | int  | Current max speed limit (0–255)          |
| `uptime`  | int  | Milliseconds since boot                  |
| `clients` | int  | Number of WiFi clients connected         |

### 2.6 Configuration — `config`

Adjust runtime parameters.

```json
{ "cmd": "config", "max_speed": 200 }
```

| Field       | Type | Range   | Description                |
|-------------|------|---------|----------------------------|
| `max_speed` | int  | 0–255   | Limit maximum motor output |

**Response:**

```json
{ "resp": "ack", "cmd": "config", "max_spd": 200 }
```

---

## 3. Responses  (ESP32 → Phone)

Every response is a JSON object with a **`resp`** field.

| `resp` value | Meaning                  | When sent                        |
|--------------|--------------------------|----------------------------------|
| `"pong"`     | Ping reply               | In response to `ping`            |
| `"status"`   | Robot state              | In response to `status`          |
| `"ack"`      | Command acknowledged     | In response to `stop`, `config`  |
| `"error"`    | Something went wrong     | Invalid JSON, unknown command    |

### Error response format

```json
{ "resp": "error", "msg": "description of what went wrong" }
```

Possible error messages:
- `"invalid JSON"` — packet could not be parsed
- `"missing cmd field"` — JSON object has no `cmd` key
- `"unknown command"` — `cmd` value not recognised

---

## 4. Safety Features

The firmware includes several safety mechanisms the app should be aware of:

### 4.1 Dead-man's switch (command timeout)

If **no valid command** is received for **500 ms**, motors are automatically
stopped. The app must send commands continuously (e.g. joystick position every
30–50 ms) to keep the robot moving.

**Implication for the app:** When the user lifts their finger from the
joystick, you can either:
- Send `{ "cmd": "joy", "x": 0, "y": 0 }` (explicit stop), or
- Simply stop sending — the robot will stop after 500 ms.

Sending an explicit stop is recommended for immediate response.

### 4.2 Client disconnect

When the WiFi client disconnects, motors are immediately stopped.

### 4.3 Max speed limiter

Motor outputs are clamped to `max_speed` (default 255). The app can lower this
at runtime with the `config` command — useful for a "beginner mode" slider.

---

## 5. Differential Drive Mixing

When using the `joy` command, the ESP32 converts joystick axes to individual
motor speeds using this formula:

```
left_motor  = (y + x) * max_speed
right_motor = (y - x) * max_speed
```

If either value exceeds ±max_speed, both are scaled down proportionally.

| Joystick position       | Left motor | Right motor | Result            |
|-------------------------|------------|-------------|-------------------|
| y=1.0, x=0.0            | +max       | +max        | Straight forward  |
| y=−1.0, x=0.0           | −max       | −max        | Straight reverse  |
| y=0.0, x=+1.0           | +max       | −max        | Spin right        |
| y=0.0, x=−1.0           | −max       | +max        | Spin left         |
| y=0.7, x=0.3            | +max       | +0.57×max   | Forward right arc |

---

## 6. Recommended App Implementation

### Joystick UI
- Use a virtual dual-axis joystick (thumb pad).
- Map the joystick X/Y to the `joy` command's `x`/`y` fields.
- Send joystick updates at 20–50 Hz while the user's finger is on the pad.
- On finger release, send `{ "cmd": "stop" }` for immediate halt.

### Connection management
- On app launch, connect to WiFi `RobotControl`.
- Send `{ "cmd": "ping" }` periodically (every 1–2 s) to detect connection
  loss. Expect a `pong` response within ~100 ms.
- Show connection status indicator in the UI.

### Speed control
- Provide a slider (0–255) mapped to `{ "cmd": "config", "max_speed": N }`.
- This lets the user limit top speed for indoor use or learning.

### Status display (optional)
- Poll `{ "cmd": "status" }` every 1–2 s to display motor speeds and uptime.

---

## 7. Quick-Start Example Session

```
Phone                          ESP32
  |                              |
  |--- WiFi connect ----------->|
  |                              |
  |--- {"cmd":"ping"}        -->|
  |<-- {"resp":"pong",       ---|
  |     "v":"1.0.0",            |
  |     "uptime":1234}          |
  |                              |
  |--- {"cmd":"config",      -->|
  |     "max_speed":180}        |
  |<-- {"resp":"ack",        ---|
  |     "cmd":"config",         |
  |     "max_spd":180}          |
  |                              |
  |--- {"cmd":"joy",         -->|  (repeated at ~30 Hz)
  |     "x":0.0,"y":0.8}       |
  |                              |
  |--- {"cmd":"joy",         -->|
  |     "x":0.3,"y":0.5}       |
  |                              |
  |--- {"cmd":"stop"}        -->|  (user releases joystick)
  |<-- {"resp":"ack",        ---|
  |     "cmd":"stop"}           |
  |                              |
  |--- {"cmd":"status"}      -->|
  |<-- {"resp":"status",     ---|
  |     "l":0,"r":0,            |
  |     "max_spd":180,          |
  |     "uptime":5678,          |
  |     "clients":1}            |
```

---

## 8. Hardware Reference

```
            ┌──────────────┐
            │    ESP32     │
            │              │
  GPIO 32 ──┤ L_RPWM       ├── GPIO 25  R_RPWM
  GPIO 33 ──┤ L_LPWM       ├── GPIO 26  R_LPWM
  GPIO 18 ──┤ L_R_EN       ├── GPIO 27  R_R_EN
  GPIO 19 ──┤ L_L_EN       ├── GPIO 14  R_L_EN
            │              │
  GPIO  2 ──┤ STATUS LED   │
            └──────────────┘
               │      │
          ┌────┘      └────┐
          ▼                ▼
    ┌──────────┐    ┌──────────┐
    │ BTS7960  │    │ BTS7960  │
    │ (Left)   │    │ (Right)  │
    └────┬─────┘    └────┬─────┘
         │               │
    ┌────┴─────┐    ┌────┴─────┐
    │  Left    │    │  Right   │
    │  Wiper   │    │  Wiper   │
    │  Motor   │    │  Motor   │
    └──────────┘    └──────────┘
```

### Robot layout

```
        Front (castor wheels)
      ┌───────────────────────┐
      │   ○               ○   │   ← castor wheels (passive)
      │                       │
      │       [ESP32]         │
      │                       │
      │  ◉               ◉   │   ← wiper motors (driven)
      └───────────────────────┘
        Rear (drive wheels)
        Left             Right
```
