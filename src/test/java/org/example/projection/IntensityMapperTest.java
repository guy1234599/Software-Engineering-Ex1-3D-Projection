package org.example.projection;

import org.junit.jupiter.api.Test;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import static org.junit.jupiter.api.Assertions.*;

class IntensityMapperTest {
    @Test void mapsSixteenBitValuesWithLinearScaling() {
        IntensityMapper mapper = new IntensityMapper(0, 4000);
        assertEquals(0, mapper.toGray8(0));
        assertEquals(64, mapper.toGray8(1000));
        assertEquals(128, mapper.toGray8(2000));
        assertEquals(191, mapper.toGray8(3000));
        assertEquals(255, mapper.toGray8(4000));
    }

    @Test void clipsSamplesOutsideWindow() {
        IntensityMapper mapper = new IntensityMapper(100, 200);
        assertEquals(0, mapper.toGray8(0));
        assertEquals(0, mapper.toGray8(99));
        assertEquals(255, mapper.toGray8(201));
    }

    @Test void handlesFullUnsigned32Range() {
        IntensityMapper mapper = new IntensityMapper(0, 0xFFFF_FFFFL);
        assertEquals(0, mapper.toGray8(0));
        assertEquals(128, mapper.toGray8(0x8000_0000L));
        assertEquals(255, mapper.toGray8(0xFFFF_FFFFL));
    }

    @Test void handlesConstantZeroAndPositiveValues() {
        assertEquals(0, new IntensityMapper(0, 0).toGray8(0));
        IntensityMapper constant = new IntensityMapper(50, 50);
        assertEquals(0, constant.toGray8(0));
        assertEquals(255, constant.toGray8(50));
    }

    @Test void calculatesOneFixedWindowFromUnsignedByteVolume() {
        Volume volume = new ByteVolume(3, 1, 1, new byte[]{0, 100, (byte) 200});
        IntensityMapper mapper = IntensityMapper.fromVolume(volume);
        assertEquals(0, mapper.minValue());
        assertEquals(200, mapper.maxValue());
        assertEquals(128, mapper.toGray8(100));
    }

    @Test void scansUnsigned32WithoutSignedOverflow() {
        Volume volume = new IntVolume(2, 1, 1, new int[]{1, -1});
        IntensityMapper mapper = IntensityMapper.fromVolume(volume);
        assertEquals(1L, mapper.minValue());
        assertEquals(0xFFFF_FFFFL, mapper.maxValue());
    }

    @Test void convertsImagePixelsInRowMajorOrderWithoutChangingSource() {
        Image2D image = new Image2D(2, 2);
        image.set(0, 0, 0);
        image.set(1, 0, 128);
        image.set(0, 1, 255);
        image.set(1, 1, 64);
        BufferedImage output = new IntensityMapper(0, 255).toBufferedImage(image);
        assertEquals(BufferedImage.TYPE_BYTE_GRAY, output.getType());
        byte[] raw = ((DataBufferByte) output.getRaster().getDataBuffer()).getData();
        assertArrayEquals(new byte[]{0, (byte)128, (byte)255, 64}, raw);
        assertEquals(255L, image.getUnsigned(0, 1));
    }

    @Test void refusesInvalidRangeAndOutOfRangeSample() {
        assertThrows(IllegalArgumentException.class, () -> new IntensityMapper(-1, 255));
        assertThrows(IllegalArgumentException.class, () -> new IntensityMapper(200, 100));
        assertThrows(IllegalArgumentException.class, () -> new IntensityMapper(0, 0x1_0000_0000L));
        assertThrows(IllegalArgumentException.class, () -> new IntensityMapper(0, 255).toGray8(-1));
        assertThrows(IllegalArgumentException.class, () -> new IntensityMapper(0, 255).toGray8(0x1_0000_0000L));
    }
}
