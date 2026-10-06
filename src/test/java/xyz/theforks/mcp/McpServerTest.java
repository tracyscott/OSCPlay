package xyz.theforks.mcp;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import xyz.theforks.nodes.DropNode;
import xyz.theforks.nodes.OSCNode;
import xyz.theforks.nodes.RenameNode;
import xyz.theforks.service.OSCProxyService;
import xyz.theforks.util.DataDirectory;

class McpServerTest {

    @TempDir
    Path tempDir;

    private final ObjectMapper mapper = new ObjectMapper();
    private OSCProxyService proxyService;
    private McpServer server;
    private final AtomicInteger changeCount = new AtomicInteger();
    private final AtomicInteger recordingChangeCount = new AtomicInteger();
    private int nextId = 1;

    @BeforeEach
    void setUp() {
        DataDirectory.setTestOverrideDir(tempDir);
        proxyService = new OSCProxyService();
        McpHost host = new McpHost() {
            @Override
            public void onPipelineChanged() {
                changeCount.incrementAndGet();
            }

            @Override
            public void onRecordingChanged() {
                recordingChangeCount.incrementAndGet();
            }

            @Override
            public void saveProject() {
            }
        };
        // Run tools inline instead of on the FX thread
        server = new McpServer(new PipelineTools(proxyService, null, host), Runnable::run, "test");
    }

    @AfterEach
    void tearDown() {
        server.stop();
        proxyService.stopProxy();
        DataDirectory.setTestOverrideDir(null);
    }

    private JsonNode request(String method, Object params) {
        ObjectNode req = mapper.createObjectNode();
        req.put("jsonrpc", "2.0");
        req.put("id", nextId++);
        req.put("method", method);
        if (params != null) {
            req.set("params", mapper.valueToTree(params));
        }
        return server.handle(req);
    }

    /** Call a tool and return its result object (content + isError). */
    private JsonNode call(String tool, Object arguments) {
        JsonNode response = request("tools/call", java.util.Map.of("name", tool, "arguments", arguments));
        assertNull(response.get("error"), () -> "Unexpected JSON-RPC error: " + response);
        return response.get("result");
    }

    /** Call a tool that should succeed and parse its JSON text content. */
    private JsonNode callOk(String tool, Object arguments) throws Exception {
        JsonNode result = call(tool, arguments);
        String text = result.at("/content/0/text").asText();
        assertFalse(result.get("isError").asBoolean(), () -> tool + " failed: " + text);
        return mapper.readTree(text);
    }

    private String callError(String tool, Object arguments) {
        JsonNode result = call(tool, arguments);
        assertTrue(result.get("isError").asBoolean(), () -> tool + " should have failed: " + result);
        return result.at("/content/0/text").asText();
    }

    private static java.util.Map<String, Object> node(String type, String... args) {
        return java.util.Map.of("type", type, "args", List.of(args));
    }

    @Test
    void testInitializeNegotiatesProtocolVersion() {
        JsonNode result = request("initialize", java.util.Map.of("protocolVersion", "2025-03-26")).get("result");
        assertEquals("2025-03-26", result.get("protocolVersion").asText());
        assertEquals("oscplay", result.at("/serverInfo/name").asText());
        assertTrue(result.at("/capabilities/tools").isObject());

        JsonNode unknown = request("initialize", java.util.Map.of("protocolVersion", "1999-01-01")).get("result");
        assertEquals(McpServer.LATEST_PROTOCOL_VERSION, unknown.get("protocolVersion").asText());
    }

    @Test
    void testNotificationsGetNoResponse() {
        ObjectNode notification = mapper.createObjectNode();
        notification.put("jsonrpc", "2.0");
        notification.put("method", "notifications/initialized");
        assertNull(server.handle(notification));
    }

    @Test
    void testUnknownMethodAndTool() {
        assertEquals(-32601, request("bogus", null).at("/error/code").asInt());
        JsonNode response = request("tools/call", java.util.Map.of("name", "nope"));
        assertEquals(-32602, response.at("/error/code").asInt());
    }

    @Test
    void testToolsListHasSchemas() {
        JsonNode tools = request("tools/list", null).at("/result/tools");
        assertTrue(tools.size() > 5);
        for (JsonNode tool : tools) {
            assertEquals("object", tool.at("/inputSchema/type").asText(), tool.get("name").asText());
        }
    }

    @Test
    void testSetChainAppliesToLiveOutput() throws Exception {
        JsonNode output = callOk("set_chain", java.util.Map.of(
                "output_id", "default",
                "nodes", List.of(node("Drop", "/debug/.*"), node("RenameNode", "/a/.*", "/a/", "/b/"))));

        assertEquals(2, output.get("nodes").size());
        List<OSCNode> live = proxyService.getNodeChain("default").getNodes();
        assertInstanceOf(DropNode.class, live.get(0));
        assertInstanceOf(RenameNode.class, live.get(1));
        assertEquals(1, changeCount.get());
    }

