package org.example.projection;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.util.Objects;

/**
 * Converts unsigned voxel intensities (up to 32 bits) to 8-bit grayscale.
 *
 * The min/max window is fixed for the lifetime of this object; construct it
 * once from the source volume to avoid exposure changes between video frames.
 * The original Image2D samples are not modified.
 */
public final class IntensityMapper {
    private static final long MAX_U32 = 0xFFFF_FFFFL;
    private final long minValue;
    private final long maxValue;

    public IntensityMapper(long minValue, long maxValue) {
        if (minValue < 0 || maxValue > MAX_U32 || minValue > maxValue) {
            throw new IllegalArgumentException(
                    "Invalid unsigned 32-bit intensity range: " + minValue + ".." + maxValue);
        }
        this.minValue = minValue;
        this.maxValue = maxValue;
    }

    /** Scans the original 3-D volume once to determine a fixed window. */
    public static IntensityMapper fromVolume(Volume volume) {
        Objects.requireNonNull(volume, "volume");
        int sx = volume.sizeX();
        int sy = volume.sizeY();
        int sz = volume.sizeZ();
        if (sx <= 0 || sy <= 0 || sz <= 0) {
            throw new IllegalArgumentException("Volume dimensions must be positive");
        }
        long min = MAX_U32;
        long max = 0;
        for (int z = 0; z < sz; z++) {
            for (int y = 0; y < sy; y++) {
                for (int x = 0; x < sx; x++) {
                    long value = volume.getVoxel(x, y, z);
                    if (value < 0 || value > MAX_U32) {
                        throw new IllegalArgumentException("Volume contains an invalid unsigned 32-bit value");
                    }
                    if (value < min) min = value;
                    if (value > max) max = value;
                }
            }
        }
        return new IntensityMapper(min, max);
    }

    public long minValue() { return minValue; }
    public long maxValue() { return maxValue; }

    /** Map one unsigned input intensity to a grayscale value in [0, 255]. */
    public int toGray8(long value) {
        if (value < 0 || value > MAX_U32) {
            throw new IllegalArgumentException("Intensity must be unsigned 32-bit: " + value);
        }
        // A constant positive-valued scan appears white; zero is black.
        // This also prevents division by zero when all voxels are identical.
        if (minValue == maxValue) {
            return minValue == 0 || value < minValue ? 0 : 255;
        }
        if (value <= minValue) return 0;
        if (value >= maxValue) return 255;
        return (int) Math.round(255.0 * (value - minValue) / (maxValue - minValue));
    }

    /** Create a grayscale image without changing Image2D or its unsigned data. */
    public BufferedImage toBufferedImage(Image2D image) {
        Objects.requireNonNull(image, "image");
        BufferedImage output = new BufferedImage(
                image.width(), image.height(), BufferedImage.TYPE_BYTE_GRAY);
        byte[] dst = ((DataBufferByte) output.getRaster().getDataBuffer()).getData();
        int i = 0;
        for (int y = 0; y < image.height(); y++) {
            for (int x = 0; x < image.width(); x++) {
                dst[i++] = (byte) toGray8(image.getUnsigned(x, y));
            }
        }
        return output;
    }
}
