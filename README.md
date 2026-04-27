# PhoneKB

A minimal Android app that emulates a Bluetooth keyboard (HID) so the phone can send key presses to a Linux machine.

Requirements:
- Android 9+ (API 28+)
- A phone that supports Android's Bluetooth HID Device API
- Linux machine with BlueZ (bluetoothd)

Build & run:
1. Open this folder in Android Studio.
2. Build and install on your phone.
3. Grant Bluetooth permissions when prompted.

Usage (Linux side):
1. Ensure bluetoothd is running: `sudo systemctl enable --now bluetooth`
2. Use `bluetoothctl` to pair/trust/connect:

   sudo bluetoothctl
   power on
   agent on
   default-agent
   scan on    # find "PhoneKB" or your phone
   pair XX:XX:XX:XX:XX:XX
   trust XX:XX:XX:XX:XX:XX
   connect XX:XX:XX:XX:XX:XX

3. Once paired/connected, use the app ("Send 'a'") to send a key.

Notes & troubleshooting:
- If pairing fails, try pairing from Linux GUI (Blueman) or check `sudo journalctl -u bluetooth` for errors.
- This project is a minimal starting point; the HID implementation may need tuning for your device.
