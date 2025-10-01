# OSCPlay Script Node JavaScript API

This directory contains JavaScript files for use with the ScriptNode in OSCPlay.

## Overview

ScriptNode allows you to process OSC messages using JavaScript code at runtime, without recompiling the application. Scripts are automatically reloaded when modified, making development fast and iterative.

## Script Structure

Every script must define a `process(message)` function:

```javascript
function process(message) {
    // Your processing logic here
    return message;  // or modified message, or null to drop
}
```

## API Reference

### The `message` Object

The incoming OSC message has these methods:

- `message.getAddress()` - Returns the OSC address as a string (e.g., "/note/C4")
- `message.getArguments()` - Returns a Java List of arguments

### Creating New Messages

Use the `createMessage(address, args)` helper function:

```javascript
// Create a new message with different address
return createMessage("/new/address", message.getArguments().toArray());

// Create message with custom arguments
return createMessage("/midi/note", [60, 127, "on"]);
```

### Return Values

Your `process()` function can return:

- **Modified OSCMessage** - The message will be sent with your changes
- **Original message** - Pass through unchanged
- **null** - Drop the message (won't be sent)
- **false** - Drop the message (won't be sent)

### Accessing Arguments

```javascript
var args = message.getArguments();

// Get number of arguments
var count = args.size();

// Access individual arguments (0-indexed)
var firstArg = args.get(0);
var secondArg = args.get(1);

// Check if list is empty
if (args.isEmpty()) {
    return null;
}

// Convert to JavaScript array
var argsArray = args.toArray();
```

### JavaScript Types

Arguments can be:
- Numbers (integers, floats)
- Strings
- Booleans (converted to/from OSC types)

Check types with:
```javascript
typeof args.get(0) === 'number'
typeof args.get(0) === 'string'
```

## Examples

### 1. Simple Rename

```javascript
function process(message) {
    var address = message.getAddress();
    if (address.startsWith("/old/")) {
        var newAddress = address.replace("/old/", "/new/");
        return createMessage(newAddress, message.getArguments().toArray());
    }
    return message;
}
```

### 2. Filter by Value

```javascript
function process(message) {
    var args = message.getArguments();

    // Drop messages where first arg is less than 10
    if (args.size() > 0 && args.get(0) < 10) {
        return null;
    }

    return message;
}
```

### 3. Transform Arguments

```javascript
function process(message) {
    var args = message.getArguments();

    // Scale all numeric arguments by 2
    var newArgs = [];
    for (var i = 0; i < args.size(); i++) {
        var arg = args.get(i);
        if (typeof arg === 'number') {
            newArgs.push(arg * 2);
        } else {
            newArgs.push(arg);
        }
    }

    return createMessage(message.getAddress(), newArgs);
}
```

### 4. Regex Pattern Matching

```javascript
function process(message) {
    var address = message.getAddress();

    // Match pattern like /sensor/1/temp
    var pattern = /^\/sensor\/(\d+)\/temp$/;
    var match = address.match(pattern);

    if (match) {
        var sensorId = match[1];
        return createMessage("/temperature/sensor" + sensorId,
                           message.getArguments().toArray());
    }

    return message;
}
```

### 5. Stateful Processing

```javascript
// Variables persist between calls
var messageCount = 0;
var lastValue = 0;

function process(message) {
    messageCount++;

    var args = message.getArguments();
    if (args.size() > 0) {
        var value = args.get(0);

        // Calculate delta from last value
        var delta = value - lastValue;
        lastValue = value;

        // Add delta as additional argument
        var newArgs = args.toArray();
        newArgs.push(delta);

        return createMessage(message.getAddress(), newArgs);
    }

    return message;
}
```

## Usage in OSCPlay

1. Place your `.js` file in the `scripts/` directory
2. In OSCPlay, add a "Script" node
3. Configure with:
   - **Address Pattern**: Regex pattern to match (e.g., `.*` for all, or `/note/.*` for specific addresses)
   - **Script Path**: Filename relative to scripts/ (e.g., `rename_example.js`)

The script will be loaded immediately and reloaded automatically when you modify the file.

## Debugging

- Check the OSCPlay console/log for script errors
- Use the "Test" button in the Script Node preferences to test with sample data
- The "Reload" button manually reloads the script
- View script content in the preferences dialog

## Performance Notes

- Scripts are compiled once and cached
- Script engines are reused for performance
- Hot-reloading only occurs when the file timestamp changes
- For high-throughput scenarios, keep scripts simple and fast

## Advanced: Java Interop

You can access Java classes directly:

```javascript
// Import Java types
var Arrays = Java.type('java.util.Arrays');
var ArrayList = Java.type('java.util.ArrayList');

function process(message) {
    // Use Java collections
    var list = new ArrayList();
    list.add(1);
    list.add(2);

    return createMessage("/test", list.toArray());
}
```

## Error Handling

If your script throws an error:
- The error is logged to the console
- The original message is passed through unchanged
- The script continues processing subsequent messages

```javascript
function process(message) {
    try {
        // Your risky code here
        return transformMessage(message);
    } catch (e) {
        // Log error (visible in console)
        print("Error: " + e.message);
        // Return original message
        return message;
    }
}
```
