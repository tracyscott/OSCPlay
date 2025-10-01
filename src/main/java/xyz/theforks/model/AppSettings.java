package xyz.theforks.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Application-level settings stored in ~/Documents/OSCPlay/oscplay.cfg
 * Contains the currently selected project path.
 */
public class AppSettings {

    private String currentProjectPath;

    public AppSettings() {
        this.currentProjectPath = null;
    }

    @JsonCreator
    public AppSettings(@JsonProperty("currentProjectPath") String currentProjectPath) {
        this.currentProjectPath = currentProjectPath;
    }

    public String getCurrentProjectPath() {
        return currentProjectPath;
    }

    public void setCurrentProjectPath(String currentProjectPath) {
        this.currentProjectPath = currentProjectPath;
    }
}
