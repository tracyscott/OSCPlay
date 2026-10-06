QLab's OSC API
QLab has an extensive API (application program interface) for OSC which allows you to control QLab from any device or software which can broadcast OSC messages. What follows here is a complete dictionary of QLab’s OSC implementation.

Transport layer
The QLab OSC API can be used over both UDP and TCP transport layers. QLab listens for incoming OSC on port 53000.

When talking to QLab via UDP, each OSC message corresponds to one UDP datagram. Replies to OSC via UDP are sent on port 53001.

When talking to QLab via TCP, messages are framed using the double END SLIP protocol (RFC 1055) as required by the OSC 1.1 specification. Replies to OSC via TCP are sent on port 53000.

QLab also listens for plain text on UDP port 53535, and attempts to interpret it as OSC. For example, sending the text /cue/selected/start to QLab on UDP port 53535 will have the same result as sending the actual OSC command /cue/selected/start to port 53000.

The OSC API behaves almost identically when using both UDP and TCP. Exceptions are noted below, such as cases where a reply may be larger than the maximum size of a UDP datagram.

Reply format
All replies from QLab take the form:

/reply/{/invoked/osc/method} json_string
The reply is sent to the IP address from which the original message was received.

The address of the reply starts with /reply/ and ends with the address of the invoked method.

json_string takes the form:

{
    "workspace_id" : string,
    "address": "/invoked/osc/method",
    "status": string,
    "data": value
}
workspace_id is optional, and only specified if the reply is specifically from the given workspace.

status is either ok or error.

data is the JSON-encoded result of invoking the method at address

For example, a workspace cueLists method:

/workspace/34200B51-835A-4918-A137-B6511784B6CA/cueLists
would cause QLab to respond with:

/reply/workspace/34200B51-835A-4918-A137-B6511784B6CA/cueLists {json_string}
Update format
When a client has requested updates from a specific workspace (i.e. has sent that workspace the /workspace/{id}/updates 1 command), it will receive push notifications when the client needs to update state for a cue or workspace. The client may receive the following messages at any time:

/update/workspace/{workspace_id}
This message is sent if you need to reload the cue lists for the workspace.

/update/workspace/{workspace_id}/cue_id/{cue_id}
This message is sent if you need to reload the state for the given cue. If the cue is a group cue or cue list, you should also reload the children of the cue.

/update/workspace/{workspace_id}/cueList/{cue_list_id}/playbackPosition {cue_id}
This message is sent when the playback position of {cue_list_id} changes to {cue_id}. If there is no current playback position, there will be no {cue_id} argument.

/update/workspace/{workspace_id}/disconnect
This message is sent if you need to disconnect from the given workspace (because it is going away).

Application methods
QLab will respond to the following general commands:

/version
Returns QLab’s version number.

/alwaysReply {boolean}
By default, QLab will only send a reply if the method generates a reply to send.

However, if alwaysReply is set to any non-zero number, QLab will send a reply for every OSC message it receives. Messages that would not normally generate a reply will generate one with a JSON string argument that contains:

{
    "workspace_id" : string,
    "address": "/invoked/osc/method",
    "status": string
}
The status string will be either “ok” or “error”.

Note that the data field does not exist in this reply.

/workspaces
Returns an array of workspace dictionaries:

[
    {
        "uniqueID": string,
        "displayName": string
        "hasPasscode": number
    }
]
/toggleAuditionWindow
Show or hide the audition window.

/auditionWindow {number}
Number is interpreted as a boolean, and sets whether the audition window is visible. If no argument is provided, this returns the current visibility of the audition window.

/workingDirectory {path}
Get or set the current working directory. If provided, the path string is the working directory you wish to set. You can provide two kinds of paths:

Full paths, e.g. /a/full/path/to/some/directory/
Paths beginning with a tilde, e.g. ~/a/path/to some/directory
Paths beginning with a tilde (~) will be expanded; the tilde signifies “relative to the user’s home directory”.

