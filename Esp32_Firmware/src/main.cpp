#include <Arduino.h>
#include <WiFi.h>
#include <WiFiUdp.h>
#include <ArduinoJson.h>

#include "config.h"
#include "motor_driver.h"

// =============================================================================
//  Motor driver instances
// =============================================================================
MotorDriver motorLeft(
    MOTOR_L_RPWM, MOTOR_L_LPWM, MOTOR_L_R_EN, MOTOR_L_L_EN,
    LEDC_CH_L_FWD, LEDC_CH_L_REV
);
MotorDriver motorRight(
    MOTOR_R_RPWM, MOTOR_R_LPWM, MOTOR_R_R_EN, MOTOR_R_L_EN,
    LEDC_CH_R_FWD, LEDC_CH_R_REV
);

// =============================================================================
//  Networking
// =============================================================================
WiFiUDP udp;
char packetBuffer[UDP_BUFFER_SIZE];
IPAddress clientIP;
uint16_t  clientPort = 0;

// =============================================================================
//  Runtime state
// =============================================================================
int           maxSpeed         = MAX_SPEED_DEFAULT;
unsigned long lastCommandTime  = 0;
bool          motorsActive     = false;
uint8_t       lastClientCount  = 0;

// Forward declarations
void processCommand(const char* json);
void sendResponse(JsonDocument& doc);
void applyJoystick(float x, float y);
void applyMotorSpeeds(int left, int right);
void stopMotors();
void handleSafetyTimeout();
void handleClientDisconnect();
void updateStatusLED();

// =============================================================================
//  setup()
// =============================================================================
void setup() {
    Serial.begin(115200);
    Serial.println();
    Serial.println("=============================================");
    Serial.println("  Dist0rted_Sentinel  v" FIRMWARE_VERSION);
    Serial.println("=============================================");

    // Status LED
    pinMode(STATUS_LED_PIN, OUTPUT);
    digitalWrite(STATUS_LED_PIN, LOW);

    // Initialise motor drivers
    motorLeft.begin();
    motorRight.begin();
    Serial.println("[OK] Motor drivers initialised");

    // Start WiFi Access Point
    WiFi.mode(WIFI_AP);
    WiFi.softAP(WIFI_SSID, WIFI_PASSWORD, WIFI_CHANNEL, 0, WIFI_MAX_CONN);
    delay(100);

    Serial.printf("[OK] WiFi AP started  SSID: %s  PASS: %s\n", WIFI_SSID, WIFI_PASSWORD);
    Serial.print("[OK] AP IP address: ");
    Serial.println(WiFi.softAPIP());

    // Start UDP listener
    udp.begin(UDP_PORT);
    Serial.printf("[OK] UDP server listening on port %d\n", UDP_PORT);

    Serial.println("=============================================");
    Serial.println("  Waiting for connections ...");
    Serial.println("=============================================");
    digitalWrite(STATUS_LED_PIN, HIGH);
}

// =============================================================================
//  loop()
// =============================================================================
void loop() {
    // ---- Process incoming UDP packets ----
    int packetSize = udp.parsePacket();
    if (packetSize > 0 && packetSize < UDP_BUFFER_SIZE) {
        int len = udp.read(packetBuffer, UDP_BUFFER_SIZE - 1);
        packetBuffer[len] = '\0';

        clientIP   = udp.remoteIP();
        clientPort = udp.remotePort();

        processCommand(packetBuffer);
    }

    // ---- Safety checks ----
    handleSafetyTimeout();
    handleClientDisconnect();

    // ---- Visual feedback ----
    updateStatusLED();
}

