#include <Wire.h>
#include <LiquidCrystal_I2C.h>

LiquidCrystal_I2C lcd(0x27, 16, 2);

#define LDR_DO 2

const unsigned long ZERO_MS = 150;
const unsigned long ONE_MS  = 350;
const unsigned long MIN_PULSE_MS = 80;
const unsigned long MAX_PULSE_MS = 600;
const unsigned long GAP_MS = 150;
const char *SYNC = "101010101010";

bool readPulse(unsigned long &duration) {
  // Wait for light ON.
  unsigned long t = millis();
  while (digitalRead(LDR_DO) == LOW) {
    if (millis() - t > 1500) return false;
  }
  unsigned long start = millis();
  while (digitalRead(LDR_DO) == HIGH) {
    if (millis() - start > MAX_PULSE_MS) return false;
  }
  duration = millis() - start;
  return duration >= MIN_PULSE_MS && duration <= MAX_PULSE_MS;
}

char pulseToBit(unsigned long d) {
  return (d >= 250) ? '1' : '0';
}

bool readBit(char &bit) {
  unsigned long d;
  if (!readPulse(d)) return false;
  bit = pulseToBit(d);
  delay(GAP_MS);
  return true;
}

bool findSync() {
  String window = "";
  while (true) {
    char b;
    if (!readBit(b)) return false;
    window += b;
    if (window.length() > 12) window.remove(0, 1);
    if (window.length() == 12 && window == SYNC) return true;
  }
}

bool readNBits(int n, unsigned long &value) {
  value = 0;
  for (int i = 0; i < n; i++) {
    char b;
    if (!readBit(b)) return false;
    value = (value << 1) | (b == '1' ? 1 : 0);
  }
  return true;
}

void showText(const String &text) {
  lcd.clear();
  lcd.setCursor(0, 0);
  lcd.print("Received:");
  if (text.length() <= 16) {
    lcd.setCursor(0, 1);
    lcd.print(text);
    delay(3000);
    return;
  }
  // Scroll the complete received sentence across the 16x2 display.
  String s = "                " + text + "                ";
  for (int i = 0; i <= (int)s.length() - 16; i++) {
    lcd.setCursor(0, 1);
    lcd.print(s.substring(i, i + 16));
    delay(300);
  }
}

void setup() {
  pinMode(LDR_DO, INPUT);
  lcd.init();
  lcd.backlight();
  lcd.clear();
  lcd.setCursor(0, 0);
  lcd.print("LiFi Receiver");
  lcd.setCursor(0, 1);
  lcd.print("Waiting...");
}

void loop() {
  if (!findSync()) return;

  unsigned long length;
  if (!readNBits(16, length)) return;
  if (length == 0 || length > 512) return; // safety limit

  String text = "";
  text.reserve(length);
  byte checksum = 0;

  lcd.clear();
  lcd.setCursor(0, 0);
  lcd.print("Receiving...");

  for (unsigned long i = 0; i < length; i++) {
    unsigned long v;
    if (!readNBits(8, v)) return;
    byte b = (byte)v;
    checksum ^= b;
    text += (char)b;
    lcd.setCursor(0, 1);
    String preview = text;
    if (preview.length() > 16) preview = preview.substring(preview.length() - 16);
    lcd.print("                ");
    lcd.setCursor(0, 1);
    lcd.print(preview);
    delay(10);
  }

  unsigned long receivedChecksum;
  if (!readNBits(8, receivedChecksum)) return;

  if (checksum == (byte)receivedChecksum) {
    showText(text);
  } else {
    lcd.clear();
    lcd.setCursor(0, 0);
    lcd.print("Checksum Error");
    lcd.setCursor(0, 1);
    lcd.print("Send again");
    delay(2000);
  }
  lcd.clear();
  lcd.setCursor(0, 0);
  lcd.print("Waiting...");
}