Current workspace methods
The current workspace is the front-most, active document. All Workspace Methods can be sent to the current workspace without specifying its uniqueID. To do this, specify the workspace method without the /workspace/{id} portion of the address.

For example, the following commands would be applied to the current workspace:

/connect {passcode_string}

/disconnect

/go

/stop

/undo

etc.

The full set of Workspace Methods is described below.

Workspace methods
Workspaces may be addressed either by their display name or their unique ID. For all OSC methods shown below of the form /workspace/{id}/... the id may be either the display name of the workspace, such as MyGroovyWorkspace.cues, or the unique ID of the workspace, which can be found in the Info tab of the Status Window.

Note, however, that addressing by display name will work only if the display name is composed of characters allowed in OSC method names. This does NOT include spaces, unicode, diacritical, or other “special” characters.

Addressing a workspace by its display name looks like this:

/workspace/demo.cues/cue/1/colorName green

Individual workspaces (documents) will respond to the following commands:

/workspace/{id}/connect {passcode_string}
Connect to this workspace with an optional passcode string. If the workspace has a passcode, you MUST supply it before any other commands will be accepted by the workspace or the cues it contains. If the workspace does not have a passcode, the /workspace/{id}/connect method is optional.

Returns ok if there is no passcode, or the passcode matches.

Returns badpass if the passcode does not match.

Returns error if the workspace does not exist.

/workspace/{id}/disconnect
Disconnect from this workspace. You should invoke this method when you will no longer be sending messages to this workspace.

If you are communicating to QLab via UDP, QLab will automatically disconnect your client if it has not heard any messages from it in the last 31 seconds. Any message (e.g. “thump”) will serve to keep the client connected. If you are disconnected and the workspace has a password, you will need to reconnect with that password before further commands will be accepted.

If you are communicating to QLab via TCP, QLab will not automatically disconnect your client. The client will remain connected until the client sends the disconnect message or the TCP connection itself is disconnected.

/workspace/{id}/thump
A simple heartbeat method for this workspace. Returns the data “thump”. (Thump-thump. Thump-thump.)

/workspace/{id}/updates {number}
number is interpreted as a boolean. If yes, your client wants push notifications of cue changes. If no, your client no longer wants push notifications of cue changes.

/workspace/{id}/save
Tell the given workspace to save itself to disk.

/workspace/{id}/undo
/workspace/{id}/redo
Undo or redo the most recent change of the workspace.

/workspace/{id}/go
Tell the current visible cue list of the given workspace to GO.

/workspace/{id}/stop
Stop playback but allow effects to continue. e.g., playback stops but echos continue.

/workspace/{id}/hardStop
Stop playback and cut all effects immediately.

/workspace/{id}/panic
A brief gradual fade out leading into a hard stop. A double panic will trigger an immediate hard stop.

/workspace/{id}/pause
/workspace/{id}/resume
/workspace/{id}/reset
/workspace/{id}/toggleFullScreen
/workspace/{id}/fullScreen {number}
Number is interpreted as a boolean, and sets whether the workspace is full screen. If no argument is provided, this returns the current full screen status of the workspace.

/workspace/{id}/toggleSelectionIsPlayhead
/workspace/{id}/selectionIsPlayhead {number}
Number is interpreted as a boolean, and sets whether the selection is the playhead. If no argument is provided, this returns whether the selection is currently locked to always be the playhead.

/workspace/{id}/toggleEditShowMode
/workspace/{id}/showMode {number}
Number is interpreted as a boolean, and sets whether the workspace is in show mode. If no argument is provided, this returns whether the workspace is currently in show mode.

/workspace/{id}/liveFadePreview {number}
Number is interpreted as a boolean, and sets whether the workspace has live fade previews enabled. If no argument is provided, this returns whether the workspace has live fade previews enabled.

/workspace/{id}/select/next
/workspace/{id}/select/previous
Select the next or previous cue.

/workspace/{id}/select/{cue_number}
/workspace/{id}/select_id/{id}
These methods will select the given cue(s).

