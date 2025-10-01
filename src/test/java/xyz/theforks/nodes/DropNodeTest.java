package xyz.theforks.nodes;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.illposed.osc.OSCMessage;

class DropNodeTest {
    private DropNode node;

    @BeforeEach
    void setUp() {
        node = new DropNode();
    }

    @Test
    void testDropMatchingMessage() {
        node.configure(new String[]{"/debug/.*"});
        OSCMessage input = new OSCMessage("/debug/log", Collections.singletonList("test"));
        OSCMessage result = node.process(input);

        assertNull(result);
    }

    @Test
    void testPassNonMatchingMessage() {
        node.configure(new String[]{"/debug/.*"});
        OSCMessage input = new OSCMessage("/synth/osc1/freq", Collections.singletonList(440.0f));
        OSCMessage result = node.process(input);

        assertNotNull(result);
        assertEquals(input, result);
    }

    @Test
    void testDropExactMatch() {
        node.configure(new String[]{"/debug"});
        OSCMessage input = new OSCMessage("/debug", Collections.emptyList());
        OSCMessage result = node.process(input);

        assertNull(result);
    }

    @Test
    void testDropWithComplexPattern() {
        node.configure(new String[]{"/synth/osc[12]/.*"});

        OSCMessage input1 = new OSCMessage("/synth/osc1/freq", Collections.singletonList(440.0f));
        OSCMessage result1 = node.process(input1);
        assertNull(result1);

        OSCMessage input2 = new OSCMessage("/synth/osc2/volume", Collections.singletonList(0.8f));
        OSCMessage result2 = node.process(input2);
        assertNull(result2);

        OSCMessage input3 = new OSCMessage("/synth/osc3/freq", Collections.singletonList(880.0f));
        OSCMessage result3 = node.process(input3);
        assertNotNull(result3);
        assertEquals(input3, result3);
    }

    @Test
    void testPassMessageWithMultipleArguments() {
        node.configure(new String[]{"/drop/.*"});
        OSCMessage input = new OSCMessage("/keep/note", Arrays.asList(60, 127, 1000));
        OSCMessage result = node.process(input);

        assertNotNull(result);
        assertEquals(input, result);
        assertEquals(3, result.getArguments().size());
    }

    @Test
    void testDropMessageWithMultipleArguments() {
        node.configure(new String[]{"/drop/.*"});
        OSCMessage input = new OSCMessage("/drop/note", Arrays.asList(60, 127, 1000));
        OSCMessage result = node.process(input);

        assertNull(result);
    }

    @Test
    void testDropAllMessages() {
        node.configure(new String[]{"/.*"});

        OSCMessage input1 = new OSCMessage("/anything", Collections.singletonList(1));
        assertNull(node.process(input1));

        OSCMessage input2 = new OSCMessage("/synth/osc1", Collections.singletonList(2));
        assertNull(node.process(input2));
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
        node.configure(new String[]{"/test/.*"});

        assertEquals("Drop", node.label());
        assertEquals(1, node.getNumArgs());
        assertEquals("/test/.*", node.getAddressPattern());
        assertArrayEquals(new String[]{"/test/.*"}, node.getArgs());
        assertArrayEquals(new String[]{"Address Pattern"}, node.getArgNames());
        assertNotNull(node.getHelp());
    }
}
