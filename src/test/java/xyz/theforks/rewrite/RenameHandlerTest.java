package xyz.theforks.rewrite;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.regex.PatternSyntaxException;

import com.illposed.osc.OSCMessage;

class RenameHandlerTest {

    private RenameHandler handler;

    @BeforeEach
    void setUp() {
        handler = new RenameHandler();
    }

    @Test
    void testLabel() {
        assertEquals("Rename", handler.label());
    }

    @Test
    void testGetNumArgs() {
        assertEquals(3, handler.getNumArgs());
    }

    @Test
    void testGetArgNames() {
        String[] argNames = handler.getArgNames();
        assertEquals(3, argNames.length);
        assertEquals("Address Pattern", argNames[0]);
        assertEquals("Regex", argNames[1]);
        assertEquals("Replace With", argNames[2]);
    }

    @Test
    void testGetHelp() {
        assertNotNull(handler.getHelp());
        assertTrue(handler.getHelp().contains("regular expression"));
    }

    @Test
    void testConfigureWithValidArgs() {
        String[] args = {"/test/.*", "/test/(\\d+)", "/renamed/$1"};
        assertTrue(handler.configure(args));
        
        String[] retrievedArgs = handler.getArgs();
        assertArrayEquals(args, retrievedArgs);
        assertEquals("/test/.*", handler.getAddressPattern());
    }

    @Test
    void testConfigureWithInvalidArgCount() {
        String[] tooFew = {"/test"};
        String[] tooMany = {"/test", "regex", "replace", "extra"};
        
        assertThrows(IllegalArgumentException.class, () -> handler.configure(tooFew));
        assertThrows(IllegalArgumentException.class, () -> handler.configure(tooMany));
    }

    @Test
    void testConfigureWithInvalidRegex() {
        String[] argsWithInvalidRegex = {"/test/.*", "[invalid", "/renamed"};
        
        assertThrows(PatternSyntaxException.class, () -> handler.configure(argsWithInvalidRegex));
    }

    @Test
    void testProcessWithMatchingAddress() {
        handler.configure(new String[]{"/test/.*", "/test/(\\d+)", "/renamed/$1"});
        
        OSCMessage originalMessage = new OSCMessage("/test/123", Arrays.asList(1.0f, "hello"));
        OSCMessage processedMessage = handler.process(originalMessage);
        
        assertEquals("/renamed/123", processedMessage.getAddress());
        assertEquals(originalMessage.getArguments(), processedMessage.getArguments());
    }

    @Test
    void testProcessWithNonMatchingAddress() {
        handler.configure(new String[]{"/test/.*", "/test/(\\d+)", "/renamed/$1"});
        
        OSCMessage originalMessage = new OSCMessage("/other/123", Arrays.asList(1.0f, "hello"));
        OSCMessage processedMessage = handler.process(originalMessage);
        
        // Should return the same message unchanged
        assertEquals(originalMessage.getAddress(), processedMessage.getAddress());
        assertEquals(originalMessage.getArguments(), processedMessage.getArguments());
    }

    @Test
    void testProcessWithComplexRegexReplacement() {
        // Test a more complex regex that swaps parts of the address
        handler.configure(new String[]{"/lx/modulation/Mag[123]/mag", "/lx/modulation/Mag(\\d)/mag", "/lx/modulation/Angles/angle$1"});
        
        OSCMessage message1 = new OSCMessage("/lx/modulation/Mag1/mag", Arrays.asList(0.5f));
        OSCMessage processed1 = handler.process(message1);
        assertEquals("/lx/modulation/Angles/angle1", processed1.getAddress());
        
        OSCMessage message2 = new OSCMessage("/lx/modulation/Mag3/mag", Arrays.asList(0.8f));
        OSCMessage processed2 = handler.process(message2);
        assertEquals("/lx/modulation/Angles/angle3", processed2.getAddress());
    }

    @Test
    void testProcessWithNoGroups() {
        // Test regex replacement without capture groups
        handler.configure(new String[]{"/old.*", "/old", "/new"});
        
        OSCMessage originalMessage = new OSCMessage("/old/path", Arrays.asList(42));
        OSCMessage processedMessage = handler.process(originalMessage);
        
        assertEquals("/new/path", processedMessage.getAddress());
        assertEquals(originalMessage.getArguments(), processedMessage.getArguments());
    }

    @Test
    void testProcessPreservesArguments() {
        handler.configure(new String[]{"/test/.*", "/test/(.*)", "/renamed/$1"});
        
        Object[] originalArgs = {1.0f, 2, "string", true, 3.14};
        OSCMessage originalMessage = new OSCMessage("/test/path", Arrays.asList(originalArgs));
        OSCMessage processedMessage = handler.process(originalMessage);
        
        assertEquals("/renamed/path", processedMessage.getAddress());
        assertEquals(Arrays.asList(originalArgs), processedMessage.getArguments());
    }

    @Test
    void testMultipleReplacements() {
        // Test regex that can match multiple parts
        handler.configure(new String[]{".*", "/(\\w+)/(\\w+)/(\\w+)", "/$3-$2-$1"});
        
        OSCMessage originalMessage = new OSCMessage("/first/second/third", Arrays.asList(1.0f));
        OSCMessage processedMessage = handler.process(originalMessage);
        
        assertEquals("/third-second-first", processedMessage.getAddress());
    }
}