    @Test
    void testInvalidChainLeavesExistingChainUntouched() throws Exception {
        callOk("set_chain", java.util.Map.of("output_id", "default", "nodes", List.of(node("Drop", "/x"))));

        String error = callError("set_chain", java.util.Map.of(
                "output_id", "default",
                "nodes", List.of(node("Drop", "/y"), node("Rename", "only-one-arg"))));
        assertTrue(error.contains("nodes[1]"), error);
        assertTrue(error.contains("Regex"), "error should list the expected args: " + error);

        List<OSCNode> live = proxyService.getNodeChain("default").getNodes();
        assertEquals(1, live.size());
        assertEquals("/x", live.get(0).getArgs()[0]);
        assertEquals(1, changeCount.get(), "failed call should not notify the host");
    }

    @Test
    void testUnknownNodeType() {
        String error = callError("add_node", java.util.Map.of("output_id", "default", "type", "Frobnicate"));
        assertTrue(error.contains("list_node_types"), error);
    }

    @Test
    void testAddMoveUpdateRemoveNode() throws Exception {
        callOk("add_node", java.util.Map.of("output_id", "default", "type", "Drop", "args", List.of("/one")));
        callOk("add_node", java.util.Map.of("output_id", "default", "type", "Pass", "args", List.of("/two")));
        callOk("add_node", java.util.Map.of("output_id", "default", "type", "Drop", "args", List.of("/zero"), "index", 0));

        JsonNode nodes = callOk("move_node", java.util.Map.of("output_id", "default", "from", 0, "to", 2)).get("nodes");
        assertEquals("/one", nodes.at("/0/args/0").asText());
        assertEquals("/two", nodes.at("/1/args/0").asText());
        assertEquals("/zero", nodes.at("/2/args/0").asText());

        nodes = callOk("update_node", java.util.Map.of("output_id", "default", "index", 1, "args", List.of("/2"))).get("nodes");
        assertEquals("PassNode", nodes.at("/1/type").asText());
        assertEquals("/2", nodes.at("/1/args/0").asText());

        nodes = callOk("remove_node", java.util.Map.of("output_id", "default", "index", 0)).get("nodes");
        assertEquals(2, nodes.size());

        String error = callError("remove_node", java.util.Map.of("output_id", "default", "index", 5));
        assertTrue(error.contains("out of range"), error);
    }

    @Test
    void testOutputLifecycle() throws Exception {
        JsonNode added = callOk("add_output", java.util.Map.of("id", "lights", "port", 9100, "enabled", false));
        assertEquals("127.0.0.1", added.get("host").asText());
        assertNotNull(proxyService.getOutput("lights"));

        callError("add_output", java.util.Map.of("id", "lights", "port", 9100));
        callError("add_output", java.util.Map.of("id", "bad", "port", 70000));

        JsonNode updated = callOk("update_output", java.util.Map.of("id", "lights", "host", "10.0.0.5"));
        assertEquals("10.0.0.5", updated.get("host").asText());
        assertEquals(9100, updated.get("port").asInt());

        JsonNode pipeline = callOk("get_pipeline", java.util.Map.of());
        assertEquals("default", pipeline.at("/outputs/0/id").asText());
        assertEquals("lights", pipeline.at("/outputs/1/id").asText());

        callError("remove_output", java.util.Map.of("id", "default"));
        callOk("remove_output", java.util.Map.of("id", "lights"));
        assertNull(proxyService.getOutput("lights"));
    }

    @Test
    void testTestChainDoesNotTouchLiveChain() throws Exception {
        callOk("set_chain", java.util.Map.of(
                "output_id", "default",
                "nodes", List.of(node("Drop", "/debug/.*"), node("Rename", "/a/.*", "/a/", "/b/"))));
        OSCNode liveDrop = proxyService.getNodeChain("default").getNodes().get(0);

        JsonNode renamed = callOk("test_chain", java.util.Map.of(
                "output_id", "default", "address", "/a/x", "args", List.of(1, 2.5, "s", true)));
        assertEquals("/b/x", renamed.at("/output/0/address").asText());
        JsonNode args = renamed.at("/output/0/args");
        assertTrue(args.get(0).isInt());
        assertEquals(2.5, args.get(1).asDouble(), 1e-6);
        assertEquals("s", args.get(2).asText());
        assertTrue(args.get(3).asBoolean());

        JsonNode dropped = callOk("test_chain", java.util.Map.of("output_id", "default", "address", "/debug/x"));
        assertEquals(0, dropped.get("output").size());

        JsonNode proposed = callOk("test_chain", java.util.Map.of(
                "nodes", List.of(node("Delay", ".*", "250")), "address", "/a"));
        assertEquals(250, proposed.at("/output/0/delayMs").asInt());

        assertSame(liveDrop, proxyService.getNodeChain("default").getNodes().get(0));
        assertEquals(1, changeCount.get(), "test_chain should not notify the host");
    }

