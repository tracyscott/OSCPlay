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
    var OSCMessage = Java.type('com.illposed.osc.OSCMessage');
    
    var args = message.getArguments();
    var newArgs = [];
    for (var i = 0; i < args.size(); i++) {
        var arg = args.get(i);
        if (typeof arg === 'string') {
            newArgs.push(arg.toLowerCase());
        } else {
            newArgs.push(arg);
        }
    }

    // Create a new instance
    var newMessage = new OSCMessage(message.getAddress(), newArgs);
    
    return newMessage;
}
