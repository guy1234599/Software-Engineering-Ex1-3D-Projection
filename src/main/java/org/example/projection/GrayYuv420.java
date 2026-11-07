package org.example.projection;

import org.jcodec.common.model.ColorSpace;
import org.jcodec.common.model.Picture;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.util.Arrays;
import java.util.Objects;

/**
 * Experimental fast path for monochrome frames. Converts one TYPE_BYTE_GRAY
 * image directly to full-range YUV420J. Uses neutral chroma, so no artificial
 * coloration is introduced. The returned Picture owns its planes and can be
 * handed to SequenceEncoder immediately.
 *
 * IMPORTANT: JCodec Picture planes use 8-bit signed values shifted by -128.
 */
final class GrayYuv420 {
    private GrayYuv420() { }

    static Picture fromGrayImage(BufferedImage gray, int frameWidth, int frameHeight) {
        Objects.requireNonNull(gray, "gray");
        if (gray.getType() != BufferedImage.TYPE_BYTE_GRAY
                || !(gray.getRaster().getDataBuffer() instanceof DataBufferByte)) {
            throw new IllegalArgumentException("Expected TYPE_BYTE_GRAY image");
        }
        if (frameWidth < 2 || frameHeight < 2 || (frameWidth & 1) != 0 || (frameHeight & 1) != 0
                || gray.getWidth() > frameWidth || gray.getHeight() > frameHeight) {
            throw new IllegalArgumentException("Invalid YUV canvas or grayscale image dimensions");
        }
        // IntensityMapper returns a fresh contiguous TYPE_BYTE_GRAY raster.
        // Reject views with nonstandard backing layout rather than sampling
        // different pixels silently.
        byte[] src = ((DataBufferByte) gray.getRaster().getDataBuffer()).getData();
        int srcWidth = gray.getWidth();
        int srcHeight = gray.getHeight();
        if (src.length != srcWidth * srcHeight) {
            throw new IllegalArgumentException("Only contiguous grayscale images are supported");
        }

        Picture out = Picture.create(frameWidth, frameHeight, ColorSpace.YUV420J);
        byte[][] planes = out.getData();
        byte[] luma = planes[0];
        // Neutral chroma (U = V = 128), represented internally by signed 0.
        // JCodec freshly initializes the planes to zero, but fill explicitly
        // so that this contract is not sensitive to allocation strategy.
        Arrays.fill(planes[1], (byte) 0);
        Arrays.fill(planes[2], (byte) 0);
        // Outside the original slice should be black (Y = 0 -> signed -128).
        Arrays.fill(luma, (byte) -128);
        int startX = (frameWidth - srcWidth) / 2;
        int startY = (frameHeight - srcHeight) / 2;
        for (int y = 0; y < srcHeight; y++) {
            int destStart = (startY + y) * frameWidth + startX;
            int srcStart = y * srcWidth;
            for (int x = 0; x < srcWidth; x++) {
                luma[destStart + x] = (byte) ((src[srcStart + x] & 0xff) - 128);
            }
        }
        return out;
    }

}
