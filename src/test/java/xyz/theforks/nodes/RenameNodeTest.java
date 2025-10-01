package xyz.theforks.nodes;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.regex.PatternSyntaxException;

import com.illposed.osc.OSCMessage;

class RenameNodeTest {

    private RenameNode node;

    @BeforeEach
    void setUp() {
        node = new RenameNode();
    }

    @Test
    void testLabel() {
        assertEquals("Rename", node.label());
    }

    @Test
    void testGetNumArgs() {
        assertEquals(3, node.getNumArgs());
    }

    @Test
    void testGetArgNames() {
        String[] argNames = node.getArgNames();
        assertEquals(3, argNames.length);
        assertEquals("Address Pattern", argNames[0]);
        assertEquals("Regex", argNames[1]);
        assertEquals("Replace With", argNames[2]);
    }

    @Test
    void testGetHelp() {
        assertNotNull(node.getHelp());
        assertTrue(node.getHelp().contains("regular expression"));
    }

    @Test
    void testConfigureWithValidArgs() {
        String[] args = {"/test/.*", "/test/(\\d+)", "/renamed/$1"};
        assertTrue(node.configure(args));

        String[] retrievedArgs = node.getArgs();
        assertArrayEquals(args, retrievedArgs);
        assertEquals("/test/.*", node.getAddressPattern());
    }

    @Test
    void testConfigureWithInvalidArgCount() {
        String[] tooFew = {"/test"};
        String[] tooMany = {"/test", "regex", "replace", "extra"};

        assertThrows(IllegalArgumentException.class, () -> node.configure(tooFew));
        assertThrows(IllegalArgumentException.class, () -> node.configure(tooMany));
    }

    @Test
    void testConfigureWithInvalidRegex() {
        String[] argsWithInvalidRegex = {"/test/.*", "[invalid", "/renamed"};

        assertThrows(PatternSyntaxException.class, () -> node.configure(argsWithInvalidRegex));
    }

    @Test
    void testProcessWithMatchingAddress() {
        node.configure(new String[]{"/test/.*", "/test/(\\d+)", "/renamed/$1"});

        OSCMessage originalMessage = new OSCMessage("/test/123", Arrays.asList(1.0f, "hello"));
        OSCMessage processedMessage = node.process(originalMessage);

        assertEquals("/renamed/123", processedMessage.getAddress());
        assertEquals(originalMessage.getArguments(), processedMessage.getArguments());
    }

    @Test
    void testProcessWithNonMatchingAddress() {
        node.configure(new String[]{"/test/.*", "/test/(\\d+)", "/renamed/$1"});

        OSCMessage originalMessage = new OSCMessage("/other/123", Arrays.asList(1.0f, "hello"));
        OSCMessage processedMessage = node.process(originalMessage);

        // Should return the same message unchanged
        assertEquals(originalMessage.getAddress(), processedMessage.getAddress());
        assertEquals(originalMessage.getArguments(), processedMessage.getArguments());
    }

    @Test
    void testProcessWithComplexRegexReplacement() {
        // Test a more complex regex that swaps parts of the address
        node.configure(new String[]{"/lx/modulation/Mag[123]/mag", "/lx/modulation/Mag(\\d)/mag", "/lx/modulation/Angles/angle$1"});

        OSCMessage message1 = new OSCMessage("/lx/modulation/Mag1/mag", Arrays.asList(0.5f));
        OSCMessage processed1 = node.process(message1);
        assertEquals("/lx/modulation/Angles/angle1", processed1.getAddress());

        OSCMessage message2 = new OSCMessage("/lx/modulation/Mag3/mag", Arrays.asList(0.8f));
        OSCMessage processed2 = node.process(message2);
        assertEquals("/lx/modulation/Angles/angle3", processed2.getAddress());
    }

    @Test
    void testProcessWithNoGroups() {
        // Test regex replacement without capture groups
        node.configure(new String[]{"/old.*", "/old", "/new"});

        OSCMessage originalMessage = new OSCMessage("/old/path", Arrays.asList(42));
        OSCMessage processedMessage = node.process(originalMessage);

        assertEquals("/new/path", processedMessage.getAddress());
        assertEquals(originalMessage.getArguments(), processedMessage.getArguments());
    }

    @Test
    void testProcessPreservesArguments() {
        node.configure(new String[]{"/test/.*", "/test/(.*)", "/renamed/$1"});

        Object[] originalArgs = {1.0f, 2, "string", true, 3.14};
        OSCMessage originalMessage = new OSCMessage("/test/path", Arrays.asList(originalArgs));
        OSCMessage processedMessage = node.process(originalMessage);

        assertEquals("/renamed/path", processedMessage.getAddress());
        assertEquals(Arrays.asList(originalArgs), processedMessage.getArguments());
    }

    @Test
    void testMultipleReplacements() {
        // Test regex that can match multiple parts
        node.configure(new String[]{".*", "/(\\w+)/(\\w+)/(\\w+)", "/$3-$2-$1"});

        OSCMessage originalMessage = new OSCMessage("/first/second/third", Arrays.asList(1.0f));
        OSCMessage processedMessage = node.process(originalMessage);

        assertEquals("/third-second-first", processedMessage.getAddress());
    }
}
