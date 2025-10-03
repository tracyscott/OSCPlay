# OSCPlay

**Record and replay OSC automation without the source application.**

OSCPlay is a Java-based OSC (Open Sound Control) proxy server that captures, records, and replays OSC messages. Perfect for installations where you need to recreate Ableton Live, TouchOSC, or other OSC-based automation without the original software.

## Features

- **OSC Proxy Server**: Captures OSC messages in real-time and forwards them to destinations
- **Multiple Outputs**: Fan out messages to multiple destinations simultaneously
- **Session Recording**: Save OSC automation as JSON sessions with precise timing
- **Session Playback**: Replay recorded sessions without the original OSC source
  - Synchronized audio playback support - play audio files alongside OSC automation
- **Manual Editing**: Edit recorded OSC messages directly through the UI
- **Sampler Banks**: Built-in pad interface for triggering recordings
  - MIDI integration - trigger sampler pads via MIDI input
  - OSC triggering - trigger pads remotely via OSC messages
- **Message Processing**: Chain configurable nodes to modify OSC messages:
  - Rename addresses with regex patterns
  - Apply moving averages for smoothing
  - Filter/drop messages by pattern
  - Runtime JavaScript processing for custom transformations
  - And more...
- **Cross-Platform**: Runs on Windows, macOS, and Linux
- **Both GUI and CLI**: Interactive JavaFX interface or command-line operation

## Quick Start

### Download

Download the latest `osc-play-X.X.X-shaded.jar` from the [Releases](../../releases) page.

### Running

**GUI Mode (Interactive):**
```bash
java -jar osc-play-1.1.1-shaded.jar --port 3030
```

**CLI Mode (Playback):**
```bash
# Windows
oscsession.bat mysession

# macOS/Linux
./oscsession.sh mysession
```

**Command Options:**
- `--port <port>` - Proxy server port (default: 3030)
- `--host <hostname>` - Destination host (default: localhost)
- `--session <name>` - Auto-load and play session (CLI mode)
- `--help` - Show help

## How It Works

1. **Setup**: Point your OSC controller (e.g., TouchOSC) to OSCPlay's proxy server
2. **Record**: OSCPlay forwards messages to your destination (e.g., Ableton) while recording
3. **Save**: Save the recorded session as a JSON file in your project
4. **Replay**: Play back the session on any computer without the original OSC source

OSCPlay stores projects in `~/Documents/OSCPlay/Projects/` with recordings, node configurations, and processing scripts organized per-project.

## Building from Source

Requires Java 17 and Maven:

```bash
mvn clean package
java -jar target/osc-play-1.1.1-shaded.jar --port 3030
```

## Documentation

- [CLAUDE.md](CLAUDE.md) - Developer documentation and architecture details
- Message processing nodes - Configure via the GUI's "Manage Node Chains" interface
- JavaScript scripting - Use ScriptNode for runtime-configurable message processing

## License

[Add license information]

## Version

Current version: 1.1.1



