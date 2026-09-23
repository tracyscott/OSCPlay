package xyz.theforks.nodes;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import xyz.theforks.calibration.Calibration;
import xyz.theforks.calibration.CalibrationBuilder;
import xyz.theforks.calibration.CalibrationSample;
import xyz.theforks.calibration.CalibrationStore;
import xyz.theforks.calibration.CurveModel;

/**
 * Interlace tower rotation from a magnetometer: a {@link CalibrateNode} preset.
 *
 * Each tower rotates through a 270 degree arc between welded limit stops. The surrounding
 * steel distorts the field, so the sensor traces a warped 3D curve rather than a circle,
 * which {@link CurveModel} handles. Calibrate by recording the tower resting against the
 * 0 degree stop, then at each 10 degree mark, ending at the 270 degree stop; see
 * {@link #calibrationBuilder(int)}. The output is the tower angle in degrees.
 *
 * If the project has no calibration, falls back to the legacy calibration{N}.csv
 * (timestamp,magx,magy,magz) in the working directory, which outputs 0-1 along the sweep.
 */
public class InterlaceMagNode extends CalibrateNode {
    public static final double ARC_DEGREES = 270;
    public static final double MARK_SPACING_DEGREES = 10;

    private int magNum;

    public static String addressFor(int magNum) {
        return "/lx/modulation/Mag" + magNum + "/mag";
    }

    public static String calibrationNameFor(int magNum) {
        return "Interlace-Mag" + magNum;
    }

    /**
     * Builder for a tower's calibration from a mark-to-mark recording.
     */
    public static CalibrationBuilder calibrationBuilder(int magNum) {
        return new CalibrationBuilder()
            .name(calibrationNameFor(magNum))
            .address(addressFor(magNum))
            .dimensions(3)
            .model(CurveModel.TYPE)
            .labels(CalibrationBuilder.labelRange(0, ARC_DEGREES, MARK_SPACING_DEGREES));
    }

    @Override
    public String label() {
        return "Interlace Magnometer";
    }

    @Override
    public String getHelp() {
        Calibration calibration = getCalibration();
        if (calibration == null) {
            return "Interlace Magnometer " + magNum + ": no calibration";
        }
        return "Interlace Magnometer " + magNum + ": " + calibration.getName() + ", "
            + (calibration.getPoints().isEmpty() ? "0-1 along sweep" : "degrees");
    }

    @Override
    public int getNumArgs() {
        return 1;
    }

    @Override
    public String[] getArgNames() {
        return new String[]{"Magnometer Number"};
    }

    @Override
    public String[] getArgs() {
        return new String[]{String.valueOf(magNum)};
    }

    @Override
    public boolean configure(String[] args) {
        if (args.length != 1) {
            return false;
        }
        int magNum;
        try {
            magNum = Integer.parseInt(args[0].trim());
        } catch (NumberFormatException e) {
            System.err.println("Invalid argument: " + args[0]);
            return false;
        }
        if (magNum < 1 || magNum > 3) {
            return false;
        }
        this.magNum = magNum;

        try {
            CalibrationStore store = calibrationStore();
            String name = calibrationNameFor(magNum);
            Calibration calibration = store != null && store.exists(name)
                ? store.load(name)
                : loadLegacyCsv("calibration" + magNum + ".csv");
            return useCalibration(addressFor(magNum), calibration);
        } catch (IOException e) {
            System.err.println("Error loading calibration data: " + e.getMessage());
            return false;
        }
    }

    /**
     * Load a legacy calibration sweep CSV (timestamp,magx,magy,magz) as an unlabeled curve calibration.
     */
    public Calibration loadLegacyCsv(String filename) throws IOException {
        List<CalibrationSample> samples = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line = reader.readLine(); // Skip header
            while ((line = reader.readLine()) != null) {
                String[] values = line.split(",");
                if (values.length >= 4) {
                    samples.add(new CalibrationSample(
                        Long.parseLong(values[0].trim()),
                        new double[]{
                            Double.parseDouble(values[1].trim()),
                            Double.parseDouble(values[2].trim()),
                            Double.parseDouble(values[3].trim())
                        }));
                }
            }
        }
        Calibration calibration = new Calibration(calibrationNameFor(magNum), CurveModel.TYPE,
            addressFor(magNum), 3, samples, new ArrayList<>());
        calibration.setSourceRecording(filename);
        return calibration;
    }
}
