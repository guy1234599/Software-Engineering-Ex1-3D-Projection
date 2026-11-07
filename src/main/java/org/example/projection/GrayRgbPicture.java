package org.example.projection;

import org.jcodec.common.model.ColorSpace;
import org.jcodec.common.model.Picture;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.util.Arrays;
import java.util.Objects;

/**
 * Converts a contiguous TYPE_BYTE_GRAY image directly into a JCodec RGB Picture.
 * This avoids the temporary, full-canvas BufferedImage in FrameComposer and
 * the AWTUtil per-pixel conversion, but the H.264 encoder still converts RGB to YUV.
 * Gray values are copied byte-for-byte (after the signed JCodec offset),
 * matching FrameComposer's rendering of TYPE_BYTE_GRAY on an RGB canvas.
 * JCodec's Picture stores each 8-bit channel value shifted down by 128.
 */
final class GrayRgbPicture {
    private GrayRgbPicture() { }

    static Picture fromGrayImage(BufferedImage gray, int frameWidth, int frameHeight) {
        Objects.requireNonNull(gray, "gray");
        if (gray.getType() != BufferedImage.TYPE_BYTE_GRAY
                || !(gray.getRaster().getDataBuffer() instanceof DataBufferByte)) {
            throw new IllegalArgumentException("Expected TYPE_BYTE_GRAY image");
        }
        if (frameWidth < 2 || frameHeight < 2 || (frameWidth & 1) != 0 || (frameHeight & 1) != 0
                || gray.getWidth() > frameWidth || gray.getHeight() > frameHeight) {
            throw new IllegalArgumentException("Invalid RGB canvas or grayscale image dimensions");
        }
        byte[] source = ((DataBufferByte) gray.getRaster().getDataBuffer()).getData();
        int srcW = gray.getWidth();
        int srcH = gray.getHeight();
        // Frame from IntensityMapper is a contiguous, zero-offset raster.
        if (gray.getRaster().getMinX() != 0 || gray.getRaster().getMinY() != 0
                || gray.getRaster().getSampleModelTranslateX() != 0
                || gray.getRaster().getSampleModelTranslateY() != 0
                || source.length != srcW * srcH) {
            throw new IllegalArgumentException("Only fresh, contiguous grayscale images are supported");
        }

        Picture picture = Picture.create(frameWidth, frameHeight, ColorSpace.RGB);
        byte[] rgb = picture.getData()[0]; // interleaved R,G,B
        // A black pixel is RGB(0,0,0) => signed -128 in each channel.
        Arrays.fill(rgb, (byte) -128);
        int left = (frameWidth - srcW) / 2;
        int top = (frameHeight - srcH) / 2;
        for (int y = 0; y < srcH; y++) {
            int srcOffset = y * srcW;
            int dstOffset = ((top + y) * frameWidth + left) * 3;
            for (int x = 0; x < srcW; x++) {
                byte value = (byte) ((source[srcOffset + x] & 0xff) - 128);
                int pos = dstOffset + x * 3;
                rgb[pos] = value;
                rgb[pos + 1] = value;
                rgb[pos + 2] = value;
            }
        }
        return picture;
    }
}
