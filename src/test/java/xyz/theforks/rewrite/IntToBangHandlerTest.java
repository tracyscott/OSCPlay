package xyz.theforks.rewrite;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.illposed.osc.OSCMessage;

class IntToBangHandlerTest {
    private IntToBangHandler handler;

    @BeforeEach
    void setUp() {
        handler = new IntToBangHandler();
        handler.configure(new String[]{"/trigger/.*"});
    }

    @Test
    void testConvertIntegerOneToArgumentless() {
        OSCMessage input = new OSCMessage("/trigger/button", Collections.singletonList(1));
        OSCMessage result = handler.process(input);
        
        assertNotNull(result);
        assertEquals("/trigger/button", result.getAddress());
        assertTrue(result.getArguments().isEmpty());
    }

    @Test
    void testDropNonOneIntegers() {
        OSCMessage input = new OSCMessage("/trigger/button", Collections.singletonList(0));
        OSCMessage result = handler.process(input);
        
        assertNull(result);
    }

    @Test
    void testDropNegativeIntegers() {
        OSCMessage input = new OSCMessage("/trigger/button", Collections.singletonList(-1));
        OSCMessage result = handler.process(input);
        
        assertNull(result);
    }

    @Test
    void testPassThroughNonMatchingAddress() {
        OSCMessage input = new OSCMessage("/other/button", Collections.singletonList(1));
        OSCMessage result = handler.process(input);
        
        assertNotNull(result);
        assertEquals(input, result);
    }

    @Test
    void testPassThroughNonIntegerArguments() {
        OSCMessage input = new OSCMessage("/trigger/button", Collections.singletonList(1.0f));
        OSCMessage result = handler.process(input);
        
        assertNotNull(result);
        assertEquals(input, result);
    }

    @Test
    void testPassThroughMultipleArguments() {
        OSCMessage input = new OSCMessage("/trigger/button", Arrays.asList(1, 2));
        OSCMessage result = handler.process(input);
        
        assertNotNull(result);
        assertEquals(input, result);
    }

    @Test
    void testPassThroughNoArguments() {
        OSCMessage input = new OSCMessage("/trigger/button", Collections.emptyList());
        OSCMessage result = handler.process(input);
        
        assertNotNull(result);
        assertEquals(input, result);
    }
}