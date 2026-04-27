PhoneKB network & wired modes

TCP network mode
- On Linux: install xdotool: `sudo apt install xdotool`
- Run the server: `python3 server/phonekb_server.py`
- In the app: choose Network mode, enter host IP (Linux machine) and port (default 7777). Connect and Send text.

Wired (USB) using adb reverse
- Connect phone via USB and enable USB debugging
- On Linux run: `adb reverse tcp:7777 tcp:7777`
- In the app: check "Use adb reverse" and connect to 127.0.0.1:7777

Notes
- This server uses xdotool (X11). For Wayland, use appropriate compositor-specific tooling.
- For security, run the server only on trusted networks or use SSH tunnels.
