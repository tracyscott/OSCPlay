package xyz.theforks.calibration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import xyz.theforks.model.OSCMessageRecord;

class CalibrationBuilderTest {

    private static final String ADDRESS = "/sensor/rotation";

    @TempDir
    Path tempDir;

    private static List<OSCMessageRecord> recording(List<Double> marks) {
        return SyntheticSensor.messages(SyntheticSensor.samples(
            SyntheticSensor.markToMarkAngles(marks, 2500, 700), true, 11), ADDRESS);
    }

    private static CalibrationBuilder builder(List<Double> labels) {
        return new CalibrationBuilder()
            .name("rotation")
            .address(ADDRESS)
            .dimensions(3)
            .model(CurveModel.TYPE)
            .labels(labels);
    }

    @Test
    void testLabelRange() {
        assertEquals(List.of(0.0, 10.0, 20.0, 30.0), CalibrationBuilder.labelRange(0, 30, 10));
        assertEquals(List.of(90.0, 45.0, 0.0), CalibrationBuilder.labelRange(90, 0, -45));
        assertEquals(28, CalibrationBuilder.labelRange(0, 270, 10).size());
        assertThrows(IllegalArgumentException.class, () -> CalibrationBuilder.labelRange(0, 90, -10));
        assertThrows(IllegalArgumentException.class, () -> CalibrationBuilder.labelRange(0, 90, 0));
    }

    @Test
    void testExtractsOnlyMatchingAddress() {
        List<Double> marks = CalibrationBuilder.labelRange(0, 90, 30);
        List<CalibrationSample> samples = builder(marks).extractSamples(recording(marks));

        assertEquals(recording(marks).size() / 2, samples.size());
        assertEquals(0, samples.get(0).getTimestamp());
        assertEquals(3, samples.get(0).getValues().length);
    }

    @Test
    void testNumericAddresses() {
        List<Double> marks = CalibrationBuilder.labelRange(0, 90, 30);
        List<OSCMessageRecord> messages = new java.util.ArrayList<>(recording(marks));
        messages.add(new OSCMessageRecord("/text", new Object[]{"hello"}));
        messages.add(new OSCMessageRecord("/mixed", new Object[]{1.5f, "label", 2}));
        messages.add(new OSCMessageRecord("/mixed", new Object[]{1.5f, "label", 2}));

        java.util.Map<String, Integer> addresses = SampleExtractor.numericAddresses(messages);
        // Most frequent first; the two sensors tie, so they're alphabetical
        assertEquals(List.of("/other/sensor", ADDRESS, "/mixed"), List.copyOf(addresses.keySet()));
        assertEquals(3, addresses.get(ADDRESS));
        assertEquals(1, addresses.get("/other/sensor"));
        assertEquals(1, addresses.get("/mixed"));
    }

    @Test
    void testBuildsLabeledCalibration() {
        List<Double> marks = CalibrationBuilder.labelRange(0, 270, 10);
        CalibrationBuilder.Result result = builder(marks).build(recording(marks));

        assertEquals(marks.size(), result.getHolds().size());
        Calibration calibration = result.getCalibration();
        assertEquals(marks.size(), calibration.getPoints().size());
        for (int i = 0; i < marks.size(); i++) {
            assertEquals(marks.get(i), calibration.getPoints().get(i).getLabel());
        }
        assertEquals(135, calibration.getFittedModel().map(SyntheticSensor.curve(135, true)), 1.0);
    }

    @Test
    void testHoldCountMismatchIsReported() {
        List<Double> marks = CalibrationBuilder.labelRange(0, 270, 10);
        // Recording skips the last mark
        List<OSCMessageRecord> messages = recording(marks.subList(0, marks.size() - 1));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> builder(marks).build(messages));
        assertTrue(e.getMessage().contains("27 holds but 28 labels"), e.getMessage());
    }

    @Test
    void testNoMatchingMessages() {
        List<Double> marks = CalibrationBuilder.labelRange(0, 90, 30);
        CalibrationBuilder b = builder(marks).address("/nothing/here");
        assertThrows(IllegalArgumentException.class, () -> b.build(recording(marks)));
    }

    @Test
    void testUnlabeledCurve() {
        List<Double> marks = CalibrationBuilder.labelRange(0, 270, 30);
        Calibration calibration = builder(null).build(recording(marks)).getCalibration();

        assertTrue(calibration.getPoints().isEmpty());
        assertEquals(1.0, calibration.getFittedModel().map(SyntheticSensor.curve(270, true)), 0.02);
    }

    @Test
    void testStoreRoundTrip() throws IOException {
        List<Double> marks = CalibrationBuilder.labelRange(0, 270, 10);
        Calibration built = builder(marks).build(recording(marks)).getCalibration();
        CalibrationStore store = new CalibrationStore(tempDir.resolve("Calibrations"));

        store.save(built);
        assertTrue(store.exists("rotation"));
        assertEquals(List.of("rotation"), store.list());

        Calibration loaded = store.load("rotation");
        assertEquals(CurveModel.TYPE, loaded.getModel());
        assertEquals(ADDRESS, loaded.getAddress());
        assertEquals(3, loaded.getDimensions());
        for (double deg = 0; deg <= 270; deg += 45) {
            double[] reading = SyntheticSensor.curve(deg, true);
            assertEquals(built.getFittedModel().map(reading), loaded.getFittedModel().map(reading), 1e-9);
        }
    }

    @Test
    void testStoreMissingCalibration() {
        CalibrationStore store = new CalibrationStore(tempDir.resolve("Calibrations"));
        assertThrows(IOException.class, () -> store.load("missing"));
        assertDoesNotThrow(() -> assertTrue(store.list().isEmpty()));
    }

    @Test
    void testLabelRangeRejectsStepThatDoesNotDivideEvenly() {
        // 20 degrees across a 270 degree arc would put the last mark at 280, silently
        // mislabelling the far stop.
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> CalibrationBuilder.labelRange(0, 270, 20));
        assertTrue(e.getMessage().contains("280"), e.getMessage());
        assertTrue(e.getMessage().contains("270"), e.getMessage());
    }

    @Test
    void testLabelRangeAcceptsEveryInterlaceMarkSpacing() {
        // The spacings offered for trading setup time against accuracy.
        for (double step : new double[]{5, 6, 9, 10, 15, 18, 27, 30, 45, 54, 90, 135}) {
            List<Double> labels = CalibrationBuilder.labelRange(0, 270, step);
            assertEquals(270.0, labels.get(labels.size() - 1), 1e-9,
                "spacing " + step + " should end on the far stop");
            assertEquals(0.0, labels.get(0), 1e-9);
            assertEquals((int) Math.round(270 / step) + 1, labels.size(), "spacing " + step);
        }
    }

    @Test
    void testLabelRangeMessageNamesBothEnds() {
        // 100 / 30 rounds to 3 steps, so the last mark would land on 90, short of 100.
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> CalibrationBuilder.labelRange(0, 100, 30));
        assertTrue(e.getMessage().contains("90"), e.getMessage());
        assertTrue(e.getMessage().contains("100"), e.getMessage());
        // Reads as whole numbers rather than 90.0 / 100.0.
        assertFalse(e.getMessage().contains("100.0"), e.getMessage());
    }
}