/workspace/{id}/playhead/next
/workspace/{id}/playhead/previous
/workspace/{id}/playbackPosition/next
/workspace/{id}/playbackPosition/previous
Move the playhead (aka playbackPosition) to the the next or previous cue.

/workspace/{id}/playhead/nextSequence
/workspace/{id}/playhead/previousSequence
/workspace/{id}/playbackPosition/nextSequence
/workspace/{id}/playbackPosition/previousSequence
Move the playhead (aka playbackPosition) to the top of the next or previous cue sequence.

/workspace/{id}/playhead/{cue_number}
/workspace/{id}/playbackPosition/{cue_number}
These methods will set the playback position to the given cue.

/workspace/{id}/new cue_type
cue_type is a string describing which kind of cue to create. Supported strings include: audio, mic, video, camera, fade, osc, midi, midi file, timecode, group, start, stop, pause, load, reset, devamp, goto, target, arm, disarm, wait, memo, script, cuelist, or cue list.

This method returns the unique ID of the new cue. The newly created cue will also be selected, so subsequent commands can address the new cue either using the unique ID or simply addressing the currently selected cue.

/workspace/{id}/delete/{cue_number}
/workspace/{id}/delete/selected
/workspace/{id}/delete_id/{cue_id}
Delete the given cue(s).

/workspace/{id}/renumber startAtNumber incrementByNumber
Renumbers the selected cues.

/workspace/{id}/cueLists
/workspace/{id}/selectedCues
/workspace/{id}/runningCues
/workspace/{id}/runningOrPausedCues
All return an array of cue dictionaries:

[
    {
        "uniqueID": string,
        "number": string
        "name": string
        "listName": string
        "type": string
        "colorName": string
        "flagged": number
        "armed": number
    }
]
If the cue is a group, the dictionary will include an array of cue dictionaries for all children in the group:

[
    {
        "uniqueID": string,
        "number": string
        "name": string
        "listName": string
        "type": string
        "colorName": string
        "flagged": number
        "armed": number
        "cues": [ { }, { }, { } ]
    }
]
colorName may be: none, red, orange, green, blue, or purple.

Note: Methods that reply with an array of cue dictionaries may generate large OSC messages. These messages can easily grow larger than the maximum size supported by UDP datagrams. If you need to access these methods you should communicate to QLab over a TCP connection rather than a UDP connection.

/workspace/{id}/cue/{number}
/workspace/{id}/cue_id/{id}
These are addresses for a specific cue in a specific workspace. See below for cue commands that can be invoked on these addresses.

Cue methods
Cue methods can be invoked either on a specific workspace (using the /workspace/{id}/cue/... address pattern as described above) or “rootless”, where the /cue/... address pattern is applied to the current front-most workspace.

Cues can be addressed either by their cue number or their unique ID.

Cues always have a unique ID. They do not always have a number, but if it exists it will be unique within the workspace.

For the commands below, any instance of the address pattern:

/cue/{number}

Can be replaced by the equivalent unique ID address:

/cue_id/{id}

Alternately, the currently selected cue or cues in the front workspace can be addressed by the pattern:

/cue/selected

Or the current playback position can be addressed by either of the equivalent patterns:

/cue/playbackPosition

/cue/playhead

Finally, because QLab supports OSC address patterns, you may use OSC wildcards in {number} or {id} specifications. For example:

/cue/*/armed 0 would disarm all cues in the workspace.

/cue/understudy-*/colorName green would set green as the display color for all cues that have a number that starts with the string “understudy-”.

Increment/Decrement Syntax
Simple number properties of cues can be incremented or decremented with the following syntax:

/cue/1/property/+ delta

/cue/1/property/- delta

For example, the command /cue/10/preWait/+ 1 would increase the preWait of cue 10 by one second.

/cue/{number}/valuesForKeys json_string
This special method can be used to request a custom collection of state about the given cue. json_string must be a JSON-formatted string representing an array of keys you wish to query. For example:

