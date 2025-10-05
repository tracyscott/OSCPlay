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

class IntToBangNodeTest {
    private IntToBangNode node;

    @BeforeEach
    void setUp() {
        node = new IntToBangNode();
        node.configure(new String[]{"/trigger/.*"});
    }

    @Test
    void testConvertIntegerOneToArgumentless() {
        OSCMessage input = new OSCMessage("/trigger/button", Collections.singletonList(1));
        List<MessageRequest> requests = new ArrayList<>();
        requests.add(new MessageRequest(input));
        node.process(requests);

        assertFalse(requests.isEmpty());
        OSCMessage result = requests.get(0).getMessage();
        assertNotNull(result);
        assertEquals("/trigger/button", result.getAddress());
        assertTrue(result.getArguments().isEmpty());
    }

    @Test
    void testDropNonOneIntegers() {
        OSCMessage input = new OSCMessage("/trigger/button", Collections.singletonList(0));
        List<MessageRequest> requests = new ArrayList<>();
        requests.add(new MessageRequest(input));
        node.process(requests);

        assertTrue(requests.isEmpty());
    }

    @Test
    void testDropNegativeIntegers() {
        OSCMessage input = new OSCMessage("/trigger/button", Collections.singletonList(-1));
        List<MessageRequest> requests = new ArrayList<>();
        requests.add(new MessageRequest(input));
        node.process(requests);

        assertTrue(requests.isEmpty());
    }

    @Test
    void testPassThroughNonMatchingAddress() {
        OSCMessage input = new OSCMessage("/other/button", Collections.singletonList(1));
        List<MessageRequest> requests = new ArrayList<>();
        requests.add(new MessageRequest(input));
        node.process(requests);

        assertFalse(requests.isEmpty());
        OSCMessage result = requests.get(0).getMessage();
        assertNotNull(result);
        assertEquals(input, result);
    }

    @Test
    void testPassThroughNonIntegerArguments() {
        OSCMessage input = new OSCMessage("/trigger/button", Collections.singletonList(1.0f));
        List<MessageRequest> requests = new ArrayList<>();
        requests.add(new MessageRequest(input));
        node.process(requests);

        assertFalse(requests.isEmpty());
        OSCMessage result = requests.get(0).getMessage();
        assertNotNull(result);
        assertEquals(input, result);
    }

    @Test
    void testPassThroughMultipleArguments() {
        OSCMessage input = new OSCMessage("/trigger/button", Arrays.asList(1, 2));
        List<MessageRequest> requests = new ArrayList<>();
        requests.add(new MessageRequest(input));
        node.process(requests);

        assertFalse(requests.isEmpty());
        OSCMessage result = requests.get(0).getMessage();
        assertNotNull(result);
        assertEquals(input, result);
    }

    @Test
    void testPassThroughNoArguments() {
        OSCMessage input = new OSCMessage("/trigger/button", Collections.emptyList());
        List<MessageRequest> requests = new ArrayList<>();
        requests.add(new MessageRequest(input));
        node.process(requests);

        assertFalse(requests.isEmpty());
        OSCMessage result = requests.get(0).getMessage();
        assertNotNull(result);
        assertEquals(input, result);
    }
}
