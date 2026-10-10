package se233.project1.controller;

import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import se233.project1.AppContext;
import se233.project1.task.IngestTask;
import se233.project1.util.AlertUtil;
import se233.project1.util.ErrorMessages;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Screen 1: accepts dropped images / zip archives (or a file chooser selection) and hands them to the editor. */
public final class DropZoneController {

    private static final PseudoClass DRAG_OVER = PseudoClass.getPseudoClass("drag-over");

    private final AppContext context;
    private final Navigator navigator;

    @FXML private StackPane root;
    @FXML private VBox dropZone;
    @FXML private VBox busyBox;
    @FXML private Label busyLabel;
    @FXML private ProgressBar progressBar;

    private boolean busy;

    public DropZoneController(AppContext context, Navigator navigator) {
        this.context = context;
        this.navigator = navigator;
    }

    @FXML
    private void initialize() {
        root.setOnDragOver(this::onDragOver);
        root.setOnDragEntered(e -> dropZone.pseudoClassStateChanged(DRAG_OVER, !busy && e.getDragboard().hasFiles()));
        root.setOnDragExited(e -> dropZone.pseudoClassStateChanged(DRAG_OVER, false));
        root.setOnDragDropped(this::onDragDropped);
    }

    private void onDragOver(DragEvent event) {
        if (!busy && event.getDragboard().hasFiles()) {
            event.acceptTransferModes(TransferMode.COPY);
        }
        event.consume();
    }

    private void onDragDropped(DragEvent event) {
        Dragboard board = event.getDragboard();
        boolean accepted = false;
        if (!busy && board.hasFiles()) {
            handleFiles(board.getFiles());
            accepted = true;
        }
        dropZone.pseudoClassStateChanged(DRAG_OVER, false);
        event.setDropCompleted(accepted);
        event.consume();
    }

    @FXML
    private void onBrowse() {
        if (busy) {
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select images or a zip archive");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Images and ZIP archives",
                        "*.jpg", "*.jpeg", "*.png", "*.zip", "*.JPG", "*.JPEG", "*.PNG", "*.ZIP"),
                new FileChooser.ExtensionFilter("All files", "*.*"));
        List<File> files = chooser.showOpenMultipleDialog(window());
        if (files != null && !files.isEmpty()) {
            handleFiles(files);
        }
    }

    private void handleFiles(List<File> files) {
        List<Path> paths = new ArrayList<>();
        for (File file : files) {
            paths.add(file.toPath());
        }
        startIngest(paths);
    }

    private void startIngest(List<Path> paths) {
        final Window owner = window();
        IngestTask task = new IngestTask(paths, context.zipService());
        setBusy(true);
        progressBar.progressProperty().bind(task.progressProperty());
        busyLabel.textProperty().bind(task.messageProperty());

        task.setOnSucceeded(e -> {
            unbind();
            IngestTask.Result result = task.getValue();
            setBusy(false);
            navigator.showEditor(result.images());
            if (!result.warnings().isEmpty()) {
                AlertUtil.warning(owner, "Some items were skipped", String.join("\n", result.warnings()));
            }
        });
        task.setOnFailed(e -> {
            unbind();
            setBusy(false);
            AlertUtil.error(owner, "Cannot open the dropped files", ErrorMessages.describe(task.getException()));
        });
        task.setOnCancelled(e -> {
            unbind();
            setBusy(false);
        });
        context.background().execute(task);
    }

    private void unbind() {
        progressBar.progressProperty().unbind();
        busyLabel.textProperty().unbind();
    }

    private void setBusy(boolean value) {
        busy = value;
        busyBox.setVisible(value);
        busyBox.setManaged(value);
        dropZone.setDisable(value);
        dropZone.setOpacity(value ? 0.35 : 1.0);
        if (value) {
            progressBar.setProgress(-1);
            busyLabel.setText("Preparing files…");
        }
    }

    private Window window() {
        return root.getScene() == null ? null : root.getScene().getWindow();
    }
}
