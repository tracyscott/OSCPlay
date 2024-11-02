# OSCPlay
OSC Proxy Record Play utility.


# Pre-requisites
OSCPlay was built using JDK 17.  You will need to
install JDK 17.

OSCPlay uses JavaFX.  You will need to install the Java FX SDK v17 and set an environment variable
to that location (windows syntax):
```
SET JFX_SDK=C:\\Users\tracy\\javafx-sdk-17.0.13\\lib
```

# Installation
Unzip the osc-play.zip file in a convenient location.  There are a couple of wrapper batch files and a JAR file.  When running, it will store data relative to the current working directory.  There are currently no shell script wrappers for Mac or Linux but they should be straightforward.


# Usage
OSCPlay can be run interactively with a UI for configuring the proxy host and port, recording named sessions, and associating audio files with a recorded session.
```
oscplay.bat --help
```


It is also possible to run it from the command-line non-interactively to play a named session and exit when finished.
```
oscsession.bat mysession
```

If there is an associated audio file (configured previously via the interactive UI) then it will also play when running non-interactively.

Typically I will do the visual scoring for an LED installation inside Ableton Live in order to edit all the automation curves.  Once the score is complete, routing the OSC traffic through OSCPlay will allow for recording the control data and playing it back without Ableton Live.  The audio file can be played from OSCPlay.  The one caveat is that there is no synchronization.  An attempt is made to start the audio as close to the first OSC message as possible, so when scoring it is advised to send some OSC message immediately as the audio starts in Ableton Live.

# Caveats
This is a quick utility meant to solve an immediate need.  Everything is loaded in memory (no streaming to or from disk) and no stored data compression.  I've used it for minutes long visual scoring of audio with a good number of automation tracks in Ableton Live.

Also, it doesn't respect native OSC timing constraints.  Everything is simply based on the original arrival times.

# Future Work
* Streaming to and from disk.
* Proper OSC time handling
* Address re-writing
* Ability to duplicate/tee streams to more endpoints
* Various signal cleaning features such as moving averages, compression, clamping, remapping, normalization, etc.
* Ability to trigger recorded sessions via OSC 
* General usability
* Automation data editor



