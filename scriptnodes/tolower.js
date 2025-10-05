/**
 * Delayed Lowercase Echo
 *
 * Shows how to produce delayed output using MessageRequest helpers.
 * - createMessage(address, argsArray) builds a new OSCMessage
 * - createMessageRequest(message, delayMs?, outputId?) wraps the message with routing metadata
 *
 * The script returns an array so the node sends multiple messages.
 */
function process(message) {
    var args = message.getArguments();
    if (args.isEmpty()) {
        return message;
    }

    var loweredArgs = [];
    var mutated = false;

    for (var i = 0; i < args.size(); i++) {
        var value = args.get(i);
        if (typeof value === 'string') {
            var lowered = value.toLowerCase();
            loweredArgs.push(lowered);
            if (lowered !== value) {
                mutated = true;
            }
        } else {
            loweredArgs.push(value);
        }
    }

    if (!mutated) {
        // Nothing to change; pass the original through unchanged.
        return message;
    }

    var loweredMessage = createMessage(message.getAddress(), loweredArgs);

    // Send the lowered message immediately and again after 120ms (e.g., for a soft echo).
    return [
        createMessageRequest(loweredMessage),
        createMessageRequest(loweredMessage, 120)
    ];
}
