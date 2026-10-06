# MCP Server

OSCPlay has a built-in [Model Context Protocol](https://modelcontextprotocol.io) server. AI agents can use it to inspect and change a running OSCPlay's outputs and node chains.

## Starting it

The server is off by default. Turn it on in either of these ways:

- **Tools > MCP Server** in the menu
- start OSCPlay with `--mcp`. Add `--mcp-port <port>` to use a port other than 7770.

```sh
java -jar target/osc-play-*-shaded.jar --project MyProject --mcp
```

The log shows `MCP server listening at http://127.0.0.1:7770/mcp`. The server only listens on the loopback interface. It rejects browser requests from non-local origins.

## Connecting an agent

Claude Code:

```sh
claude mcp add --transport http oscplay http://127.0.0.1:7770/mcp
```

Any other MCP client that supports the Streamable HTTP transport can use the same URL.

## Tools

For how agents should use these tools, and a reference for every node type, see [MCP_AGENT_GUIDE.md](MCP_AGENT_GUIDE.md).

| Tool | What it does |
|------|--------------|
| `get_pipeline` | Show the input settings and every output with its node chain. Nodes are listed by index. |
| `list_node_types` | List the available node types, with help text and the positional args each one takes |
| `add_output` / `update_output` / `remove_output` | Manage outputs. `default` cannot be removed. |
| `set_chain` | Replace an output's whole chain atomically. Every node is validated first. |
| `add_node` / `update_node` / `remove_node` / `move_node` | Edit a chain by index |
| `test_chain` | Dry-run a message through an output's chain or through a proposed chain, and show the resulting messages, delays and target outputs. Nothing is sent, and the live nodes' state is left alone. |
| `send_message` | Inject a message through the live chains to real outputs. The message is not recorded. |
| `list_scripts` / `read_script` / `write_script` | Manage JavaScript files in the project's `Scripts/` directory, for ScriptNode |
| `save_project` | Write the current outputs and chains to the project's `.opp` file |

Edits take effect on the live proxy straight away, and the UI updates to show them. They are written to the project file only when `save_project` is called or when someone uses **File > Save**.

## Example

Here is an example of asking an agent: *"On a new output for the lighting desk at 10.0.0.20:7000, drop anything under /debug and rename /fader/N to /light/N"*. The agent would make these calls:

1. `add_output {"id": "lights", "host": "10.0.0.20", "port": 7000}`
2. `set_chain {"output_id": "lights", "nodes": [{"type": "Drop", "args": ["/debug/.*"]}, {"type": "Rename", "args": ["/fader/.*", "/fader/", "/light/"]}]}`
3. `test_chain {"output_id": "lights", "address": "/fader/3", "args": [0.5]}` returns `/light/3 [0.5]`
4. `save_project {}`

## Security note

ScriptNode runs JavaScript with host access. Anyone who can reach the MCP server can therefore run code as the OSCPlay user. That is why the server binds only to `127.0.0.1` and is off unless you turn it on.
