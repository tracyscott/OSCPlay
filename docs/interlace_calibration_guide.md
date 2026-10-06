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
- Marks on each tower, running from the **0° stop** to the **270° stop**, with both stops counting as marks. Check they are there and readable before you begin. How many to use is up to you:

  | Spacing | Marks | Recording takes | Typical error | Worst error |
  |---|---|---|---|---|
  | 10° | 28 | about 2 min | 0.05° | 0.26° |
  | 15° | 19 | about 1.5 min | 0.06° | 0.15° |
  | **30°** | **10** | **about 40 s** | **0.13°** | **0.34°** |
  | 45° | 7 | about 30 s | 0.31° | 0.74° |
  | 90° | 4 | about 15 s | 1.32° | 2.43° |

  **30° (10 marks) is a good default.** It is well inside the degree or two you can see in the tower, and far quicker to mark out and walk through than 28 stops. Use 10° or 15° if you want the most accurate result and have the time. 90° is only worth it as a rough check.

  The spacing must divide 270° evenly, so 10, 15, 18, 27, 30, 45, 54, 90 and 135 all work; 20 does not. If you enter one that doesn't, the Sensor Calibration window says so instead of quietly mislabelling the last mark.

To check a sensor is working, click **Monitor** in OSCPlay and turn the tower a little. Lines for that tower (for example `/mag1/xyz`) should appear and change as it moves. Close the Monitor window when you're done.

## Step 1: Record the tower visiting every mark

1. Turn the tower until it rests against the **0° stop**.
2. In OSCPlay, click **Start Recording**. Name the recording after the tower and the date, for example `tower1-2026-09-23`. Set **Address filter** to just this tower's sensor, for example `/mag1/xyz`, then click **OK**. All three towers send continuously as soon as they are powered up, so without the filter the recording also fills with the other two.
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
2. Set **Preset** to the tower you just recorded, for example **Interlace Mag 1** for tower 1. This fills in the other settings. Leave them alone except for **Step**, which must match the spacing you marked out: set it to 30 if you used 30° marks. Leave **From** at 0 and **To** at 270.
3. Set **Recording** to the recording you just made. If it's not in the list, click **Refresh**.
4. Look at the green or yellow message at the bottom of the window:
   - **Green, ending "All N marks found."** Good, where N matches your spacing: 28 marks at 10°, 10 marks at 30°. The graph shows that many shaded stripes labelled 0 to 270.
   - **Yellow, "Found N holds for M marks."** The recording didn't come out cleanly, or **Step** doesn't match the spacing you marked. See [If something goes wrong](#if-something-goes-wrong).
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

Do steps 1 to 3 for each remaining tower, choosing the matching preset (**Interlace Mag 2**, **Interlace Mag 3**). The **Address filter** box keeps whatever you last typed, so remember to change its tower number too.

## If something goes wrong

**The message says fewer marks were found than you expect.**
Some stops were probably too short. Record again, and count to 3 at every mark. If the number found is right for the spacing you actually walked, correct **Step** instead of re-recording.

**The message says more marks were found than you expect.**
The tower was probably paused somewhere between marks, or stayed still before you started turning. Record again, and keep the tower moving between marks.

**The recording is much bigger than expected.**
The **Address filter** was probably left blank, so the recording holds all three towers. The marks are still found from the right tower's messages, so the calibration is usable, but the recording is three times the size for no benefit. Set the filter next time.

**"No messages on /mag1/xyz..."**
Either the recording didn't pick up this tower's sensor, or you chose the wrong preset or recording. Check the preset matches the tower. Then use **Monitor** to check the sensor is sending (see [Before you start](#before-you-start)).

**The Save button is greyed out.**
The message at the bottom must be green before you can save. If it's green and Save is still greyed out, check that a project is open (**File → Open...**).

**The numbers in Monitor don't match the marks.**
Check you chose the right preset for this tower. Check the recording started at the 0° stop and ended at the 270° stop. If both are right, record and save the calibration again.

**If all else fails:** it's safe to record and save again as many times as you need. Each save replaces the tower's previous calibration.
