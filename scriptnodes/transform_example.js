/**
 * Complex argument transformation example
 *
 * This script modifies OSC message arguments in various ways.
 */

function process(message) {
    var address = message.getAddress();
    var args = message.getArguments();

    // Scale velocity messages by 0.8
    if (address.startsWith("/velocity/")) {
        if (args.size() > 0 && typeof args.get(0) === 'number') {
            var scaled = args.get(0) * 0.8;
            return createMessage(address, [scaled]);
        }
    }

    // Convert note messages to MIDI format
    if (address === "/note") {
        // Expect: /note <note_number> <velocity>
        if (args.size() >= 2) {
            var note = args.get(0);
            var velocity = args.get(1);

            // Convert to MIDI note on format: /midi/noteon <channel> <note> <velocity>
            return createMessage("/midi/noteon", [1, note, velocity]);
        }
    }

    // Add timestamp to control messages
    if (address.startsWith("/control/")) {
        var timestamp = Date.now();
        var newArgs = args.toArray();
        newArgs.push(timestamp);
        return createMessage(address, newArgs);
    }

    return message;
}