/cue/2/valuesForKeys "[\"opacity\",\"surfaceSize\"]"

Would return the values of opacity and surfaceSize of video cue number 2.

/cue/{number}/valuesForKeysWithArguments json_string
This special method can be used to request a custom collection of state about the given cue. json_string must be a JSON-formatted string representing a dictionary of keys and arguments you wish to query. For example:

/cue/2/valuesForKeysWithArguments "{\"level\":[0,0]}"

Would return a dictionary that contains the value of the master volume level of a cue. Note that this method is limited to returning one value per key, even if you send multiple keys with different arguments.

/cue/{number}/start
/cue/{number}/stop
/cue/{number}/hardStop
/cue/{number}/pause
/cue/{number}/resume
/cue/{number}/togglePause
/cue/{number}/load
/cue/{number}/preview
/cue/{number}/reset
/cue/{number}/panic
Tell the specified cue to start/stop/hardStop/pause/resume/togglePause/load/preview/reset/panic.

/cue/{number}/loadAt {number}
If given a number, load the cue at that time in seconds. If no number, equivalent to load.

/cue/{number}/uniqueID
/cue/{number}/type
/cue/{number}/defaultName
/cue/{number}/displayName
/cue/{number}/listName
/cue/{number}/hasFileTargets
/cue/{number}/hasCueTargets
/cue/{number}/allowsEditingDuration
/cue/{number}/isLoaded
/cue/{number}/isRunning
/cue/{number}/isPaused
/cue/{number}/isBroken
/cue/{number}/preWaitElapsed
/cue/{number}/actionElapsed
/cue/{number}/postWaitElapsed
/cue/{number}/percentPreWaitElapsed
/cue/{number}/percentActionElapsed
/cue/{number}/percentPostWaitElapsed
Read-only properties.

/cue/{number}/number {string}
/cue/{number}/name {string}
/cue/{number}/notes {string}
Getters/setters for the given property. Give an argument to set the property.

/cue/{number}/fileTarget {string}
Getter/setter for the file target, if the cue has file targets. You can provide three kinds of paths:

Full paths, e.g. /a/full/path/to/some/file.wav
Paths beginning with a tilde, e.g. ~/a/path/to some/file.mov
Relative paths, e.g. this/is/a/relative/path.mid
Paths beginning with a tilde (~) will be expanded; the tilde signifies “relative to the user’s home directory”.

Relative paths will be interpreted according to the current working directory. Use QLab’s /workingDirectory method to set or get the current working directory.

/cue/{number}/cueTargetNumber {string}
/cue/{number}/cueTargetId {string}
/cue/{number}/preWait {number}
/cue/{number}/duration {number}
/cue/{number}/postWait {number}
Getters/setters for the given property. Give an argument to set the property.

/cue/{number}/continueMode {number}
Get/set the continue mode. Values can be:

0 - NO CONTINUE
1 - AUTO CONTINUE
2 - AUTO FOLLOW
/cue/{number}/flagged {number}
/cue/{number}/autoLoad {number}
/cue/{number}/armed {number}
The final {number} is interpreted as a boolean; 0 equals false, any other number equals true.

/cue/{number}/colorName {string}
Getters/setters for the given property. Give an argument to set the property.

/cue/{number}/children
Read-only; gets a list of the children of the cue. Returns the same data as the workspace /cueLists method.

Group cue methods
Methods specific to group cues.

/cue/{number}/mode {number}
Getter/setter for the mode of the group cue. Valid modes to set on a group are 1, 2, 3, or 4. A cue list will return mode 0, but the mode can not be set to 0.

/cue/{number}/playheadId {string}
/cue/{number}/playbackPositionId {string}
Getter/setter for the playback position of the cue.

As a getter, returns a cue uniqueID, or “none” if there is no current playback position.

As a setter, provide a cue uniqueID string.

Only applies if the group is a cue list.

/cue/{number}/playheadNumber {string}
/cue/{number}/playbackPositionNumber {string}
Getter/setter for the playback position of the cue.