    @Test
    void testScriptToolsNeedOpenProject() {
        String error = callError("write_script", java.util.Map.of("path", "x.js", "content", "1"));
        assertTrue(error.contains("No project"), error);
    }

    @Test
    void testOriginCheck() {
        assertTrue(McpServer.isAllowedOrigin(null));
        assertTrue(McpServer.isAllowedOrigin("http://localhost:6274"));
        assertTrue(McpServer.isAllowedOrigin("http://127.0.0.1"));
        assertFalse(McpServer.isAllowedOrigin("https://evil.example.com"));
        assertFalse(McpServer.isAllowedOrigin("http://localhost.evil.example.com"));
    }

    @Test
    void testHttpTransport() throws Exception {
        server.start(0);
        HttpClient client = HttpClient.newHttpClient();
        URI uri = URI.create(server.getUrl());

        HttpResponse<String> init = client.send(HttpRequest.newBuilder(uri)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-06-18\"}}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, init.statusCode());
        assertEquals("application/json", init.headers().firstValue("Content-Type").orElse(""));
        assertEquals("2025-06-18", mapper.readTree(init.body()).at("/result/protocolVersion").asText());

        HttpResponse<String> notification = client.send(HttpRequest.newBuilder(uri)
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(202, notification.statusCode());

        HttpResponse<String> get = client.send(HttpRequest.newBuilder(uri).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(405, get.statusCode());

        HttpResponse<String> foreign = client.send(HttpRequest.newBuilder(uri)
                .header("Origin", "https://evil.example.com")
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(403, foreign.statusCode());
    }

    @Test
    void testRecordingToolsAreListed() throws Exception {
        JsonNode tools = request("tools/list", null).at("/result/tools");
        java.util.List<String> names = new java.util.ArrayList<>();
        tools.forEach(t -> names.add(t.get("name").asText()));
        assertTrue(names.contains("start_recording"), names.toString());
        assertTrue(names.contains("stop_recording"), names.toString());
    }

    @Test
    void testStartAndStopRecording() throws Exception {
        JsonNode started = callOk("start_recording", java.util.Map.of("name", "agent-take"));
        assertEquals("agent-take", started.get("recording").asText());
        assertTrue(started.get("address_filter").isNull());
        assertTrue(proxyService.isRecording());
        assertEquals(1, recordingChangeCount.get());

        JsonNode stopped = callOk("stop_recording", java.util.Map.of());
        assertEquals("agent-take", stopped.get("saved").asText());
        assertEquals(0, stopped.get("messages").asInt());
        assertFalse(proxyService.isRecording());
        assertEquals(2, recordingChangeCount.get());

        assertTrue(proxyService.getRecordedSessions().contains("agent-take"));
    }

    @Test
    void testStartRecordingWithAddressFilter() throws Exception {
        JsonNode started = callOk("start_recording", java.util.Map.of(
                "name", "tower2", "address_filter", "/mag2/xyz"));

        assertEquals("/mag2/xyz", started.get("address_filter").asText());
        assertEquals("/mag2/xyz", proxyService.getRecordFilter());

        JsonNode stopped = callOk("stop_recording", java.util.Map.of());
        assertEquals("/mag2/xyz", stopped.get("address_filter").asText());
    }

    @Test
    void testStartRecordingRejectsInvalidFilter() {
        String error = callError("start_recording", java.util.Map.of(
                "name", "bad", "address_filter", "/mag[2/xyz"));

        assertTrue(error.contains("not a valid regular expression"), error);
        assertFalse(proxyService.isRecording());
    }

    @Test
    void testStartRecordingRejectsPathSeparatorsInName() {
        String error = callError("start_recording", java.util.Map.of("name", "../escape"));

        assertTrue(error.contains("path separators"), error);
        assertFalse(proxyService.isRecording());
    }

    @Test
    void testStartRecordingRejectsBlankName() {
        String error = callError("start_recording", java.util.Map.of("name", "   "));

        assertTrue(error.contains("must not be blank"), error);
        assertFalse(proxyService.isRecording());
    }

    @Test
    void testStartRecordingRefusesWhenAlreadyRecording() throws Exception {
        callOk("start_recording", java.util.Map.of("name", "first"));

        String error = callError("start_recording", java.util.Map.of("name", "second"));
        assertTrue(error.contains("Already recording"), error);
        assertTrue(error.contains("first"), error);

        // The first recording is untouched.
        assertEquals("first", proxyService.getRecordingName());
        callOk("stop_recording", java.util.Map.of());
    }

    @Test
    void testStopRecordingWithoutStarting() {
        String error = callError("stop_recording", java.util.Map.of());
        assertTrue(error.contains("Nothing is being recorded"), error);
    }
}
