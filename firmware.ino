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
  Serial.begin(9600);

  servo1.attach(SERVO1_PIN);
  servo2.attach(SERVO2_PIN);

  servo1.write(0);
  servo2.write(0);

  logLine("Romeo BLE Ready");
}

// ============================
// Main Loop
// ============================

void loop() {
  while (Serial.available()) {
    char c = Serial.read();
    Serial.print("RX: ");
    Serial.println(c);
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

  Serial.print("CMD: ");
  Serial.println(cmd);

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
