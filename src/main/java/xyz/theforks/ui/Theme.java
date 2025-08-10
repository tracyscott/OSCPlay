package xyz.theforks.ui;

import javafx.scene.Scene;

/**
 * Centralized theming utilities for the application.
 */
public final class Theme {
    private Theme() {}

    private static final String DARK_CSS = Theme.class
            .getResource("/theme/dark.css")
            .toExternalForm();

    public static void applyDark(Scene scene) {
        if (scene == null) return;
        scene.getStylesheets().remove(DARK_CSS);
        scene.getStylesheets().add(DARK_CSS);
    }
}