As a getter, returns a cue number (which may be an empty string), or “none” if there is no current playback position.

As a setter, provide a cue number string.

Only applies if the group is a cue list.

Audio cue methods
Methods specific to audio cues.

/cue/{number}/patchList
Read-only; return a list of audio patches defined for this workspace:

[
    {
        "patchNumber": integer,
        "patchName": string
    }
]
/cue/{number}/patch {number}
A number from 1 to 8.

/cue/{number}/startTime {number}
/cue/{number}/endTime {number}
Times must be entered in seconds as any whole or decimal number greater than or equal to zero.

/cue/{number}/playCount {number}
playCount can be any whole number greater than or equal to 0.

/cue/{number}/infiniteLoop {number}
The final {number} is interpreted as a boolean; 0 equals false, any other number equals true.

/cue/{number}/rate {number}
Rate can be any positive decimal value from 0.03 to 33.0.

/cue/{number}/doPitchShift {number}
The final {number} is interpreted as a boolean; 0 equals false, any other number equals true.

/cue/{number}/doFade {number}
Getters/setters for the given property. Give an argument to set the property.

/cue/{number}/sliderLevel channel {decibel}
/cue/{number}/sliderLevel/channel {decibel}
Get or set a single slider volume level.

channel is either an integer from 0 to 48, or a string (the channel name). 0 is the master slider.

decibel is an optional floating point number. When present it is the decibel value to set.

If decibel is sent as a string (e.g. “-inf”) QLab will use the minimum decibel value set in the workspace settings.

/cue/{number}/sliderLevels
Read-only; returns an array of 49 numbers.

/cue/{number}/level inChannel outChannel {decibel}
/cue/{number}/level/inChannel/outChannel {decibel}
Get or set a single volume level.

inChannel is an integer from 0 to 24.

outChannel is either an integer from 0 to 48, or a string (the channel name).

decibel is an optional floating point number. When present it is the decibel value to set.

If decibel is sent as a string (e.g. “-inf”) QLab will use the minimum decibel value set in the workspace settings.

/cue/{number}/level/inChannel/outChannel/+ decibel
/cue/{number}/level/inChannel/outChannel/- decibel
Similar to the above method, but makes a relative adjustment adding or subtracting the given decibels to the given level entry.

/cue/{number}/gang inChannel outChannel {gang}
/cue/{number}/gang/inChannel/outChannel {gang}
Get or set a single level gang.

inChannel is an integer from 0 to 24.

outChannel is either an integer from 0 to 48, or a string (the channel name).

gang is an optional text string. When present it is the gang to set.

/cue/{number}/liveRate {number}
These “live” methods are the same as their non-live counterparts, but operate on the active, “live” value of a running cue, rather than changing the “start state” of the cue. Invoking these methods does not cause the document to have unsaved changes.

Mic cue methods
Methods specific to microphone cues.

/cue/{number}/patchList
Read-only; return a list of audio patches defined for this workspace:

[
    {
        "patchNumber": integer,
        "patchName": string
    }
]
/cue/{number}/patch {number}
A number from 1 to 8.

/cue/{number}/sliderLevel channel {decibel}
/cue/{number}/sliderLevel/channel {decibel}
Get or set a single slider volume level.

channel is either an integer from 0 to 48, or a string (the channel name). 0 is the master slider.

decibel is an optional floating point number. When present it is the decibel value to set.

If decibel is sent as a string (e.g. “-inf”) QLab will use the minimum decibel value set in the workspace settings.

/cue/{number}/sliderLevels
Read-only; returns an array of 49 numbers.

/cue/{number}/level inChannel outChannel {decibel}
/cue/{number}/level/inChannel/outChannel {decibel}
Get or set a single volume level.

inChannel is an integer from 0 to 24.

outChannel is either an integer from 0 to 48, or a string (the channel name).

decibel is an optional floating point number. When present it is the decibel value to set.

