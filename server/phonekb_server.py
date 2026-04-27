#!/usr/bin/env python3
"""
Simple TCP server for PhoneKB network mode.
Receives lines of UTF-8 text and uses xdotool to type them into the active X11 window.
Requires: sudo apt-get install xdotool
"""
import socket
import subprocess

HOST = '0.0.0.0'
PORT = 7777

s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
s.bind((HOST, PORT))
s.listen(1)
print(f"Listening on {HOST}:{PORT}")

while True:
    conn, addr = s.accept()
    print('Connected by', addr)
    with conn:
        buf = b''
        while True:
            data = conn.recv(4096)
            if not data:
                break
            buf += data
            while b"\n" in buf:
                line, buf = buf.split(b"\n", 1)
                try:
                    text = line.decode('utf-8')
                except Exception:
                    text = line.decode('latin-1')
                if not text:
                    continue
                print('Typing:', text)
                try:
                    subprocess.run(['xdotool', 'type', '--delay', '0', text], check=True)
                    subprocess.run(['xdotool', 'key', 'Return'], check=True)
                except Exception as e:
                    print('xdotool error', e)
