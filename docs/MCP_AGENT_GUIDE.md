# OSCPlay Agent Guide

This guide is for AI agents that configure OSCPlay through its MCP server. It covers how messages flow through the system, how to make changes safely, and what each node type does. Tool names and behaviour match the `oscplay` MCP server.

## The model

```
               ┌─► output "default" ─► [node 0] ─► [node 1] ─► ... ─► UDP host:port
OSC input ─────┼─► output "lights"  ─► [node 0] ─► ...                ─► UDP host:port
(UDP or TCP)   └─► output "..."     ─► ...
```

- **Input.** There is one OSC input (UDP or TCP host and port). `get_pipeline` shows it. The MCP tools cannot change it.
- **Outputs.** Every message that reaches the input is copied to **every enabled output**. Each output has an `id`, a destination `host`/`port` (sent over UDP), an `enabled` flag, and **its own node chain**. There is no global chain. The `default` output always exists and cannot be removed.
- **Node chains.** A chain is an ordered list of nodes. A message goes through them in order. At each node it can pass through unchanged, be changed, be dropped, be split into several messages, or be delayed. Nodes have no IDs; you refer to them by their **zero-based index** in the chain.
- **Internal commands.** Addresses starting with `/oscplay` are commands to OSCPlay itself (e.g. `/oscplay/sampler1 3` triggers sampler pad 3). They are never forwarded to outputs or run through chains.
- **Recording.** Recordings store the **raw input** before any chain runs. When a recording is played back, it goes through the chains as they are at that moment. Changing a chain therefore changes how existing recordings sound when replayed.

## How a node decides what to process

Every node except Interlace Magnometer has an **Address Pattern** as its first arg (Interlace Magnometer sets its own from the magnetometer number). The chain checks it **before** it calls the node:

- If the message address matches the pattern, the node processes the message.
- If not, the message **passes the node unchanged**, and the node never sees it.

Address Patterns are **Java regular expressions that must match the whole address**. They are not OSC glob patterns.

| You want | Pattern |
|----------|---------|
| Everything | `.*` |
| One exact address | `/synth/cutoff` |
| Everything under `/synth` | `/synth/.*` |
| `/fader1` … `/fader8` | `/fader[1-8]` |
| Anything ending in `/x` | `.*/x` |
| Everything **except** under `/debug` | `(?!/debug/).*` |

Escape regex metacharacters that should be literal (`.`, `+`, `?`, `(`, `[`, …). For example, `/v1\.2/.*`.

## Workflow

1. **Look first.** Call `get_pipeline` to see the outputs and their current chains, and `list_node_types` to see the exact arg names.
2. **Design the chain, then dry-run it.** Call `test_chain` with `nodes` set to your proposed chain, plus representative messages. Include messages that should *not* be affected. It returns the messages the chain would produce, plus any `delayMs` and `targetOutput`. An empty `output` means the message was dropped. Nothing is sent, and live nodes are left alone.
3. **Apply it.** Use `set_chain` to replace a whole chain, or `add_node` / `update_node` / `remove_node` / `move_node` for small edits. `set_chain` checks every node before it changes anything, so a bad node leaves the existing chain as it was.
4. **Verify.** Call `test_chain` with `output_id` to test the chain as it now stands on that output. Use `send_message` only when you deliberately want real traffic to reach the destination, because it sends real UDP packets.
5. **Persist.** Edits are live straight away but are **not saved** until you call `save_project`. Call it when the user is happy with the result.

### Things to keep in mind

- **Changes are live.** Every change takes effect on running traffic immediately. If you are experimenting, try it first on a new output (`add_output` with `enabled: false`) and test there with `test_chain`.
- **Reconfiguring resets state.** `update_node` and `set_chain` build new node instances, which resets stateful nodes such as Moving Average.
- **Node args are strings.** Pass all node args as strings, even numbers: `["/x", "250"]`, not `["/x", 250]`.
- **OSC message argument types.** In `test_chain` and `send_message`, the message arguments are converted like this:
  - JSON integers become int32 (or int64 if they are too big for int32)
  - decimals become float32
  - strings stay strings, and booleans stay booleans

  Several nodes care about the type. For example, Moving Average only handles float32, and Pitch Shift only handles int32. So `1` and `1.0` behave differently.
- **Outputs are addressed by id.** An unknown `output_id` error lists the valid ids.

## Node reference

`type` can be the class name (`DropNode`), the short name (`Drop`), or the label (`Moving Average`). All three are case-insensitive. Args are positional strings, in the order shown here.

### Routing and filtering

