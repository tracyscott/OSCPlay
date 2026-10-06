package xyz.theforks.mcp;

import java.io.IOException;

/**
 * Callbacks from the MCP tools into the hosting application.
 * Both methods are invoked on the tool executor thread (the JavaFX thread in the app).
 */
public interface McpHost {

    /**
     * Called after a tool changes outputs or node chains so the UI can refresh
     * and the auto-saved app config can be updated.
     */
    void onPipelineChanged();

    /**
     * Persist the live outputs and node chains into the current project's .opp file.
     */
    void saveProject() throws IOException;
}
