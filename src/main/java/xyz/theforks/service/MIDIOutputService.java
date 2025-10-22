package xyz.theforks.service;

import java.io.IOException;
import java.util.List;

import javax.sound.midi.*;

import com.illposed.osc.OSCMessage;
import com.illposed.osc.OSCSerializeException;
import xyz.theforks.nodes.NodeChain;
import xyz.theforks.ui.MonitorWindow;

/**
 * MIDI output service that converts OSC messages to MIDI.
 * Supports OSC patterns like /midi/note and /midi/cc.
 */
public class MIDIOutputService implements OutputService {
    private final String id;
    private String midiDeviceName;
    private MidiDevice midiDevice;
    private Receiver midiReceiver;
    private final NodeChain nodeChain;
    private boolean enabled = true;
    private MonitorWindow monitorWindow;
    private ProxyDelayProcessor delayProcessor;

    public MIDIOutputService(String id) {
        this.id = id;
        this.nodeChain = new NodeChain(NodeChain.Context.PROXY);
    }

    public void setMidiDeviceName(String midiDeviceName) {
        this.midiDeviceName = midiDeviceName;
    }

    public String getMidiDeviceName() {
        return midiDeviceName;
    }

    @Override
    public void start() throws IOException {
        try {
            // Find the MIDI device by name
            MidiDevice.Info[] infos = MidiSystem.getMidiDeviceInfo();
            MidiDevice.Info targetInfo = null;

            for (MidiDevice.Info info : infos) {
                if (info.getName().equals(midiDeviceName)) {
                    targetInfo = info;
                    break;
                }
            }

            if (targetInfo == null) {
                throw new IOException("MIDI device not found: " + midiDeviceName);
            }

            // Open the MIDI device
            midiDevice = MidiSystem.getMidiDevice(targetInfo);
            if (!midiDevice.isOpen()) {
                midiDevice.open();
            }

            // Get the receiver
            midiReceiver = midiDevice.getReceiver();

            System.out.println("MIDI output started on device: " + midiDeviceName);
        } catch (MidiUnavailableException e) {
            throw new IOException("Failed to open MIDI device: " + midiDeviceName, e);
        }
    }

