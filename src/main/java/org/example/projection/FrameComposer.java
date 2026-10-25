package org.example.projection;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Objects;

/** Places a source image at its native size on a black, even-dimensioned canvas. */
public final class FrameComposer {
    private FrameComposer() { }

    /** Common codecs work reliably with even frame dimensions (e.g. YUV 4:2:0). */
    public static int evenSize(int requested) {
        if (requested <= 0 || requested >= Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Canvas dimension must be in [1, Integer.MAX_VALUE-1]");
        }
        return (requested + 1) & ~1;
    }

    /** No scaling: center the given image and pad with black. */
    public static BufferedImage compose(BufferedImage image, int canvasWidth, int canvasHeight) {
        Objects.requireNonNull(image, "image");
        if (canvasWidth < 2 || canvasHeight < 2 || (canvasWidth & 1) != 0 || (canvasHeight & 1) != 0) {
            throw new IllegalArgumentException("Canvas dimensions must be even and at least 2");
        }
        if (image.getWidth() > canvasWidth || image.getHeight() > canvasHeight) {
            throw new IllegalArgumentException("Slice " + image.getWidth() + "x" + image.getHeight()
                    + " does not fit canvas " + canvasWidth + "x" + canvasHeight);
        }
        BufferedImage result = new BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g = result.createGraphics();
        try {
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, canvasWidth, canvasHeight);
            int left = (canvasWidth - image.getWidth()) / 2;
            int top = (canvasHeight - image.getHeight()) / 2;
            g.drawImage(image, left, top, null);
        } finally {
            g.dispose();
        }
        return result;
    }
}
