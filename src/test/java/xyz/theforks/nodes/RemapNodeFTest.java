package xyz.theforks.nodes;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.illposed.osc.OSCMessage;
import xyz.theforks.model.MessageRequest;

class RemapNodeFTest {
    private RemapNodeF node;

    @BeforeEach
    void setUp() {
        node = new RemapNodeF();
    }

    /** Degrees of tower rotation onto a normalized Chromatik parameter. */
    private void configureDegreesToNormalized(boolean clamp) {
        node.configure(new String[]{"/lx/modulation/Angles/angle[123]", "0", "270", "0", "1",
            String.valueOf(clamp)});
    }

    private List<MessageRequest> run(OSCMessage input) {
        List<MessageRequest> requests = new ArrayList<>();
        requests.add(new MessageRequest(input));
        node.process(requests);
        return requests;
    }

    private Object firstArgument(List<MessageRequest> requests) {
        assertFalse(requests.isEmpty());
        return requests.get(0).getMessage().getArguments().get(0);
    }

    @Test
    void testRemapsOntoOutputRange() {
        configureDegreesToNormalized(true);
        OSCMessage input = new OSCMessage("/lx/modulation/Angles/angle1",
            Collections.singletonList(135.0f));

        assertEquals(0.5f, (Float) firstArgument(run(input)), 1e-6f);
    }

    @Test
    void testEmitsFloat32() {
        configureDegreesToNormalized(true);
        // A float64 input must still come out as float32, so that Chromatik and
        // downstream float-only nodes such as MovingAvgNode see the right type.
        OSCMessage input = new OSCMessage("/lx/modulation/Angles/angle1",
            Collections.singletonList(135.0d));

        Object result = firstArgument(run(input));
        assertInstanceOf(Float.class, result);
        assertEquals(0.5f, (Float) result, 1e-6f);
    }

    @Test
    void testIntegerArgumentBecomesFloat() {
        configureDegreesToNormalized(true);
        OSCMessage input = new OSCMessage("/lx/modulation/Angles/angle1",
            Collections.singletonList(270));

        Object result = firstArgument(run(input));
        assertInstanceOf(Float.class, result);
        assertEquals(1.0f, (Float) result, 1e-6f);
    }

    @Test
    void testClampsOutsideInputRange() {
        configureDegreesToNormalized(true);
        assertEquals(1.0f, (Float) firstArgument(run(new OSCMessage(
            "/lx/modulation/Angles/angle1", Collections.singletonList(300.0f)))), 1e-6f);
        assertEquals(0.0f, (Float) firstArgument(run(new OSCMessage(
            "/lx/modulation/Angles/angle1", Collections.singletonList(-20.0f)))), 1e-6f);
    }

    @Test
    void testWithoutClampExtrapolates() {
        configureDegreesToNormalized(false);
        OSCMessage input = new OSCMessage("/lx/modulation/Angles/angle1",
            Collections.singletonList(540.0f));

        assertEquals(2.0f, (Float) firstArgument(run(input)), 1e-6f);
    }

    @Test
    void testInvertedOutputRange() {
        node.configure(new String[]{"/sensor/.*", "0", "270", "1", "0", "true"});
        OSCMessage input = new OSCMessage("/sensor/a", Collections.singletonList(270.0f));

        assertEquals(0.0f, (Float) firstArgument(run(input)), 1e-6f);
    }

    @Test
    void testZeroWidthInputRangePinsToOutputMin() {
        node.configure(new String[]{"/sensor/.*", "5", "5", "2", "9", "true"});
        OSCMessage input = new OSCMessage("/sensor/a", Collections.singletonList(5.0f));

        assertEquals(2.0f, (Float) firstArgument(run(input)), 1e-6f);
    }

    @Test
    void testRemapsEveryNumericArgument() {
        configureDegreesToNormalized(true);
        OSCMessage input = new OSCMessage("/lx/modulation/Angles/angle1",
            Arrays.asList(0.0f, 135.0f, 270.0f));

        List<Object> result = run(input).get(0).getMessage().getArguments();
        assertEquals(3, result.size());
        assertEquals(0.0f, (Float) result.get(0), 1e-6f);
        assertEquals(0.5f, (Float) result.get(1), 1e-6f);
        assertEquals(1.0f, (Float) result.get(2), 1e-6f);
    }

    @Test
    void testNonNumericArgumentsPassThrough() {
        configureDegreesToNormalized(true);
        OSCMessage input = new OSCMessage("/lx/modulation/Angles/angle1",
            Arrays.asList("label", 135.0f));

        List<Object> result = run(input).get(0).getMessage().getArguments();
        assertEquals("label", result.get(0));
        assertEquals(0.5f, (Float) result.get(1), 1e-6f);
    }

    @Test
    void testMessageWithNoNumericArgumentsIsUnchanged() {
        configureDegreesToNormalized(true);
        OSCMessage input = new OSCMessage("/lx/modulation/Angles/angle1",
            Collections.singletonList("still"));

        List<MessageRequest> requests = run(input);
        assertSame(input, requests.get(0).getMessage());
    }

    @Test
    void testPassNonMatchingMessage() {
        configureDegreesToNormalized(true);
        OSCMessage input = new OSCMessage("/synth/osc1/freq", Collections.singletonList(440.0f));

        List<MessageRequest> requests = run(input);
        assertSame(input, requests.get(0).getMessage());
    }

    @Test
    void testConfigureRejectsWrongArgCount() {
        assertThrows(IllegalArgumentException.class,
            () -> node.configure(new String[]{"/sensor/.*", "0", "270"}));
    }

    @Test
    void testConfigureRejectsNonNumericRange() {
        assertThrows(IllegalArgumentException.class,
            () -> node.configure(new String[]{"/sensor/.*", "0", "lots", "0", "1", "true"}));
    }

    @Test
    void testRegisteredInNodeRegistry() {
        assertTrue(Arrays.stream(NodeRegistry.getNodeLabels()).anyMatch("Remap Float"::equals));
    }
}