    @Override
    public void stop() {
        if (midiReceiver != null) {
            midiReceiver.close();
            midiReceiver = null;
        }
        if (midiDevice != null && midiDevice.isOpen()) {
            midiDevice.close();
            midiDevice = null;
        }
        System.out.println("MIDI output stopped");
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public boolean isStarted() {
        return midiDevice != null && midiDevice.isOpen();
    }

    @Override
    public void send(OSCMessage message) throws IOException, OSCSerializeException {
        send(message, false, false);
    }

    @Override
    public void send(OSCMessage message, boolean bypassEnabledCheck) throws IOException, OSCSerializeException {
        send(message, bypassEnabledCheck, false);
    }

    @Override
    public void send(OSCMessage message, boolean bypassEnabledCheck, boolean bypassNodeChain) throws IOException, OSCSerializeException {
        // If bypassing enabled check and device is not open, start it
        if (bypassEnabledCheck && !isStarted()) {
            start();
        }

        if (midiReceiver == null || message == null) {
            return;
        }

        // Only check enabled flag if not bypassing
        if (!bypassEnabledCheck && !enabled) {
            return;
        }

        if (bypassNodeChain) {
            // Send directly without node chain processing
            convertAndSendMIDI(message);
        } else {
            // Apply node chain to message
            List<xyz.theforks.model.MessageRequest> requests = nodeChain.processMessage(message);

            for (xyz.theforks.model.MessageRequest req : requests) {
                if (req.isImmediate()) {
                    // Send immediately
                    convertAndSendMIDI(req.getMessage());
                } else if (delayProcessor != null && delayProcessor.isRunning()) {
                    // Schedule delayed message through the delay processor
                    delayProcessor.scheduleMessage(req, id);
                } else {
                    // No delay processor available, send immediately as fallback
                    System.err.println("Warning: Delayed message requested but no delay processor available, sending immediately");
                    convertAndSendMIDI(req.getMessage());
                }
            }
        }
    }

    /**
     * Convert an OSC message to MIDI and send it.
     * Supports patterns:
     * - /midi/note <noteNumber> [velocity] [channel]
     * - /midi/cc <ccNumber> <value> [channel]
     */
    private void convertAndSendMIDI(OSCMessage message) {
        String address = message.getAddress();
        List<Object> args = message.getArguments();

        try {
            if (address.startsWith("/midi/note")) {
                handleNoteMessage(args);
            } else if (address.startsWith("/midi/cc")) {
                handleCCMessage(args);
            } else {
                // Not a MIDI message, ignore
                System.err.println("Warning: MIDI output received non-MIDI OSC message: " + address);
            }

            // Send to monitor window if one is open
            if (monitorWindow != null && monitorWindow.isOpen()) {
                monitorWindow.addMessage(message);
            }
        } catch (Exception e) {
            System.err.println("Error converting OSC to MIDI: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Handle /midi/note messages.
     * Format: /midi/note <noteNumber> [velocity] [channel]
     */
    private void handleNoteMessage(List<Object> args) throws InvalidMidiDataException {
        if (args.isEmpty()) {
            System.err.println("MIDI note message missing note number");
            return;
        }

        // Parse note number
        int noteNumber = getIntArg(args, 0, 60); // Default to middle C
        int velocity = getIntArg(args, 1, 127);  // Default velocity
        int channel = getIntArg(args, 2, 0);     // Default to channel 0

        // Validate ranges
        noteNumber = Math.max(0, Math.min(127, noteNumber));
        velocity = Math.max(0, Math.min(127, velocity));
        channel = Math.max(0, Math.min(15, channel));

        // Create and send MIDI note on message
        ShortMessage noteOn = new ShortMessage();
        noteOn.setMessage(ShortMessage.NOTE_ON, channel, noteNumber, velocity);
        midiReceiver.send(noteOn, -1);

        System.out.println("MIDI Note On: channel=" + channel + " note=" + noteNumber + " velocity=" + velocity);
    }

    /**
     * Handle /midi/cc messages.
     * Format: /midi/cc <ccNumber> <value> [channel]
     */
    private void handleCCMessage(List<Object> args) throws InvalidMidiDataException {
        if (args.size() < 2) {
            System.err.println("MIDI CC message requires at least CC number and value");
            return;
        }

        // Parse CC number and value
        int ccNumber = getIntArg(args, 0, 0);
        int value = getIntArg(args, 1, 0);
        int channel = getIntArg(args, 2, 0);  // Default to channel 0

        // Validate ranges
        ccNumber = Math.max(0, Math.min(127, ccNumber));
        value = Math.max(0, Math.min(127, value));
        channel = Math.max(0, Math.min(15, channel));

        // Create and send MIDI control change message
        ShortMessage cc = new ShortMessage();
        cc.setMessage(ShortMessage.CONTROL_CHANGE, channel, ccNumber, value);
        midiReceiver.send(cc, -1);

        System.out.println("MIDI CC: channel=" + channel + " cc=" + ccNumber + " value=" + value);
    }

    /**
     * Get an integer argument from the OSC message arguments.
     * Handles Integer, Float, and String types.
     */
    private int getIntArg(List<Object> args, int index, int defaultValue) {
        if (index >= args.size()) {
            return defaultValue;
        }

        Object arg = args.get(index);
        if (arg instanceof Integer) {
            return (Integer) arg;
        } else if (arg instanceof Float) {
            return ((Float) arg).intValue();
        } else if (arg instanceof String) {
            try {
                return Integer.parseInt((String) arg);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    @Override
    public NodeChain getNodeChain() {
        return nodeChain;
    }

    @Override
    public void setDelayProcessor(ProxyDelayProcessor delayProcessor) {
        this.delayProcessor = delayProcessor;
    }

    @Override
    public ProxyDelayProcessor getDelayProcessor() {
        return delayProcessor;
    }

    @Override
    public void setMonitorWindow(MonitorWindow monitorWindow) {
        this.monitorWindow = monitorWindow;
    }

    @Override
    public MonitorWindow getMonitorWindow() {
        return monitorWindow;
    }

    /**
     * Get a list of available MIDI output devices.
     */
    public static List<String> getAvailableMIDIDevices() {
        java.util.ArrayList<String> devices = new java.util.ArrayList<>();
        MidiDevice.Info[] infos = MidiSystem.getMidiDeviceInfo();

        for (MidiDevice.Info info : infos) {
            try {
                MidiDevice device = MidiSystem.getMidiDevice(info);
                // Check if the device can receive MIDI messages (has a receiver)
                if (device.getMaxReceivers() != 0) {
                    devices.add(info.getName());
                }
            } catch (MidiUnavailableException e) {
                // Skip devices that can't be opened
            }
        }

        return devices;
    }
}
