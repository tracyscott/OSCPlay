#!/usr/bin/env python3
"""Send raw OSC over TCP without SLIP or size-prefix framing."""
import socket
from pythonosc.osc_message_builder import OscMessageBuilder

ip = "127.0.0.1"
port = 8000

# Build the OSC message
builder = OscMessageBuilder(address='/lx/mixer/master/fader')
builder.add_arg(0.5)
msg = builder.build()

# Send raw bytes over TCP (no framing)
with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
    s.connect((ip, port))
    s.sendall(msg.dgram)
    print(f"Sent {len(msg.dgram)} bytes: {msg.dgram.hex()}")
