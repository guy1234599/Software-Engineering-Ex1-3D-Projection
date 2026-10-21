package org.example.projection;

public final class ShortVolume extends ArrayVolume {
    private final short[] data;
    // Ownership is transferred; caller must not mutate data after construction.
    public ShortVolume(int sx, int sy, int sz, short[] data) {
        super(sx,sy,sz,java.util.Objects.requireNonNull(data).length);
        this.data = data;
    }
    @Override public long getVoxel(int x, int y, int z) {
        return Short.toUnsignedInt(data[index(x,y,z)]);
    }
}