If decibel is sent as a string (e.g. “-inf”) QLab will use the minimum decibel value set in the workspace settings.

/cue/{number}/level/inChannel/outChannel/+ decibel
/cue/{number}/level/inChannel/outChannel/- decibel
Similar to the above method, but makes a relative adjustment adding or subtracting the given decibels to the given level entry.

/cue/{number}/gang inChannel outChannel {gang}
/cue/{number}/gang/inChannel/outChannel {gang}
Get or set a single level gang.

inChannel is an integer from 0 to 24.

outChannel is either an integer from 0 to 48, or a string (the channel name).

gang is an optional text string. When present it is the gang to set.

Video cue methods
Methods specific to video cues.

/cue/{number}/surfaceList
Read-only; return a list of surfaces defined for this workspace:

[
    {
        "surfaceName": string,
        "surfaceID": number
    }
]
/cue/{number}/surfaceID {number}
Get/set the surface ID of the cue.

/cue/{number}/patchList
Read-only; return a list of audio patches defined for this workspace:

[
    {
        "patchNumber": integer,
        "patchName": string
    }
]
/cue/{number}/patch {number}
Get/set the audio patch of the cue. A number from 1 to 8.

/cue/{number}/startTime {number}
/cue/{number}/endTime {number}
Times must be entered in seconds as any whole or decimal number greater than or equal to zero.

/cue/{number}/playCount {number}
playCount can be any whole number greater than or equal to 0.

/cue/{number}/infiniteLoop {number}
The final {number} is interpreted as a boolean; 0 equals false, any other number equals true.

/cue/{number}/rate {number}
Rate can be any positive decimal value from 0.03 to 33.0.

/cue/{number}/doPitchShift {number}
The final {number} is interpreted as a boolean; 0 equals false, any other number equals true.

/cue/{number}/holdLastFrame {number}
/cue/{number}/doFade {number}
/cue/{number}/fullScreen {number}
Getters/setters for the given property. Give an argument to set the property.

/cue/{number}/layer {number}
A number between 0 and 1000, inclusive.

/cue/{number}/opacity {number}
A float between 0 and 1.

/cue/{number}/preserveAspectRatio {number}
The final {number} is interpreted as a boolean; 0 equals false, any other number equals true.

/cue/{number}/translationX {number}
/cue/{number}/translationY {number}
/cue/{number}/scaleX {number}
/cue/{number}/scaleY {number}
/cue/{number}/originX {number}
/cue/{number}/originY {number}
Getters/setters for the given property; translation, scale, and origin (anchor). Give an argument to set the property.

/cue/{number}/quaternion {number number number number}
Get/set an array of four floats representing the video’s orientation as a quaternion.

/cue/{number}/cueSize
Read-only; returns the natural size of the cue’s video frame:

{
    "width": number,
    "height": number
}
/cue/{number}/surfaceSize
Read-only; returns the size of the cue’s display surface:

{
    "width": number,
    "height": number
}
/cue/{number}/doEffect {number}
Get/set whether video processing is enabled. The {number} parameter is interpreted as a boolean.

/cue/{number}/effect {number}
Get/set which effect is used:

/cue/{number}/effectSet keyString value
Set the Quartz Composer input for keyString to the given value. Value can be a string or number.

/cue/{number}/sliderLevel channel {decibel}
/cue/{number}/sliderLevel/channel {decibel}
Get or set a single slider volume level.

channel is either an integer from 0 to 48, or a string (the channel name). 0 is the master slider.

decibel is an optional floating point number. When present it is the decibel value to set.

If decibel is sent as a string (e.g. “-inf”) QLab will use the minimum decibel value set in the workspace settings.

/cue/{number}/sliderLevels
Read-only; returns an array of 49 numbers.

/cue/{number}/level inChannel outChannel {decibel}
/cue/{number}/level/inChannel/outChannel {decibel}
Get or set a single volume level.

inChannel is an integer from 0 to 24.

outChannel is either an integer from 0 to 48, or a string (the channel name).

