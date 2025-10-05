/**
 * Message Filtering Example
 *
 * Returns null/false to drop messages that fail the checks.
 * The ScriptNode environment also exposes:
 * - createMessage(address, argsArray)
 * - createMessageRequest(message, delayMs?, outputId?)
 * so the script can transform or reroute messages when needed.
 */
function process(message) {
    var args = message.getArguments();

    if (args.isEmpty()) {
        return null; // Drop empty payloads
    }

    // Drop messages where the first argument is 0 or below threshold
    var first = args.get(0);
    if (first === 0) {
        return null;
    }

    var threshold = 10;
    if (typeof first === 'number' && first < threshold) {
        return null;
    }

    return message;
}
