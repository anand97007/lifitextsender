# LiFi Text Sender + Arduino Receiver

This project sends **only the text typed by the user at that moment** through the phone flashlight. There is no preset-word list and no hard-coded message.

## Protocol
- UTF-8 payload, so normal English text, numbers, spaces and punctuation are supported.
- 12-bit sync: `101010101010`
- 16-bit payload length
- payload bytes
- 8-bit XOR checksum
- bit `0`: flashlight ON ~150 ms
- bit `1`: flashlight ON ~350 ms
- OFF gap: ~150 ms

The Arduino receiver uses the same protocol.

## Hardware
### LM393 LDR module
- VCC -> Arduino 5V
- GND -> Arduino GND
- DO -> Arduino D2
- AO -> not connected

### 16x2 I2C LCD
- VCC -> 5V
- GND -> GND
- SDA -> A4 (UNO)
- SCL -> A5 (UNO)

Adjust the LM393 potentiometer until the DO output changes reliably when the phone flashlight is ON/OFF.

## Build APK with GitHub Actions
1. Upload this folder to a GitHub repository.
2. Push to `main`.
3. Open **Actions** -> **Build Android APK**.
4. Download the `LiFiTextSender-debug-apk` artifact.

You can also open the project in Android Studio and build `app -> assembleDebug`.
