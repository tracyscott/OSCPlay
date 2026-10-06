package xyz.theforks.mcp;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.illposed.osc.OSCMessage;

import xyz.theforks.model.MessageRequest;
import xyz.theforks.nodes.NodeChain;
import xyz.theforks.nodes.NodeRegistry;
import xyz.theforks.nodes.OSCNode;
import xyz.theforks.nodes.ScriptNode;
import xyz.theforks.service.OSCOutputService;
import xyz.theforks.service.OSCProxyService;
import xyz.theforks.service.ProjectManager;

/**
 * MCP tools for inspecting and configuring OSCPlay pipelines: outputs, their
 * node chains, and the JavaScript files used by ScriptNode.
 *
 * Tools are not thread-safe on their own; McpServer runs them on a single
 * executor (the JavaFX thread in the app), the same thread the UI edits on.
 */
public class PipelineTools {

    @FunctionalInterface
    public interface Handler {
        Object call(JsonNode args) throws Exception;
    }

    /**
     * A tool definition. When mutates is true the host is notified after a successful call.
     */
    public record Tool(String name, String description, ObjectNode inputSchema, boolean mutates, Handler handler) {
    }

    /**
     * A tool failure caused by bad input or state; reported to the agent as a tool error.
     */
    public static class ToolException extends Exception {
        public ToolException(String message) {
            super(message);
        }
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final OSCProxyService proxyService;
    private final ProjectManager projectManager;
    private final McpHost host;
    private final Map<String, Tool> tools = new LinkedHashMap<>();

    public PipelineTools(OSCProxyService proxyService, ProjectManager projectManager, McpHost host) {
        this.proxyService = proxyService;
        this.projectManager = projectManager;
        this.host = host;
        registerTools();
    }

    public List<Tool> getTools() {
        return new ArrayList<>(tools.values());
    }

    public Tool getTool(String name) {
        return tools.get(name);
    }

    public McpHost getHost() {
        return host;
    }

    private void register(String name, String description, ObjectNode schema, boolean mutates, Handler handler) {
        tools.put(name, new Tool(name, description, schema, mutates, handler));
    }

