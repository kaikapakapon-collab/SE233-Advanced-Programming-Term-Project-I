# SE233 Advanced Programming - Term Project I

## Project Overview

SE233 Vector Converter is a JavaFX desktop application for converting JPG and PNG images into SVG vector files.

The application supports image preview, color configuration, background removal, ZIP handling, batch processing, and SVG export.

---

## Features

- Drag and drop JPG and PNG images
- Import multiple images
- Import images from ZIP archives
- Convert JPG and PNG images to SVG
- Original and vectorized image preview
- Zoom In, Zoom Out, and Zoom to Fit
- Back and Next image navigation
- Detail levels: Low, Medium, High
- Unlimited and Custom color modes
- Custom color selection up to 5 colors
- Default custom color count: 2
- Remove background option
- Apply settings to all images
- Batch SVG export
- Parallel processing under required conditions
- New Batch feature

---

## Technologies

- Java
- JavaFX
- FXML
- CSS
- Maven
- Potrace

### JavaFX
Used to build the desktop graphical user interface.

### FXML
Used to define the structure and layout of the user interface.

### CSS
Used to style the colors, buttons, spacing, and overall appearance.

### Potrace
Used to trace raster images and generate vector paths for SVG output.

---

## How the Conversion Works

1. Load a JPG or PNG image.
2. Analyze the image colors.
3. Reduce or preserve colors based on the selected settings.
4. Create binary masks for image regions.
5. Use Potrace to trace the masks into vector paths.
6. Combine the vector paths into an SVG file.
7. Display the result in the preview.
8. Export the SVG file.

---

## Parallel Processing

Parallel processing is used when:

- Multiple images are loaded
- Detail level is set to Medium
- Color mode is set to Custom
- The number of custom colors is within the supported range

Background tasks are used to keep the JavaFX user interface responsive during heavy processing.

---

## Important Classes

### EditorController
Handles the main editor screen and user interactions.

### ConversionSettings
Stores detail level, color mode, number of colors, and background removal settings.

### ColorQuantizer
Reduces the number of colors in the image.

### PotraceTracingService
Uses Potrace to trace image regions and create SVG output.

### ExecutionPolicy
Determines whether processing should run sequentially or in parallel.

### BatchProcessor
Handles processing of multiple images.

### BatchExportTask
Handles batch SVG exporting.

---

## Error Handling

The application handles errors such as:

- Unsupported image formats
- Corrupted images
- Invalid ZIP archives
- Missing conversion tools
- Conversion errors

---

## Additional Feature

### New Batch

Allows users to clear the current images and start a new conversion batch without restarting the application.

---

## Course

SE233 Advanced Programming  
Term Project I  
Chiang Mai University