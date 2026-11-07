package org.example.projection;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jcodec.api.awt.AWTFrameGrab;
import org.jcodec.common.model.ColorSpace;
import org.jcodec.common.model.Picture;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class Stage6DirectRgbTest {
    @TempDir Path dir;

    @Test void rgbPictureAgreesWithLegacyCanvasAtEveryPixel() {
        BufferedImage gray = new BufferedImage(7, 3, BufferedImage.TYPE_BYTE_GRAY);
        byte[] raw = ((DataBufferByte) gray.getRaster().getDataBuffer()).getData();
        for (int i = 0; i < raw.length; i++) raw[i] = (byte) (i * 12);

        Picture pic = GrayRgbPicture.fromGrayImage(gray, 128, 130);
        BufferedImage legacy = FrameComposer.compose(gray, 128, 130);
        assertEquals(ColorSpace.RGB, pic.getColor());
        assertEquals(128, pic.getWidth());
        assertEquals(130, pic.getHeight());
        byte[] rgb = pic.getData()[0];
        assertEquals(128 * 130 * 3, rgb.length);
        for (int y = 0; y < 130; y++) {
            for (int x = 0; x < 128; x++) {
                int expected = legacy.getRGB(x, y);
                int pos = (y * 128 + x) * 3;
                assertEquals((expected >>> 16) & 0xff, ((rgb[pos] + 128) & 0xff), "red at " + x + "," + y);
                assertEquals((expected >>> 8) & 0xff, ((rgb[pos + 1] + 128) & 0xff), "green at " + x + "," + y);
                assertEquals(expected & 0xff, ((rgb[pos + 2] + 128) & 0xff), "blue at " + x + "," + y);
            }
        }
    }

    @Test void rejectsOtherImageTypes() {
        BufferedImage wrong = new BufferedImage(128, 128, BufferedImage.TYPE_INT_RGB);
        assertThrows(IllegalArgumentException.class, () -> GrayRgbPicture.fromGrayImage(wrong, 128, 128));
    }

    @Test void producesDecodableTwoFrameMp4WithExperimentalRgbInput() throws Exception {
        Path output = dir.resolve("rgb-direct.mp4");
        BufferedImage gray = new BufferedImage(128, 128, BufferedImage.TYPE_BYTE_GRAY);
        byte[] samples = ((DataBufferByte) gray.getRaster().getDataBuffer()).getData();
        try (VideoWriter writer = new VideoWriter(output, 128, 128)) {
            Arrays.fill(samples, (byte) 225);
            writer.writeRgbPicture(gray);
            Arrays.fill(samples, (byte) 25);
            writer.writeRgbPicture(gray);
            assertEquals(2, writer.frameCount());
        }
        assertTrue(Files.isRegularFile(output));
        assertTrue(Files.size(output) > 100);
        BufferedImage frame0 = AWTFrameGrab.getFrame(output.toFile(), 0);
        BufferedImage frame1 = AWTFrameGrab.getFrame(output.toFile(), 1);
        assertNotNull(frame0);
        assertNotNull(frame1);
        assertEquals(128, frame0.getWidth());
        int bright = frame0.getRGB(64, 64) & 0xff;
        int dark = frame1.getRGB(64, 64) & 0xff;
        assertTrue(bright > dark + 80, "Expected visibly different grayscale frames");
        int color = frame0.getRGB(64, 64);
        assertTrue(Math.abs(((color >>> 16) & 0xff) - (color & 0xff)) < 20, "Unexpected color tint");
    }
}
