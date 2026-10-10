package se233.project1.controller;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundImage;
import javafx.scene.layout.BackgroundPosition;
import javafx.scene.layout.BackgroundRepeat;
import javafx.scene.layout.BackgroundSize;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;
import javafx.scene.control.ScrollPane;
import se233.project1.AppContext;
import se233.project1.exception.ToolNotFoundException;
import se233.project1.model.ColorMode;
import se233.project1.model.ConversionSettings;
import se233.project1.model.DetailLevel;
import se233.project1.model.ImageItem;
import se233.project1.model.ImageQueue;
import se233.project1.task.BatchExportTask;
import se233.project1.task.ExecutionPolicy;
import se233.project1.task.PreprocessTask;
import se233.project1.task.PreviewTask;
import se233.project1.util.AlertUtil;
import se233.project1.util.ErrorMessages;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Screen 2: the editor. Wires the top panel (zoom), the center preview, the right panel
 * (detail, colors, remove background, navigation, export) and the status bar to the image queue and to the
 * background tasks. Everything here runs on the JavaFX Application Thread.
 */
public final class EditorController {

    private final AppContext context;
    private final Navigator navigator;
    private final ImageQueue queue;

    // ---- top panel ----
    @FXML private BorderPane root;
    @FXML private Button zoomInButton;
    @FXML private Button zoomOutButton;
    @FXML private Button zoomFitButton;

    // ---- center ----
    @FXML private ScrollPane originalScroll;
    @FXML private ScrollPane vectorScroll;
    @FXML private StackPane originalContent;
    @FXML private StackPane vectorContent;
    @FXML private ImageView originalView;
    @FXML private ImageView vectorView;
    @FXML private Label originalCaption;
    @FXML private Label vectorCaption;
    @FXML private VBox vectorOverlay;
    @FXML private ProgressIndicator overlayIndicator;
    @FXML private Label overlayLabel;

    // ---- right panel ----
    @FXML private Button exportButton;
    @FXML private VBox configBox;
    @FXML private ToggleButton detailLowButton;
    @FXML private ToggleButton detailMediumButton;
    @FXML private ToggleButton detailHighButton;
    @FXML private ToggleButton colorsUnlimitedButton;
    @FXML private ToggleButton colorsCustomButton;
    @FXML private HBox customColorsBox;
    @FXML private Spinner<Integer> colorCountSpinner;
    @FXML private Label colorHintLabel;
    @FXML private CheckBox removeBackgroundCheck;
    @FXML private CheckBox applyToAllCheck;
    @FXML private Button backButton;
    @FXML private Button nextButton;
    @FXML private Label positionLabel;

    // ---- status bar ----
    @FXML private Label statusLabel;
    @FXML private ProgressBar progressBar;
    @FXML private Label modeLabel;
    @FXML private Button cancelButton;
    @FXML private Button newBatchButton;

    private PreviewPaneController preview;
    private ToggleGroup detailGroup;
    private ToggleGroup colorGroup;
    private final BooleanProperty busy = new SimpleBooleanProperty(false);

    private boolean updatingControls;
    private boolean disposed;
    private boolean toolAlertShown;

    private ImageItem observedItem;
    private ImageItem shownItem;
    private Image shownOriginal;
    private ImageItem previewItem;          // item whose preview task is scheduled / running

    private PreprocessTask preprocessTask;
    private BatchExportTask exportTask;
    private File lastExportDir;
    private List<String> skippedOnExport = List.of();

    private final ChangeListener<Object> itemListener = (obs, oldValue, newValue) -> onObservedItemChanged();
    private final ChangeListener<ImageItem> currentListener = (obs, oldItem, newItem) -> onCurrentItemChanged(oldItem, newItem);

    public EditorController(AppContext context, Navigator navigator) {
        this.context = context;
        this.navigator = navigator;
        this.queue = context.queue();
    }

    // =====================================================================
    // Initialisation
    // =====================================================================

