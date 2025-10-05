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
        List<MessageRequest> requests = new ArrayList<>();
        requests.add(new MessageRequest(input));
        node.process(requests);

        assertTrue(requests.isEmpty());
    }

    @Test
    void testPassNonMatchingMessage() {
        node.configure(new String[]{"/debug/.*"});
        OSCMessage input = new OSCMessage("/synth/osc1/freq", Collections.singletonList(440.0f));
        List<MessageRequest> requests = new ArrayList<>();
        requests.add(new MessageRequest(input));
        node.process(requests);

        assertFalse(requests.isEmpty());
        OSCMessage result = requests.get(0).getMessage();
        assertNotNull(result);
        assertEquals(input, result);
    }

    @Test
    void testDropExactMatch() {
        node.configure(new String[]{"/debug"});
        OSCMessage input = new OSCMessage("/debug", Collections.emptyList());
        List<MessageRequest> requests = new ArrayList<>();
        requests.add(new MessageRequest(input));
        node.process(requests);

        assertTrue(requests.isEmpty());
    }

    @Test
    void testDropWithComplexPattern() {
        node.configure(new String[]{"/synth/osc[12]/.*"});

        OSCMessage input1 = new OSCMessage("/synth/osc1/freq", Collections.singletonList(440.0f));
        List<MessageRequest> requests1 = new ArrayList<>();
        requests1.add(new MessageRequest(input1));
        node.process(requests1);
        assertTrue(requests1.isEmpty());

        OSCMessage input2 = new OSCMessage("/synth/osc2/volume", Collections.singletonList(0.8f));
        List<MessageRequest> requests2 = new ArrayList<>();
        requests2.add(new MessageRequest(input2));
        node.process(requests2);
        assertTrue(requests2.isEmpty());

        OSCMessage input3 = new OSCMessage("/synth/osc3/freq", Collections.singletonList(880.0f));
        List<MessageRequest> requests3 = new ArrayList<>();
        requests3.add(new MessageRequest(input3));
        node.process(requests3);
        assertFalse(requests3.isEmpty());
        OSCMessage result3 = requests3.get(0).getMessage();
        assertNotNull(result3);
        assertEquals(input3, result3);
    }

    @Test
    void testPassMessageWithMultipleArguments() {
        node.configure(new String[]{"/drop/.*"});
        OSCMessage input = new OSCMessage("/keep/note", Arrays.asList(60, 127, 1000));
        List<MessageRequest> requests = new ArrayList<>();
        requests.add(new MessageRequest(input));
        node.process(requests);

        assertFalse(requests.isEmpty());
        OSCMessage result = requests.get(0).getMessage();
        assertNotNull(result);
        assertEquals(input, result);
        assertEquals(3, result.getArguments().size());
    }

    @Test
    void testDropMessageWithMultipleArguments() {
        node.configure(new String[]{"/drop/.*"});
        OSCMessage input = new OSCMessage("/drop/note", Arrays.asList(60, 127, 1000));
        List<MessageRequest> requests = new ArrayList<>();
        requests.add(new MessageRequest(input));
        node.process(requests);

        assertTrue(requests.isEmpty());
    }

    @Test
    void testDropAllMessages() {
        node.configure(new String[]{"/.*"});

        OSCMessage input1 = new OSCMessage("/anything", Collections.singletonList(1));
        List<MessageRequest> requests1 = new ArrayList<>();
        requests1.add(new MessageRequest(input1));
        node.process(requests1);
        assertTrue(requests1.isEmpty());

        OSCMessage input2 = new OSCMessage("/synth/osc1", Collections.singletonList(2));
        List<MessageRequest> requests2 = new ArrayList<>();
        requests2.add(new MessageRequest(input2));
        node.process(requests2);
        assertTrue(requests2.isEmpty());
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
