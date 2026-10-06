# Calibrating the Interlace Towers

Each of the three Interlace towers has a sensor that reports which way the tower is facing. The steel in the installation bends the sensor's readings, so after every install the sensors need to be calibrated. You do this by turning each tower to a series of marked positions while OSCPlay records what the sensor reads at each one.

It takes about 10 minutes per tower. It's easiest with two people: one to turn the tower, and one at the laptop.

## Before you start

**Fill these in for this installation:**

| | Tower 1 | Tower 2 | Tower 3 |
|---|---|---|---|
| Which tower is it? | | | |
| Which stop is 0°? | | | |

You need:

- The laptop running **OSCPlay** with the **Interlace project** open. The project name is shown in the window title. If it's the wrong one, use **File → Open...**.
- The tower sensors powered on and connected.
- **28 marks** on each tower, 10° apart. The marks run from the **0° stop** to the **270° stop**, and both stops count as marks. Check the marks are there and readable before you begin.

To check a sensor is working, click **Monitor** in OSCPlay and turn the tower a little. Lines for that tower (for example `/lx/modulation/Mag1/mag`) should appear and change as it moves. Close the Monitor window when you're done.

## Step 1: Record the tower visiting every mark

1. Turn the tower until it rests against the **0° stop**.
2. In OSCPlay, click **Start Recording**. Name the recording after the tower and the date, for example `tower1-2026-09-23`, and click **OK**.
3. **Hold the tower still for 3 seconds.** Count "one thousand one, one thousand two, one thousand three".
4. Turn the tower to the **10°** mark and **hold still for 3 seconds**.
5. Keep going, **mark by mark, in order**: 20°, 30°, and so on. At each mark, hold still for 3 seconds.
6. Finish against the **270° stop** and hold still for 3 seconds.
7. Click **Stop Recording**.

Tips:

- Only stop at the marks. Keep moving between them.
- Don't skip a mark, and don't go back to one you've already done.
- If you make a mistake, click **Stop Recording** and start again from the 0° stop with a new recording name. That's quicker than trying to fix it afterwards.

## Step 2: Turn the recording into a calibration

1. In OSCPlay, open **Tools → Sensor Calibration...**.
2. Set **Preset** to the tower you just recorded, for example **Interlace Mag 1** for tower 1. This fills in the other settings. Don't change them.
3. Set **Recording** to the recording you just made. If it's not in the list, click **Refresh**.
4. Look at the green or yellow message at the bottom of the window:
   - **Green, ending "All 28 marks found."** Good. The graph shows 28 shaded stripes labelled 0 to 270.
   - **Yellow, "Found N holds for 28 marks."** The recording didn't come out cleanly. See [If something goes wrong](#if-something-goes-wrong).
5. Click **Save**. If it asks whether to replace the existing calibration, click **OK**.

The tower uses the new calibration straight away. You don't need to restart anything.

## Step 3: Check it works

**First install only:** each tower needs an **Interlace Magnometer** node in OSCPlay. If this tower already has one, skip ahead to the check below. To add one:

1. In the main OSCPlay window, click the **+** (Add) button in the node chain area.
2. Choose **Interlace Magnometer**.
3. Enter the tower number (**1**, **2** or **3**) and confirm.

To check the calibration:

1. Click **Monitor**.
2. Turn the tower to the **90°** mark. The line for this tower should show a number close to **90**.
3. Try another mark or two, such as 0° and 270°. The numbers should match the marks, within a degree or two.

## Repeat for the other towers

Do steps 1 to 3 for each remaining tower, choosing the matching preset (**Interlace Mag 2**, **Interlace Mag 3**).

## If something goes wrong

**The message says fewer than 28 marks were found.**
Some stops were probably too short. Record again, and count to 3 at every mark.

**The message says more than 28 marks were found.**
The tower was probably paused somewhere between marks, or stayed still before you started turning. Record again, and keep the tower moving between marks.

**"No messages on /lx/modulation/Mag1/mag..."**
Either the recording didn't pick up this tower's sensor, or you chose the wrong preset or recording. Check the preset matches the tower. Then use **Monitor** to check the sensor is sending (see [Before you start](#before-you-start)).

**The Save button is greyed out.**
The message at the bottom must be green before you can save. If it's green and Save is still greyed out, check that a project is open (**File → Open...**).

**The numbers in Monitor don't match the marks.**
Check you chose the right preset for this tower. Check the recording started at the 0° stop and ended at the 270° stop. If both are right, record and save the calibration again.

**If all else fails:** it's safe to record and save again as many times as you need. Each save replaces the tower's previous calibration.