#### Drop — `DropNode`
Args: `Address Pattern`

Drops every message that matches the pattern. Everything else passes through.
```json
{"type": "Drop", "args": ["/debug/.*"]}
```

#### Pass — `PassNode`
Args: `Address Pattern`

The node is meant to keep only matching messages and drop everything else. **Inside a chain it currently does nothing**: the chain only calls a node for messages that match its pattern, so non-matching messages skip the node and are never dropped. To keep only `/synth/...`, use a Drop with a negative lookahead instead:
```json
{"type": "Drop", "args": ["(?!/synth/).*"]}
```

#### Delay — `DelayNode`
Args: `Address Pattern`, `Delay (ms)` (non-negative integer)

Holds matching messages for the given time before they are sent.
```json
{"type": "Delay", "args": ["/cue/.*", "500"]}
```

**Combining Delay with other nodes is fragile.** Check every chain that contains a Delay with `test_chain`. Here is why:

1. When a delayed message fires, it is **run through the output's whole chain again, from the top**. The message it starts from is the one the chain produced on the first pass.
2. It carries an "already delayed" marker, so Delay nodes pass it through. But **any node that changes the message creates a fresh one without the marker**.

That leads to two failure modes:

- **A node *after* the Delay changes the delayed message.** The change discards the delay, and the message is sent immediately.
- **A node *before* the Delay changes the message again on the second pass.** The marker is lost, so the Delay delays it again. **The message then repeats forever**, changed again each time. For example, Pitch Shift before Delay sends the note an octave higher every 500 ms, forever.

Rules that keep it working:

- Delay alone in a chain, or alongside nodes that don't change the delayed messages (Drop, or nodes whose patterns don't match them), is safe.
- A change before the Delay is safe **only if the changed message no longer matches that node's pattern**. Then it is left alone on the second pass. Example: Rename `/a/.*` → `/b/...`, then Delay `/b/.*`.
- Otherwise, delay on a **separate output** whose chain contains only the Delay, and do the changes on another output.

`test_chain` shows only the first pass, so check the second pass yourself: run `test_chain` again with the *output* message as the input. If a node other than a Delay changes it, or it comes back with a `delayMs`, the chain will misbehave.

### Address rewriting

#### Rename — `RenameNode`
Args: `Address Pattern`, `Regex`, `Replace With`

For matching messages, replaces the **first** match of `Regex` in the address with `Replace With`. The arguments are kept. `Regex` doesn't need to match the whole address. `Replace With` can use groups (`$1`).
```json
{"type": "Rename", "args": ["/fader/.*", "/fader/", "/light/"]}
{"type": "Rename", "args": ["/ch(\\d+)/vol", "/ch(\\d+)/vol", "/mixer/$1/gain"]}
```
`/fader/3 0.5` → `/light/3 0.5`; `/ch7/vol 0.2` → `/mixer/7/gain 0.2`.

#### PathTrim — `PathTrimNode`
Args: `Address Pattern`

Removes the last part of the address and puts it in front of the arguments as a string.
`/scene/recall/intro 1` → `/scene/recall "intro" 1`. A single-part address like `/go` is left unchanged.
```json
{"type": "PathTrim", "args": ["/scene/recall/.*"]}
```

#### Splitter — `SplitterNode`
Args: `Address Pattern` (optional: an empty pattern or no args at all means `.*`, i.e. every message)

For matching messages with 2 or more arguments, makes one message per argument. Each new address is the original address plus a 1-based number, **with no separator**. Messages with 0 or 1 argument, and messages that don't match, pass through unchanged.
`/xy 0.1 0.7` → `/xy1 0.1`, `/xy2 0.7`.
```json
{"type": "Splitter", "args": ["/xy"]}
```
Give it a pattern. A Splitter with `.*` also splits every other multi-argument message, such as `/rgb 255 0 0` or `/note 60 100`.

### Value processing

#### Moving Average — `MovingAvgNode`
Args: `Address Pattern`, `Window Size` (integer ≥ 1)

Smooths messages that have exactly **one float32** argument. It outputs the mean of the last N values, keeping a separate window for each address. Other messages pass through. Because it keeps state, rebuilding the node resets the window.
```json
{"type": "Moving Average", "args": ["/sensor/.*", "8"]}
```

#### Remap Float — `RemapNodeF`
Args: `Address Pattern`, `In Min`, `In Max`, `Out Min`, `Out Max`, `Clamp` (`true`/`false`)

