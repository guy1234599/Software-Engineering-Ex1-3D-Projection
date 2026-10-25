package org.example.projection;

import org.junit.jupiter.api.Test;
import java.awt.Color;
import java.awt.image.BufferedImage;
import static org.junit.jupiter.api.Assertions.*;

class FrameComposerTest {
    @Test void oddDimensionsRoundUp() {
        assertEquals(2, FrameComposer.evenSize(1));
        assertEquals(6, FrameComposer.evenSize(5));
        assertEquals(6, FrameComposer.evenSize(6));
    }

    @Test void sourceIsCenteredWithoutResizing() {
        BufferedImage source = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        source.setRGB(0, 0, Color.WHITE.getRGB());
        BufferedImage out = FrameComposer.compose(source, 6, 4);
        assertEquals(6, out.getWidth());
        assertEquals(4, out.getHeight());
        assertEquals(Color.BLACK.getRGB(), out.getRGB(0, 0));
        assertEquals(Color.WHITE.getRGB(), out.getRGB(2, 1));
        assertEquals(Color.BLACK.getRGB(), out.getRGB(4, 1));
    }

    @Test void rejectsOversizeSliceAndOddCanvas() {
        BufferedImage input = new BufferedImage(6, 6, BufferedImage.TYPE_INT_RGB);
        assertThrows(IllegalArgumentException.class, () -> FrameComposer.compose(input, 4, 6));
        assertThrows(IllegalArgumentException.class, () -> FrameComposer.compose(input, 7, 6));
    }
}
