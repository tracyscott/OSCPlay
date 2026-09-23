package xyz.theforks.nodes;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.illposed.osc.OSCMessage;

import xyz.theforks.calibration.Calibration;
import xyz.theforks.calibration.CalibrationBuilder;
import xyz.theforks.calibration.CalibrationSample;
import xyz.theforks.calibration.CalibrationStore;
import xyz.theforks.calibration.CurveModel;
import xyz.theforks.calibration.SyntheticSensor;
import xyz.theforks.model.MessageRequest;

class InterlaceMagNodeTest {

    @TempDir
    Path tempDir;

    private CalibrationStore store;

    @BeforeEach
    void setUp() {
        store = new CalibrationStore(tempDir.resolve("Calibrations"));
        CalibrateNode.setCalibrationStore(store);
    }

    @AfterEach
    void tearDown() {
        CalibrateNode.setCalibrationStore(null);
    }

    /** Record tower 1 visiting each mark and save its calibration. */
    private void saveMarkToMarkCalibration() throws IOException {
        CalibrationBuilder builder = InterlaceMagNode.calibrationBuilder(1);
        List<CalibrationSample> samples = SyntheticSensor.samples(
            SyntheticSensor.markToMarkAngles(builder.getLabels(), 2500, 700), true, 21);
        store.save(builder.build(SyntheticSensor.messages(samples, builder.getAddress())).getCalibration());
    }

    private static List<MessageRequest> reading(int magNum, double[] v) {
        List<MessageRequest> requests = new ArrayList<>();
        requests.add(new MessageRequest(new OSCMessage(InterlaceMagNode.addressFor(magNum),
            Arrays.asList((int) Math.round(v[0]), (int) Math.round(v[1]), (int) Math.round(v[2])))));
        return requests;
    }

    @Test
    void testRegistered() {
        assertTrue(Arrays.stream(NodeRegistry.getNodes()).anyMatch(n -> n instanceof InterlaceMagNode));
    }

    @Test
    void testPreset() {
        CalibrationBuilder builder = InterlaceMagNode.calibrationBuilder(2);
        assertEquals("Interlace-Mag2", builder.getName());
        assertEquals("/lx/modulation/Mag2/mag", builder.getAddress());
        assertEquals(3, builder.getDimensions());
        assertEquals(CurveModel.TYPE, builder.getModel());
        assertEquals(28, builder.getLabels().size());
        assertEquals(0.0, builder.getLabels().get(0));
        assertEquals(270.0, builder.getLabels().get(27));
    }

    @Test
    void testArgs() {
        InterlaceMagNode node = new InterlaceMagNode();
        assertEquals(1, node.getNumArgs());
        assertArrayEquals(new String[]{"Magnometer Number"}, node.getArgNames());
        assertFalse(node.configure(new String[]{"0"}));
        assertFalse(node.configure(new String[]{"4"}));
        assertFalse(node.configure(new String[]{"x"}));
        assertFalse(node.configure(new String[]{"1", "2"}));
    }

    @Test
    void testOutputsDegreesFromProjectCalibration() throws IOException {
        saveMarkToMarkCalibration();
        InterlaceMagNode node = new InterlaceMagNode();
        assertTrue(node.configure(new String[]{"1"}));
        assertArrayEquals(new String[]{"1"}, node.getArgs());
        assertEquals("/lx/modulation/Mag1/mag", node.getAddressPattern());

        for (double deg = 0; deg <= 270; deg += 15) {
            List<MessageRequest> requests = reading(1, SyntheticSensor.curve(deg, true));
            node.process(requests);
            OSCMessage out = requests.get(0).getMessage();
            assertEquals("/lx/modulation/Mag1/mag", out.getAddress());
            assertEquals(deg, (Float) out.getArguments().get(0), 1.0, "at " + deg + " degrees");
        }
    }

    @Test
    void testLegacyCsv() throws IOException {
        Path csv = tempDir.resolve("calibration1.csv");
        List<CalibrationSample> samples = SyntheticSensor.samples(SyntheticSensor.sweepAngles(), false, 22);
        try (PrintWriter out = new PrintWriter(csv.toFile())) {
            out.println("timestamp,magx,magy,magz");
            for (CalibrationSample s : samples) {
                double[] v = s.getValues();
                out.printf("%d,%d,%d,%d%n", s.getTimestamp(), Math.round(v[0]), Math.round(v[1]), Math.round(v[2]));
            }
        }

        InterlaceMagNode node = new InterlaceMagNode();
        Calibration calibration = node.loadLegacyCsv(csv.toString());
        assertEquals(CurveModel.TYPE, calibration.getModel());
        assertTrue(calibration.getPoints().isEmpty());
        assertEquals(samples.size(), calibration.getSamples().size());
        // Unlabeled: 0-1 along the sweep
        assertEquals(0.5, calibration.getFittedModel().map(SyntheticSensor.curve(135, false)), 0.01);
    }

    @Test
    void testNoCalibrationAvailable() {
        // No project calibration and no calibration3.csv in the working directory
        InterlaceMagNode node = new InterlaceMagNode();
        assertFalse(node.configure(new String[]{"3"}));

        OSCMessage message = new OSCMessage("/lx/modulation/Mag3/mag", Arrays.asList(1, 2, 3));
        List<MessageRequest> requests = new ArrayList<>();
        requests.add(new MessageRequest(message));
        node.process(requests);
        assertSame(message, requests.get(0).getMessage());
    }
}
