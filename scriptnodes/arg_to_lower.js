/**
 * Lowercase Arguments
 *
 * Demonstrates transforming message arguments with the ScriptNode helper API.
 * Available helpers:
 * - message.getAddress() / message.getArguments()
 * - createMessage(address, argsArray)
 * - createMessageRequest(message, delayMs?, outputId?)
 *
 * Return options:
 * - OSCMessage or MessageRequest (single result)
 * - Array of messages/requests (fan out)
 * - null/false to drop the message
 */
function process(message) {
    var args = message.getArguments();
    if (args.isEmpty()) {
        return message;
    }

    var updated = [];
    var mutated = false;

    for (var i = 0; i < args.size(); i++) {
        var value = args.get(i);
        if (typeof value === 'string') {
            var lowered = value.toLowerCase();
            updated.push(lowered);
            if (lowered !== value) {
                mutated = true;
            }
        } else {
            updated.push(value);
        }
    }

    return mutated ? createMessage(message.getAddress(), updated) : message;
}
