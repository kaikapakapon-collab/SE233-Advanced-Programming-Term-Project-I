package se233.project1;

import javafx.application.Application;

/**
 * Real entry point. It deliberately does NOT extend {@link Application}: a main class that extends
 * Application makes "java -jar" fail with "JavaFX runtime components are missing" when JavaFX is on the classpath.
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        // Batik / BufferedImage work does not need the AWT toolkit; headless avoids toolkit clashes with JavaFX.
        System.setProperty("java.awt.headless", "true");
        Application.launch(MainApp.class, args);
    }
}
