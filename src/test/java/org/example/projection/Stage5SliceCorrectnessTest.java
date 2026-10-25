package org.example.projection;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Independent, hand-calculated acceptance examples for the pixel-center
 * projection specification in ALGORITHM.md. Does not use PlaneSampler to
 * manufacture its expected results.
 */
class Stage5SliceCorrectnessTest {
    private final PlaneSampler projector = new PlaneSampler(new NearestNeighborSampler());

    @Test void triangleInXYPlaneMatchesHandCalculatedPixels() {
        // Formula: M(x,y,z) = x + 10*y + 100*z, with x changing fastest.
        Image2D slice = projector.project(numberedVolume(), new TrianglePlane(
                p(2,0,0), p(0,4,0), p(4,4,0)));
        assertPixels(slice, new long[][]{
                {0, 0, 0, 0},
                {0,12,13, 0},
                {0,22,23, 0},
                {31,32,33,34},
                {41,42,43,44}
        });
    }

    @Test void trapezoidInXYPlaneMatchesHandCalculatedPixels() {
        Image2D slice = projector.project(numberedVolume(), new TrapezoidPlane(
                p(1,0,0), p(3,0,0), p(0,4,0), p(4,4,0)));
        assertPixels(slice, new long[][]{
                {0, 2, 3, 0},
                {0,12,13, 0},
                {21,22,23,24},
                {31,32,33,34},
                {41,42,43,44}
        });
    }

    @Test void triangleInXZPlaneSamplesTheCorrectAxis() {
        // y=3 is constant; the vertical image coordinate traverses z.
        // This catches an accidental assumption that every plane lies in XY.
        Image2D slice = projector.project(numberedVolume(), new TrianglePlane(
                p(2,3,0), p(0,3,4), p(4,3,4)));
        assertPixels(slice, new long[][]{
                {0,  0,  0,  0},
                {0,132,133,  0},
                {0,232,233,  0},
                {331,332,333,334},
                {431,432,433,434}
        });
    }

    @Test void translationAlongZChangesOnlyNonBackgroundVoxelValues() {
        Image2D slice = projector.project(numberedVolume(), new TrianglePlane(
                p(2,0,3), p(0,4,3), p(4,4,3)));
        assertPixels(slice, new long[][]{
                {0,  0,  0,  0},
                {0,312,313,  0},
                {0,322,323,  0},
                {331,332,333,334},
                {341,342,343,344}
        });
    }

    @Test void slantedTrapezoidSamplesMultipleZLayers() {
        // Upper base lies on z=0; lower base lies on z=2.
        // Legs have equal length sqrt(21); image size is 4x5.
        // Expected nearest-neighbor layers are hand-derived from
        // the perpendicular direction (0,4,2)/sqrt(20).
        Image2D slice = projector.project(numberedVolume(), new TrapezoidPlane(
                p(1,0,0), p(3,0,0), p(0,4,2), p(4,4,2)));
        assertPixels(slice, new long[][]{
                {0,   2,   3, 0},
                {0, 112, 113, 0},
                {121,122,123,124},
                {131,132,133,134},
                {241,242,243,244}
        });
    }

    @Test void aPlaneOutsideTheVolumeProducesOnlyZeroSamples() {
        Image2D slice = projector.project(numberedVolume(), new TrianglePlane(
                p(102,0,0), p(100,4,0), p(104,4,0)));
        assertEquals(4, slice.width());
        assertEquals(5, slice.height());
        for (int y = 0; y < slice.height(); y++) {
            for (int x = 0; x < slice.width(); x++) {
                assertEquals(0L, slice.getUnsigned(x,y), "out-of-volume pixel at " + x + "," + y);
            }
        }
    }

    @Test void verySmallValidTriangleStillProducesAnImage() {
        Image2D slice = projector.project(numberedVolume(), new TrianglePlane(
                p(0.5,0,0), p(0,0.5,0), p(1,0.5,0)));
        assertEquals(1, slice.width());
        assertEquals(1, slice.height());
    }

    @Test void nearestNeighborTieBreakingAndHalfOpenBoundsAreExplicit() {
        Volume volume = new ByteVolume(3,1,1, new byte[]{10,20,30});
        NearestNeighborSampler nn = new NearestNeighborSampler();
        assertEquals(10L, nn.sample(volume, 0.49,0,0));
        assertEquals(20L, nn.sample(volume, 0.5,0,0));
        assertEquals(20L, nn.sample(volume, 1.49,0,0));
        assertEquals(30L, nn.sample(volume, 1.5,0,0));
        assertEquals(30L, nn.sample(volume, 2.49,0,0));
        assertEquals(0L, nn.sample(volume, 2.5,0,0)); // rounds to x=3, invalid
        assertEquals(0L, nn.sample(volume, 3.0,0,0));
        assertEquals(0L, nn.sample(volume,-0.001,0,0));
        assertEquals(0L, nn.sample(volume,Double.NaN,0,0));
    }

