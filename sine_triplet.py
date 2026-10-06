#!/usr/bin/env python3
"""Send three sine waves as one OSC message with three float args.

Each value is in 0..1. The default frequencies are 0.5, 0.25 and 0.125 Hz. By default it sends
/lx/modulation/Angles/angle to OSCPlay's input, where a Splitter node turns it into angle1, angle2
and angle3.

Usage: python3 sine_triplet.py [--host 127.0.0.1] [--port 8000] [--rate 30] [--duration 0]
"""

import argparse
import math
import socket
import struct
import time


def osc_string(s):
    """Encode an OSC string: null-terminated, padded to a multiple of 4 bytes."""
    b = s.encode("utf-8") + b"\0"
    return b + b"\0" * (-len(b) % 4)


def osc_message(address, floats):
    return (osc_string(address)
            + osc_string("," + "f" * len(floats))
            + b"".join(struct.pack(">f", v) for v in floats))


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--host", default="127.0.0.1", help="OSCPlay input host")
    parser.add_argument("--port", type=int, default=8000, help="OSCPlay input port")
    parser.add_argument("--address", default="/lx/modulation/Angles/angle")
    parser.add_argument("--freqs", type=float, nargs=3, default=[0.5, 0.25, 0.125], metavar="HZ")
    parser.add_argument("--rate", type=float, default=30, help="Messages per second")
    parser.add_argument("--duration", type=float, default=0, help="Seconds to run (0 = until Ctrl-C)")
    args = parser.parse_args()

    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    period = 1.0 / args.rate
    start = time.monotonic()
    next_send = start
    count = 0
    print(f"Sending {args.address} to {args.host}:{args.port} at {args.rate:g} msg/s, "
          f"freqs {args.freqs} Hz. Ctrl-C to stop.", flush=True)
    try:
        while True:
            t = time.monotonic() - start
            if args.duration and t >= args.duration:
                break
            values = [0.5 + 0.5 * math.sin(2 * math.pi * f * t) for f in args.freqs]
            sock.sendto(osc_message(args.address, values), (args.host, args.port))
            count += 1
            if count % int(args.rate) == 0:
                print(f"t={t:6.2f}s  " + "  ".join(f"{v:.3f}" for v in values), flush=True)
            # Schedule against the start time so the rate doesn't drift
            next_send += period
            time.sleep(max(0.0, next_send - time.monotonic()))
    except KeyboardInterrupt:
        pass
    print(f"Sent {count} messages")


if __name__ == "__main__":
    main()
