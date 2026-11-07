package org.example.projection;

import org.jcodec.api.awt.AWTSequenceEncoder;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Objects;

/**
 * Writes equally sized RGB frames to an H.264 MP4, in the exact supplied order.
 *
 * Uses a temporary file; only publishes the requested output path when all
 * frames have been encoded and finalized successfully. Must be closed.
 * The caller is responsible for making all frames the same even dimensions
 * and at least MIN_COMPATIBLE_DIMENSION pixels on each axis.
 */
public final class VideoWriter implements AutoCloseable {
    public static final int DEFAULT_FPS = 30;
    private static final long MAX_CANVAS_PIXELS = 25_000_000L;
    // Some Windows H.264 decoders require >=48 pixels on each axis.
    // Use a more conservative floor so standalone callers cannot generate tiny MP4s.
    public static final int MIN_COMPATIBLE_DIMENSION = 128;

    private final Path target;
    private final Path temporary;
    private final AWTSequenceEncoder encoder;
    private final int width;
    private final int height;
    private int frameCount;
    private boolean closed;
    private boolean failed;

    public VideoWriter(Path outputMp4, int width, int height) throws IOException {
        this(outputMp4, width, height, DEFAULT_FPS);
    }

    public VideoWriter(Path outputMp4, int width, int height, int fps) throws IOException {
        Objects.requireNonNull(outputMp4, "outputMp4");
        if (width < MIN_COMPATIBLE_DIMENSION || height < MIN_COMPATIBLE_DIMENSION
                || (width & 1) != 0 || (height & 1) != 0
                || (long) width * height > MAX_CANVAS_PIXELS) {
            throw new IllegalArgumentException("Video dimensions must be even, >= " + MIN_COMPATIBLE_DIMENSION + " and <= "
                    + MAX_CANVAS_PIXELS + " pixels per frame");
        }
        if (fps <= 0 || fps > 240) {
            throw new IllegalArgumentException("Invalid video fps: " + fps);
        }
        Path normalized = outputMp4.toAbsolutePath().normalize();
        if (normalized.getFileName() == null ||
                !normalized.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".mp4")) {
            throw new IllegalArgumentException("Output filename must end with .mp4");
        }
        Path parent = normalized.getParent();
        Files.createDirectories(parent);
        Path temp = Files.createTempFile(parent, "." + normalized.getFileName() + "-", ".part.mp4");
        AWTSequenceEncoder created;
        try {
            created = AWTSequenceEncoder.createSequenceEncoder(temp.toFile(), fps);
        } catch (IOException | RuntimeException error) {
            Files.deleteIfExists(temp);
            throw error;
        }
        this.target = normalized;
        this.temporary = temp;
        this.encoder = created;
        this.width = width;
        this.height = height;
    }

    /** Write exactly one MP4 frame. No frame duplication or interpolation. */
    public void writeFrame(BufferedImage image) throws IOException {
        if (closed || failed) throw new IllegalStateException("VideoWriter is closed or failed");
        Objects.requireNonNull(image, "image");
        if (image.getWidth() != width || image.getHeight() != height) {
            throw new IllegalArgumentException("Frame must be " + width + "x" + height +
                    ", got " + image.getWidth() + "x" + image.getHeight());
        }
        try {
            encoder.encodeImage(image);
            frameCount++;
        } catch (IOException | RuntimeException error) {
            failed = true;
            throw error;
        }
    }

    /**
     * Experimental fast path that avoids creating the RGB BufferedImage canvas.
     * JCodec 0.2.5 SequenceEncoder accepts RGB pictures; it performs the RGB-to-YUV
     * conversion inside the encoder. No claim is made that this is native YUV.
     */
    public void writeRgbPicture(BufferedImage gray) throws IOException {
        if (closed || failed) throw new IllegalStateException("VideoWriter is closed or failed");
        Objects.requireNonNull(gray, "gray");
        org.jcodec.common.model.Picture picture = GrayRgbPicture.fromGrayImage(gray, width, height);
        try {
            encoder.encodeNativeFrame(picture);
            frameCount++;
        } catch (IOException | RuntimeException error) {
            failed = true;
            throw error;
        }
    }

    public int frameCount() {
        return frameCount;
    }

    /**
     * Finalize and publish on success. If encoding failed or there were no
     * frames, remove the temporary file instead of creating a partial output.
     */
    @Override
    public void close() throws IOException {
        if (closed) return;
        closed = true;
        IOException finishError = null;
        try {
            // Calling finish even on failure attempts to release the channel.
            encoder.finish();
        } catch (IOException | RuntimeException ex) {
            finishError = new IOException("Could not finalize MP4", ex);
        }
        if (failed || frameCount == 0 || finishError != null) {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException deleteError) {
                if (finishError == null) finishError = deleteError;
                else finishError.addSuppressed(deleteError);
            }
            if (finishError != null) throw finishError;
            throw new IOException(failed ? "MP4 encoding failed; output was not published"
                    : "Cannot produce a video with zero frames");
        }
        try {
            try {
                Files.move(temporary, target,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            Files.deleteIfExists(temporary);
            throw ex;
        }
    }
}
