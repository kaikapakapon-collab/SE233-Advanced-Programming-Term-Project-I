package se233.project1.controller;

import java.nio.file.Path;
import java.util.List;

/** Lets controllers switch between the two screens without knowing about the Stage. */
public interface Navigator {

    /** Shows the drag-and-drop screen and clears the image queue. */
    void showDropZone();

    /** Fills the queue with the given image files and shows the editor (which starts preprocessing). */
    void showEditor(List<Path> images);
}
