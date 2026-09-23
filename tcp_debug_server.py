#!/usr/bin/env python3
"""Simple TCP server to capture raw bytes from OSC clients."""
import socket

HOST = '127.0.0.1'
PORT = 8000

with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
    s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    s.bind((HOST, PORT))
    s.listen(1)
    print(f"Listening on {HOST}:{PORT}...")

    while True:
        conn, addr = s.accept()
        with conn:
            print(f"\nConnection from {addr}")
            data = conn.recv(1024)
            print(f"Received {len(data)} bytes:")
            print(f"  Hex: {data.hex()}")
            print(f"  Raw: {data}")