    @FXML
    private void initialize() {
        preview = new PreviewPaneController(originalScroll, originalView, vectorScroll, vectorView);
        installCheckerBackground(originalContent);
        installCheckerBackground(vectorContent);

        zoomInButton.disableProperty().bind(preview.hasContentProperty().not());
        zoomOutButton.disableProperty().bind(preview.hasContentProperty().not());
        zoomFitButton.disableProperty().bind(preview.hasContentProperty().not());

        setupToggleGroups();
        colorCountSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(
                ConversionSettings.MIN_CUSTOM_COLORS, ConversionSettings.MAX_CUSTOM_COLORS,
                ConversionSettings.DEFAULT_CUSTOM_COLORS));
        colorCountSpinner.valueProperty().addListener((obs, old, value) -> onControlsChanged());
        removeBackgroundCheck.selectedProperty().addListener((obs, old, value) -> onControlsChanged());
        applyToAllCheck.selectedProperty().addListener((obs, old, selected) -> {
            ImageItem current = queue.getCurrent();
            if (selected && !updatingControls && current != null) {
                propagateSettings(current);
            }
        });

        positionLabel.textProperty().bind(queue.positionTextProperty());
        backButton.disableProperty().bind(queue.hasPreviousProperty().not());
        nextButton.disableProperty().bind(queue.hasNextProperty().not());
        exportButton.disableProperty().bind(busy.or(queue.emptyProperty()));

        setShown(progressBar, false);
        setShown(modeLabel, false);
        setShown(cancelButton, false);
        setShown(vectorOverlay, false);
        statusLabel.setText("Ready");

