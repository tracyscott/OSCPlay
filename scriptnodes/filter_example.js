/**
 * Message filtering example
 *
 * This script filters out messages based on argument values.
 * Returns null to drop a message.
 */

function process(message) {
    var args = message.getArguments();

    // Drop messages with no arguments
    if (args.isEmpty()) {
        return null;
    }

    // Drop messages where first argument is 0
    if (args.size() > 0 && args.get(0) === 0) {
        return null;
    }

    // Drop messages where first argument is less than threshold
    var threshold = 10;
    if (args.size() > 0 && typeof args.get(0) === 'number' && args.get(0) < threshold) {
        return null;
    }

    // Pass through all other messages
    return message;
}
