package xyz.theforks.nodes;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.illposed.osc.OSCMessage;

class ScriptNodeTest {

    private ScriptNode node;
    private Path tempScriptDir;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        node = new ScriptNode();
        // Reset project manager to null for each test
        ScriptNode.setProjectManager(null);

        // Create a temporary script directory
        tempScriptDir = tempDir.resolve("scripts");
        Files.createDirectories(tempScriptDir);
    }

    @AfterEach
    void tearDown() {
        // Clean up any temporary files if needed
    }

    @Test
    void testConfigureWithValidArgs() {
        Path scriptPath = tempScriptDir.resolve("test.js");
        assertTrue(node.configure(new String[]{"/test/*", scriptPath.toString()}));
        assertEquals("/test/*", node.getAddressPattern());
        assertArrayEquals(new String[]{"/test/*", scriptPath.toString()}, node.getArgs());
    }

    @Test
    void testConfigureWithWrongArgCount() {
        assertThrows(IllegalArgumentException.class, () -> {
            node.configure(new String[]{"/test/*"});
        });

        assertThrows(IllegalArgumentException.class, () -> {
            node.configure(new String[]{"/test/*", "script.js", "extra"});
        });
    }

    @Test
    void testGetters() {
        Path scriptPath = tempScriptDir.resolve("myscript.js");
        node.configure(new String[]{"/script/*", scriptPath.toString()});

        assertEquals("Script", node.label());
        assertEquals(2, node.getNumArgs());
        assertArrayEquals(new String[]{"Address Pattern", "Script Path"}, node.getArgNames());
        assertNotNull(node.getHelp());
        assertEquals("/script/*", node.getAddressPattern());
    }

    @Test
    void testProcessBeforeScriptLoad() {
        Path nonexistentPath = tempScriptDir.resolve("nonexistent.js");
        node.configure(new String[]{"/test/*", nonexistentPath.toString()});
        OSCMessage input = new OSCMessage("/test/message", Collections.singletonList("test"));

        // Should return original message when script not loaded
        OSCMessage result = node.process(input);
        assertEquals(input, result);
    }

    @Test
    void testLoadPassThroughScript() throws IOException {
        // Create a simple pass-through script
        String scriptContent = """
            function process(message) {
                return message;
            }
            """;
        Path scriptPath = tempScriptDir.resolve("passthrough.js");
        Files.writeString(scriptPath, scriptContent);

        node.configure(new String[]{"/test/*", scriptPath.toString()});
        assertTrue(node.configure(new String[]{"/test/*", scriptPath.toString()}));

        OSCMessage input = new OSCMessage("/test/message", Arrays.asList(1, 2.5f, "test"));
        OSCMessage result = node.process(input);

        assertNotNull(result);
        assertEquals(input.getAddress(), result.getAddress());
        assertEquals(input.getArguments(), result.getArguments());
    }

    @Test
    void testLoadTransformScript() throws IOException {
        // Create a script that transforms string arguments to uppercase
        String scriptContent = """
            function process(message) {
                var args = message.getArguments();
                var newArgs = [];
                for (var i = 0; i < args.size(); i++) {
                    var arg = args.get(i);
                    if (typeof arg === 'string') {
                        newArgs.push(arg.toUpperCase());
                    } else {
                        newArgs.push(arg);
                    }
                }
                return createMessage(message.getAddress(), newArgs);
            }
            """;
        Path scriptPath = tempScriptDir.resolve("transform.js");
        Files.writeString(scriptPath, scriptContent);

        node.configure(new String[]{"/test/*", scriptPath.toString()});

        OSCMessage input = new OSCMessage("/test/message", Arrays.asList(1, "hello", 2.5f));
        OSCMessage result = node.process(input);

        assertNotNull(result);
        assertEquals("/test/message", result.getAddress());
        assertEquals(3, result.getArguments().size());
        assertEquals(1, result.getArguments().get(0));
        assertEquals("HELLO", result.getArguments().get(1));
        assertEquals(2.5, result.getArguments().get(2));
    }

    @Test
    void testLoadDropScript() throws IOException {
        // Create a script that drops messages
        String scriptContent = """
            function process(message) {
                return null; // Drop the message
            }
            """;
        Path scriptPath = tempScriptDir.resolve("drop.js");
        Files.writeString(scriptPath, scriptContent);

        node.configure(new String[]{"/test/*", scriptPath.toString()});

        OSCMessage input = new OSCMessage("/test/message", Collections.singletonList("test"));
        OSCMessage result = node.process(input);

        assertNull(result);
    }

    @Test
    void testLoadScriptWithBooleanFalse() throws IOException {
        // Create a script that returns false to drop messages
        String scriptContent = """
            function process(message) {
                return false; // Drop the message
            }
            """;
        Path scriptPath = tempScriptDir.resolve("drop_false.js");
        Files.writeString(scriptPath, scriptContent);

        node.configure(new String[]{"/test/*", scriptPath.toString()});

        OSCMessage input = new OSCMessage("/test/message", Collections.singletonList("test"));
        OSCMessage result = node.process(input);

        assertNull(result);
    }

    @Test
    void testScriptErrorHandling() throws IOException {
        // Create a script with syntax error
        String scriptContent = """
            function process(message) {
                throw new Error("Test error");
            }
            """;
        Path scriptPath = tempScriptDir.resolve("error.js");
        Files.writeString(scriptPath, scriptContent);

        node.configure(new String[]{"/test/*", scriptPath.toString()});

        OSCMessage input = new OSCMessage("/test/message", Collections.singletonList("test"));
        OSCMessage result = node.process(input);

        // Should return original message on error
        assertNotNull(result);
        assertEquals(input, result);
    }

    @Test
    void testScriptWithoutProcessFunction() throws IOException {
        // Create a script without process function
        String scriptContent = """
            function otherFunction() {
                return "something";
            }
            """;
        Path scriptPath = tempScriptDir.resolve("no_process.js");
        Files.writeString(scriptPath, scriptContent);

        node.configure(new String[]{"/test/*", scriptPath.toString()});

        OSCMessage input = new OSCMessage("/test/message", Collections.singletonList("test"));
        OSCMessage result = node.process(input);

        // Should return original message when process function not found
        assertNotNull(result);
        assertEquals(input, result);
    }

    @Test
    void testBoilerplateScriptCreation() throws IOException {
        // Configure with non-existent script path
        String scriptName = "newscript.js";
        Path scriptPath = tempScriptDir.resolve(scriptName);

        node.configure(new String[]{"/test/*", scriptPath.toString()});

        // Script should be created automatically
        assertTrue(Files.exists(scriptPath));

        // Check that it contains the boilerplate content
        String content = Files.readString(scriptPath);
        assertTrue(content.contains("function process(message)"));
        assertTrue(content.contains("return message;"));
        assertTrue(content.contains("OSCPlay Script Node"));
    }

    @Test
    void testScriptHotReload() throws IOException, InterruptedException {
        // Create initial script
        String initialScript = """
            function process(message) {
                return createMessage("/initial", [42]);
            }
            """;
        Path scriptPath = tempScriptDir.resolve("reload.js");
        Files.writeString(scriptPath, initialScript);

        node.configure(new String[]{"/test/*", scriptPath.toString()});

        // Process a message with initial script
        OSCMessage input = new OSCMessage("/test/input", Collections.singletonList(1));
        OSCMessage result1 = node.process(input);
        assertEquals("/initial", result1.getAddress());

        // Wait a bit and modify the script
        Thread.sleep(100);
        String updatedScript = """
            function process(message) {
                return createMessage("/updated", [99]);
            }
            """;
        Files.writeString(scriptPath, updatedScript);

        // Process another message - should reload and use updated script
        OSCMessage result2 = node.process(input);
        assertEquals("/updated", result2.getAddress());
    }

    @Test
    void testScriptCreationWhenNonExistent() {
        Path nonExistentPath = tempScriptDir.resolve("does_not_exist.js");

        // Should return true and create boilerplate script when script doesn't exist
        boolean result = node.configure(new String[]{"/test/*", nonExistentPath.toString()});
        assertTrue(result);
        assertTrue(Files.exists(nonExistentPath));
    }
}