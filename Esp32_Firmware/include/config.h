#ifndef CONFIG_H
#define CONFIG_H

// =============================================================================
//  WiFi Access Point Configuration
// =============================================================================
#define WIFI_SSID           "RobotControl"
#define WIFI_PASSWORD       "robot1234"
#define WIFI_CHANNEL        1
#define WIFI_MAX_CONN       2

// =============================================================================
//  UDP Configuration
//  The ESP32 listens on this port for JSON commands from the phone app.
//  The ESP32 AP IP is always 192.168.4.1
// =============================================================================
#define UDP_PORT            4210
#define UDP_BUFFER_SIZE     256

// =============================================================================
//  BTS7960 Motor Driver Pin Assignments
//
//  Each BTS7960 has:
//    RPWM  - PWM input for forward rotation
//    LPWM  - PWM input for reverse rotation
//    R_EN  - Right half-bridge enable (HIGH = enabled)
//    L_EN  - Left half-bridge enable  (HIGH = enabled)
//
//  Wiring: Connect VCC to motor power supply, GND shared with ESP32.
//          Logic level pins connect directly to ESP32 GPIO.
// =============================================================================

// Left Motor (Motor A)
#define MOTOR_L_RPWM        32
#define MOTOR_L_LPWM        33
#define MOTOR_L_R_EN        18
#define MOTOR_L_L_EN        19

// Right Motor (Motor B)
#define MOTOR_R_RPWM        25
#define MOTOR_R_LPWM        26
#define MOTOR_R_R_EN        27
#define MOTOR_R_L_EN        14

// =============================================================================
//  PWM Configuration
// =============================================================================
#define PWM_FREQ            20000   // 20 kHz (above audible range)
#define PWM_RESOLUTION      8       // 8-bit resolution (duty: 0-255)

// LEDC channels — only used on ESP32 Arduino Core < 3.0
#define LEDC_CH_L_FWD       0
#define LEDC_CH_L_REV       1
#define LEDC_CH_R_FWD       2
#define LEDC_CH_R_REV       3

// =============================================================================
//  Safety Configuration
// =============================================================================
#define COMMAND_TIMEOUT_MS  500     // Motors stop if no command within this window
#define MAX_SPEED_DEFAULT   255     // Default maximum motor speed (0-255)

// =============================================================================
//  Status LED (built-in LED on most ESP32 dev boards)
// =============================================================================
#define STATUS_LED_PIN      2

// =============================================================================
//  Firmware Metadata
// =============================================================================
#define FIRMWARE_VERSION    "1.0.0"

#endif
