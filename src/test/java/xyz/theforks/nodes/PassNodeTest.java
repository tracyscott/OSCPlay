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
        List<MessageRequest> requests = new ArrayList<>();
        requests.add(new MessageRequest(input));
        node.process(requests);

        assertFalse(requests.isEmpty());
        OSCMessage result = requests.get(0).getMessage();
        assertNotNull(result);
        assertEquals(input, result);
    }

    @Test
    void testDropNonMatchingMessage() {
        node.configure(new String[]{"/synth/.*"});
        OSCMessage input = new OSCMessage("/debug/log", Collections.singletonList("test"));
        List<MessageRequest> requests = new ArrayList<>();
        requests.add(new MessageRequest(input));
        node.process(requests);

        assertTrue(requests.isEmpty());
    }

    @Test
    void testPassExactMatch() {
        node.configure(new String[]{"/synth"});
        OSCMessage input = new OSCMessage("/synth", Collections.emptyList());
        List<MessageRequest> requests = new ArrayList<>();
        requests.add(new MessageRequest(input));
        node.process(requests);

        assertFalse(requests.isEmpty());
        OSCMessage result = requests.get(0).getMessage();
        assertNotNull(result);
        assertEquals(input, result);
    }

    @Test
    void testPassWithComplexPattern() {
        node.configure(new String[]{"/control/(volume|pan)"});

        OSCMessage input1 = new OSCMessage("/control/volume", Collections.singletonList(0.8f));
        List<MessageRequest> requests1 = new ArrayList<>();
        requests1.add(new MessageRequest(input1));
        node.process(requests1);
        assertFalse(requests1.isEmpty());
        OSCMessage result1 = requests1.get(0).getMessage();
        assertNotNull(result1);
        assertEquals(input1, result1);

        OSCMessage input2 = new OSCMessage("/control/pan", Collections.singletonList(0.5f));
        List<MessageRequest> requests2 = new ArrayList<>();
        requests2.add(new MessageRequest(input2));
        node.process(requests2);
        assertFalse(requests2.isEmpty());
        OSCMessage result2 = requests2.get(0).getMessage();
        assertNotNull(result2);
        assertEquals(input2, result2);

        OSCMessage input3 = new OSCMessage("/control/reverb", Collections.singletonList(0.3f));
        List<MessageRequest> requests3 = new ArrayList<>();
        requests3.add(new MessageRequest(input3));
        node.process(requests3);
        assertTrue(requests3.isEmpty());
    }

    @Test
    void testPassMessageWithMultipleArguments() {
        node.configure(new String[]{"/synth/.*"});
        OSCMessage input = new OSCMessage("/synth/note", Arrays.asList(60, 127, 1000));
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
        node.configure(new String[]{"/synth/.*"});
        OSCMessage input = new OSCMessage("/other/note", Arrays.asList(60, 127, 1000));
        List<MessageRequest> requests = new ArrayList<>();
        requests.add(new MessageRequest(input));
        node.process(requests);

        assertTrue(requests.isEmpty());
    }

    @Test
    void testPassAllMessages() {
        node.configure(new String[]{"/.*"});

        OSCMessage input1 = new OSCMessage("/anything", Collections.singletonList(1));
        List<MessageRequest> requests1 = new ArrayList<>();
        requests1.add(new MessageRequest(input1));
        node.process(requests1);
        assertFalse(requests1.isEmpty());

        OSCMessage input2 = new OSCMessage("/synth/osc1", Collections.singletonList(2));
        List<MessageRequest> requests2 = new ArrayList<>();
        requests2.add(new MessageRequest(input2));
        node.process(requests2);
        assertFalse(requests2.isEmpty());
    }

    @Test
    void testDropAllMessagesWithStrictPattern() {
        node.configure(new String[]{"/exact/match/only"});

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

        OSCMessage input3 = new OSCMessage("/exact/match/only", Collections.singletonList(3));
        List<MessageRequest> requests3 = new ArrayList<>();
        requests3.add(new MessageRequest(input3));
        node.process(requests3);
        assertFalse(requests3.isEmpty());
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
