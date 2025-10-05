/**
 * Address Renaming Example
 *
 * Demonstrates simple address rewriting while returning a MessageRequest.
 * Helpers available inside ScriptNode:
 * - message.getAddress() / message.getArguments()
 * - createMessage(address, argsArray)
 * - createMessageRequest(message, delayMs?, outputId?)
 */
function process(message) {
    var address = message.getAddress();

    if (address.startsWith("/note/")) {
        var newAddress = address.replace("/note/", "/midi/note/");
        var renamedMessage = createMessage(newAddress, message.getArguments().toArray());
        return createMessageRequest(renamedMessage);
    }

    return message;
}
