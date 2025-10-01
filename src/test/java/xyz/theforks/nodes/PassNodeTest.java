package xyz.theforks.nodes;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.illposed.osc.OSCMessage;

class PassNodeTest {
    private PassNode node;

    @BeforeEach
    void setUp() {
        node = new PassNode();
    }

    @Test
    void testPassMatchingMessage() {
        node.configure(new String[]{"/synth/.*"});
        OSCMessage input = new OSCMessage("/synth/osc1/freq", Collections.singletonList(440.0f));
        OSCMessage result = node.process(input);

        assertNotNull(result);
        assertEquals(input, result);
    }

    @Test
    void testDropNonMatchingMessage() {
        node.configure(new String[]{"/synth/.*"});
        OSCMessage input = new OSCMessage("/debug/log", Collections.singletonList("test"));
        OSCMessage result = node.process(input);

        assertNull(result);
    }

    @Test
    void testPassExactMatch() {
        node.configure(new String[]{"/synth"});
        OSCMessage input = new OSCMessage("/synth", Collections.emptyList());
        OSCMessage result = node.process(input);

        assertNotNull(result);
        assertEquals(input, result);
    }

    @Test
    void testPassWithComplexPattern() {
        node.configure(new String[]{"/control/(volume|pan)"});

        OSCMessage input1 = new OSCMessage("/control/volume", Collections.singletonList(0.8f));
        OSCMessage result1 = node.process(input1);
        assertNotNull(result1);
        assertEquals(input1, result1);

        OSCMessage input2 = new OSCMessage("/control/pan", Collections.singletonList(0.5f));
        OSCMessage result2 = node.process(input2);
        assertNotNull(result2);
        assertEquals(input2, result2);

        OSCMessage input3 = new OSCMessage("/control/reverb", Collections.singletonList(0.3f));
        OSCMessage result3 = node.process(input3);
        assertNull(result3);
    }

    @Test
    void testPassMessageWithMultipleArguments() {
        node.configure(new String[]{"/synth/.*"});
        OSCMessage input = new OSCMessage("/synth/note", Arrays.asList(60, 127, 1000));
        OSCMessage result = node.process(input);

        assertNotNull(result);
        assertEquals(input, result);
        assertEquals(3, result.getArguments().size());
    }

    @Test
    void testDropMessageWithMultipleArguments() {
        node.configure(new String[]{"/synth/.*"});
        OSCMessage input = new OSCMessage("/other/note", Arrays.asList(60, 127, 1000));
        OSCMessage result = node.process(input);

        assertNull(result);
    }

    @Test
    void testPassAllMessages() {
        node.configure(new String[]{"/.*"});

        OSCMessage input1 = new OSCMessage("/anything", Collections.singletonList(1));
        assertNotNull(node.process(input1));

        OSCMessage input2 = new OSCMessage("/synth/osc1", Collections.singletonList(2));
        assertNotNull(node.process(input2));
    }

    @Test
    void testDropAllMessagesWithStrictPattern() {
        node.configure(new String[]{"/exact/match/only"});

        OSCMessage input1 = new OSCMessage("/anything", Collections.singletonList(1));
        assertNull(node.process(input1));

        OSCMessage input2 = new OSCMessage("/synth/osc1", Collections.singletonList(2));
        assertNull(node.process(input2));

        OSCMessage input3 = new OSCMessage("/exact/match/only", Collections.singletonList(3));
        assertNotNull(node.process(input3));
    }

    @Test
    void testConfigureThrowsExceptionWithWrongArgCount() {
        assertThrows(IllegalArgumentException.class, () -> {
            node.configure(new String[]{});
        });

        assertThrows(IllegalArgumentException.class, () -> {
            node.configure(new String[]{"/pattern1", "/pattern2"});
        });
    }

    @Test
    void testGetters() {
        node.configure(new String[]{"/synth/.*"});

        assertEquals("Pass", node.label());
        assertEquals(1, node.getNumArgs());
        assertEquals("/synth/.*", node.getAddressPattern());
        assertArrayEquals(new String[]{"/synth/.*"}, node.getArgs());
        assertArrayEquals(new String[]{"Address Pattern"}, node.getArgNames());
        assertNotNull(node.getHelp());
    }
}
