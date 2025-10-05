/**
 * Transformation Showcase
 *
 * Highlights several capabilities of the updated ScriptNode API:
 * - Rewriting arguments with createMessage
 * - Converting one message into many
 * - Scheduling delayed sends with createMessageRequest
 */
function process(message) {
    var address = message.getAddress();
    var args = message.getArguments();

    // Scale velocity messages by 0.8
    if (address.startsWith("/velocity/") && args.size() > 0 && typeof args.get(0) === 'number') {
        var scaled = args.get(0) * 0.8;
        return createMessage(address, [scaled]);
    }

    // Convert note messages to MIDI note-on format
    if (address === "/note" && args.size() >= 2) {
        var note = args.get(0);
        var velocity = args.get(1);
        return createMessage("/midi/noteon", [1, note, velocity]);
    }

    // Add timestamp metadata to control messages
    if (address.startsWith("/control/")) {
        var newArgs = args.toArray();
        newArgs.push(Date.now());
        return createMessage(address, newArgs);
    }

    // For clock pulses, emit a delayed accent hit as well
    if (address === "/clock/pulse") {
        var accent = createMessage("/clock/accent", args.toArray());
        return [
            createMessageRequest(message),           // keep the original pulse
            createMessageRequest(accent, 120)        // add a delayed accent
        ];
    }

    return message;
}