    private void registerTools() {
        register("list_node_types",
                "List the node types that can be placed in a chain, with their help text and the "
                + "positional string args each one takes (in order).",
                new Schema().build(), false, args -> listNodeTypes());

        register("get_pipeline",
                "Show the input settings and every output with its host, port, enabled state and node chain. "
                + "Nodes are addressed by their zero-based index within an output's chain.",
                new Schema().str("output_id", "Only show this output", false).build(), false, this::getPipeline);

        register("add_output",
                "Add a new OSC output. Every input message is sent to every enabled output, each through its own node chain.",
                new Schema()
                        .str("id", "Unique output ID", true)
                        .str("host", "Destination host (default 127.0.0.1)", false)
                        .integer("port", "Destination UDP port", true)
                        .bool("enabled", "Forward proxied messages to this output (default true)", false)
                        .build(),
                true, this::addOutput);

        register("update_output",
                "Change an output's host, port or enabled state. Omitted fields are left unchanged.",
                new Schema()
                        .str("id", "Output ID", true)
                        .str("host", "Destination host", false)
                        .integer("port", "Destination UDP port", false)
                        .bool("enabled", "Forward proxied messages to this output", false)
                        .build(),
                true, this::updateOutput);

        register("remove_output",
                "Remove an output and its node chain. The 'default' output cannot be removed.",
                new Schema().str("id", "Output ID", true).build(), true, this::removeOutput);

        register("set_chain",
                "Replace an output's entire node chain in one atomic step. All nodes are validated before anything "
                + "changes. Pass an empty list to clear the chain.",
                new Schema()
                        .str("output_id", "Output ID", true)
                        .nodeList("nodes", "Nodes in processing order", true)
                        .build(),
                true, this::setChain);

        register("add_node",
                "Insert a node into an output's chain.",
                new Schema()
                        .str("output_id", "Output ID", true)
                        .str("type", "Node type from list_node_types (class name or label)", true)
                        .stringList("args", "Positional string args, in the order list_node_types gives", false)
                        .integer("index", "Position to insert at (default: end of chain)", false)
                        .build(),
                true, this::addNode);

        register("update_node",
                "Reconfigure the node at an index with new args. The node is rebuilt, so any internal state "
                + "(e.g. a moving average window) is reset.",
                new Schema()
                        .str("output_id", "Output ID", true)
                        .integer("index", "Zero-based index of the node", true)
                        .stringList("args", "Positional string args", true)
                        .build(),
                true, this::updateNode);

        register("remove_node",
                "Remove the node at an index from an output's chain.",
                new Schema()
                        .str("output_id", "Output ID", true)
                        .integer("index", "Zero-based index of the node", true)
                        .build(),
                true, this::removeNode);

        register("move_node",
                "Move a node to a new position within an output's chain.",
                new Schema()
                        .str("output_id", "Output ID", true)
                        .integer("from", "Current zero-based index", true)
                        .integer("to", "New zero-based index", true)
                        .build(),
                true, this::moveNode);

        register("test_chain",
                "Dry-run an OSC message through a chain and return the messages it would produce, without sending "
                + "anything. Uses fresh node instances, so the live nodes' state is not touched. Give either "
                + "output_id (test that output's current chain) or nodes (test a proposed chain).",
                new Schema()
                        .str("output_id", "Test this output's current chain", false)
                        .nodeList("nodes", "Test this proposed chain instead", false)
                        .str("address", "OSC address, e.g. /synth/osc1/freq", true)
                        .oscArgs("args", "OSC arguments", false)
                        .build(),
                false, this::testChain);

        register("send_message",
                "Send an OSC message through the live node chains to real outputs, as if it had arrived at the input. "
                + "It is not recorded. Without output_id it goes to every enabled, running output.",
                new Schema()
                        .str("address", "OSC address", true)
                        .oscArgs("args", "OSC arguments", false)
                        .str("output_id", "Send only to this output (even if disabled)", false)
                        .build(),
                false, this::sendMessage);

        register("list_scripts",
                "List the JavaScript files in the current project's Scripts directory, for use with ScriptNode.",
                new Schema().build(), false, args -> listScripts());

        register("read_script",
                "Read a JavaScript file from the current project's Scripts directory.",
                new Schema().str("path", "Path relative to the Scripts directory", true).build(),
                false, this::readScript);

        register("write_script",
                "Create or overwrite a JavaScript file in the current project's Scripts directory. The script must "
                + "define process(message) and return an OSCMessage, a MessageRequest, an array of either, or "
                + "null/false to drop. Helpers createMessage(address, argsArray) and "
                + "createMessageRequest(message, delayMs, outputId) are available. ScriptNodes using the file "
                + "reload it automatically.",
                new Schema()
                        .str("path", "Path relative to the Scripts directory, e.g. scale.js", true)
                        .str("content", "Full script source", true)
                        .build(),
                false, this::writeScript);

        register("start_recording",
                "Start recording incoming OSC messages to a named session. The raw input is recorded, "
                + "before any node processing. With address_filter only the messages whose address matches "
                + "are recorded, which is how you capture one device while others are streaming; it is a "
                + "Java regex that must match the whole address, so /mag2/xyz records one "
                + "Interlace tower and /mag[23]/xyz records two. Fails if a recording is "
                + "already in progress.",
                new Schema()
                        .str("name", "Name for the recording session", true)
                        .str("address_filter",
                             "Regex matching the whole OSC address; omit to record every message", false)
                        .build(),
                false, this::startRecording);

        register("stop_recording",
                "Stop the recording in progress and save it to the project's Recordings directory, so it "
                + "can be played back or used to build a sensor calibration. Reports how many messages "
                + "were kept.",
                new Schema().build(), false, args -> stopRecording());

        register("save_project",
                "Save the current outputs and node chains to the project's .opp file so they are restored next "
                + "time the project is opened. Changes made by other tools are live immediately but are not "
                + "written to the project until this is called.",
                new Schema().build(), false, args -> saveProject());
    }

    // ========== Node types ==========

