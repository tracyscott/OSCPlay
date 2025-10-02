package xyz.theforks;

/**
 * Launcher class for shaded JAR compatibility.
 *
 * When using maven-shade-plugin to create a fat JAR with JavaFX, the main class
 * cannot extend javafx.application.Application directly due to JavaFX's module
 * system checks. This launcher class serves as a non-Application entry point that
 * delegates to the actual JavaFX Application class.
 *
 * See: https://stackoverflow.com/questions/52653836/maven-shade-javafx-runtime-components-are-missing
 */
public class Launcher {
    public static void main(String[] args) {
        OSCProxyApp.main(args);
    }
}
