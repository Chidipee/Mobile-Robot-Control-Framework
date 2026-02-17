#ifndef MOTOR_DRIVER_H
#define MOTOR_DRIVER_H

#include <Arduino.h>
#include "config.h"

// Detect ESP32 Arduino Core version for LEDC API compatibility.
// Core 3.x uses pin-based API; Core 2.x uses channel-based API.
#if defined(ESP_ARDUINO_VERSION) && (ESP_ARDUINO_VERSION >= ESP_ARDUINO_VERSION_VAL(3, 0, 0))
    #define LEDC_API_V3
#endif

class MotorDriver {
public:
    MotorDriver(uint8_t rpwm_pin, uint8_t lpwm_pin,
                uint8_t r_en_pin, uint8_t l_en_pin,
                uint8_t ch_fwd = 0, uint8_t ch_rev = 1)
        : _rpwm(rpwm_pin), _lpwm(lpwm_pin),
          _r_en(r_en_pin), _l_en(l_en_pin),
          _ch_fwd(ch_fwd), _ch_rev(ch_rev),
          _speed(0) {}

    void begin() {
        pinMode(_r_en, OUTPUT);
        pinMode(_l_en, OUTPUT);

        #ifdef LEDC_API_V3
        ledcAttach(_rpwm, PWM_FREQ, PWM_RESOLUTION);
        ledcAttach(_lpwm, PWM_FREQ, PWM_RESOLUTION);
        #else
        ledcSetup(_ch_fwd, PWM_FREQ, PWM_RESOLUTION);
        ledcAttachPin(_rpwm, _ch_fwd);
        ledcSetup(_ch_rev, PWM_FREQ, PWM_RESOLUTION);
        ledcAttachPin(_lpwm, _ch_rev);
        #endif

        enable();
        brake();
    }

    /// Set motor speed. Range: -255 (full reverse) to +255 (full forward).
    void setSpeed(int speed) {
        speed = constrain(speed, -255, 255);
        _speed = speed;

        if (speed > 0) {
            writePWM(_rpwm, _ch_fwd, (uint32_t)speed);
            writePWM(_lpwm, _ch_rev, 0);
        } else if (speed < 0) {
            writePWM(_rpwm, _ch_fwd, 0);
            writePWM(_lpwm, _ch_rev, (uint32_t)(-speed));
        } else {
            brake();
        }
    }

    /// Active brake — both low-side FETs conduct, shorting motor terminals.
    void brake() {
        writePWM(_rpwm, _ch_fwd, 0);
        writePWM(_lpwm, _ch_rev, 0);
        _speed = 0;
    }

    /// Coast — disable half-bridges so motor spins freely.
    void coast() {
        disable();
        writePWM(_rpwm, _ch_fwd, 0);
        writePWM(_lpwm, _ch_rev, 0);
        _speed = 0;
    }

    void enable() {
        digitalWrite(_r_en, HIGH);
        digitalWrite(_l_en, HIGH);
    }

    void disable() {
        digitalWrite(_r_en, LOW);
        digitalWrite(_l_en, LOW);
    }

    int getSpeed() const { return _speed; }

private:
    uint8_t _rpwm, _lpwm, _r_en, _l_en;
    uint8_t _ch_fwd, _ch_rev;
    int     _speed;

    inline void writePWM(uint8_t pin, uint8_t channel, uint32_t duty) {
        #ifdef LEDC_API_V3
        ledcWrite(pin, duty);
        #else
        ledcWrite(channel, duty);
        #endif
    }
};

#endif