decibel is an optional floating point number. When present it is the decibel value to set.

If decibel is sent as a string (e.g. “-inf”) QLab will use the minimum decibel value set in the workspace settings.

/cue/{number}/level/inChannel/outChannel/+ decibel
/cue/{number}/level/inChannel/outChannel/- decibel
Similar to the above method, but makes a relative adjustment adding or subtracting the given decibels to the given level entry.

/cue/{number}/gang inChannel outChannel {gang}
/cue/{number}/gang/inChannel/outChannel {gang}
Get or set a single level gang.

inChannel is an integer from 0 to 24.

outChannel is either an integer from 0 to 48, or a string (the channel name).

gang is an optional text string. When present it is the gang to set.

/cue/{number}/liveRate {number}
/cue/{number}/liveEffectSet keyString value
These “live” methods are the same as their non-live counterparts, but operate on the active, “live” value of a running cue, rather than changing the “start state” of the cue. Invoking these methods does not cause the document to have unsaved changes.

Titles cue methods
In addition to the following, titles cues respond to all the same methods as video cues.

/cue/{number}/text {string}
Get/set the text of a titles cue as an unformatted string. When setting, the formatting will match the first character of the existing text.

/cue/{number}/liveText {string}
Get/set the live text of a titles cue as an unformatted string. When setting, the formatting will match the first character of the existing text. The live text will be the same as the text by default. If you set the live text, it will change what the cue displays without marking the workspace as edited. Resetting the cue will change the text back to the initial text.

/cue/{number}/fixedWidth {number}
Get/set the “fixedWidth” property of a Titles cue.

Fade cue methods
Methods specific to fade cues.

/cue/{number}/sliderLevel channel {decibel}
/cue/{number}/sliderLevel/channel {decibel}
Get or set a single slider volume level.

channel is an integer from 0 to 48. 0 is the master slider.

decibel is an optional floating point number. When present it is the decibel value to set.

If decibel is sent as a string (e.g. “-inf”) QLab will use the minimum decibel value set in the workspace settings.

/cue/{number}/sliderLevels
Read-only; returns an array of 49 numbers.

/cue/{number}/level inChannel outChannel {decibel}
/cue/{number}/level/inChannel/outChannel {decibel}
Get or set a single volume level.

inChannel is an integer from 0 to 24.

outChannel is an integer from 0 to 48.

decibel is an optional floating point number. When present it is the decibel value to set.

If decibel is sent as a string (e.g. “-inf”) QLab will use the minimum decibel value set in the workspace settings.

/cue/{number}/level/inChannel/outChannel/+ decibel
/cue/{number}/level/inChannel/outChannel/- decibel
Similar to the above method, but makes a relative adjustment adding or subtracting the given decibels to the given level entry.

/cue/{number}/gang inChannel outChannel {gang}
/cue/{number}/gang/inChannel/outChannel {gang}
Get or set a single level gang.

inChannel is an integer from 0 to 24.

outChannel is an integer from 0 to 48.

gang is an optional text string. When present it is the gang to set.

OSC cue methods
Methods specific to OSC cues.

/cue/{number}/patch {number}
A number from 1 to 16.

/cue/{number}/messageType {number}
/cue/{number}/qlabCommand {number}
/cue/{number}/qlabCueNumber {string}
/cue/{number}/qlabCueParameters {string}
/cue/{number}/customString {string}
/cue/{number}/udpString {string}
MIDI cue methods
Methods specific to MIDI cues.

/cue/{number}/duration {number}
If the cue fades over a value, this is the duration of the fade.

/cue/{number}/patch {number}
A number from 1 to 8.

/cue/{number}/messageType {number}
1 = MIDI Voice Message ("Musical MIDI")
2 = MIDI Show Control Message (MSC)
3 = MIDI SysEx Message
/cue/{number}/status {number}
0 = Note Off
1 = Note On
2 = Key Pressure (Aftertouch)
3 = Control Change
4 = Program Change
5 = Channel Pressure Change
6 = Pitch Bend Change
/cue/{number}/channel {number}
A number from 1 to 16.