    @Test void unsigned8Unsigned16AndUnsigned32ArePreserved() {
        Volume v8 = new ByteVolume(2,1,1, new byte[]{0,(byte)255});
        Volume v16 = new ShortVolume(2,1,1, new short[]{0,(short)65535});
        Volume v32 = new IntVolume(2,1,1, new int[]{0,-1});
        NearestNeighborSampler nn = new NearestNeighborSampler();
        assertEquals(255L, nn.sample(v8,1,0,0));
        assertEquals(65535L, nn.sample(v16,1,0,0));
        assertEquals(4_294_967_295L, nn.sample(v32,1,0,0));
    }

    @Test void malformedGeometryIsRejectedRatherThanSilentlyRendered() {
        assertThrows(IllegalArgumentException.class, () -> new TrianglePlane(
                p(0,0,0), p(1,1,0), p(2,2,0))); // collinear
        assertThrows(IllegalArgumentException.class, () -> new TrianglePlane(
                p(0,0,0), p(1,3,0), p(4,3,0))); // unequal legs
        assertThrows(IllegalArgumentException.class, () -> new TrapezoidPlane(
                p(1,0,0), p(3,0,0), p(0,4,0), p(4,4,1))); // not coplanar
        assertThrows(IllegalArgumentException.class, () -> new TrapezoidPlane(
                p(1,0,0), p(3,0,0), p(1,0,0), p(3,0,0))); // zero height
        assertThrows(IllegalArgumentException.class, () -> p(Double.NaN,0,0));
    }

    @Test void writingOutsideImageDoesNotSilentlyOverwriteAnotherRow() {
        Image2D image = new Image2D(4, 3);
        image.set(0, 1, 77);
        assertThrows(IndexOutOfBoundsException.class, () -> image.set(4, 0, 99));
        assertThrows(IndexOutOfBoundsException.class, () -> image.set(-1, 1, 99));
        assertThrows(IndexOutOfBoundsException.class, () -> image.set(0, 3, 99));
        assertEquals(77L, image.getUnsigned(0,1), "Wrong-coordinate write must not corrupt valid pixels");
    }

    @Test void blackPaddingCentersTheSliceWithoutRescalingItsPixels() {
        BufferedImage image = new BufferedImage(2,2, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0,0, Color.WHITE.getRGB());
        image.setRGB(1,0, Color.RED.getRGB());
        image.setRGB(0,1, Color.GREEN.getRGB());
        image.setRGB(1,1, Color.BLUE.getRGB());
        BufferedImage frame = FrameComposer.compose(image, 128,128);
        assertEquals(Color.BLACK.getRGB(), frame.getRGB(0,0));
        assertEquals(Color.WHITE.getRGB(), frame.getRGB(63,63));
        assertEquals(Color.RED.getRGB(), frame.getRGB(64,63));
        assertEquals(Color.GREEN.getRGB(), frame.getRGB(63,64));
        assertEquals(Color.BLUE.getRGB(), frame.getRGB(64,64));
        assertEquals(Color.BLACK.getRGB(), frame.getRGB(65,64));
        assertEquals(Color.BLACK.getRGB(), frame.getRGB(64,65));
    }

    private static ShortVolume numberedVolume() {
        short[] pixels = new short[8*8*8];
        for (int z=0;z<8;z++) for (int y=0;y<8;y++) for (int x=0;x<8;x++) {
            pixels[x + 8*(y+8*z)] = (short)(x + 10*y + 100*z);
        }
        return new ShortVolume(8,8,8,pixels);
    }

    private static Point3D p(double x,double y,double z) { return new Point3D(x,y,z); }

    private static void assertPixels(Image2D actual, long[][] expected) {
        assertEquals(expected.length, actual.height(), "slice height");
        for (int y=0;y<expected.length;y++) {
            assertEquals(expected[y].length, actual.width(), "slice width at y=" + y);
            for (int x=0;x<expected[y].length;x++) {
                assertEquals(expected[y][x],actual.getUnsigned(x,y),
                        "slice pixel (x="+x+", y="+y+")");
            }
        }
    }
}
