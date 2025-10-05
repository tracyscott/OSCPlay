package xyz.theforks.nodes;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import com.illposed.osc.OSCMessage;
import xyz.theforks.model.MessageRequest;

class MovingAvgNodeTest {

    private MovingAvgNode node;

    @BeforeEach
    void setUp() {
        node = new MovingAvgNode();
    }

    @Test
    void testLabel() {
        assertEquals("Moving Average", node.label());
    }

    @Test
    void testGetNumArgs() {
        assertEquals(2, node.getNumArgs());
    }

    @Test
    void testGetArgNames() {
        String[] argNames = node.getArgNames();
        assertEquals(2, argNames.length);
        assertEquals("Address Pattern", argNames[0]);
        assertEquals("Window Size", argNames[1]);
    }

    @Test
    void testGetHelp() {
        assertNotNull(node.getHelp());
        assertTrue(node.getHelp().toLowerCase().contains("moving average"));
    }

    @Test
    void testConfigureWithValidArgs() {
        String[] args = {"/test/.*", "5"};
        assertTrue(node.configure(args));
        
        String[] retrievedArgs = node.getArgs();
        assertArrayEquals(args, retrievedArgs);
        assertEquals("/test/.*", node.getAddressPattern());
    }

    @Test
    void testConfigureWithInvalidArgCount() {
        String[] tooFew = {"/test"};
        String[] tooMany = {"/test", "5", "extra"};
        
        assertThrows(IllegalArgumentException.class, () -> node.configure(tooFew));
        assertThrows(IllegalArgumentException.class, () -> node.configure(tooMany));
    }

    @Test
    void testConfigureWithInvalidWindowSize() {
        String[] invalidNumber = {"/test", "not-a-number"};
        String[] negativeNumber = {"/test", "-1"};
        String[] zeroNumber = {"/test", "0"};
        
        assertThrows(IllegalArgumentException.class, () -> node.configure(invalidNumber));
        assertThrows(IllegalArgumentException.class, () -> node.configure(negativeNumber));
        assertThrows(IllegalArgumentException.class, () -> node.configure(zeroNumber));
    }

    @Test
    void testProcessSingleValue() {
        node.configure(new String[]{"/test", "3"});

        OSCMessage message = new OSCMessage("/test", List.of(5.0f));
        List<MessageRequest> requests = new ArrayList<>();
        requests.add(new MessageRequest(message));
        node.process(requests);

        assertFalse(requests.isEmpty());
        OSCMessage processed = requests.get(0).getMessage();
        assertEquals("/test", processed.getAddress());
        assertEquals(1, processed.getArguments().size());
        assertEquals(5.0f, (Float) processed.getArguments().get(0), 0.001);
    }

    @Test
    void testProcessMovingAverageCalculation() {
        node.configure(new String[]{"/sensor", "3"});

        // First value: [1.0] -> avg = 1.0
        OSCMessage msg1 = new OSCMessage("/sensor", List.of(1.0f));
        List<MessageRequest> requests1 = new ArrayList<>();
        requests1.add(new MessageRequest(msg1));
        node.process(requests1);
        assertFalse(requests1.isEmpty());
        OSCMessage result1 = requests1.get(0).getMessage();
        assertEquals(1.0f, (Float) result1.getArguments().get(0), 0.001);

        // Second value: [1.0, 2.0] -> avg = 1.5
        OSCMessage msg2 = new OSCMessage("/sensor", List.of(2.0f));
        List<MessageRequest> requests2 = new ArrayList<>();
        requests2.add(new MessageRequest(msg2));
        node.process(requests2);
        assertFalse(requests2.isEmpty());
        OSCMessage result2 = requests2.get(0).getMessage();
        assertEquals(1.5f, (Float) result2.getArguments().get(0), 0.001);

        // Third value: [1.0, 2.0, 3.0] -> avg = 2.0
        OSCMessage msg3 = new OSCMessage("/sensor", List.of(3.0f));
        List<MessageRequest> requests3 = new ArrayList<>();
        requests3.add(new MessageRequest(msg3));
        node.process(requests3);
        assertFalse(requests3.isEmpty());
        OSCMessage result3 = requests3.get(0).getMessage();
        assertEquals(2.0f, (Float) result3.getArguments().get(0), 0.001);

        // Fourth value: [2.0, 3.0, 4.0] -> avg = 3.0 (window of 3, oldest dropped)
        OSCMessage msg4 = new OSCMessage("/sensor", List.of(4.0f));
        List<MessageRequest> requests4 = new ArrayList<>();
        requests4.add(new MessageRequest(msg4));
        node.process(requests4);
        assertFalse(requests4.isEmpty());
        OSCMessage result4 = requests4.get(0).getMessage();
        assertEquals(3.0f, (Float) result4.getArguments().get(0), 0.001);
    }

