package xyz.theforks.mcp;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Model Context Protocol server exposing PipelineTools over the Streamable HTTP
 * transport (JSON responses only, no SSE stream), bound to the loopback interface.
 *
 * Connect with e.g. {@code claude mcp add --transport http oscplay http://127.0.0.1:7770/mcp}.
 */
public class McpServer {

    public static final int DEFAULT_PORT = 7770;
    public static final String PATH = "/mcp";

    static final String LATEST_PROTOCOL_VERSION = "2025-06-18";
    private static final List<String> SUPPORTED_PROTOCOL_VERSIONS =
            List.of(LATEST_PROTOCOL_VERSION, "2025-03-26", "2024-11-05");
    private static final long TOOL_TIMEOUT_SECONDS = 30;

    private static final String INSTRUCTIONS =
            "OSCPlay proxies OSC messages from one input to several outputs. Each output has its own node chain "
            + "that processes messages in order before they are sent. Start with get_pipeline and list_node_types. "
            + "Use test_chain to check a chain's behaviour before or after applying it. Edits take effect "
            + "immediately; call save_project to persist them in the project file.";

    private final ObjectMapper mapper = new ObjectMapper();
    private final PipelineTools tools;
    private final Executor toolExecutor;
    private final String version;
    private HttpServer httpServer;
    private ExecutorService httpExecutor;

    /**
     * @param tools The tools to expose
     * @param toolExecutor Executor every tool call runs on (Platform::runLater in the app)
     * @param version Server version reported to clients
     */
    public McpServer(PipelineTools tools, Executor toolExecutor, String version) {
        this.tools = tools;
        this.toolExecutor = toolExecutor;
        this.version = version;
    }

    public synchronized void start(int port) throws IOException {
        if (httpServer != null) {
            return;
        }
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
        server.createContext(PATH, this::handleHttp);
        httpExecutor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "mcp-http");
            t.setDaemon(true);
            return t;
        });
        server.setExecutor(httpExecutor);
        server.start();
        httpServer = server;
    }

    public synchronized void stop() {
        if (httpServer != null) {
            httpServer.stop(0);
            httpExecutor.shutdownNow();
            httpServer = null;
            httpExecutor = null;
        }
    }

    public synchronized boolean isRunning() {
        return httpServer != null;
    }

    /**
     * @return The URL clients connect to, or null if not running
     */
    public synchronized String getUrl() {
        if (httpServer == null) {
            return null;
        }
        return "http://127.0.0.1:" + httpServer.getAddress().getPort() + PATH;
    }

    // ========== HTTP transport ==========

    private void handleHttp(HttpExchange exchange) throws IOException {
        try (exchange) {
            if (!isAllowedOrigin(exchange.getRequestHeaders().getFirst("Origin"))) {
                sendStatus(exchange, 403);
                return;
            }
            if (!"POST".equals(exchange.getRequestMethod())) {
                // No server-initiated stream (GET) and no sessions to delete (DELETE)
                exchange.getResponseHeaders().set("Allow", "POST");
                sendStatus(exchange, 405);
                return;
            }

            JsonNode body;
            try (InputStream in = exchange.getRequestBody()) {
                body = mapper.readTree(in);
            } catch (IOException e) {
                sendJson(exchange, 400, error(null, -32700, "Parse error"));
                return;
            }

            JsonNode response;
            if (body != null && body.isArray()) {
                ArrayNode responses = mapper.createArrayNode();
                for (JsonNode message : body) {
                    JsonNode r = handle(message);
                    if (r != null) {
                        responses.add(r);
                    }
                }
                response = responses.isEmpty() ? null : responses;
            } else {
                response = handle(body);
            }

            if (response == null) {
                // Notifications and responses get no body
                sendStatus(exchange, 202);
            } else {
                sendJson(exchange, 200, response);
            }
        } catch (RuntimeException e) {
            System.err.println("MCP request failed: " + e.getMessage());
        }
    }

    /**
     * Reject browser requests from non-local pages (DNS rebinding protection).
     * Non-browser clients send no Origin header.
     */
    static boolean isAllowedOrigin(String origin) {
        if (origin == null) {
            return true;
        }
        try {
            String originHost = URI.create(origin).getHost();
            return "localhost".equals(originHost) || "127.0.0.1".equals(originHost) || "[::1]".equals(originHost);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private void sendStatus(HttpExchange exchange, int status) throws IOException {
        exchange.sendResponseHeaders(status, -1);
    }

    private void sendJson(HttpExchange exchange, int status, JsonNode json) throws IOException {
        byte[] bytes = mapper.writeValueAsBytes(json);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    // ========== JSON-RPC ==========

    /**
     * Handle one JSON-RPC message.
     * @return The response, or null for notifications and client responses
     */
    public JsonNode handle(JsonNode message) {
        if (message == null || !message.isObject() || !message.hasNonNull("method")) {
            if (message != null && message.isObject() && (message.has("result") || message.has("error"))) {
                return null; // A response to a server request; we never send any
            }
            return error(message != null ? message.get("id") : null, -32600, "Invalid Request");
        }
        JsonNode id = message.get("id");
        if (id == null) {
            return null; // Notification (e.g. notifications/initialized)
        }

        String method = message.get("method").asText();
        JsonNode params = message.hasNonNull("params") ? message.get("params") : mapper.createObjectNode();
        switch (method) {
            case "initialize":
                return result(id, initialize(params));
            case "ping":
                return result(id, mapper.createObjectNode());
            case "tools/list":
                return result(id, listTools());
            case "tools/call":
                return callTool(id, params);
            default:
                return error(id, -32601, "Method not found: " + method);
        }
    }

    private ObjectNode initialize(JsonNode params) {
        String requested = params.path("protocolVersion").asText(LATEST_PROTOCOL_VERSION);
        ObjectNode result = mapper.createObjectNode();
        result.put("protocolVersion",
                SUPPORTED_PROTOCOL_VERSIONS.contains(requested) ? requested : LATEST_PROTOCOL_VERSION);
        result.putObject("capabilities").putObject("tools").put("listChanged", false);
        ObjectNode serverInfo = result.putObject("serverInfo");
        serverInfo.put("name", "oscplay");
        serverInfo.put("version", version);
        result.put("instructions", INSTRUCTIONS);
        return result;
    }

    private ObjectNode listTools() {
        ObjectNode result = mapper.createObjectNode();
        ArrayNode list = result.putArray("tools");
        for (PipelineTools.Tool tool : tools.getTools()) {
            ObjectNode t = list.addObject();
            t.put("name", tool.name());
            t.put("description", tool.description());
            t.set("inputSchema", tool.inputSchema());
        }
        return result;
    }

    private JsonNode callTool(JsonNode id, JsonNode params) {
        String name = params.path("name").asText(null);
        PipelineTools.Tool tool = name != null ? tools.getTool(name) : null;
        if (tool == null) {
            return error(id, -32602, "Unknown tool: " + name);
        }
        JsonNode args = params.hasNonNull("arguments") ? params.get("arguments") : mapper.createObjectNode();

        CompletableFuture<Object> future = new CompletableFuture<>();
        toolExecutor.execute(() -> {
            try {
                Object value = tool.handler().call(args);
                if (tool.mutates()) {
                    tools.getHost().onPipelineChanged();
                }
                future.complete(value);
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });

        try {
            Object value = future.get(TOOL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            String text = value instanceof String
                    ? (String) value
                    : mapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
            return result(id, toolResult(text, false));
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            String msg = cause instanceof PipelineTools.ToolException
                    ? cause.getMessage()
                    : cause.getClass().getSimpleName() + ": " + cause.getMessage();
            return result(id, toolResult(msg, true));
        } catch (TimeoutException e) {
            return result(id, toolResult("Timed out waiting for OSCPlay to run " + name, true));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return result(id, toolResult("Interrupted", true));
        } catch (IOException e) {
            return result(id, toolResult("Could not serialize result: " + e.getMessage(), true));
        }
    }

    private ObjectNode toolResult(String text, boolean isError) {
        ObjectNode result = mapper.createObjectNode();
        ObjectNode content = result.putArray("content").addObject();
        content.put("type", "text");
        content.put("text", text);
        result.put("isError", isError);
        return result;
    }

    private ObjectNode result(JsonNode id, JsonNode result) {
        ObjectNode response = mapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", id);
        response.set("result", result);
        return response;
    }

    private ObjectNode error(JsonNode id, int code, String message) {
        ObjectNode response = mapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", id);
        ObjectNode err = response.putObject("error");
        err.put("code", code);
        err.put("message", message);
        return response;
    }
}
