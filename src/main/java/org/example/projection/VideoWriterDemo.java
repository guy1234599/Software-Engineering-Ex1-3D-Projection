package org.example.projection;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Path;

/** Standalone 3-second video smoke test; NRRD/CSV integration comes next. */
public final class VideoWriterDemo {
    private VideoWriterDemo() { }

    public static void main(String[] args) throws Exception {
        if (args.length > 1) {
            System.err.println("Usage: VideoWriterDemo [output.mp4]");
            System.exit(2);
        }
        Path output = Path.of(args.length == 1 ? args[0] : "target/video-demo.mp4");
        int frames = 90;
        int canvasW = 320;
        int canvasH = 240;
        try (VideoWriter writer = new VideoWriter(output, canvasW, canvasH)) {
            for (int i = 0; i < frames; i++) {
                // Deliberately smaller than canvas: demonstrates black padding,
                // stable frame dimensions, no stretching, and changing content.
                BufferedImage slice = new BufferedImage(240, 120, BufferedImage.TYPE_BYTE_GRAY);
                for (int y = 0; y < slice.getHeight(); y++) {
                    for (int x = 0; x < slice.getWidth(); x++) {
                        int gray = Math.min(255, (x + i * 2) % 256);
                        int rgb = (gray << 16) | (gray << 8) | gray;
                        slice.setRGB(x, y, rgb);
                    }
                }
                Graphics2D g = slice.createGraphics();
                try {
                    g.setColor(Color.WHITE);
                    g.fillOval(10 + (i * 2) % 200, 40, 26, 26);
                } finally {
                    g.dispose();
                }
                writer.writeFrame(FrameComposer.compose(slice, canvasW, canvasH));
            }
            System.out.println("Encoded " + writer.frameCount() + " frames at 30 FPS");
        }
        System.out.println("Created: " + output.toAbsolutePath().normalize());
        System.out.println("Expected video duration: 3 seconds");
    }
}
