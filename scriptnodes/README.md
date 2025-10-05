# OSCPlay Script Node JavaScript API

This directory contains JavaScript files for use with the ScriptNode in OSCPlay.
The ScriptNode lets you transform, filter, fan out, or delay OSC traffic at runtime without recompiling the application. Scripts reload automatically whenever the file changes.

## Script Structure

Every script defines a single entry point:

```javascript
function process(message) {
    // Custom logic here
    return message;
}
```

The input list always contains one `MessageRequest` when `process` runs. You modify the request by returning new values from `process`.

## Message Object

The `message` parameter is an OSCMessage with familiar helpers:

- `message.getAddress()` → returns the OSC address (e.g. `/note/C4`)
- `message.getArguments()` → returns a Java `List` of arguments (`size()`, `get(i)`, `isEmpty()`, etc.)

Common patterns:

```javascript
var args = message.getArguments();
var first = args.isEmpty() ? null : args.get(0);
var jsArray = args.toArray(); // copy to a JavaScript array
```

## Helper Functions

Two helpers are injected by OSCPlay:

- `createMessage(address, argsArray)` → build a new OSCMessage
- `createMessageRequest(message, delayMs?, outputId?)` → wrap a message with delay (milliseconds) and optional output routing id

The `MessageRequest` class is also available if you prefer manual instantiation: `Java.type('xyz.theforks.model.MessageRequest')`.

## Return Options

Your `process` function may return:

- An `OSCMessage` → replace the message immediately
- A `MessageRequest` → replace with custom delay/routing metadata
- An array containing `OSCMessage` and/or `MessageRequest` instances → emit many messages from one input
- `null` or `false` → drop the message entirely
- `undefined` → pass the original `MessageRequest` through unchanged

## Examples

### Rename an Address

```javascript
function process(message) {
    if (message.getAddress().startsWith("/note/")) {
        var renamed = createMessage(
            message.getAddress().replace("/note/", "/midi/note/"),
            message.getArguments().toArray()
        );
        return createMessageRequest(renamed);
    }
    return message;
}
```

### Filter Low Values

```javascript
function process(message) {
    var args = message.getArguments();
    if (args.isEmpty()) {
        return null;
    }
    var first = args.get(0);
    return typeof first === 'number' && first < 10 ? null : message;
}
```

### Emit a Delayed Echo

```javascript
function process(message) {
    var lowered = createMessage(message.getAddress(), [message.getArguments().get(0).toLowerCase()]);
    return [
        createMessageRequest(lowered),         // immediate
        createMessageRequest(lowered, 120)     // delayed echo
    ];
}
```

## Usage in OSCPlay

1. Place your `.js` file in the project `Scripts/` directory.
2. In OSCPlay, add a **Script** node to a chain.
3. Configure it with:
   - **Address Pattern** – regex that selects which inbound messages reach the script (`.*` for all traffic)
   - **Script Path** – your filename in the Scripts/ directory (e.g. `transform_example.js`)

The node will load immediately and refresh whenever you save changes. Use the node's **Reload** and **Test** buttons to validate behavior quickly.

## Debugging Tips

- Script errors are printed to the OSCPlay console; the original message passes through on error.
- Use `print('debug: ' + value);` for quick logging.
- The preferences dialog shows current script contents and offers Reload/Test actions.
- Keep hot-path scripts simple; heavy computation can stall realtime playback.

## Advanced: Java Interop

You can interoperate with Java classes as needed:

```javascript
var Arrays = Java.type('java.util.Arrays');
var MessageRequest = Java.type('xyz.theforks.model.MessageRequest');

function process(message) {
    var clone = new MessageRequest(
        createMessage(message.getAddress(), Arrays.asList(1, 2, 3).toArray()),
        50
    );
    return clone;
}
```

