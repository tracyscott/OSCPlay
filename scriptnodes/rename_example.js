/**
 * Simple address renaming example
 *
 * This script renames OSC addresses from /note/* to /midi/note/*
 */

function process(message) {
    var address = message.getAddress();

    // Rename /note/* to /midi/note/*
    if (address.startsWith("/note/")) {
        var newAddress = address.replace("/note/", "/midi/note/");
        return createMessage(newAddress, message.getArguments().toArray());
    }

    // Return original message unchanged
    return message;
}
