# QLab2OSC Converter

This tool converts OSC Play recording sessions into QLab Network OSC cues, allowing you to replay recorded OSC automation sequences through QLab.

## Overview

The QLab2OSC Converter reads OSC recording sessions created by OSC Play and generates corresponding Network OSC cues in QLab using the QLab OSC API. Each recorded OSC message becomes a Network OSC cue with appropriate timing.

## Features

- **Automatic Cue Creation**: Converts each OSC message to a Network OSC cue
- **Timing Preservation**: Maintains original timing between messages using pre-wait times
- **Auto-Continue Setup**: Configures cues for seamless playback
- **Configurable Options**: Flexible configuration via JSON file
- **Session Organization**: Creates a dedicated cue list for each converted session

## Usage

### Command Line

```bash
# Basic usage
java -cp "target/osc-play-1.1.1-jar-with-dependencies.jar" xyz.theforks.QLab2OSCConverter <session-name>

# With custom configuration
java -cp "target/osc-play-1.1.1-jar-with-dependencies.jar" xyz.theforks.QLab2OSCConverter <session-name> --config my_config.json
```

### Shell Scripts

For convenience, use the provided shell scripts:

**macOS/Linux:**
```bash
./qlab2osc.sh mission1
./qlab2osc.sh mission1 --config my_config.json
```

**Windows:**
```cmd
qlab2osc.bat mission1
qlab2osc.bat mission1 --config my_config.json
```

## Configuration

The converter uses a JSON configuration file (default: `qlab2osc_config.json`) with these options:

```json
{
  "qlabHost": "localhost",        // QLab host address
  "qlabPort": 53000,              // QLab OSC port
  "workspaceId": null,            // QLab workspace ID (null = current workspace)
  "destinationHost": "localhost", // OSC destination host for the cues
  "destinationPort": 3030,        // OSC destination port for the cues
  "oscPatch": 1,                  // QLab OSC patch number to use
  "createCueList": true,          // Create a new cue list for the session
  "usePreWait": true,             // Use pre-wait timing for message delays
  "autoContinue": true,           // Set cues to auto-continue
  "timeScale": 1.0                // Time scaling factor (1.0 = original timing)
}
```

## Requirements

1. **Java 17+** with JavaFX SDK
2. **QLab** running and configured to accept OSC on port 53000
3. **OSC Play recordings** in the `recordings/` directory

## QLab Setup

1. **Enable OSC**: In QLab, ensure OSC is enabled in Network preferences
2. **OSC Port**: QLab should be listening on port 53000 (default)
3. **Workspace**: Open the workspace where you want to create the cues
4. **OSC Patches**: Configure OSC output patches in QLab to match your destination

## Generated Cues

For each OSC message in the recording, the converter creates:

- **Network OSC Cue** with the message content
- **Cue Number** (sequential: 1, 2, 3, ...)
- **Cue Name** based on OSC address and first argument
- **Pre-Wait Time** to maintain original timing
- **Auto-Continue** for seamless playback
- **OSC Patch** assignment for output routing

## Example Workflow

1. **Record OSC session** using OSC Play:
   ```bash
   ./oscplay.sh --port 3030
   # Record your automation, save as "myshow"
   ```

2. **Convert to QLab cues**:
   ```bash
   ./qlab2osc.sh myshow
   ```

3. **Configure QLab**:
   - Set up OSC output patch to your destination
   - Review and adjust cue timing if needed
   - Test playback

4. **Playback**:
   - Use QLab's normal playback controls
   - Cues will fire automatically with correct timing

## Troubleshooting

**"Recording file not found"**: Ensure the recording exists in `recordings/<name>.json`

**"Connection failed"**: Check that QLab is running and OSC is enabled

**"Unknown host"**: Verify QLab host address in configuration

**Timing issues**: Adjust `timeScale` in configuration (0.5 = half speed, 2.0 = double speed)

**Missing cues**: Check QLab workspace and ensure proper permissions

## Notes

- The converter connects to the current workspace if no workspace ID is specified
- All timing is relative to the first message in the recording
- Messages with identical timestamps are processed in sequence
- The converter does not modify existing QLab cues