    private Object listNodeTypes() {
        List<Map<String, Object>> types = new ArrayList<>();
        for (OSCNode node : NodeRegistry.getNodes()) {
            Map<String, Object> t = new LinkedHashMap<>();
            t.put("type", node.getClass().getSimpleName());
            t.put("label", node.label());
            t.put("help", node.getHelp());
            t.put("argNames", Arrays.asList(node.getArgNames()));
            types.add(t);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("notes", "Args are positional strings. An 'Address Pattern' arg is a Java regex that must match "
                + "the whole OSC address (e.g. '/synth/.*'); a node only processes messages whose address matches it, "
                + "and every other message passes through it unchanged.");
        result.put("types", types);
        return result;
    }

    // ========== Pipeline inspection ==========

    private Object getPipeline(JsonNode args) throws ToolException {
        Map<String, Object> result = new LinkedHashMap<>();
        if (projectManager != null && projectManager.hasOpenProject()) {
            result.put("project", projectManager.getCurrentProjectName());
            result.put("projectDir", projectManager.getProjectDir().toString());
        }

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("host", proxyService.getInputService().getInHost());
        input.put("port", proxyService.getInputService().getInPort());
        input.put("protocol", proxyService.getInputService().isUseTcp() ? "TCP" : "UDP");
        result.put("input", input);

        String onlyId = optString(args, "output_id");
        List<Map<String, Object>> outputs = new ArrayList<>();
        if (onlyId != null) {
            outputs.add(describeOutput(requireOutput(onlyId)));
        } else {
            for (OSCOutputService output : sortedOutputs()) {
                outputs.add(describeOutput(output));
            }
        }
        result.put("outputs", outputs);
        return result;
    }

    private List<OSCOutputService> sortedOutputs() {
        List<OSCOutputService> outputs = proxyService.getOutputs();
        // "default" first, then alphabetical, so indexes in listings are stable
        outputs.sort((a, b) -> {
            if (a.getId().equals(b.getId())) return 0;
            if ("default".equals(a.getId())) return -1;
            if ("default".equals(b.getId())) return 1;
            return a.getId().compareTo(b.getId());
        });
        return outputs;
    }

    private Map<String, Object> describeOutput(OSCOutputService output) {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("id", output.getId());
        o.put("host", output.getOutHost());
        o.put("port", output.getOutPort());
        o.put("enabled", output.isEnabled());
        o.put("running", output.isStarted());
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<OSCNode> chain = output.getNodeChain().getNodes();
        for (int i = 0; i < chain.size(); i++) {
            nodes.add(describeNode(i, chain.get(i)));
        }
        o.put("nodes", nodes);
        return o;
    }

    private Map<String, Object> describeNode(int index, OSCNode node) {
        Map<String, Object> n = new LinkedHashMap<>();
        n.put("index", index);
        n.put("type", node.getClass().getSimpleName());
        n.put("label", node.label());
        n.put("argNames", Arrays.asList(node.getArgNames()));
        n.put("args", Arrays.asList(node.getArgs()));
        return n;
    }

    // ========== Outputs ==========

    private Object addOutput(JsonNode args) throws Exception {
        String id = requireString(args, "id").trim();
        if (id.isEmpty()) {
            throw new ToolException("Output id cannot be empty");
        }
        if (proxyService.getOutput(id) != null) {
            throw new ToolException("Output '" + id + "' already exists");
        }
        String outHost = args.hasNonNull("host") ? args.get("host").asText() : "127.0.0.1";
        int port = validPort(requireInt(args, "port"));
        boolean enabled = !args.hasNonNull("enabled") || args.get("enabled").asBoolean();

        OSCOutputService output = new OSCOutputService(id);
        output.setOutHost(outHost);
        output.setOutPort(port);
        output.setEnabled(enabled);
        proxyService.addOutput(output);
        if (enabled) {
            output.start();
        }
        return describeOutput(output);
    }

    private Object updateOutput(JsonNode args) throws Exception {
        OSCOutputService output = requireOutput(requireString(args, "id"));
        boolean addressChanged = false;
        if (args.hasNonNull("host")) {
            output.setOutHost(args.get("host").asText());
            addressChanged = true;
        }
        if (args.hasNonNull("port")) {
            output.setOutPort(validPort(requireInt(args, "port")));
            addressChanged = true;
        }
        if (args.hasNonNull("enabled")) {
            output.setEnabled(args.get("enabled").asBoolean());
        }
        // Senders are bound to a host/port when started, so rebuild them on change
        if (addressChanged && output.isStarted()) {
            output.stop();
            output.start();
        } else if (output.isEnabled() && !output.isStarted()) {
            output.start();
        }
        return describeOutput(output);
    }

    private Object removeOutput(JsonNode args) throws ToolException {
        String id = requireString(args, "id");
        if ("default".equals(id)) {
            throw new ToolException("The 'default' output cannot be removed");
        }
        requireOutput(id);
        proxyService.removeOutput(id);
        return Map.of("removed", id);
    }

    // ========== Node chains ==========

    private Object setChain(JsonNode args) throws ToolException {
        OSCOutputService output = requireOutput(requireString(args, "output_id"));
        List<OSCNode> nodes = buildNodes(requireArray(args, "nodes"));
        output.getNodeChain().setNodes(nodes);
        return describeOutput(output);
    }

    private Object addNode(JsonNode args) throws ToolException {
        OSCOutputService output = requireOutput(requireString(args, "output_id"));
        OSCNode node = createNode(requireString(args, "type"), stringList(args.get("args")));
        List<OSCNode> nodes = output.getNodeChain().getNodes();
        int index = args.hasNonNull("index") ? requireInt(args, "index") : nodes.size();
        if (index < 0 || index > nodes.size()) {
            throw new ToolException("index must be between 0 and " + nodes.size());
        }
        nodes.add(index, node);
        output.getNodeChain().setNodes(nodes);
        return describeOutput(output);
    }

    private Object updateNode(JsonNode args) throws ToolException {
        OSCOutputService output = requireOutput(requireString(args, "output_id"));
        List<OSCNode> nodes = output.getNodeChain().getNodes();
        int index = requireIndex(args, "index", nodes.size());
        OSCNode replacement = createNode(nodes.get(index).getClass(), stringList(requireArray(args, "args")));
        nodes.set(index, replacement);
        output.getNodeChain().setNodes(nodes);
        return describeOutput(output);
    }

    private Object removeNode(JsonNode args) throws ToolException {
        OSCOutputService output = requireOutput(requireString(args, "output_id"));
        List<OSCNode> nodes = output.getNodeChain().getNodes();
        nodes.remove(requireIndex(args, "index", nodes.size()));
        output.getNodeChain().setNodes(nodes);
        return describeOutput(output);
    }

    private Object moveNode(JsonNode args) throws ToolException {
        OSCOutputService output = requireOutput(requireString(args, "output_id"));
        List<OSCNode> nodes = output.getNodeChain().getNodes();
        int from = requireIndex(args, "from", nodes.size());
        int to = requireIndex(args, "to", nodes.size());
        nodes.add(to, nodes.remove(from));
        output.getNodeChain().setNodes(nodes);
        return describeOutput(output);
    }

    private List<OSCNode> buildNodes(JsonNode specs) throws ToolException {
        List<OSCNode> nodes = new ArrayList<>();
        for (int i = 0; i < specs.size(); i++) {
            JsonNode spec = specs.get(i);
            if (!spec.hasNonNull("type")) {
                throw new ToolException("nodes[" + i + "] is missing 'type'");
            }
            try {
                nodes.add(createNode(spec.get("type").asText(), stringList(spec.get("args"))));
            } catch (ToolException e) {
                throw new ToolException("nodes[" + i + "]: " + e.getMessage());
            }
        }
        return nodes;
    }

    private OSCNode createNode(String type, List<String> args) throws ToolException {
        return createNode(resolveNodeType(type), args);
    }

    private OSCNode createNode(Class<? extends OSCNode> nodeClass, List<String> args) throws ToolException {
        OSCNode node;
        try {
            node = nodeClass.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new ToolException("Cannot create " + nodeClass.getSimpleName() + ": " + e.getMessage());
        }
        String expected = " Expected args: " + Arrays.toString(node.getArgNames());
        boolean configured;
        try {
            configured = node.configure(args.toArray(new String[0]));
        } catch (RuntimeException e) {
            throw new ToolException(nodeClass.getSimpleName() + ": " + e.getMessage() + "." + expected);
        }
        if (!configured) {
            String hint = node instanceof ScriptNode
                    ? " Check that the script exists in the project's Scripts directory (write_script) and defines process(message)."
                    : "";
            throw new ToolException(nodeClass.getSimpleName() + " rejected args " + args + "." + expected + hint);
        }
        return node;
    }

    private Class<? extends OSCNode> resolveNodeType(String type) throws ToolException {
        for (OSCNode node : NodeRegistry.getNodes()) {
            Class<? extends OSCNode> nodeClass = node.getClass();
            if (nodeClass.getName().equals(type)
                    || nodeClass.getSimpleName().equalsIgnoreCase(type)
                    || node.label().equalsIgnoreCase(type)) {
                return nodeClass;
            }
        }
        throw new ToolException("Unknown node type '" + type + "'. Call list_node_types for valid types.");
    }

    // ========== Messages ==========

    private Object testChain(JsonNode args) throws ToolException {
        List<OSCNode> nodes;
        if (args.hasNonNull("nodes")) {
            nodes = buildNodes(requireArray(args, "nodes"));
        } else if (args.hasNonNull("output_id")) {
            // Rebuild the live chain from its config so stateful nodes aren't disturbed
            List<OSCNode> live = requireOutput(args.get("output_id").asText()).getNodeChain().getNodes();
            nodes = new ArrayList<>();
            for (OSCNode node : live) {
                nodes.add(createNode(node.getClass(), Arrays.asList(node.getArgs())));
            }
        } else {
            throw new ToolException("Provide either output_id or nodes");
        }

        NodeChain chain = new NodeChain(NodeChain.Context.PROXY);
        chain.setNodes(nodes);
        OSCMessage message = buildMessage(args);

        List<Map<String, Object>> produced = new ArrayList<>();
        for (MessageRequest req : chain.processMessage(message)) {
            Map<String, Object> m = describeMessage(req.getMessage());
            if (!req.isImmediate()) {
                m.put("delayMs", req.getDelayMs());
            }
            if (req.hasTargetOutput()) {
                m.put("targetOutput", req.getTargetOutputId());
            }
            produced.add(m);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("input", describeMessage(message));
        result.put("output", produced);
        if (produced.isEmpty()) {
            result.put("note", "The chain dropped the message.");
        }
        return result;
    }

    private Object sendMessage(JsonNode args) throws Exception {
        OSCMessage message = buildMessage(args);
        List<String> sentTo = new ArrayList<>();
        if (args.hasNonNull("output_id")) {
            OSCOutputService output = requireOutput(args.get("output_id").asText());
            output.send(message, true);
            sentTo.add(output.getId());
        } else {
            for (OSCOutputService output : sortedOutputs()) {
                if (output.isEnabled() && output.isStarted()) {
                    output.send(message);
                    sentTo.add(output.getId());
                }
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", describeMessage(message));
        result.put("sentTo", sentTo);
        return result;
    }

    private OSCMessage buildMessage(JsonNode args) throws ToolException {
        String address = requireString(args, "address");
        if (!address.startsWith("/")) {
            throw new ToolException("OSC address must start with '/'");
        }
        List<Object> oscArgs = new ArrayList<>();
        JsonNode argsNode = args.get("args");
        if (argsNode != null && !argsNode.isNull()) {
            if (!argsNode.isArray()) {
                throw new ToolException("args must be an array");
            }
            for (JsonNode a : argsNode) {
                oscArgs.add(toOscArg(a));
            }
        }
        return new OSCMessage(address, oscArgs);
    }

    static Object toOscArg(JsonNode a) throws ToolException {
        if (a.isBoolean()) return a.booleanValue();
        if (a.isTextual()) return a.textValue();
        if (a.isIntegralNumber()) return a.canConvertToInt() ? (Object) a.intValue() : (Object) a.longValue();
        if (a.isNumber()) return a.floatValue();
        throw new ToolException("Unsupported OSC argument: " + a + " (use numbers, strings or booleans)");
    }

    private static Map<String, Object> describeMessage(OSCMessage message) {
        List<Object> args = new ArrayList<>();
        for (Object a : message.getArguments()) {
            args.add(a == null || a instanceof Number || a instanceof String || a instanceof Boolean ? a : a.toString());
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("address", message.getAddress());
        m.put("args", args);
        return m;
    }

    // ========== Scripts ==========

    private Path scriptsDir() throws ToolException {
        if (projectManager == null || !projectManager.hasOpenProject()) {
            throw new ToolException("No project is open");
        }
        return projectManager.getScriptsDir().toAbsolutePath().normalize();
    }

    private Path resolveScript(String relative) throws ToolException {
        Path dir = scriptsDir();
        Path path = dir.resolve(relative).normalize();
        if (!path.startsWith(dir) || path.equals(dir)) {
            throw new ToolException("Script path must be inside the project's Scripts directory");
        }
        return path;
    }

    private Object listScripts() throws Exception {
        Path dir = scriptsDir();
        if (!Files.isDirectory(dir)) {
            return Map.of("scriptsDir", dir.toString(), "scripts", Collections.emptyList());
        }
        try (Stream<Path> files = Files.walk(dir)) {
            List<String> scripts = files
                    .filter(Files::isRegularFile)
                    .map(p -> dir.relativize(p).toString().replace('\\', '/'))
                    .sorted()
                    .collect(Collectors.toList());
            return Map.of("scriptsDir", dir.toString(), "scripts", scripts);
        }
    }

    private Object readScript(JsonNode args) throws Exception {
        Path path = resolveScript(requireString(args, "path"));
        if (!Files.isRegularFile(path)) {
            throw new ToolException("Script not found: " + args.get("path").asText());
        }
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private Object writeScript(JsonNode args) throws Exception {
        Path path = resolveScript(requireString(args, "path"));
        Files.createDirectories(path.getParent());
        Files.writeString(path, requireString(args, "content"), StandardCharsets.UTF_8);
        return Map.of("written", path.toString());
    }

    // ========== Project ==========

    private Object saveProject() throws IOException, ToolException {
        if (projectManager == null || !projectManager.hasOpenProject()) {
            throw new ToolException("No project is open");
        }
        host.saveProject();
        return Map.of("saved", projectManager.getCurrentProjectName());
    }

    // ========== Argument helpers ==========

    private OSCOutputService requireOutput(String id) throws ToolException {
        OSCOutputService output = proxyService.getOutput(id);
        if (output == null) {
            String known = sortedOutputs().stream().map(OSCOutputService::getId).collect(Collectors.joining(", "));
            throw new ToolException("No output '" + id + "'. Outputs: " + known);
        }
        return output;
    }

    private static String optString(JsonNode args, String name) {
        return args.hasNonNull(name) ? args.get(name).asText() : null;
    }

    private static String requireString(JsonNode args, String name) throws ToolException {
        if (!args.hasNonNull(name)) {
            throw new ToolException("Missing required argument: " + name);
        }
        return args.get(name).asText();
    }

    private static int requireInt(JsonNode args, String name) throws ToolException {
        JsonNode v = args.get(name);
        if (v == null || v.isNull()) {
            throw new ToolException("Missing required argument: " + name);
        }
        if (v.isIntegralNumber() && v.canConvertToInt()) {
            return v.intValue();
        }
        try {
            return Integer.parseInt(v.asText().trim());
        } catch (NumberFormatException e) {
            throw new ToolException(name + " must be an integer");
        }
    }

    private static int requireIndex(JsonNode args, String name, int size) throws ToolException {
        int index = requireInt(args, name);
        if (index < 0 || index >= size) {
            throw new ToolException(name + " " + index + " is out of range; the chain has " + size + " node(s)");
        }
        return index;
    }

    private static int validPort(int port) throws ToolException {
        if (port < 1 || port > 65535) {
            throw new ToolException("port must be between 1 and 65535");
        }
        return port;
    }

    private static JsonNode requireArray(JsonNode args, String name) throws ToolException {
        JsonNode v = args.get(name);
        if (v == null || !v.isArray()) {
            throw new ToolException(name + " must be an array");
        }
        return v;
    }

    private static List<String> stringList(JsonNode v) throws ToolException {
        List<String> list = new ArrayList<>();
        if (v == null || v.isNull()) {
            return list;
        }
        if (!v.isArray()) {
            throw new ToolException("args must be an array of strings");
        }
        for (JsonNode item : v) {
            list.add(item.asText());
        }
        return list;
    }

    // ========== Recording ==========

    private Object startRecording(JsonNode args) throws ToolException {
        if (proxyService.isRecording()) {
            throw new ToolException("Already recording \"" + proxyService.getRecordingName()
                    + "\"; call stop_recording first");
        }

        String name = requireString(args, "name").trim();
        if (name.isEmpty()) {
            throw new ToolException("name must not be blank");
        }
        // The name becomes a directory under Recordings, so keep it to one path segment.
        if (name.contains("/") || name.contains("\\") || name.equals(".") || name.equals("..")) {
            throw new ToolException("name must be a single directory name, with no path separators");
        }

        String filter = optString(args, "address_filter");
        try {
            proxyService.startRecording(name, filter);
        } catch (PatternSyntaxException e) {
            throw new ToolException("address_filter is not a valid regular expression: "
                    + e.getDescription());
        }
        getHost().onRecordingChanged();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("recording", name);
        result.put("address_filter", proxyService.getRecordFilter());
        return result;
    }

    private Object stopRecording() throws ToolException {
        if (!proxyService.isRecording()) {
            throw new ToolException("Nothing is being recorded");
        }
        String name = proxyService.getRecordingName();
        String filter = proxyService.getRecordFilter();
        int recorded = proxyService.messageCountProperty().get();

        proxyService.stopRecording();
        getHost().onRecordingChanged();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("saved", name);
        result.put("messages", recorded);
        result.put("address_filter", filter);
        return result;
    }

    // ========== JSON schema builder ==========

    private static class Schema {
        private final ObjectNode root = MAPPER.createObjectNode();
        private final ObjectNode props = root.putObject("properties");
        private final ArrayNode required = MAPPER.createArrayNode();

        Schema() {
            root.put("type", "object");
        }

        private ObjectNode prop(String name, String type, String description, boolean isRequired) {
            ObjectNode p = props.putObject(name);
            p.put("type", type);
            p.put("description", description);
            if (isRequired) {
                required.add(name);
            }
            return p;
        }

        Schema str(String name, String description, boolean isRequired) {
            prop(name, "string", description, isRequired);
            return this;
        }

        Schema integer(String name, String description, boolean isRequired) {
            prop(name, "integer", description, isRequired);
            return this;
        }

        Schema bool(String name, String description, boolean isRequired) {
            prop(name, "boolean", description, isRequired);
            return this;
        }

        Schema stringList(String name, String description, boolean isRequired) {
            prop(name, "array", description, isRequired).putObject("items").put("type", "string");
            return this;
        }

        Schema oscArgs(String name, String description, boolean isRequired) {
            ArrayNode types = prop(name, "array", description + " (integers become int32, decimals float32)", isRequired)
                    .putObject("items").putArray("type");
            types.add("number").add("string").add("boolean");
            return this;
        }

        Schema nodeList(String name, String description, boolean isRequired) {
            ObjectNode item = prop(name, "array", description, isRequired).putObject("items");
            item.put("type", "object");
            ObjectNode itemProps = item.putObject("properties");
            itemProps.putObject("type").put("type", "string")
                    .put("description", "Node type from list_node_types");
            ObjectNode itemArgs = itemProps.putObject("args");
            itemArgs.put("type", "array");
            itemArgs.put("description", "Positional string args");
            itemArgs.putObject("items").put("type", "string");
            item.putArray("required").add("type");
            return this;
        }

        ObjectNode build() {
            if (!required.isEmpty()) {
                root.set("required", required);
            }
            return root;
        }
    }
}