// =============================================================================
//  Command processing
//
//  All commands are JSON objects with a "cmd" field.
//  See PROTOCOL.md for the full specification.
// =============================================================================
void processCommand(const char* json) {
    JsonDocument doc;
    DeserializationError err = deserializeJson(doc, json);

    if (err) {
        Serial.printf("[ERR] JSON parse: %s\n", err.c_str());
        JsonDocument resp;
        resp["resp"] = "error";
        resp["msg"]  = "invalid JSON";
        sendResponse(resp);
        return;
    }

    const char* cmd = doc["cmd"];
    if (!cmd) {
        JsonDocument resp;
        resp["resp"] = "error";
        resp["msg"]  = "missing cmd field";
        sendResponse(resp);
        return;
    }

    lastCommandTime = millis();
    String command(cmd);

    // ---- Joystick control (primary for phone apps) ----
    if (command == "joy") {
        float x = doc["x"] | 0.0f;
        float y = doc["y"] | 0.0f;
        applyJoystick(x, y);
    }
    // ---- Direct motor speed control ----
    else if (command == "motor") {
        int l = doc["l"] | 0;
        int r = doc["r"] | 0;
        applyMotorSpeeds(l, r);
    }
    // ---- Emergency stop ----
    else if (command == "stop") {
        stopMotors();
        JsonDocument resp;
        resp["resp"] = "ack";
        resp["cmd"]  = "stop";
        sendResponse(resp);
    }
    // ---- Connection check ----
    else if (command == "ping") {
        JsonDocument resp;
        resp["resp"]   = "pong";
        resp["v"]      = FIRMWARE_VERSION;
        resp["uptime"] = millis();
        sendResponse(resp);
    }
    // ---- Status query ----
    else if (command == "status") {
        JsonDocument resp;
        resp["resp"]    = "status";
        resp["l"]       = motorLeft.getSpeed();
        resp["r"]       = motorRight.getSpeed();
        resp["max_spd"] = maxSpeed;
        resp["uptime"]  = millis();
        resp["clients"] = WiFi.softAPgetStationNum();
        sendResponse(resp);
    }
    // ---- Runtime configuration ----
    else if (command == "config") {
        if (doc["max_speed"].is<int>()) {
            maxSpeed = constrain((int)doc["max_speed"], 0, 255);
            Serial.printf("[CFG] max_speed set to %d\n", maxSpeed);
        }
        JsonDocument resp;
        resp["resp"]    = "ack";
        resp["cmd"]     = "config";
        resp["max_spd"] = maxSpeed;
        sendResponse(resp);
    }
    // ---- Unknown command ----
    else {
        Serial.printf("[ERR] Unknown command: %s\n", cmd);
        JsonDocument resp;
        resp["resp"] = "error";
        resp["msg"]  = "unknown command";
        sendResponse(resp);
    }
}

// =============================================================================
//  Send a JSON response back to the last client that sent a command
// =============================================================================
void sendResponse(JsonDocument& doc) {
    if (clientPort == 0) return;

    String json;
    serializeJson(doc, json);

    udp.beginPacket(clientIP, clientPort);
    udp.print(json);
    udp.endPacket();
}

// =============================================================================
//  Differential-drive mixing from joystick axes
//
//      x : -1.0 (full left)  … 0 (centre) … +1.0 (full right)
//      y : -1.0 (full back)  … 0 (centre) … +1.0 (full forward)
//
//  Mixing:
//      left  = y + x
//      right = y - x
//  Values are normalised so neither exceeds ±1.0, then scaled by maxSpeed.
// =============================================================================
void applyJoystick(float x, float y) {
    x = constrain(x, -1.0f, 1.0f);
    y = constrain(y, -1.0f, 1.0f);

    float left  = y + x;
    float right = y - x;

    // Normalise so the larger value is at most ±1.0
    float peak = max(fabsf(left), fabsf(right));
    if (peak > 1.0f) {
        left  /= peak;
        right /= peak;
    }

    int leftSpeed  = (int)(left  * maxSpeed);
    int rightSpeed = (int)(right * maxSpeed);

    motorLeft.setSpeed(leftSpeed);
    motorRight.setSpeed(rightSpeed);
    motorsActive = (leftSpeed != 0 || rightSpeed != 0);
}

// =============================================================================
//  Apply raw motor speeds (clamped to ±maxSpeed)
// =============================================================================
void applyMotorSpeeds(int left, int right) {
    left  = constrain(left,  -maxSpeed, maxSpeed);
    right = constrain(right, -maxSpeed, maxSpeed);

    motorLeft.setSpeed(left);
    motorRight.setSpeed(right);
    motorsActive = (left != 0 || right != 0);
}

// =============================================================================
//  Immediately stop both motors (active brake)
// =============================================================================
void stopMotors() {
    motorLeft.brake();
    motorRight.brake();
    motorsActive = false;
    Serial.println("[CMD] Motors stopped");
}

// =============================================================================
//  Dead-man's switch — stop motors if no command arrives within the timeout
// =============================================================================
void handleSafetyTimeout() {
    if (motorsActive && (millis() - lastCommandTime > COMMAND_TIMEOUT_MS)) {
        stopMotors();
        Serial.println("[SAFETY] Command timeout — motors stopped");
    }
}

// =============================================================================
//  Stop motors when the WiFi client disconnects
// =============================================================================
void handleClientDisconnect() {
    uint8_t currentClients = WiFi.softAPgetStationNum();
    if (currentClients == 0 && lastClientCount > 0) {
        stopMotors();
        Serial.println("[WIFI] Client disconnected — motors stopped");
    }
    lastClientCount = currentClients;
}

// =============================================================================
//  LED feedback
//    - Solid HIGH          : AP running, no client connected
//    - Slow blink (1 Hz)   : Client connected, idle
//    - Fast blink (5 Hz)   : Client connected, motors active
// =============================================================================
void updateStatusLED() {
    static unsigned long lastToggle = 0;
    unsigned long now = millis();

    if (WiFi.softAPgetStationNum() == 0) {
        digitalWrite(STATUS_LED_PIN, HIGH);
        return;
    }

    unsigned long interval = motorsActive ? 100 : 500;
    if (now - lastToggle >= interval) {
        digitalWrite(STATUS_LED_PIN, !digitalRead(STATUS_LED_PIN));
        lastToggle = now;
    }
}
