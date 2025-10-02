package xyz.theforks.service;

import uk.co.xfactorylibrarians.coremidi4j.CoreMidiDeviceProvider;
import javax.sound.midi.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.logging.Logger;

/**
 * Service for managing MIDI devices and handling MIDI messages.
 * Supports device selection, message receiving, and learning mode for mapping MIDI to pad triggers.
 * Uses CoreMIDI4J for better macOS support.
 */
public class MIDIService {
    private static final Logger logger = Logger.getLogger(MIDIService.class.getName());

    private MidiDevice currentDevice;
    private Transmitter transmitter;
    private final Map<String, BiConsumer<Integer, Integer>> messageHandlers = new HashMap<>();
    private BiConsumer<Integer, Integer> learnModeHandler;
    private boolean isLearnMode = false;

    public MIDIService() {
        // Initialize CoreMIDI4J
        try {
            CoreMidiDeviceProvider.isLibraryLoaded();
            logger.info("CoreMIDI4J library loaded successfully");
        } catch (Exception e) {
            logger.warning("CoreMIDI4J not available, falling back to Java MIDI: " + e.getMessage());
        }
    }

    /**
     * Get a list of all available MIDI input devices.
     * @return List of device names
     */
    public List<String> getAvailableDevices() {
        List<String> devices = new ArrayList<>();
        MidiDevice.Info[] infos = MidiSystem.getMidiDeviceInfo();

        for (MidiDevice.Info info : infos) {
            try {
                MidiDevice device = MidiSystem.getMidiDevice(info);
                // Only include devices that can transmit (input devices)
                if (device.getMaxTransmitters() != 0) {
                    devices.add(info.getName());
                }
            } catch (MidiUnavailableException e) {
                logger.warning("Could not access MIDI device: " + info.getName());
            }
        }

        return devices;
    }

    /**
     * Open and connect to a MIDI device by name.
     * @param deviceName The name of the device to connect to
     * @return true if successful, false otherwise
     */
    public boolean openDevice(String deviceName) {
        // Close current device if open
        closeDevice();

        MidiDevice.Info[] infos = MidiSystem.getMidiDeviceInfo();
        for (MidiDevice.Info info : infos) {
            if (info.getName().equals(deviceName)) {
                try {
                    currentDevice = MidiSystem.getMidiDevice(info);
                    if (!currentDevice.isOpen()) {
                        currentDevice.open();
                    }

                    transmitter = currentDevice.getTransmitter();
                    transmitter.setReceiver(new MidiReceiver());

                    logger.info("Connected to MIDI device: " + deviceName);
                    return true;
                } catch (MidiUnavailableException e) {
                    logger.severe("Failed to open MIDI device: " + e.getMessage());
                    return false;
                }
            }
        }

        logger.warning("MIDI device not found: " + deviceName);
        return false;
    }

    /**
     * Close the current MIDI device.
     */
    public void closeDevice() {
        if (transmitter != null) {
            transmitter.close();
            transmitter = null;
        }

        if (currentDevice != null && currentDevice.isOpen()) {
            currentDevice.close();
            currentDevice = null;
            logger.info("MIDI device closed");
        }
    }

    /**
     * Register a handler for a specific MIDI message (note/CC combination).
     * @param midiKey The key in format "note:XX" or "cc:XX"
     * @param handler Handler that receives note/CC number and velocity/value
     */
    public void registerMessageHandler(String midiKey, BiConsumer<Integer, Integer> handler) {
        messageHandlers.put(midiKey, handler);
    }

    /**
     * Unregister a handler for a specific MIDI message.
     * @param midiKey The key in format "note:XX" or "cc:XX"
     */
    public void unregisterMessageHandler(String midiKey) {
        messageHandlers.remove(midiKey);
    }

    /**
     * Enable learn mode - the next MIDI message will be sent to the learn mode handler.
     * @param handler Handler to receive the learned MIDI message
     */
    public void enableLearnMode(BiConsumer<Integer, Integer> handler) {
        this.learnModeHandler = handler;
        this.isLearnMode = true;
        logger.info("MIDI learn mode enabled");
    }

    /**
     * Disable learn mode.
     */
    public void disableLearnMode() {
        this.isLearnMode = false;
        this.learnModeHandler = null;
        logger.info("MIDI learn mode disabled");
    }

    /**
     * Check if currently in learn mode.
     * @return true if in learn mode
     */
    public boolean isLearnMode() {
        return isLearnMode;
    }

    /**
     * Get the name of the currently connected device.
     * @return device name or null if not connected
     */
    public String getCurrentDeviceName() {
        if (currentDevice != null) {
            return currentDevice.getDeviceInfo().getName();
        }
        return null;
    }

    /**
     * Check if a device is currently connected.
     * @return true if connected
     */
    public boolean isConnected() {
        return currentDevice != null && currentDevice.isOpen();
    }

    /**
     * Clear all registered message handlers.
     */
    public void clearAllHandlers() {
        messageHandlers.clear();
    }

    /**
     * Get all currently registered MIDI mappings.
     * @return Map of MIDI keys to handler presence (for serialization)
     */
    public Map<String, BiConsumer<Integer, Integer>> getMessageHandlers() {
        return new HashMap<>(messageHandlers);
    }

    /**
     * Internal receiver for processing incoming MIDI messages.
     */
    private class MidiReceiver implements Receiver {
        @Override
        public void send(MidiMessage message, long timeStamp) {
            if (message instanceof ShortMessage) {
                ShortMessage sm = (ShortMessage) message;
                int command = sm.getCommand();
                int data1 = sm.getData1(); // note number or CC number
                int data2 = sm.getData2(); // velocity or CC value

                String midiKey = null;
                String messageType = null;

                // Note On messages
                if (command == ShortMessage.NOTE_ON && data2 > 0) {
                    midiKey = "note:" + data1;
                    messageType = "Note";
                    logger.fine("MIDI Note On: " + data1 + " velocity: " + data2);
                }
                // Note Off messages (or Note On with velocity 0)
                else if (command == ShortMessage.NOTE_OFF || (command == ShortMessage.NOTE_ON && data2 == 0)) {
                    // Ignore note off for now
                    return;
                }
                // Control Change messages
                else if (command == ShortMessage.CONTROL_CHANGE) {
                    midiKey = "cc:" + data1;
                    messageType = "CC";
                    logger.fine("MIDI CC: " + data1 + " value: " + data2);
                }

                if (midiKey != null) {
                    // If in learn mode, send to learn handler
                    if (isLearnMode && learnModeHandler != null) {
                        final String type = messageType;
                        final int num = data1;
                        // Pass both the MIDI key and data to the handler
                        // We'll pass the command type in data1 (0=note, 1=cc) and the number in data2
                        learnModeHandler.accept(command == ShortMessage.NOTE_ON ? 0 : 1, data1);
                        disableLearnMode();
                    }
                    // Otherwise, check for registered handler
                    else {
                        BiConsumer<Integer, Integer> handler = messageHandlers.get(midiKey);
                        if (handler != null) {
                            handler.accept(data1, data2);
                        }
                    }
                }
            }
        }

        @Override
        public void close() {
            // Nothing to clean up
        }
    }
}
