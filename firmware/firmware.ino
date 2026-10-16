#include <Servo.h>

// ============================
// Romeo BLE mini v2.0 (DFR0351)
// BLE UART via hardware Serial (D0/D1)
// USB and BLE share the same UART passthrough
// ============================

// ============================
// Servo Setup
// ============================

Servo servo1;
Servo servo2;

#define SERVO1_PIN 9
#define SERVO2_PIN 10

String cmdBuffer = "";

// ============================
// Logging Helpers
// ============================

void logLine(String msg) {
  Serial.println(msg);
}

// ============================
// Setup
// ============================

void setup() {
  // Must match the BLE chip's stored UART baud (Bluno default 115200).
  // On USB the PC overrides that baud, which hid the mismatch; on a
  // standalone supply the chip uses its stored value, so 9600 got no data.
  Serial.begin(115200);

  // Set the start angle before attach() so the very first pulse is 90°,
  // matching the app's centered joystick (no jump at power-up).
  servo1.write(90);
  servo2.write(90);

  servo1.attach(SERVO1_PIN);
  servo2.attach(SERVO2_PIN);

  logLine("Romeo BLE Ready");
}

// ============================
// Main Loop
// ============================

void loop() {
  // No per-character echo: at 9600 baud it took longer to print than the
  // app's ~20 updates/s allow, stalling the loop and overflowing the RX buffer.
  while (Serial.available()) {
    char c = Serial.read();
    if (c == '\n') {
      processCommand(cmdBuffer);
      cmdBuffer = "";
    } else if (c != '\r') {
      cmdBuffer += c;
    }
  }
}

// ============================
// Command Processing
// ============================

void processCommand(String cmd) {
  cmd.trim();

  if (cmd.length() == 0) {
    return;
  }

  if (cmd.startsWith("S1:")) {
    int angle = constrain(cmd.substring(3).toInt(), 0, 180);
    servo1.write(angle);
    logLine("S1 -> " + String(angle) + " deg");
  } else if (cmd.startsWith("S2:")) {
    int angle = constrain(cmd.substring(3).toInt(), 0, 180);
    servo2.write(angle);
    logLine("S2 -> " + String(angle) + " deg");
  } else {
    logLine("Unknown CMD: " + cmd);
  }
}