/cue/{number}/byte1 {number}
A number from 0 to 127.

/cue/{number}/byte2 {number}
A number from 0 to 127.

/cue/{number}/byteCombo {number}
A number from 0 to 16383.

/cue/{number}/doFade {number}
A boolean.

/cue/{number}/endValue {number}
A number from 0 to 127, unless fading a pitch bend, in which case it can go to 16383.

/cue/{number}/deviceID {number}
A number from 0 to 127.

/cue/{number}/commandFormat {number}
A number from 1 to 127, but only the following values are meaningful:

127 = All Types
1   = Lighting (General)
2   = Moving Lights
3   = Color Changers
4   = Strobes
5   = Lasers
6   = Chasers
16  = Sound (General)
17  = Music
18  = CD Players
19  = EPROM Playback
20  = Audio Tape Machines
21  = Intercoms
22  = Amplifiers
23  = Audio Effects Devices
24  = Equalizers
32  = Machinery (General)
33  = Rigging
34  = Flys
35  = Lifts
36  = Turntables
37  = Trusses
38  = Robots
39  = Animation
40  = Floats
41  = Breakaways
42  = Barges
48  = Video (General)
49  = Video Tape Machines
50  = Video Cassette Machines
51  = Video Disc Players
52  = Video Switchers
53  = Video Effects
54  = Video Character Generators
55  = Video Still Stores
56  = Video Monitors
64  = Projection (General)
65  = Film Projectors
66  = Slide Projectors
67  = Video Projectors
68  = Dissolvers
69  = Shutter Controls
80  = Process Control (General)
81  = Hydraulic Oil
82  = H2O
83  = CO2
84  = Compressed Air
85  = Natural Gas
86  = Fog
87  = Smoke
88  = Cracked Haze
96  = Pyrotechnics (General)
97  = Fireworks
98  = Explosions
99  = Flame
100 = Smoke Pots
/cue/{number}/command {number}
A number from 1 to 127, but only the following values are meaningful:

1  = GO
2  = STOP
3  = RESUME
4  = TIMED_GO
5  = LOAD
6  = SET
7  = FIRE
8  = ALL_OFF
9  = RESTORE
10 = RESET
11 = GO_OFF
16 = GO/JAM_CLOCK
17 = STANDBY_+
18 = STANDBY_-
19 = SEQUENCE_+
20 = SEQUENCE_-
21 = START_CLOCK
22 = STOP_CLOCK
23 = ZERO_CLOCK
24 = SET_CLOCK
25 = MTC_CHASE_ON
26 = MTC_CHASE_OFF
27 = OPEN_CUE_LIST
28 = CLOSE_CUE_LIST
29 = OPEN_CUE_PATH
30 = CLOSE_CUE_PATH
/cue/{number}/qNumber {string}
/cue/{number}/qList {string}
/cue/{number}/qPath {string}
/cue/{number}/macro {number}
A number from 0 to 127.

/cue/{number}/controlNumber {number}
A number from 0 to 16383.

/cue/{number}/controlValue {number}
A number from 0 to 16383.

/cue/{number}/timecodeString {string}
/cue/{number}/hours {number}
/cue/{number}/minutes {number}
/cue/{number}/seconds {number}
/cue/{number}/frames {number}
/cue/{number}/subframes {number}
/cue/{number}/timecodeFormat {number}
/cue/{number}/rawString {string}
MIDI file cue methods
Methods specific to MIDI File cues.

/cue/{number}/patch {number}
/cue/{number}/rate {number}
Devamp cue methods
Methods specific to Devamp cues.

/cue/{number}/startNextCueWhenSliceEnds {number}
/cue/{number}/stopTargetWhenSliceEnds {number}
Booleans.

Script cue methods
Methods specific to Script cues.

/cue/{number}/scriptSource
Read only; returns a string representing the AppleScript contained in the cue.
