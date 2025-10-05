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

class SplitterNodeTest {
    private SplitterNode node;

    @BeforeEach
    void setUp() {
        node = new SplitterNode();
    }

    @Test
    void testSplitThreeArguments() {
        node.configure(new String[]{});
        OSCMessage input = new OSCMessage("/foo", Arrays.asList(1, 2, 3));
        List<MessageRequest> requests = new ArrayList<>();
        requests.add(new MessageRequest(input));
        node.process(requests);

        assertEquals(3, requests.size());

        OSCMessage msg1 = requests.get(0).getMessage();
        assertEquals("/foo1", msg1.getAddress());
        assertEquals(1, msg1.getArguments().size());
        assertEquals(1, msg1.getArguments().get(0));

        OSCMessage msg2 = requests.get(1).getMessage();
        assertEquals("/foo2", msg2.getAddress());
        assertEquals(1, msg2.getArguments().size());
        assertEquals(2, msg2.getArguments().get(0));

        OSCMessage msg3 = requests.get(2).getMessage();
        assertEquals("/foo3", msg3.getAddress());
        assertEquals(1, msg3.getArguments().size());
        assertEquals(3, msg3.getArguments().get(0));
    }

    @Test
    void testSplitMixedTypes() {
        node.configure(new String[]{});
        OSCMessage input = new OSCMessage("/test", Arrays.asList(42, 3.14f, "hello"));
        List<MessageRequest> requests = new ArrayList<>();
        requests.add(new MessageRequest(input));
        node.process(requests);

        assertEquals(3, requests.size());

        OSCMessage msg1 = requests.get(0).getMessage();
        assertEquals("/test1", msg1.getAddress());
        assertEquals(42, msg1.getArguments().get(0));

        OSCMessage msg2 = requests.get(1).getMessage();
        assertEquals("/test2", msg2.getAddress());
        assertEquals(3.14f, msg2.getArguments().get(0));

        OSCMessage msg3 = requests.get(2).getMessage();
        assertEquals("/test3", msg3.getAddress());
        assertEquals("hello", msg3.getArguments().get(0));
    }

    @Test
    void testPassThroughSingleArgument() {
        node.configure(new String[]{});
        OSCMessage input = new OSCMessage("/single", Collections.singletonList(440.0f));
        List<MessageRequest> requests = new ArrayList<>();
        requests.add(new MessageRequest(input));
        node.process(requests);

        assertEquals(1, requests.size());
        OSCMessage result = requests.get(0).getMessage();
        assertEquals("/single", result.getAddress());
        assertEquals(1, result.getArguments().size());
        assertEquals(440.0f, result.getArguments().get(0));
    }

    @Test
    void testPassThroughNoArguments() {
        node.configure(new String[]{});
        OSCMessage input = new OSCMessage("/empty", Collections.emptyList());
        List<MessageRequest> requests = new ArrayList<>();
        requests.add(new MessageRequest(input));
        node.process(requests);

        assertEquals(1, requests.size());
        OSCMessage result = requests.get(0).getMessage();
        assertEquals("/empty", result.getAddress());
        assertEquals(0, result.getArguments().size());
    }

    @Test
    void testSplitTwoArguments() {
        node.configure(new String[]{});
        OSCMessage input = new OSCMessage("/pair", Arrays.asList("left", "right"));
        List<MessageRequest> requests = new ArrayList<>();
        requests.add(new MessageRequest(input));
        node.process(requests);

        assertEquals(2, requests.size());

        OSCMessage msg1 = requests.get(0).getMessage();
        assertEquals("/pair1", msg1.getAddress());
        assertEquals("left", msg1.getArguments().get(0));

        OSCMessage msg2 = requests.get(1).getMessage();
        assertEquals("/pair2", msg2.getAddress());
        assertEquals("right", msg2.getArguments().get(0));
    }

    @Test
    void testSplitManyArguments() {
        node.configure(new String[]{});
        List<Object> args = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        OSCMessage input = new OSCMessage("/many", args);
        List<MessageRequest> requests = new ArrayList<>();
        requests.add(new MessageRequest(input));
        node.process(requests);

        assertEquals(10, requests.size());

        for (int i = 0; i < 10; i++) {
            OSCMessage msg = requests.get(i).getMessage();
            assertEquals("/many" + (i + 1), msg.getAddress());
            assertEquals(1, msg.getArguments().size());
            assertEquals(i + 1, msg.getArguments().get(0));
        }
    }

    @Test
    void testAddressWithTrailingSlash() {
        node.configure(new String[]{});
        OSCMessage input = new OSCMessage("/path/", Arrays.asList(1, 2));
        List<MessageRequest> requests = new ArrayList<>();
        requests.add(new MessageRequest(input));
        node.process(requests);

        assertEquals(2, requests.size());
        assertEquals("/path/1", requests.get(0).getMessage().getAddress());
        assertEquals("/path/2", requests.get(1).getMessage().getAddress());
    }

    @Test
    void testComplexAddress() {
        node.configure(new String[]{});
        OSCMessage input = new OSCMessage("/synth/osc1/freq", Arrays.asList(440.0f, 880.0f));
        List<MessageRequest> requests = new ArrayList<>();
        requests.add(new MessageRequest(input));
        node.process(requests);

        assertEquals(2, requests.size());
        assertEquals("/synth/osc1/freq1", requests.get(0).getMessage().getAddress());
        assertEquals("/synth/osc1/freq2", requests.get(1).getMessage().getAddress());
    }

    @Test
    void testConfigureThrowsExceptionWithWrongArgCount() {
        assertThrows(IllegalArgumentException.class, () -> {
            node.configure(new String[]{"unexpected"});
        });
    }

    @Test
    void testGetters() {
        node.configure(new String[]{});

        assertEquals("Splitter", node.label());
        assertEquals(0, node.getNumArgs());
        assertEquals(".*", node.getAddressPattern());
        assertArrayEquals(new String[]{}, node.getArgs());
        assertArrayEquals(new String[]{}, node.getArgNames());
        assertNotNull(node.getHelp());
    }
}
