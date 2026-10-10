package se233.project1.service;

import se233.project1.exception.ConversionException;
import se233.project1.model.ConversionSettings;

import java.awt.image.BufferedImage;

/** Converts a raster image to an SVG document. Implementations must be thread-safe. */
public interface TracingService {

    /**
     * @return a complete, standalone SVG document
     * @throws ConversionException on tool / IO / processing failures
     * @throws java.util.concurrent.CancellationException (unchecked) if the calling thread is interrupted
     */
    String trace(BufferedImage source, ConversionSettings settings) throws ConversionException;
}