    @Test
    void testProcessDifferentAddressesSeparateWindows() {
        node.configure(new String[]{".*", "2"});

        // Process messages for different addresses
        OSCMessage msg1a = new OSCMessage("/sensor1", List.of(1.0f));
        OSCMessage msg1b = new OSCMessage("/sensor2", List.of(10.0f));
        OSCMessage msg2a = new OSCMessage("/sensor1", List.of(3.0f));
        OSCMessage msg2b = new OSCMessage("/sensor2", List.of(20.0f));

        List<MessageRequest> requests1a = new ArrayList<>();
        requests1a.add(new MessageRequest(msg1a));
        node.process(requests1a);
        assertFalse(requests1a.isEmpty());
        OSCMessage result1a = requests1a.get(0).getMessage();

        List<MessageRequest> requests1b = new ArrayList<>();
        requests1b.add(new MessageRequest(msg1b));
        node.process(requests1b);
        assertFalse(requests1b.isEmpty());
        OSCMessage result1b = requests1b.get(0).getMessage();

        List<MessageRequest> requests2a = new ArrayList<>();
        requests2a.add(new MessageRequest(msg2a));
        node.process(requests2a);
        assertFalse(requests2a.isEmpty());
        OSCMessage result2a = requests2a.get(0).getMessage();

        List<MessageRequest> requests2b = new ArrayList<>();
        requests2b.add(new MessageRequest(msg2b));
        node.process(requests2b);
        assertFalse(requests2b.isEmpty());
        OSCMessage result2b = requests2b.get(0).getMessage();

        // Each address should maintain its own window
        assertEquals(1.0f, (Float) result1a.getArguments().get(0), 0.001); // [1.0]
        assertEquals(10.0f, (Float) result1b.getArguments().get(0), 0.001); // [10.0]
        assertEquals(2.0f, (Float) result2a.getArguments().get(0), 0.001); // [1.0, 3.0]
        assertEquals(15.0f, (Float) result2b.getArguments().get(0), 0.001); // [10.0, 20.0]
    }

    @Test
    void testProcessNonFloatArguments() {
        node.configure(new String[]{"/test", "3"});

        // Message with non-float argument should pass through unchanged
        OSCMessage intMessage = new OSCMessage("/test", List.of(42));
        List<MessageRequest> requests1 = new ArrayList<>();
        requests1.add(new MessageRequest(intMessage));
        node.process(requests1);
        assertFalse(requests1.isEmpty());
        assertEquals(intMessage, requests1.get(0).getMessage());

        OSCMessage stringMessage = new OSCMessage("/test", List.of("hello"));
        List<MessageRequest> requests2 = new ArrayList<>();
        requests2.add(new MessageRequest(stringMessage));
        node.process(requests2);
        assertFalse(requests2.isEmpty());
        assertEquals(stringMessage, requests2.get(0).getMessage());

        OSCMessage multipleArgs = new OSCMessage("/test", List.of(1.0f, 2.0f));
        List<MessageRequest> requests3 = new ArrayList<>();
        requests3.add(new MessageRequest(multipleArgs));
        node.process(requests3);
        assertFalse(requests3.isEmpty());
        assertEquals(multipleArgs, requests3.get(0).getMessage());
    }

    @Test
    void testProcessEmptyArguments() {
        node.configure(new String[]{"/test", "3"});

        OSCMessage emptyMessage = new OSCMessage("/test", List.of());
        List<MessageRequest> requests = new ArrayList<>();
        requests.add(new MessageRequest(emptyMessage));
        node.process(requests);
        assertFalse(requests.isEmpty());
        assertEquals(emptyMessage, requests.get(0).getMessage());
    }

    @Test
    void testWindowSizeOne() {
        node.configure(new String[]{"/test", "1"});

        // With window size 1, output should always equal input
        OSCMessage msg1 = new OSCMessage("/test", List.of(5.0f));
        List<MessageRequest> requests1 = new ArrayList<>();
        requests1.add(new MessageRequest(msg1));
        node.process(requests1);
        assertFalse(requests1.isEmpty());
        OSCMessage result1 = requests1.get(0).getMessage();

        OSCMessage msg2 = new OSCMessage("/test", List.of(10.0f));
        List<MessageRequest> requests2 = new ArrayList<>();
        requests2.add(new MessageRequest(msg2));
        node.process(requests2);
        assertFalse(requests2.isEmpty());
        OSCMessage result2 = requests2.get(0).getMessage();

        assertEquals(5.0f, (Float) result1.getArguments().get(0), 0.001);
        assertEquals(10.0f, (Float) result2.getArguments().get(0), 0.001);
    }

    @Test
    void testLargeWindowSize() {
        node.configure(new String[]{"/test", "100"});

        // Test with window much larger than number of values
        float sum = 0;
        for (int i = 1; i <= 5; i++) {
            OSCMessage msg = new OSCMessage("/test", List.of((float) i));
            List<MessageRequest> requests = new ArrayList<>();
            requests.add(new MessageRequest(msg));
            node.process(requests);
            assertFalse(requests.isEmpty());
            OSCMessage result = requests.get(0).getMessage();
            sum += i;
            float expectedAvg = sum / i;
            assertEquals(expectedAvg, (Float) result.getArguments().get(0), 0.001);
        }
    }
}
