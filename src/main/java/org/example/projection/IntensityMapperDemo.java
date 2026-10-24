package org.example.projection;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Standalone smoke demo: no program arguments required. */
public final class IntensityMapperDemo {
    private IntensityMapperDemo() { }

    public static void main(String[] args) throws IOException {
        final int width = 256;
        final int height = 80;
        Image2D image = new Image2D(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.set(x, y, Math.round(x * 65535.0 / (width - 1)));
            }
        }

        IntensityMapper mapper = new IntensityMapper(0, 65535);
        BufferedImage grayscale = mapper.toBufferedImage(image);
        Path output = Path.of("target", "intensity-preview.png");
        Files.createDirectories(output.getParent());
        if (!ImageIO.write(grayscale, "png", output.toFile())) {
            throw new IOException("PNG encoder unavailable");
        }
        System.out.println("IntensityMapper demo OK");
        System.out.println("16-bit: 0 -> " + mapper.toGray8(0)
                + ", 32768 -> " + mapper.toGray8(32768)
                + ", 65535 -> " + mapper.toGray8(65535));
        System.out.println("Created: " + output.toAbsolutePath());
    }
}
