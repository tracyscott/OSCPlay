package xyz.theforks.calibration;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import xyz.theforks.model.RecordingSession;
import xyz.theforks.nodes.InterlaceMagNode;
import xyz.theforks.service.ProjectManager;

/**
 * Command line tool that builds a calibration from a recording and saves it to a project.
 */
public class CalibrationTool {

    private static void usage() {
        System.out.println("Usage: CalibrationTool --project <name> (--recording <name> | --csv <file>) [options]");
        System.out.println();
        System.out.println("Sensor (either a preset or explicit settings):");
        System.out.println("  --preset interlace --mag <1-3>   Interlace tower: 3-axis curve, marks 0-270 every 10 degrees");
        System.out.println("  --name <calibration>            Calibration name");
        System.out.println("  --address <regex>               Sensor message address pattern");
        System.out.println("  --dims <n>                      Numeric arguments per reading (default 1)");
        System.out.println("  --model linear|curve            Calibration model (default linear)");
        System.out.println("  --labels <start>:<end>:<step>   Values at the marks, in the order visited");
        System.out.println("  --no-labels                     Curve model only: output 0-1 along the sweep");
        System.out.println();
        System.out.println("Hold detection:");
        System.out.println("  --min-hold <ms>                 Minimum time held still at a mark (default "
            + HoldDetector.DEFAULT_MIN_HOLD_MS + ")");
        System.out.println("  --threshold <units>             Stillness threshold (default: "
            + HoldDetector.DEFAULT_NOISE_MULTIPLE + " x estimated noise)");
        System.out.println();
        System.out.println("  --csv <file>                    Read timestamp,v1,v2,... rows instead of a recording");
        System.out.println("  --dry-run                       Report what was found without saving");
    }

    public static void main(String[] args) {
        try {
            System.exit(run(args));
        } catch (IllegalArgumentException | IOException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }

    static int run(String[] args) throws IOException {
        String project = null;
        String recording = null;
        String csv = null;
        String preset = null;
        Integer mag = null;
        String labels = null;
        boolean noLabels = false;
        boolean dryRun = false;
        CalibrationBuilder builder = new CalibrationBuilder();

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.equals("--dry-run")) {
                dryRun = true;
                continue;
            } else if (arg.equals("--no-labels")) {
                noLabels = true;
                continue;
            } else if (arg.equals("--help")) {
                usage();
                return 0;
            }
            if (i + 1 >= args.length) {
                throw new IllegalArgumentException("Missing value for " + arg);
            }
            String value = args[++i];
            switch (arg) {
                case "--project": project = value; break;
                case "--recording": recording = value; break;
                case "--csv": csv = value; break;
                case "--preset": preset = value; break;
                case "--mag": mag = Integer.parseInt(value); break;
                case "--name": builder.name(value); break;
                case "--address": builder.address(value); break;
                case "--dims": builder.dimensions(Integer.parseInt(value)); break;
                case "--model": builder.model(value); break;
                case "--labels": labels = value; break;
                case "--min-hold": builder.minHoldMs(Long.parseLong(value)); break;
                case "--threshold": builder.threshold(Double.parseDouble(value)); break;
                default:
                    throw new IllegalArgumentException("Unknown option: " + arg);
            }
        }

        if (preset != null) {
            if (!preset.equals("interlace")) {
                throw new IllegalArgumentException("Unknown preset: " + preset);
            }
            if (mag == null) {
                throw new IllegalArgumentException("--preset interlace needs --mag <1-3>");
            }
            CalibrationBuilder p = InterlaceMagNode.calibrationBuilder(mag);
            builder.name(builder.getName() != null ? builder.getName() : p.getName())
                .address(builder.getAddress() != null ? builder.getAddress() : p.getAddress())
                .dimensions(p.getDimensions())
                .model(p.getModel())
                .labels(p.getLabels());
        }
        if (labels != null) {
            String[] parts = labels.split(":");
            if (parts.length != 3) {
                throw new IllegalArgumentException("--labels must be <start>:<end>:<step>");
            }
            builder.labels(CalibrationBuilder.labelRange(
                Double.parseDouble(parts[0]), Double.parseDouble(parts[1]), Double.parseDouble(parts[2])));
        }
        if (noLabels) {
            builder.labels(null);
        }
        if (project == null || builder.getName() == null || builder.getAddress() == null
                || (recording == null) == (csv == null)) {
            usage();
            return 1;
        }

        List<CalibrationSample> samples;
        String source;
        if (csv != null) {
            samples = readCsv(csv, builder.getDimensions());
            source = csv;
        } else {
            RecordingSession.setRecordingsDirectory(
                ProjectManager.getProjectsDir().resolve(project).resolve("Recordings"));
            RecordingSession session = RecordingSession.loadSession(recording);
            if (session == null) {
                throw new IOException("Recording not found: " + recording);
            }
            samples = builder.extractSamples(session.getMessages());
            source = recording;
        }

        System.out.printf("%d samples from %s%n", samples.size(), source);
        if (samples.isEmpty()) {
            return 1;
        }

        if (builder.getLabels() != null) {
            HoldDetector detector = builder.holdDetector(samples);
            System.out.printf("Estimated noise %.1f, stillness threshold %.1f, minimum hold %d ms%n",
                HoldDetector.estimateNoise(samples), detector.getThreshold(), detector.getMinHoldMs());
            List<HoldDetector.Hold> holds = detector.detect(samples);
            List<Double> labelList = builder.getLabels();
            System.out.printf("Found %d holds, expected %d:%n", holds.size(), labelList.size());
            for (int i = 0; i < holds.size(); i++) {
                HoldDetector.Hold h = holds.get(i);
                System.out.printf("  %2d  %7.2fs - %7.2fs  %s%s%n", i + 1,
                    h.getStartTime() / 1000.0, h.getEndTime() / 1000.0, format(h.getMean()),
                    i < labelList.size() ? "  -> " + labelList.get(i) : "  (no label)");
            }
        }

        CalibrationBuilder.Result result = builder.buildFromSamples(samples);
        Calibration calibration = result.getCalibration();
        calibration.setSourceRecording(source);
        System.out.println("Fit " + calibration.getModel() + " calibration "
            + calibration.getName() + " with " + calibration.getPoints().size() + " labeled points");

        if (dryRun) {
            System.out.println("Dry run: not saved");
            return 0;
        }
        CalibrationStore store = new CalibrationStore(ProjectManager.getCalibrationsDir(project));
        store.save(calibration);
        System.out.println("Saved " + store.pathFor(calibration.getName()));
        return 0;
    }

    /**
     * Read timestamp,v1,v2,... rows (with a header line) as samples, using the first
     * {@code dimensions} values of each row.
     */
    private static List<CalibrationSample> readCsv(String file, int dimensions) throws IOException {
        List<CalibrationSample> samples = new ArrayList<>();
        long firstTimestamp = -1;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line = reader.readLine(); // Skip header
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length < dimensions + 1) {
                    continue;
                }
                long timestamp = Long.parseLong(parts[0].trim());
                if (firstTimestamp < 0) {
                    firstTimestamp = timestamp;
                }
                double[] values = new double[dimensions];
                for (int k = 0; k < dimensions; k++) {
                    values[k] = Double.parseDouble(parts[k + 1].trim());
                }
                samples.add(new CalibrationSample(timestamp - firstTimestamp, values));
            }
        }
        return samples;
    }

    private static String format(double[] values) {
        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < values.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(String.format("%.0f", values[i]));
        }
        return sb.append(")").toString();
    }
}