        queue.currentProperty().addListener(currentListener);
        onCurrentItemChanged(null, queue.getCurrent());
    }

    private void setupToggleGroups() {
        detailGroup = new ToggleGroup();
        detailLowButton.setToggleGroup(detailGroup);
        detailLowButton.setUserData(DetailLevel.LOW);
        detailMediumButton.setToggleGroup(detailGroup);
        detailMediumButton.setUserData(DetailLevel.MEDIUM);
        detailHighButton.setToggleGroup(detailGroup);
        detailHighButton.setUserData(DetailLevel.HIGH);
        detailMediumButton.setSelected(true);

        colorGroup = new ToggleGroup();
        colorsUnlimitedButton.setToggleGroup(colorGroup);
        colorsUnlimitedButton.setUserData(ColorMode.UNLIMITED);
        colorsCustomButton.setToggleGroup(colorGroup);
        colorsCustomButton.setUserData(ColorMode.CUSTOM);
        colorsCustomButton.setSelected(true);

        keepOneSelected(detailGroup);
        keepOneSelected(colorGroup);
        detailGroup.selectedToggleProperty().addListener((obs, old, now) -> onControlsChanged());
        colorGroup.selectedToggleProperty().addListener((obs, old, now) -> onControlsChanged());
    }

    /** A segmented control must always have exactly one selected button. */
    private static void keepOneSelected(ToggleGroup group) {
        group.selectedToggleProperty().addListener((obs, old, now) -> {
            if (now == null && old != null) {
                old.setSelected(true);
            }
        });
    }

    /** Shows the classic transparency checkerboard behind both previews. */
    private static void installCheckerBackground(Region region) {
        final int cell = 8;
        WritableImage tile = new WritableImage(cell * 2, cell * 2);
        PixelWriter writer = tile.getPixelWriter();
        Color light = Color.web("#ffffff");
        Color dark = Color.web("#e3e5ea");
        for (int y = 0; y < cell * 2; y++) {
            for (int x = 0; x < cell * 2; x++) {
                boolean even = ((x / cell) + (y / cell)) % 2 == 0;
                writer.setColor(x, y, even ? light : dark);
            }
        }
        region.setBackground(new Background(new BackgroundImage(tile, BackgroundRepeat.REPEAT,
                BackgroundRepeat.REPEAT, BackgroundPosition.DEFAULT, BackgroundSize.DEFAULT)));
    }

    /** Called by MainApp right after the scene is installed: starts preprocessing of the dropped images. */
    public void begin() {
        List<ImageItem> items = new ArrayList<>(queue.items());
        if (items.isEmpty()) {
            return;
        }
        for (ImageItem item : items) {
            item.setStatus(ImageItem.Status.PROCESSING);
        }
        PreprocessTask task = new PreprocessTask(context.pipeline(), items, this::onPreprocessOutcome);
        preprocessTask = task;
        beginWork(task, task.getDecision());

        task.setOnSucceeded(e -> {
            endWork(preprocessSummary(items));
            preprocessTask = null;
            ensurePreview(queue.getCurrent(), 0);
        });
        task.setOnFailed(e -> {
            Throwable error = task.getException();
            String message = ErrorMessages.describe(error);
            for (ImageItem item : items) {
                if (item.getStatus() == ImageItem.Status.PROCESSING) {
                    item.markFailed(message);
                }
            }
            endWork("Preprocessing failed");
            preprocessTask = null;
            showToolOrError(error, "Cannot process the images");
        });
        task.setOnCancelled(e -> {
            endWork("Preprocessing cancelled");
            preprocessTask = null;
        });
        context.background().execute(task);
    }

    // =====================================================================
    // Preprocess / preview results
    // =====================================================================

    private void onPreprocessOutcome(PreprocessTask.ItemOutcome outcome) {
        if (disposed || !queue.items().contains(outcome.item())) {
            return;
        }
        ImageItem item = outcome.item();
        if (outcome.error() != null) {
            item.markFailed(outcome.error());
            return;
        }
        item.applyAnalysis(outcome.width(), outcome.height(), outcome.maxColors());
        item.setOriginalImage(outcome.original());
        item.setVectorPreview(outcome.preview());
        item.setResult(outcome.svg(), outcome.settings());
    }

    private String preprocessSummary(List<ImageItem> items) {
        int failed = 0;
        for (ImageItem item : items) {
            if (item.getStatus() == ImageItem.Status.FAILED) {
                failed++;
            }
        }
        int ok = items.size() - failed;
        return failed == 0
                ? "Ready  •  " + ok + (ok == 1 ? " image" : " images") + " processed"
                : "Ready  •  " + ok + " processed, " + failed + " failed";
    }

    /** Schedules a preview for {@code item} unless it is already up to date or cannot be processed. */
    private void ensurePreview(ImageItem item, long debounceMillis) {
        if (disposed || item == null || item.getOriginalWidth() == 0) {
            return;
        }
        ImageItem.Status status = item.getStatus();
        if (status == ImageItem.Status.PROCESSING || status == ImageItem.Status.FAILED) {
            return;
        }
        if (item.getErrorMessage() != null || item.isResultCurrent()) {
            return;
        }
        requestPreview(item, debounceMillis);
    }

    private void requestPreview(ImageItem item, long debounceMillis) {
        if (previewItem != null && previewItem != item) {
            restoreStatus(previewItem);
        }
        previewItem = item;
        item.errorMessageProperty().set(null);
        item.setStatus(ImageItem.Status.PROCESSING);
        context.previewScheduler().schedule(context.pipeline(), item.getSourcePath(), item.getSettings(),
                debounceMillis,
                result -> onPreviewSucceeded(item, result),
                error -> onPreviewFailed(item, error));
    }

    private void onPreviewSucceeded(ImageItem item, PreviewTask.Result result) {
        if (previewItem == item) {
            previewItem = null;
        }
        if (disposed || !queue.items().contains(item)) {
            return;
        }
        item.setVectorPreview(result.preview());
        item.setResult(result.svg(), result.settings());
        if (item == queue.getCurrent()) {
            ensurePreview(item, PreviewTask.DEFAULT_DEBOUNCE_MILLIS); // settings changed again while tracing
        }
    }

    private void onPreviewFailed(ImageItem item, Throwable error) {
        if (previewItem == item) {
            previewItem = null;
        }
        if (disposed || !queue.items().contains(item)) {
            return;
        }
        String message = ErrorMessages.describe(error);
        if (item.getSvgContent() != null) {
            item.errorMessageProperty().set(message);
            item.setStatus(ImageItem.Status.READY);
        } else {
            item.markFailed(message);
        }
        showToolOrError(error, "Cannot update the preview");
    }

    private static void restoreStatus(ImageItem item) {
        if (item.getStatus() == ImageItem.Status.PROCESSING) {
            item.setStatus(item.getSvgContent() != null ? ImageItem.Status.READY : ImageItem.Status.QUEUED);
        }
    }

    /** Missing-Potrace is shown once as a dialog; other problems are shown inline in the preview. */
    private void showToolOrError(Throwable error, String header) {
        if (error instanceof ToolNotFoundException) {
            if (!toolAlertShown) {
                toolAlertShown = true;
                AlertUtil.error(window(), "Potrace was not found", error.getMessage());
            }
        } else if (error instanceof OutOfMemoryError) {
            AlertUtil.error(window(), header, ErrorMessages.describe(error));
        }
    }

    // =====================================================================
    // Current item / controls
    // =====================================================================

    private void onCurrentItemChanged(ImageItem oldItem, ImageItem newItem) {
        if (oldItem != null) {
            detach(oldItem);
        }
        observedItem = newItem;
        if (newItem != null) {
            attach(newItem);
        }
        syncControls(newItem);
        refreshView();
        ensurePreview(newItem, 0);
    }

    private void attach(ImageItem item) {
        item.statusProperty().addListener(itemListener);
        item.settingsProperty().addListener(itemListener);
        item.originalImageProperty().addListener(itemListener);
        item.vectorPreviewProperty().addListener(itemListener);
        item.originalWidthProperty().addListener(itemListener);
        item.maxCustomColorsProperty().addListener(itemListener);
        item.errorMessageProperty().addListener(itemListener);
    }

    private void detach(ImageItem item) {
        item.statusProperty().removeListener(itemListener);
        item.settingsProperty().removeListener(itemListener);
        item.originalImageProperty().removeListener(itemListener);
        item.vectorPreviewProperty().removeListener(itemListener);
        item.originalWidthProperty().removeListener(itemListener);
        item.maxCustomColorsProperty().removeListener(itemListener);
        item.errorMessageProperty().removeListener(itemListener);
    }

    private void onObservedItemChanged() {
        syncControls(queue.getCurrent());
        refreshView();
    }

    private static boolean isConfigurable(ImageItem item) {
        return item != null && item.getOriginalWidth() > 0 && item.getStatus() != ImageItem.Status.FAILED;
    }

    /** Copies the item's settings into the right-panel controls (without triggering re-tracing). */
    private void syncControls(ImageItem item) {
        configBox.setDisable(!isConfigurable(item));
        updatingControls = true;
        try {
            if (item != null && item.getOriginalWidth() > 0) {
                ConversionSettings s = item.getSettings();
                selectToggle(detailGroup, s.detail());
                selectToggle(colorGroup, s.colorMode());
                int max = item.getMaxCustomColors();
                configureSpinner(max, Math.min(s.customColorCount(), max));
                removeBackgroundCheck.setSelected(s.removeBackground());
                colorHintLabel.setText("Auto-detected: up to " + max + (max == 1 ? " color" : " colors"));
            }
            boolean custom = colorGroup.getSelectedToggle() == colorsCustomButton;
            setShown(customColorsBox, custom);
            setShown(colorHintLabel, custom);
        } finally {
            updatingControls = false;
        }
    }

    private static void selectToggle(ToggleGroup group, Object userData) {
        for (Toggle toggle : group.getToggles()) {
            if (toggle.getUserData() == userData) {
                group.selectToggle(toggle);
                return;
            }
        }
    }

    private void configureSpinner(int max, int value) {
        SpinnerValueFactory<Integer> factory = colorCountSpinner.getValueFactory();
        if (factory instanceof SpinnerValueFactory.IntegerSpinnerValueFactory
                && ((SpinnerValueFactory.IntegerSpinnerValueFactory) factory).getMax() == max) {
            factory.setValue(value);
        } else {
            colorCountSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(
                    ConversionSettings.MIN_CUSTOM_COLORS, max, value));
        }
    }

    /** A control in the right panel changed: store the new settings and schedule a debounced preview. */
    private void onControlsChanged() {
        boolean custom = colorGroup.getSelectedToggle() == colorsCustomButton;
        setShown(customColorsBox, custom);
        setShown(colorHintLabel, custom);

        ImageItem item = queue.getCurrent();
        if (updatingControls || !isConfigurable(item)) {
            return;
        }
        ConversionSettings settings = readControls(item);
        if (settings == null || settings.equals(item.getSettings())) {
            return;
        }
        item.setSettings(settings);
        if (applyToAllCheck.isSelected()) {
            propagateSettings(item);
        }
        requestPreview(item, PreviewTask.DEFAULT_DEBOUNCE_MILLIS);
    }

    private ConversionSettings readControls(ImageItem item) {
        Toggle detailToggle = detailGroup.getSelectedToggle();
        Toggle colorToggle = colorGroup.getSelectedToggle();
        if (detailToggle == null || colorToggle == null) {
            return null;
        }
        Integer spinnerValue = colorCountSpinner.getValue();
        int count = spinnerValue == null ? ConversionSettings.DEFAULT_CUSTOM_COLORS : spinnerValue;
        count = Math.max(ConversionSettings.MIN_CUSTOM_COLORS, Math.min(item.getMaxCustomColors(), count));
        return new ConversionSettings((DetailLevel) detailToggle.getUserData(),
                (ColorMode) colorToggle.getUserData(), count, removeBackgroundCheck.isSelected());
    }

    /** "Apply to all": copies the settings to every other analysed image (their results become stale). */
    private void propagateSettings(ImageItem source) {
        ConversionSettings settings = source.getSettings();
        for (ImageItem other : queue.items()) {
            if (other != source && other.getOriginalWidth() > 0 && other.getStatus() != ImageItem.Status.FAILED) {
                other.setSettings(settings.clampedTo(other.getMaxCustomColors()));
            }
        }
    }

    // =====================================================================
    // View refresh
    // =====================================================================

    private void refreshView() {
        ImageItem item = queue.getCurrent();
        if (item == null) {
            preview.clear();
            shownItem = null;
            shownOriginal = null;
            originalCaption.setText("");
            vectorCaption.setText("");
            setShown(vectorOverlay, false);
            return;
        }

        Image original = item.getOriginalImage();
        boolean newContent = item != shownItem || original != shownOriginal;
        preview.setContent(original, item.getVectorPreview(),
                item.getOriginalWidth(), item.getOriginalHeight(), newContent);
        shownItem = item;
        shownOriginal = original;

        originalCaption.setText(item.getOriginalWidth() > 0
                ? item.getDisplayName() + "  (" + item.getOriginalWidth() + " x " + item.getOriginalHeight() + " px)"
                : item.getDisplayName());
        vectorCaption.setText(describeResult(item));

        ImageItem.Status status = item.getStatus();
        boolean working = status == ImageItem.Status.PROCESSING || status == ImageItem.Status.QUEUED;
        String error = item.getErrorMessage();
        vectorOverlay.getStyleClass().remove("overlay-error");
        if (working) {
            setShown(vectorOverlay, true);
            setShown(overlayIndicator, true);
            overlayLabel.setText(item.getVectorPreview() == null ? "Preprocessing…" : "Updating preview…");
        } else if (status == ImageItem.Status.FAILED || error != null) {
            setShown(vectorOverlay, true);
            setShown(overlayIndicator, false);
            overlayLabel.setText(error == null ? "This image could not be processed." : error);
            vectorOverlay.getStyleClass().add("overlay-error");
        } else {
            setShown(vectorOverlay, false);
        }
    }

    private static String describeResult(ImageItem item) {
        ConversionSettings s = item.resultSettingsProperty().get();
        if (item.getSvgContent() == null || s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.detail().displayName()).append(" detail, ");
        sb.append(s.usesCustomColors()
                ? s.customColorCount() + (s.customColorCount() == 1 ? " color" : " colors")
                : "Unlimited colors");
        if (s.removeBackground()) {
            sb.append(", transparent background");
        }
        if (!item.isResultCurrent()) {
            sb.append("  (updating…)");
        }
        return sb.toString();
    }

    // =====================================================================
    // Top panel + navigation handlers
    // =====================================================================

    @FXML private void onZoomIn() { preview.zoomIn(); }
    @FXML private void onZoomOut() { preview.zoomOut(); }
    @FXML private void onZoomFit() { preview.zoomToFit(); }
    @FXML private void onBack() { queue.previous(); }
    @FXML private void onNext() { queue.next(); }

    @FXML
    private void onNewBatch() {
        cancelAllWork();
        navigator.showDropZone();
    }

    // =====================================================================
    // Export
    // =====================================================================

    @FXML
    private void onExportAll() {
        if (busy.get() || queue.isEmpty()) {
            return;
        }
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose the output folder");
        if (lastExportDir != null && lastExportDir.isDirectory()) {
            chooser.setInitialDirectory(lastExportDir);
        }
        File directory = chooser.showDialog(window());
        if (directory == null) {
            return;
        }
        lastExportDir = directory;
        Path outputDir = directory.toPath();

        List<ImageItem> exportable = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (ImageItem item : queue.items()) {
            if (item.getOriginalWidth() > 0 && item.getStatus() != ImageItem.Status.FAILED) {
                exportable.add(item);
            } else {
                String reason = item.getErrorMessage() != null ? item.getErrorMessage() : "not processed";
                skipped.add(item.getDisplayName() + ": " + reason);
            }
        }
        if (exportable.isEmpty()) {
            AlertUtil.error(window(), "Nothing to export", "None of the images could be processed.");
            return;
        }

        List<String> names = new ArrayList<>();
        for (ImageItem item : exportable) {
            names.add(item.getDisplayName());
        }
        List<Path> targets = context.exportService().planOutputPaths(names, outputDir, true);
        int existing = 0;
        for (Path target : targets) {
            if (Files.exists(target)) {
                existing++;
            }
        }
        if (existing > 0) {
            AlertUtil.OverwriteChoice choice = AlertUtil.confirmOverwrite(window(), existing, outputDir);
            if (choice == AlertUtil.OverwriteChoice.CANCEL) {
                return;
            }
            if (choice == AlertUtil.OverwriteChoice.KEEP_BOTH) {
                targets = context.exportService().planOutputPaths(names, outputDir, false);
            }
        }

        // A pending preview is irrelevant now: the export re-traces whatever is out of date.
        context.previewScheduler().cancelCurrent();
        if (previewItem != null) {
            restoreStatus(previewItem);
            previewItem = null;
        }

        List<BatchExportTask.Job> jobs = new ArrayList<>();
        for (int i = 0; i < exportable.size(); i++) {
            ImageItem item = exportable.get(i);
            jobs.add(new BatchExportTask.Job(item.getSourcePath(), item.getDisplayName(), item.getSettings(),
                    item.isResultCurrent() ? item.getSvgContent() : null, targets.get(i)));
        }
        skippedOnExport = skipped;

        BatchExportTask task = new BatchExportTask(context.pipeline(), context.exportService(), jobs, outputDir);
        exportTask = task;
        beginWork(task, task.getDecision());
        setShown(cancelButton, true);

        task.setOnSucceeded(e -> {
            exportTask = null;
            setShown(cancelButton, false);
            BatchExportTask.Summary summary = task.getValue();
            endWork("Export finished  •  " + summary.succeeded() + " / " + summary.total() + " saved");
            showExportSummary(summary);
            for (ImageItem item : queue.items()) {
                ensureCurrentOnly(item);
            }
        });
        task.setOnFailed(e -> {
            exportTask = null;
            setShown(cancelButton, false);
            endWork("Export failed");
            Throwable error = task.getException();
            if (error instanceof ToolNotFoundException) {
                AlertUtil.error(window(), "Potrace was not found", error.getMessage());
            } else {
                AlertUtil.error(window(), "Export failed", ErrorMessages.describe(error));
            }
        });
        task.setOnCancelled(e -> {
            exportTask = null;
            setShown(cancelButton, false);
            endWork("Export cancelled (files already written were kept)");
        });
        context.background().execute(task);
    }

    private void ensureCurrentOnly(ImageItem item) {
        if (item == queue.getCurrent()) {
            ensurePreview(item, 0);
        }
    }

    private void showExportSummary(BatchExportTask.Summary summary) {
        StringBuilder message = new StringBuilder();
        message.append(summary.succeeded()).append(" of ").append(summary.total())
               .append(summary.total() == 1 ? " file was saved to:\n" : " files were saved to:\n")
               .append(summary.outputDir());
        message.append("\n\nMode: ").append(summary.decision().isParallel()
                ? "parallel (" + summary.decision().threadCount() + " threads)" : "sequential");
        List<String> problems = new ArrayList<>(summary.failures());
        problems.addAll(skippedOnExport);
        if (problems.isEmpty()) {
            AlertUtil.info(window(), "Export complete", message.toString());
        } else {
            message.append("\n\nNot exported:\n").append(String.join("\n", problems));
            AlertUtil.warning(window(), "Export finished with problems", message.toString());
        }
    }

    @FXML
    private void onCancelExport() {
        if (exportTask != null) {
            exportTask.cancel(true);
        }
    }

    // =====================================================================
    // Progress / busy state
    // =====================================================================

    private void beginWork(Task<?> task, ExecutionPolicy.Decision decision) {
        busy.set(true);
        progressBar.progressProperty().bind(task.progressProperty());
        statusLabel.textProperty().bind(task.messageProperty());
        setShown(progressBar, true);
        modeLabel.setText(decision.isParallel() ? "Parallel × " + decision.threadCount() : "Sequential");
        modeLabel.setTooltip(new Tooltip(decision.reason()));
        modeLabel.getStyleClass().remove("mode-parallel");
        if (decision.isParallel()) {
            modeLabel.getStyleClass().add("mode-parallel");
        }
        setShown(modeLabel, true);
    }

    private void endWork(String finalMessage) {
        progressBar.progressProperty().unbind();
        statusLabel.textProperty().unbind();
        setShown(progressBar, false);
        setShown(modeLabel, false);
        statusLabel.setText(finalMessage);
        busy.set(false);
    }

    // =====================================================================
    // Lifecycle
    // =====================================================================

    private void cancelAllWork() {
        if (preprocessTask != null) {
            preprocessTask.cancel(true);
        }
        if (exportTask != null) {
            exportTask.cancel(true);
        }
        context.previewScheduler().cancelCurrent();
        previewItem = null;
    }

    /** Called by MainApp before this screen is replaced: stops all work and removes global listeners. */
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        cancelAllWork();
        queue.currentProperty().removeListener(currentListener);
        if (observedItem != null) {
            detach(observedItem);
            observedItem = null;
        }
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private Window window() {
        return root.getScene() == null ? null : root.getScene().getWindow();
    }

    private static void setShown(Node node, boolean shown) {
        node.setVisible(shown);
        node.setManaged(shown);
    }
}
