package se233.project1;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import se233.project1.controller.DropZoneController;
import se233.project1.controller.EditorController;
import se233.project1.controller.Navigator;
import se233.project1.model.ImageItem;
import se233.project1.util.AlertUtil;
import se233.project1.util.ErrorMessages;
import se233.project1.util.TempFileManager;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * JavaFX application: owns the Stage and swaps the scene root between the drop zone and the editor.
 * On exit it stops background work and deletes the temp directory (extracted zips, Potrace scratch files).
 */
public class MainApp extends Application implements Navigator {

    private Stage stage;
    private Scene scene;
    private AppContext context;
    private EditorController editorController;

    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage;
        this.context = new AppContext();

        // ปรับขนาดเริ่มต้นลงมาเป็น 1080 x 650 ให้พอดีกับจอและ Display Scaling
        scene = new Scene(new StackPane(), 1080, 650);
        URL css = MainApp.class.getResource("css/app.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }

        stage.setTitle("SE233 Vector Converter");
        stage.setMinWidth(850);
        stage.setMinHeight(550);
        stage.setScene(scene);
        showDropZone();
        stage.show();
    }

    @Override
    public void showDropZone() {
        disposeEditor();
        context.queue().clear();
        try {
            FXMLLoader loader = newLoader("view/drop-zone.fxml");
            Parent view = loader.load();
            scene.setRoot(view);
        } catch (IOException | RuntimeException e) {
            fatal(e);
        }
    }

    @Override
    public void showEditor(List<Path> images) {
        disposeEditor();
        List<ImageItem> items = new ArrayList<>();
        for (Path image : images) {
            items.add(new ImageItem(image));
        }
        context.queue().setAll(items);
        try {
            FXMLLoader loader = newLoader("view/editor.fxml");
            Parent view = loader.load();
            editorController = loader.getController();
            scene.setRoot(view);
            editorController.begin();
        } catch (IOException | RuntimeException e) {
            fatal(e);
        }
    }

    @Override
    public void stop() {
        disposeEditor();
        if (context != null) {
            context.shutdown();
        }
        TempFileManager.cleanup();
    }

    private FXMLLoader newLoader(String resource) {
        URL url = MainApp.class.getResource(resource);
        if (url == null) {
            throw new IllegalStateException("Missing resource: " + resource);
        }
        FXMLLoader loader = new FXMLLoader(url);
        loader.setControllerFactory(this::createController);
        return loader;
    }

    private Object createController(Class<?> type) {
        if (type == DropZoneController.class) {
            return new DropZoneController(context, this);
        }
        if (type == EditorController.class) {
            return new EditorController(context, this);
        }
        try {
            return type.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot create controller " + type.getName(), e);
        }
    }

    private void disposeEditor() {
        if (editorController != null) {
            editorController.dispose();
            editorController = null;
        }
    }

    private void fatal(Throwable error) {
        AlertUtil.error(stage, "The user interface could not be loaded", ErrorMessages.describe(error));
        Platform.runLater(Platform::exit);
    }
}