Linearly maps every numeric argument (float32, float64, int32, int64) from the input range onto the output range, always emitting **float32**. Non-numeric arguments pass through, and a message with nothing numeric is left untouched. With `Clamp` on, values outside the input range are held at the output ends; with it off they extrapolate. Reversing the output range inverts the signal. An `In Min` equal to `In Max` has no meaningful mapping and pins to `Out Min`.

Typical use is matching a sensor's units to a parameter's: Chromatik's `Angles/angle1-3` are normalized parameters that LX applies with `setNormalized()`, so the Interlace Magnometer node's 0-270 degrees have to be scaled to 0-1 or they clamp at full deflection.
```json
{"type": "Remap Float", "args": ["/lx/modulation/Angles/angle[123]", "0", "270", "0", "1", "true"]}
```

#### IntToBang — `IntToBangNode`
Args: `Address Pattern`

For messages with exactly one int32 argument: a `1` becomes the same address with **no arguments** (a "bang"), and any other integer is **dropped**. All other messages pass through. Useful for turning button press/release pairs into a single trigger.
```json
{"type": "IntToBang", "args": ["/button/.*"]}
```

#### Pitch Shift — `PitchShiftNode`
Args: `Address Pattern`

If the first argument is an int32, adds 12 to it (up one octave of MIDI notes). Otherwise the message passes through. The shift amount is fixed; use a Script for other amounts.
```json
{"type": "Pitch Shift", "args": ["/note/.*"]}
```

### Sensors

