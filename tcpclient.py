from pythonosc.tcp_client import SimpleTCPClient

ip = "127.0.0.1"
port = 8000

# Use mode=1.0 for size-prefix framing (OSC 1.0) - compatible with TouchOSC
# mode=1.1 would use SLIP framing (OSC 1.1)
client = SimpleTCPClient(ip, port, mode=1.0)

client.send_message("/test/message", 0.5)
print("Message sent")
