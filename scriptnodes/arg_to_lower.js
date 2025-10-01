/**
 * OSCPlay Script Node
 *
 * This script processes OSC messages. Modify the process() function
 * to transform, filter, or pass through messages.
 *
 * Available API:
 * - message.getAddress()     - Get OSC address string
 * - message.getArguments()   - Get arguments list
 * - createMessage(addr, args) - Create new OSC message
 *
 * Return values:
 * - OSCMessage: Send the message
 * - null or false: Drop the message
 */

function process(message) {
    // Pass through all messages unchanged
    return message;
}