#### Calibrate — `CalibrateNode`
Args: `Address Pattern`, `Calibration` (name of a calibration saved in the project's `Calibrations/` directory)

Turns raw sensor readings into calibrated values. The leading numeric arguments (one per calibration dimension) are replaced by a **single float32** on the same address. Messages that don't contain a full reading pass through. Calibrations are created in OSCPlay's **Tools > Sensor Calibration** window, which agents cannot use through MCP. Configuration fails if the calibration doesn't exist.
```json
{"type": "Calibrate", "args": ["/mag1/xyz", "Interlace-Mag1"]}
```

#### Interlace Magnometer — `InterlaceMagNode`
Args: `Magnometer Number` (`1`–`3`)

A Calibrate preset for the Interlace installation. It handles `/mag<N>/xyz` with the calibration named `Interlace-Mag{N}`, and outputs the tower angle in degrees (0–270). The calibration's mark spacing is chosen when it is built, not here: 10° gives 28 marks and the most accurate fit, 30° gives 10 marks and a much quicker setup for about 0.13° of error. If the project has no such calibration, it falls back to a legacy `calibration{N}.csv` in OSCPlay's working directory, which outputs 0–1 along the sweep. If neither exists, configuration fails.
```json
{"type": "Interlace Magnometer", "args": ["2"]}
```

### Custom logic

#### Script — `ScriptNode`
Args: `Address Pattern`, `Script Path` (relative to the project's `Scripts/` directory)

Runs a JavaScript function on each matching message. Use it when no built-in node does what you need. **Write the script first** with `write_script`, because configuration fails if the file is missing or doesn't load. Scripts reload automatically when the file changes, so `write_script` on a file that is already in use updates live traffic straight away.

The script must define `process(message)`:

- `message.getAddress()` returns the address string.
- `message.getArguments()` returns a Java `List`. Use `.size()` and `.get(i)` on it, or `.toArray()` to get a JS array.

`process` returns one of:

| Return | Effect |
|--------|--------|
| `message` or `undefined` | pass through unchanged |
| `null` or `false` | drop |
| an OSCMessage | replace |
| a MessageRequest | replace, with a delay and/or target output |
| an array of the above | emit several messages |

Helpers:

- `createMessage(address, argsArray)`
- `createMessageRequest(message, delayMs, outputId)`. `delayMs` and `outputId` are optional.

If the script throws an error, the original message passes through unchanged.

```javascript
// Scripts/scale_fader.js — map 0..1 faders to 0..127 ints on /cc/N
function process(message) {
    var args = message.getArguments();
    if (args.size() !== 1) return message;
    var n = message.getAddress().split('/').pop();
    return createMessage('/cc/' + n, [Math.round(args.get(0) * 127) | 0]);
}
```
```json
{"type": "Script", "args": ["/fader/[0-9]+", "scale_fader.js"]}
```

Delays and routing from scripts follow the same rules as Delay above:

- **A script that returns a delayed request delays it again every time it fires, forever.** When it fires, the delayed message goes through the chain again, and the script can't tell it has already been delayed. So only delay messages that the script will leave unchanged on the next pass. For example, give the delayed copy an address that falls outside the Script node's Address Pattern.
- Routing to another output with `createMessageRequest(msg, delayMs, "lights")` only works when `delayMs > 0`. Immediate messages always go to the output whose chain produced them. When a routed message fires, it goes through the **target** output's chain.

## Recipes

**Send only the `/synth` traffic to a new output, renamed for the receiver**
```json
add_output  {"id": "synth", "host": "127.0.0.1", "port": 9001}
set_chain   {"output_id": "synth", "nodes": [
               {"type": "Drop",   "args": ["(?!/synth/).*"]},
               {"type": "Rename", "args": ["/synth/.*", "^/synth", "/instrument"]}]}
test_chain  {"output_id": "synth", "address": "/synth/osc1/freq", "args": [440.0]}
test_chain  {"output_id": "synth", "address": "/drums/kick", "args": [1]}
```

**Send a delayed copy of cues to a second destination**
```json
add_output {"id": "cues-late", "host": "10.0.0.30", "port": 53000}
set_chain  {"output_id": "cues-late", "nodes": [
              {"type": "Drop",  "args": ["(?!/cue/).*"]},
              {"type": "Delay", "args": ["/cue/.*", "2000"]}]}
```
Delay shares its chain only with a Drop. The Drop doesn't change the cues, so the delay holds (see the Delay section for why that matters).

**Smooth a noisy sensor**
```json
set_chain {"output_id": "default", "nodes": [
             {"type": "Moving Average", "args": ["/sensor/.*", "5"]}]}
test_chain {"output_id": "default", "address": "/sensor/1", "args": [0.5]}
```
Send float args such as `0.5`, not integers, because Moving Average only handles float32. Don't add a Delay to this chain: on the second pass Moving Average would change the delayed message again, and the message would repeat forever.

**Turn a TouchOSC XY pad into two separate controls**
```json
set_chain {"output_id": "default", "nodes": [
             {"type": "Splitter", "args": ["/xy"]},
             {"type": "Rename", "args": ["/xy1", "/xy1", "/pan"]},
             {"type": "Rename", "args": ["/xy2", "/xy2", "/tilt"]}]}
```

## Recording

`start_recording` and `stop_recording` capture incoming messages to a session in the project's
`Recordings/` directory, which can then be played back or turned into a sensor calibration.

What is recorded is the **raw input**, before any node chain runs, so a recording replays through
whatever chains exist at playback time rather than baking in today's processing.

`address_filter` is the important option when several devices are sending at once. It is a Java
regex that must match the whole address, the same rule node address patterns follow:

```json
start_recording {"name": "tower2-2026-09-23", "address_filter": "/mag2/xyz"}
stop_recording {}
```

Without it every message is recorded. The three Interlace towers all stream continuously once
powered, so calibrating one without a filter produces a recording three times the necessary size.

Notes:

- Only one recording runs at a time; `start_recording` fails rather than silently replacing one in
  progress. `stop_recording` fails if nothing is being recorded.
- `name` becomes a directory name, so it cannot contain path separators.
- `stop_recording` reports `messages`, the number actually kept after filtering. Zero means the
  filter matched nothing, usually because it only matched part of the address.
- The app's record button follows these calls, so the UI and the agent cannot disagree about
  whether a recording is running.

## Tool reference

| Tool | Args | Notes |
|------|------|-------|
| `get_pipeline` | `output_id?` | Input settings and outputs with their chains |
| `list_node_types` | | Types, help text, arg names |
| `add_output` | `id`, `port`, `host?`, `enabled?` | `host` defaults to 127.0.0.1, `enabled` to true |
| `update_output` | `id`, `host?`, `port?`, `enabled?` | Fields you leave out keep their value |
| `remove_output` | `id` | Not `default` |
| `set_chain` | `output_id`, `nodes[]` | Atomic; every node is validated first; `[]` clears the chain |
| `add_node` | `output_id`, `type`, `args?`, `index?` | Adds at the end by default |
| `update_node` | `output_id`, `index`, `args` | Same type, new args; resets node state |
| `remove_node` | `output_id`, `index` | |
| `move_node` | `output_id`, `from`, `to` | |
| `test_chain` | `address`, `args?`, and `output_id` or `nodes` | Dry run; sends nothing |
| `send_message` | `address`, `args?`, `output_id?` | Real send through live chains; not recorded |
| `start_recording` | `name`, `address_filter?` | Records raw input; fails if already recording |
| `stop_recording` | | Saves the recording and reports how many messages were kept |
| `list_scripts` / `read_script` / `write_script` | `path`, `content` | Restricted to the project's `Scripts/` directory |
| `save_project` | | Writes outputs and chains to the project `.opp` file |
