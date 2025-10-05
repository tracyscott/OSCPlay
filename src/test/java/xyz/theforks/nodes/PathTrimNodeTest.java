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

class PathTrimNodeTest {
    private PathTrimNode node;

    @BeforeEach
    void setUp() {
        node = new PathTrimNode();
        node.configure(new String[]{"/synth/.*"});
    }

    @Test
    void testTrimLastPathComponent() {
        OSCMessage input = new OSCMessage("/synth/osc1/freq", Collections.singletonList(440.0f));
        List<MessageRequest> requests = new ArrayList<>();
        requests.add(new MessageRequest(input));
        node.process(requests);

        assertFalse(requests.isEmpty());
        OSCMessage result = requests.get(0).getMessage();
        assertNotNull(result);
        assertEquals("/synth/osc1", result.getAddress());
        assertEquals(2, result.getArguments().size());
        assertEquals("freq", result.getArguments().get(0));
        assertEquals(440.0f, result.getArguments().get(1));
    }

    @Test
    void testTrimWithNoExistingArguments() {
        OSCMessage input = new OSCMessage("/synth/osc1/volume", Collections.emptyList());
        List<MessageRequest> requests = new ArrayList<>();
        requests.add(new MessageRequest(input));
        node.process(requests);

        assertFalse(requests.isEmpty());
        OSCMessage result = requests.get(0).getMessage();
        assertNotNull(result);
        assertEquals("/synth/osc1", result.getAddress());
        assertEquals(1, result.getArguments().size());
        assertEquals("volume", result.getArguments().get(0));
    }

    @Test
    void testTrimWithMultipleExistingArguments() {
        OSCMessage input = new OSCMessage("/synth/osc1/note", Arrays.asList(60, 127));
        List<MessageRequest> requests = new ArrayList<>();
        requests.add(new MessageRequest(input));
        node.process(requests);

        assertFalse(requests.isEmpty());
        OSCMessage result = requests.get(0).getMessage();
        assertNotNull(result);
        assertEquals("/synth/osc1", result.getAddress());
        assertEquals(3, result.getArguments().size());
        assertEquals("note", result.getArguments().get(0));
        assertEquals(60, result.getArguments().get(1));
        assertEquals(127, result.getArguments().get(2));
    }

    @Test
    void testPassThroughNonMatchingAddress() {
        OSCMessage input = new OSCMessage("/other/osc1/freq", Collections.singletonList(440.0f));
        List<MessageRequest> requests = new ArrayList<>();
        requests.add(new MessageRequest(input));
        node.process(requests);

        assertFalse(requests.isEmpty());
        OSCMessage result = requests.get(0).getMessage();
        assertNotNull(result);
        assertEquals(input, result);
    }

    @Test
    void testPassThroughRootPath() {
        node.configure(new String[]{"/"});
        OSCMessage input = new OSCMessage("/", Collections.singletonList(1));
        List<MessageRequest> requests = new ArrayList<>();
        requests.add(new MessageRequest(input));
        node.process(requests);

        assertFalse(requests.isEmpty());
        OSCMessage result = requests.get(0).getMessage();
        assertNotNull(result);
        assertEquals(input, result);
    }

    @Test
    void testPassThroughSingleLevelPath() {
        node.configure(new String[]{"/test"});
        OSCMessage input = new OSCMessage("/test", Collections.singletonList(1));
        List<MessageRequest> requests = new ArrayList<>();
        requests.add(new MessageRequest(input));
        node.process(requests);

        assertFalse(requests.isEmpty());
        OSCMessage result = requests.get(0).getMessage();
        assertNotNull(result);
        assertEquals(input, result);
    }

    @Test
    void testTrimDeepNestedPath() {
        node.configure(new String[]{"/synth/.*"});
        OSCMessage input = new OSCMessage("/synth/osc1/filter/cutoff/value", Collections.singletonList(1000.0f));
        List<MessageRequest> requests = new ArrayList<>();
        requests.add(new MessageRequest(input));
        node.process(requests);

        assertFalse(requests.isEmpty());
        OSCMessage result = requests.get(0).getMessage();
        assertNotNull(result);
        assertEquals("/synth/osc1/filter/cutoff", result.getAddress());
        assertEquals(2, result.getArguments().size());
        assertEquals("value", result.getArguments().get(0));
        assertEquals(1000.0f, result.getArguments().get(1));
    }
}
