package xyz.theforks.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Configuration for QLab2OSCConverter
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class QLab2OSCConfig {
    private String qlabHost = "localhost";
    private int qlabPort = 53000;
    private String workspaceId = null; // null means use current workspace
    private String destinationHost = "localhost";
    private int destinationPort = 3030;
    private int oscPatch = 1;
    private boolean createCueList = true;
    private boolean usePreWait = true;
    private boolean autoContinue = true;
    private double timeScale = 1.0; // Scale timing (1.0 = original timing)
    private String cueType = "osc"; // "osc" or "memo"
    
    // Default constructor
    public QLab2OSCConfig() {}
    
    // Getters and setters
    public String getQlabHost() { return qlabHost; }
    public void setQlabHost(String qlabHost) { this.qlabHost = qlabHost; }
    
    public int getQlabPort() { return qlabPort; }
    public void setQlabPort(int qlabPort) { this.qlabPort = qlabPort; }
    
    public String getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(String workspaceId) { this.workspaceId = workspaceId; }
    
    public String getDestinationHost() { return destinationHost; }
    public void setDestinationHost(String destinationHost) { this.destinationHost = destinationHost; }
    
    public int getDestinationPort() { return destinationPort; }
    public void setDestinationPort(int destinationPort) { this.destinationPort = destinationPort; }
    
    public int getOscPatch() { return oscPatch; }
    public void setOscPatch(int oscPatch) { this.oscPatch = oscPatch; }
    
    public boolean isCreateCueList() { return createCueList; }
    public void setCreateCueList(boolean createCueList) { this.createCueList = createCueList; }
    
    public boolean isUsePreWait() { return usePreWait; }
    public void setUsePreWait(boolean usePreWait) { this.usePreWait = usePreWait; }
    
    public boolean isAutoContinue() { return autoContinue; }
    public void setAutoContinue(boolean autoContinue) { this.autoContinue = autoContinue; }
    
    public double getTimeScale() { return timeScale; }
    public void setTimeScale(double timeScale) { this.timeScale = timeScale; }
    
    public String getCueType() { return cueType; }
    public void setCueType(String cueType) { this.cueType = cueType; }
}