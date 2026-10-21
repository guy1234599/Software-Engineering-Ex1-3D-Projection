package org.example.projection;

public final class IntVolume extends ArrayVolume {
    private final int[] data;
    // Ownership is transferred; caller must not mutate data after construction.
    public IntVolume(int sx, int sy, int sz, int[] data) {
        super(sx,sy,sz,java.util.Objects.requireNonNull(data).length);
        this.data = data;
    }
    @Override public long getVoxel(int x, int y, int z) {
        return Integer.toUnsignedLong(data[index(x,y,z)]);
    }
}
