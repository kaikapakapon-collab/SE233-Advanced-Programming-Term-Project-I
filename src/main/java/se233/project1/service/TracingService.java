package se233.project1.service;

import th.ac.cmu.se233.vectorizer.exception.ConversionException;
import th.ac.cmu.se233.vectorizer.model.ConversionSettings;

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
