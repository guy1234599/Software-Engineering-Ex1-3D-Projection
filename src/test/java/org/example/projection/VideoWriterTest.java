package org.example.projection;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jcodec.api.awt.AWTFrameGrab;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class VideoWriterTest {
    @TempDir Path dir;

    @Test void encodesTwoFramesToMp4() throws Exception {
        Path output = dir.resolve("frames.mp4");
        BufferedImage frame = new BufferedImage(128, 128, BufferedImage.TYPE_3BYTE_BGR);
        try (VideoWriter writer = new VideoWriter(output, 128, 128)) {
            java.awt.Graphics2D g = frame.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(16, 16, 64, 64);
            g.dispose();
            writer.writeFrame(frame);
            g = frame.createGraphics();
            g.setColor(Color.BLACK);
            g.fillRect(16, 16, 64, 64);
            g.dispose();
            writer.writeFrame(frame);
            assertEquals(2, writer.frameCount());
            assertFalse(Files.exists(output), "Video should only appear once finalized");
        }
        assertTrue(Files.isRegularFile(output));
        assertTrue(Files.size(output) > 100);
        // More meaningful than file-exists: decoded frames must be accessible.
        BufferedImage decodedFirst = AWTFrameGrab.getFrame(output.toFile(), 0);
        BufferedImage decodedSecond = AWTFrameGrab.getFrame(output.toFile(), 1);
        assertNotNull(decodedFirst);
        assertNotNull(decodedSecond);
        assertEquals(128, decodedFirst.getWidth());
        assertEquals(128, decodedFirst.getHeight());
        int firstPixel = decodedFirst.getRGB(48, 48) & 0xff;
        int secondPixel = decodedSecond.getRGB(48, 48) & 0xff;
        assertTrue(firstPixel > secondPixel + 40, "Expected a visually changing frame");
    }


    @Test
    void rejectsWrongFrameSize() throws Exception {
        Path output = dir.resolve("wrongsize.mp4");

        try (VideoWriter writer = new VideoWriter(output, 128, 128)) {

            // Verify that an invalid frame is rejected
            assertThrows(IllegalArgumentException.class,
                    () -> writer.writeFrame(
                            new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB)
                    )
            );

            // Verify that a valid frame is accepted
            writer.writeFrame(
                    new BufferedImage(128, 128, BufferedImage.TYPE_INT_RGB)
            );

            assertEquals(1, writer.frameCount());
        }

        assertTrue(Files.isRegularFile(output));
    }


    @Test void rejectsOddCanvas() {
        assertThrows(IllegalArgumentException.class,
                () -> new VideoWriter(dir.resolve("odd.mp4"), 127, 128));
    }

    @Test void rejectsTinyH264FrameToProtectWindowsCompatibility() {
        assertThrows(IllegalArgumentException.class,
                () -> new VideoWriter(dir.resolve("tiny.mp4"), 26, 26));
    }

    @Test void noFramesDoesNotPublishOutput() throws Exception {
        Path output = dir.resolve("empty.mp4");
        VideoWriter writer = new VideoWriter(output, 128, 128);
        assertThrows(java.io.IOException.class, writer::close);
        assertFalse(Files.exists(output));
    }
}
