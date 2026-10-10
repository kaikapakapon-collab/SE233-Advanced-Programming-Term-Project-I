package se233.project1.util;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.stage.Window;

import java.nio.file.Path;
import java.util.Optional;

/** Small helpers for JavaFX dialogs. {@code error/warning/info} may be called from any thread. */
public final class AlertUtil {

    public enum OverwriteChoice { OVERWRITE, KEEP_BOTH, CANCEL }

    private AlertUtil() {
    }

    public static void error(Window owner, String header, String message) {
        show(Alert.AlertType.ERROR, owner, "Error", header, message);
    }

    public static void warning(Window owner, String header, String message) {
        show(Alert.AlertType.WARNING, owner, "Warning", header, message);
    }

    public static void info(Window owner, String header, String message) {
        show(Alert.AlertType.INFORMATION, owner, "Information", header, message);
    }

    /** Must be called on the JavaFX Application Thread (it blocks until the user answers). */
    public static OverwriteChoice confirmOverwrite(Window owner, int existingCount, Path folder) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        if (owner != null) {
            alert.initOwner(owner);
        }
        alert.setTitle("Files already exist");
        alert.setHeaderText(existingCount + (existingCount == 1 ? " file already exists" : " files already exist")
                + " in the selected folder");
        alert.getDialogPane().setContent(content("Folder: " + folder
                + "\n\nOverwrite them, or keep both by adding a number to the new file names?"));
        ButtonType overwrite = new ButtonType("Overwrite", ButtonBar.ButtonData.OK_DONE);
        ButtonType keepBoth = new ButtonType("Keep both", ButtonBar.ButtonData.OTHER);
        ButtonType cancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(overwrite, keepBoth, cancel);
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent()) {
            if (result.get() == overwrite) {
                return OverwriteChoice.OVERWRITE;
            }
            if (result.get() == keepBoth) {
                return OverwriteChoice.KEEP_BOTH;
            }
        }
        return OverwriteChoice.CANCEL;
    }

    private static void show(Alert.AlertType type, Window owner, String title, String header, String message) {
        // Always defer: showAndWait() is not allowed while the toolkit is animating or laying out.
        Platform.runLater(() -> {
            Alert alert = new Alert(type);
            if (owner != null) {
                alert.initOwner(owner);
            }
            alert.setTitle(title);
            alert.setHeaderText(header);
            alert.getDialogPane().setContent(content(message));
            alert.showAndWait();
        });
    }

    private static Label content(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setMaxWidth(520);
        label.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        return label;
    }
}
