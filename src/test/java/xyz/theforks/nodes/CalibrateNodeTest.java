package xyz.theforks.nodes;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.illposed.osc.OSCMessage;

import xyz.theforks.calibration.Calibration;
import xyz.theforks.calibration.CalibrationStore;
import xyz.theforks.calibration.LabeledPoint;
import xyz.theforks.calibration.LinearModel;
import xyz.theforks.model.MessageRequest;

class CalibrateNodeTest {

    @TempDir
    Path tempDir;

    private CalibrationStore store;
    private CalibrateNode node;

    @BeforeEach
    void setUp() throws IOException {
        store = new CalibrationStore(tempDir);
        CalibrateNode.setCalibrationStore(store);
        // Fader reading 0-1000 maps to 0-100%, with a bend at 50%
        store.save(new Calibration("fader", LinearModel.TYPE, "/fader", 1, new ArrayList<>(), Arrays.asList(
            new LabeledPoint(new double[]{0}, 0),
            new LabeledPoint(new double[]{200}, 50),
            new LabeledPoint(new double[]{1000}, 100))));
        node = new CalibrateNode();
    }

    @AfterEach
    void tearDown() {
        CalibrateNode.setCalibrationStore(null);
    }

    private static List<MessageRequest> requests(OSCMessage message) {
        List<MessageRequest> requests = new ArrayList<>();
        requests.add(new MessageRequest(message));
        return requests;
    }

    @Test
    void testRegistered() {
        assertTrue(Arrays.stream(NodeRegistry.getNodes()).anyMatch(n -> n.getClass() == CalibrateNode.class));
    }

    @Test
    void testArgs() {
        assertEquals(2, node.getNumArgs());
        assertArrayEquals(new String[]{"Address Pattern", "Calibration"}, node.getArgNames());
        assertTrue(node.configure(new String[]{"/fader.*", "fader"}));
        assertArrayEquals(new String[]{"/fader.*", "fader"}, node.getArgs());
        assertEquals("/fader.*", node.getAddressPattern());
    }

    @Test
    void testConfigureFailures() {
        assertFalse(node.configure(new String[]{"/fader"}));
        assertFalse(node.configure(new String[]{"/fader", "missing"}));

        // A calibration that can't be fit
        assertDoesNotThrow(() -> store.save(new Calibration("broken", LinearModel.TYPE, "/x", 1,
            new ArrayList<>(), new ArrayList<>())));
        assertFalse(node.configure(new String[]{"/x", "broken"}));
    }

    @Test
    void testNoProjectOpen() {
        CalibrateNode.setCalibrationStore(null);
        CalibrateNode.setProjectManager(null);
        assertFalse(node.configure(new String[]{"/fader", "fader"}));
    }

    @Test
    void testProcessCalibratesReading() {
        node.configure(new String[]{"/fader", "fader"});
        List<MessageRequest> requests = requests(new OSCMessage("/fader", Arrays.asList(600)));
        node.process(requests);

        assertEquals(1, requests.size());
        OSCMessage out = requests.get(0).getMessage();
        assertEquals("/fader", out.getAddress());
        assertEquals(75f, (Float) out.getArguments().get(0), 1e-4f);
    }

    @Test
    void testProcessPassesThroughNonReadings() {
        node.configure(new String[]{"/fader", "fader"});

        OSCMessage text = new OSCMessage("/fader", Arrays.asList("hello"));
        List<MessageRequest> requests = requests(text);
        node.process(requests);
        assertSame(text, requests.get(0).getMessage());

        OSCMessage noArgs = new OSCMessage("/fader");
        requests = requests(noArgs);
        node.process(requests);
        assertSame(noArgs, requests.get(0).getMessage());

        OSCMessage otherAddress = new OSCMessage("/other", Arrays.asList(600));
        requests = requests(otherAddress);
        node.process(requests);
        assertSame(otherAddress, requests.get(0).getMessage());
    }

    @Test
    void testReloadCalibration() throws IOException {
        node.configure(new String[]{"/fader", "fader"});
        CalibrateNode other = new CalibrateNode();
        store.save(new Calibration("knob", LinearModel.TYPE, "/knob", 1, new ArrayList<>(), Arrays.asList(
            new LabeledPoint(new double[]{0}, 0), new LabeledPoint(new double[]{10}, 1))));
        other.configure(new String[]{"/knob", "knob"});

        // Re-record the fader: now 0-1000 maps straight to 0-10
        store.save(new Calibration("fader", LinearModel.TYPE, "/fader", 1, new ArrayList<>(), Arrays.asList(
            new LabeledPoint(new double[]{0}, 0), new LabeledPoint(new double[]{1000}, 10))));
        List<OSCNode> nodes = Arrays.asList(node, other, new DropNode());
        assertEquals(1, CalibrateNode.reloadCalibration(nodes, "fader"));

        List<MessageRequest> requests = requests(new OSCMessage("/fader", Arrays.asList(600)));
        node.process(requests);
        assertEquals(6f, (Float) requests.get(0).getMessage().getArguments().get(0), 1e-4f);
    }

    @Test
    void testProcessPassesThroughWithoutCalibration() {
        OSCMessage message = new OSCMessage("/fader", Arrays.asList(600));
        List<MessageRequest> requests = requests(message);
        node.process(requests);
        assertSame(message, requests.get(0).getMessage());
    }
}
